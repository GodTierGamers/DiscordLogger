package com.discordlogger.webhook;

import com.discordlogger.metrics.Counters;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Serialises every webhook send through one worker thread so Discord's rate
 * limits are respected instead of discovered.
 *
 * <p>Before this existed, each message was posted on its own async scheduler
 * task: a busy server could fire several at once, Discord answered HTTP 429,
 * and the message was logged as a warning and thrown away. Chat logging on a
 * populated server lost messages routinely and silently.
 *
 * <p>Design notes worth preserving:
 * <ul>
 *   <li><b>One worker per destination</b> — also the ordering guarantee. Logs are
 *       a narrative; delivering them out of order is its own bug. Ordering only
 *       means anything <i>within</i> a channel, though, so destinations are
 *       independent: a channel being throttled must not hold up a different one.
 *       With a single shared worker, one slow or dead webhook stalled every
 *       other, which per-event routing turns from a corner case into the normal
 *       case.</li>
 *   <li><b>Proactive pacing.</b> Discord reports how many requests remain in the
 *       current window ({@code X-RateLimit-Remaining}) and when it resets. When
 *       the budget is spent we wait for the reset rather than earning a 429.</li>
 *   <li><b>Not a Bukkit scheduler task.</b> The worker must keep draining during
 *       {@code onDisable} (the "server stopped" message is queued there), and
 *       scheduler tasks are refused once the plugin is disabled.</li>
 *   <li><b>Bounded queue.</b> If Discord is unreachable, an unbounded queue
 *       would grow until the server dies. Beyond the cap we drop and say so.</li>
 * </ul>
 */
public final class WebhookQueue {

    /** Enough to absorb a long burst; small enough that a dead webhook can't exhaust memory. */
    private static final int CAPACITY = 1000;

    /** Attempts for transient server-side failures (5xx, network) before giving up on a message. */
    private static final int MAX_ATTEMPTS = 4;

    /** Ceiling on any single wait, so a hostile/blank Retry-After can't stall the queue for hours. */
    private static final long MAX_WAIT_MS = 60_000L;

    /** How long {@link #shutdown} will keep draining before abandoning the rest. */
    private static final long SHUTDOWN_DRAIN_MS = 5_000L;

    /**
     * One per distinct webhook URL. Discord's rate limits are per webhook, so the
     * pacing state has to be too — a shared counter would throttle a quiet channel
     * because a busy one spent its budget.
     */
    private static final Map<String, Destination> DESTINATIONS = new ConcurrentHashMap<>();

    private static volatile JavaPlugin plugin;
    private static volatile boolean running;

    private WebhookQueue() {}

    private static final class Destination {
        final String url;
        final BlockingQueue<String> queue = new ArrayBlockingQueue<>(CAPACITY);
        final Thread worker;

        /** Epoch millis before which the next request to THIS webhook must not be sent. */
        volatile long nextSendAt = 0L;

        /** Warn once per outage rather than once per dropped message. */
        volatile boolean warnedFull = false;

        Destination(String url) {
            this.url = url;
            this.worker = new Thread(() -> runLoop(this), "DiscordLogger-Webhook-" + shortId(url));
            // Not a daemon: a queued message should still get its chance if the JVM
            // is winding down. shutdown() bounds how long that can take.
            this.worker.setDaemon(false);
            this.worker.start();
        }
    }

    /**
     * The webhook id, for a readable thread name. Never the token — thread names
     * show up in stack traces and thread dumps, which get pasted into issues.
     */
    private static String shortId(String url) {
        final String[] parts = url.split("/");
        return parts.length >= 2 ? parts[parts.length - 2] : "unknown";
    }

    /** One destination's live state, for {@code /discordlogger status}. */
    public record Health(String id, int queued, int capacity, long waitMs) {}

    /**
     * A snapshot of every destination, for reporting only.
     *
     * <p>Returns the webhook <em>id</em>, never the URL. A status readout is the kind
     * of thing an admin pastes into a support thread, and the URL is a bearer
     * credential — the id is enough to tell two destinations apart and useless to
     * anyone who reads it. Same reasoning as the worker thread names.
     */
    public static List<Health> health() {
        final long now = System.currentTimeMillis();
        final List<Health> out = new ArrayList<>();
        for (Destination d : DESTINATIONS.values()) {
            out.add(new Health(shortId(d.url), d.queue.size(), CAPACITY,
                    Math.max(0L, d.nextSendAt - now)));
        }
        out.sort(Comparator.comparing(Health::id));
        return out;
    }

    /** Whether the queue is accepting work at all. */
    public static boolean isRunning() {
        return running;
    }

    /**
     * Accept work. Safe to call again on reload.
     *
     * <p>Workers are created per destination on first use rather than here, because
     * which webhooks exist is a config question that can change on reload.
     */
    public static synchronized void start(JavaPlugin pl) {
        plugin = pl;
        running = true;
    }

