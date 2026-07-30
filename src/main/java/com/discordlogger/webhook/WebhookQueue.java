package com.discordlogger.webhook;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ArrayBlockingQueue;
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
 *   <li><b>One worker thread</b> — also the ordering guarantee. Logs are a
 *       narrative; delivering them out of order is its own bug.</li>
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

    private static final BlockingQueue<Message> QUEUE = new ArrayBlockingQueue<>(CAPACITY);

    private static volatile JavaPlugin plugin;
    private static volatile Thread worker;
    private static volatile boolean running;

    /** Epoch millis before which the next request must not be sent. */
    private static volatile long nextSendAt = 0L;

    /** Warn once per outage rather than once per dropped message. */
    private static volatile boolean warnedFull = false;

    private WebhookQueue() {}

    private record Message(String url, String json) {}

    /** Start the worker. Safe to call again on reload; a second call is ignored. */
    public static synchronized void start(JavaPlugin pl) {
        plugin = pl;
        if (running && worker != null && worker.isAlive()) return;

        running = true;
        worker = new Thread(WebhookQueue::runLoop, "DiscordLogger-Webhook");
        // Not a daemon: a queued message should still get its chance if the JVM
        // is winding down. shutdown() bounds how long that can take.
        worker.setDaemon(false);
        worker.start();
    }

    /** Queue a payload. Never blocks the caller — the main thread must not wait on Discord. */
    public static void enqueue(String url, String json) {
        if (url == null || url.isBlank() || json == null) return;

        if (!QUEUE.offer(new Message(url, json))) {
            if (!warnedFull) {
                warnedFull = true;
                log().warning("[DiscordWebhook] Send queue is full (" + CAPACITY + " pending) — "
                        + "dropping messages until it drains. Discord may be unreachable, "
                        + "or this server is logging faster than the webhook allows.");
            }
            return;
        }
        warnedFull = false;
    }

    /**
     * Stop accepting work and drain what's left, bounded by {@link #SHUTDOWN_DRAIN_MS}.
     * Called from {@code onDisable} after the server-stop message has been queued.
     */
    public static void shutdown() {
        running = false;

        final Thread w = worker;
        if (w == null) return;

        // Let the worker finish the backlog on its own; it exits once drained.
        try {
            w.join(SHUTDOWN_DRAIN_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (w.isAlive()) {
            final int left = QUEUE.size();
            if (left > 0) {
                log().warning("[DiscordWebhook] Shutting down with " + left
                        + " message(s) still queued — they will not be delivered.");
            }
            w.interrupt();
        }
        worker = null;
    }

    // -------------------------------------------------------------------------

    private static void runLoop() {
        while (true) {
            final Message msg;
            try {
                if (running) {
                    // Poll rather than take() so a stopped queue can notice and exit.
                    msg = QUEUE.poll(250, TimeUnit.MILLISECONDS);
                    if (msg == null) continue;
                } else {
                    // Draining: leave as soon as the backlog is clear.
                    msg = QUEUE.poll();
                    if (msg == null) return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            try {
                deliver(msg);
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
    private static void deliver(Message msg) throws InterruptedException {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            waitUntilAllowed();

            final DiscordWebhook.Response res = DiscordWebhook.post(msg.url(), msg.json());

            if (res.rateLimited()) {
                // Told to back off explicitly — wait it out and retry the SAME message
                // without consuming an attempt, since nothing was wrong with it.
                final long waitMs = clampWait(res.retryAfterMs());
                log().warning("[DiscordWebhook] Rate limited by Discord — retrying in "
                        + waitMs + "ms (" + QUEUE.size() + " queued).");
                sleep(waitMs);
                attempt--;
                continue;
            }

            if (res.success()) {
                applyRateLimitHints(res);
                return;
            }

            if (res.retryable()) {
                if (attempt < MAX_ATTEMPTS) {
                    final long backoff = clampWait(500L * (1L << (attempt - 1))); // 0.5s, 1s, 2s…
                    sleep(backoff);
                    continue;
                }
                log().warning("[DiscordWebhook] Giving up on a message after " + MAX_ATTEMPTS
                        + " attempts (last status " + res.status() + ").");
                return;
            }

            // 4xx that isn't 429: bad/deleted webhook, malformed payload — retrying
            // can't help, so say something actionable and move on.
            log().warning("[DiscordWebhook] Discord rejected a message with HTTP " + res.status()
                    + (res.status() == 404
                        ? " — the webhook URL no longer exists. Check webhook.url in config.yml."
                        : " — not retrying."));
            return;
        }
    }

    /** Respect the pacing derived from the previous response's rate-limit headers. */
    private static void waitUntilAllowed() throws InterruptedException {
        final long delay = nextSendAt - System.currentTimeMillis();
        if (delay > 0) sleep(Math.min(delay, MAX_WAIT_MS));
    }

    /**
     * If Discord says this window's budget is spent, hold the next send until it resets.
     * This is what keeps us from earning a 429 in the first place.
     */
    private static void applyRateLimitHints(DiscordWebhook.Response res) {
        if (res.remaining() != null && res.remaining() <= 0 && res.resetAfterMs() != null) {
            nextSendAt = System.currentTimeMillis() + clampWait(res.resetAfterMs());
        } else {
            nextSendAt = 0L;
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