    /** Queue a payload. Never blocks the caller — the main thread must not wait on Discord. */
    public static void enqueue(String url, String json) {
        if (url == null || url.isBlank() || json == null) return;

        final Destination dest = DESTINATIONS.computeIfAbsent(url, Destination::new);

        if (!dest.queue.offer(json)) {
            Counters.dropped();
            if (!dest.warnedFull) {
                dest.warnedFull = true;
                log().warning("[DiscordWebhook] Send queue for webhook ..." + shortId(url)
                        + " is full (" + CAPACITY + " pending) — dropping messages until it "
                        + "drains. Discord may be unreachable, or this server is logging "
                        + "faster than that webhook allows.");
            }
            return;
        }
        dest.warnedFull = false;
    }

    /**
     * Stop accepting work and drain what's left, bounded by {@link #SHUTDOWN_DRAIN_MS}.
     * Called from {@code onDisable} after the server-stop message has been queued.
     */
    public static void shutdown() {
        running = false;

        // The drain budget is shared, not per destination: destinations drain in
        // parallel, so waiting SHUTDOWN_DRAIN_MS on each in turn would multiply the
        // shutdown delay by however many webhooks are configured.
        final long deadline = System.currentTimeMillis() + SHUTDOWN_DRAIN_MS;
        for (Destination dest : DESTINATIONS.values()) {
            final long left = deadline - System.currentTimeMillis();
            if (left <= 0) break;
            try {
                dest.worker.join(left);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        for (Destination dest : DESTINATIONS.values()) {
            if (dest.worker.isAlive()) {
                final int pending = dest.queue.size();
                if (pending > 0) {
                    log().warning("[DiscordWebhook] Shutting down with " + pending
                            + " message(s) still queued for webhook ..." + shortId(dest.url)
                            + " — they will not be delivered.");
                }
                dest.worker.interrupt();
            }
        }
        DESTINATIONS.clear();
    }

    // -------------------------------------------------------------------------

    private static void runLoop(Destination dest) {
        while (true) {
            final String json;
            try {
                if (running) {
                    // Poll rather than take() so a stopped queue can notice and exit.
                    json = dest.queue.poll(250, TimeUnit.MILLISECONDS);
                    if (json == null) continue;
                } else {
                    // Draining: leave as soon as the backlog is clear.
                    json = dest.queue.poll();
                    if (json == null) return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            try {
                deliver(dest, json);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // A single bad message must never kill the worker.
                log().warning("[DiscordWebhook] Unexpected error delivering message: " + e);
            }
        }
    }

    /** Send one message, retrying transient failures and honouring rate limits. */
    private static void deliver(Destination dest, String json) throws InterruptedException {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            waitUntilAllowed(dest);

            final DiscordWebhook.Response res = DiscordWebhook.post(dest.url, json);

            if (res.rateLimited()) {
                Counters.rateLimited();
                // Told to back off explicitly — wait it out and retry the SAME message
                // without consuming an attempt, since nothing was wrong with it.
                final long waitMs = clampWait(res.retryAfterMs());
                log().warning("[DiscordWebhook] Rate limited by Discord on webhook ..."
                        + shortId(dest.url) + " — retrying in " + waitMs + "ms ("
                        + dest.queue.size() + " queued for it).");
                sleep(waitMs);
                attempt--;
                continue;
            }

            if (res.success()) {
                Counters.sent();
                applyRateLimitHints(dest, res);
                return;
            }

            if (res.retryable()) {
                if (attempt < MAX_ATTEMPTS) {
                    final long backoff = clampWait(500L * (1L << (attempt - 1))); // 0.5s, 1s, 2s…
                    sleep(backoff);
                    continue;
                }
                Counters.failed();
                log().warning("[DiscordWebhook] Giving up on a message after " + MAX_ATTEMPTS
                        + " attempts (last status " + res.status() + ").");
                return;
            }

            // 4xx that isn't 429: bad/deleted webhook, malformed payload — retrying
            // can't help, so say something actionable and move on.
            Counters.failed();
            if (res.status() == 404) Counters.notFound();
            log().warning("[DiscordWebhook] Discord rejected a message with HTTP " + res.status()
                    + (res.status() == 404
                        ? " — webhook ..." + shortId(dest.url) + " no longer exists. Check the"
                          + " webhook URLs in config.yml."
                        : " — not retrying."));
            return;
        }
    }

    /** Respect the pacing derived from the previous response's rate-limit headers. */
    private static void waitUntilAllowed(Destination dest) throws InterruptedException {
        final long delay = dest.nextSendAt - System.currentTimeMillis();
        if (delay > 0) sleep(Math.min(delay, MAX_WAIT_MS));
    }

    /**
     * If Discord says this window's budget is spent, hold the next send until it resets.
     * This is what keeps us from earning a 429 in the first place.
     */
    private static void applyRateLimitHints(Destination dest, DiscordWebhook.Response res) {
        if (res.remaining() != null && res.remaining() <= 0 && res.resetAfterMs() != null) {
            dest.nextSendAt = System.currentTimeMillis() + clampWait(res.resetAfterMs());
        } else {
            dest.nextSendAt = 0L;
        }
    }

    private static long clampWait(long ms) {
        return Math.max(0L, Math.min(ms, MAX_WAIT_MS));
    }

    private static void sleep(long ms) throws InterruptedException {
        if (ms > 0) Thread.sleep(ms);
    }

    private static java.util.logging.Logger log() {
        final JavaPlugin p = plugin;
        return (p != null) ? p.getLogger() : java.util.logging.Logger.getLogger("DiscordLogger");
    }
}
