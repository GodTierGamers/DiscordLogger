package com.discordlogger.webhook;

import com.discordlogger.alert.OpAlert;
import com.discordlogger.metrics.Counters;
import com.discordlogger.util.Strings;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
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
 *   <li><b>Batched sends.</b> A webhook sustains roughly 30 requests a minute, and
 *       Discord accepts ten embeds in one request — so a request carrying one event
 *       wastes nine tenths of the budget. The worker sends whatever is <i>already</i>
 *       waiting as a single message, which is what turns that cap into ~300 events a
 *       minute. bStats recorded 3.47 million messages dropped past the queue cap, every
 *       one of those polls alongside a rate-limit wait and most with no failed send at
 *       all: the ceiling, not an outage.</li>
 * </ul>
 */
public final class WebhookQueue {

    /** Enough to absorb a long burst; small enough that a dead webhook can't exhaust memory. */
    private static final int CAPACITY = 1000;

    /**
     * Suppresses every mention in every message this plugin sends.
     *
     * <p>Embeds do not notify anyone, but plain-text mode posts the same player text
     * as message content, where {@code @everyone} on a sign or in chat pings the whole
     * Discord server. Escaping cannot fix that — an {@code @} is an ordinary character
     * and Markdown has no escape for it — so the payload says outright that nothing in
     * it may resolve to a mention. Discord honours this field for webhooks.
     */
    static final String NO_MENTIONS = "\"allowed_mentions\":{\"parse\":[]}";

    /** Discord's limit on embeds in one message. */
    static final int MAX_EMBEDS_PER_MESSAGE = 10;

    /**
     * Ceiling on the combined embed JSON in one message.
     *
     * <p>Discord allows 6000 characters across a message's embeds, counted over the
     * text they carry. Our JSON is always longer than that text -- keys, braces and
     * escapes -- so bounding the JSON keeps the real figure under the real limit
     * without having to measure the same thing twice.
     */
    static final int MAX_EMBED_JSON_PER_MESSAGE = 5500;

    /**
     * Ceiling on joined plain-text content. Discord's limit is 2000 characters, and a
     * stored body is already escaped, so it is never shorter than what shows.
     */
    static final int MAX_CONTENT_PER_MESSAGE = 1900;

    /**
     * One queued event, and how it may be combined with its neighbours.
     *
     * <p>The queue holds these rather than finished payloads because merging finished
     * payloads would mean taking JSON apart to get the embed back out -- and a
     * description is free to contain {@code ]} or {@code }}, so that parsing is
     * exactly the kind that works until someone dies in a world called {@code a]b}.
     * Building the envelope at send time instead means nothing is ever unparsed.
     */
    static final class Payload {

        /** EMBED and CONTENT merge with their own kind. RAW is sent exactly as given. */
        enum Kind { EMBED, CONTENT, RAW }

        final Kind kind;

        /**
         * An embed object for {@code EMBED}, an escaped content string for
         * {@code CONTENT}, a complete payload for {@code RAW}.
         */
        final String body;

        Payload(Kind kind, String body) {
            this.kind = kind;
            this.body = body;
        }
    }

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
        final BlockingQueue<Payload> queue = new ArrayBlockingQueue<>(CAPACITY);
        final Thread worker;

        /** Epoch millis before which the next request to THIS webhook must not be sent. */
        volatile long nextSendAt = 0L;

        /** Warn once per outage rather than once per dropped message. */
        volatile boolean warnedFull = false;

        /**
         * Set when the config no longer routes anything here.
         *
         * <p>Not a stop signal: the worker finishes what is already queued and then
         * exits. A destination that vanished mid-flight still delivers its backlog.
         */
        volatile boolean retired = false;

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
    /** One destination's live state, for {@code /discordlogger status}. */
    public static final class Health {
        private final String id;
        private final int queued;
        private final int capacity;
        private final long waitMs;

        public Health(String id, int queued, int capacity, long waitMs) {
            this.id = id;
            this.queued = queued;
            this.capacity = capacity;
            this.waitMs = waitMs;
        }

        public String id() { return id; }
        public int queued() { return queued; }
        public int capacity() { return capacity; }
        public long waitMs() { return waitMs; }
    }

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
            // A retired destination is finishing its backlog and then going away.
            // Listing it would show an admin a webhook the config no longer mentions.
            if (d.retired) continue;
            out.add(new Health(shortId(d.url), d.queue.size(), CAPACITY,
                    Math.max(0L, d.nextSendAt - now)));
        }
        out.sort(Comparator.comparing(Health::id));
        return out;
    }

    /**
     * Retire every destination the config no longer routes to.
     *
     * <p>Called on each reload. Destinations are created on first use and were never
     * removed until the plugin disabled, so changing {@code webhook.url} left the old
     * one with a live worker parked in {@code poll(250ms)} and its URL — a credential —
     * held in memory for the rest of the run. Switch URLs a few times and you
     * accumulate idle threads. It also meant {@code /discordlogger status} listed
     * webhooks that no longer existed, which is the part an admin actually sees.
     *
     * <p><b>Retiring is not stopping.</b> The flag lets the worker drain what it holds
     * before it exits, so a message queued a moment before a reload is still delivered.
     *
     * <p>A null set means "could not work it out", and nothing is retired. Keeping an
     * idle thread is a small cost; silently stopping delivery to a live webhook is not.
     */
    public static void retainOnly(Set<String> live) {
        if (live == null) return;
        for (Map.Entry<String, Destination> e : DESTINATIONS.entrySet()) {
            if (!shouldKeep(e.getKey(), live)) e.getValue().retired = true;
        }
    }

    /** Whether a destination is still routed to. Split out so it can be tested. */
    static boolean shouldKeep(String url, Set<String> live) {
        return live == null || live.contains(url);
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

    /**
     * Queue a complete payload, to be sent on its own.
     *
     * <p>Never blocks the caller — the main thread must not wait on Discord. Prefer
     * {@link #enqueueEmbed} or {@link #enqueueContent}, which can share a request with
     * their neighbours; this is the escape hatch for anything that cannot.
     */
    public static void enqueue(String url, String json) {
        offer(url, new Payload(Payload.Kind.RAW, json));
    }

    /** Queue one embed object, which may travel with up to nine others. */
    public static void enqueueEmbed(String url, String embedJson) {
        offer(url, new Payload(Payload.Kind.EMBED, embedJson));
    }

    /** Queue one already-escaped content line, which may travel with others. */
    public static void enqueueContent(String url, String escapedContent) {
        offer(url, new Payload(Payload.Kind.CONTENT, escapedContent));
    }

    private static void offer(String url, Payload payload) {
        if (url == null || Strings.isBlank(url) || payload.body == null) return;

        final Destination dest = DESTINATIONS.computeIfAbsent(url, Destination::new);

        if (!dest.queue.offer(payload)) {
            Counters.dropped();
            if (!dest.warnedFull) {
                dest.warnedFull = true;
                log().warning("[DiscordWebhook] Send queue for webhook ..." + shortId(url)
                        + " is full (" + CAPACITY + " pending) — dropping messages until it "
                        + "drains. Discord may be unreachable, or this server is logging "
                        + "faster than that webhook allows.");
                OpAlert.queueFull("..." + shortId(url));
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
            // Unpublish before draining, and do it here rather than in retainOnly, so
            // the order is: nothing new can be queued, THEN what is queued goes out.
            // Removing after the drain would lose anything offered in between; removing
            // from retainOnly would strand a backlog with no worker to send it.
            // computeIfAbsent means a URL used again simply gets a fresh destination.
            if (dest.retired) DESTINATIONS.remove(dest.url, dest);

            // A retired destination behaves exactly like a shutting-down one: deliver
            // the backlog, then stop.
            final boolean draining = !running || dest.retired;

            final Payload first;
            try {
                if (!draining) {
                    // Poll rather than take() so a stopped queue can notice and exit.
                    first = dest.queue.poll(250, TimeUnit.MILLISECONDS);
                    if (first == null) continue;
                } else {
                    // Draining: leave as soon as the backlog is clear.
                    first = dest.queue.poll();
                    if (first == null) return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            try {
                deliver(dest, takeBatch(first, dest.queue));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // A single bad message must never kill the worker.
                log().warning("[DiscordWebhook] Unexpected error delivering message: " + e);
            }
        }
    }

    /**
     * {@code first}, plus however many of its kind are <em>already</em> waiting.
     *
     * <p><b>Nothing is ever held back to fill a batch.</b> A lone event leaves at once,
     * exactly as before; batching only ever consumes a backlog that already exists. A
     * delay window would trade the latency of every quiet server — which is nearly all
     * of them — for a ceiling only the busiest few are hitting.
     *
     * <p>Peek-then-poll is safe because a destination has exactly one worker, so
     * nothing else can take the head between the two calls.
     */
    static List<Payload> takeBatch(Payload first, Queue<Payload> waiting) {
        final List<Payload> batch = new ArrayList<>();
        batch.add(first);
        if (first.kind == Payload.Kind.RAW) return batch;

        final boolean embeds = first.kind == Payload.Kind.EMBED;
        final int limit = embeds ? MAX_EMBEDS_PER_MESSAGE : Integer.MAX_VALUE;
        final int roomFor = embeds ? MAX_EMBED_JSON_PER_MESSAGE : MAX_CONTENT_PER_MESSAGE;
        int size = first.body.length();

        while (batch.size() < limit) {
            final Payload next = waiting.peek();
            // A different kind ends the batch rather than being skipped: stepping over
            // it to find a matching one behind it would deliver them out of order, and
            // ordering is the whole reason there is one worker per destination.
            if (next == null || next.kind != first.kind) break;
            // "+ 1" is the separator this will cost: a comma, or a newline escape.
            if (size + next.body.length() + 1 > roomFor) break;
            size += next.body.length() + 1;
            batch.add(waiting.poll());
        }
        return batch;
    }

    /**
     * The message a batch becomes.
     *
     * <p>A single embed produces the same envelope as a batch of ten, so there is one
     * output shape rather than a special case that only the quiet path exercises.
     */
    static String envelope(List<Payload> batch) {
        final Payload first = batch.get(0);
        if (first.kind == Payload.Kind.RAW) return first.body;

        final StringBuilder sb = new StringBuilder(256 * batch.size());
        if (first.kind == Payload.Kind.EMBED) {
            sb.append("{\"content\":null,\"embeds\":[");
            for (int i = 0; i < batch.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(batch.get(i).body);
            }
            sb.append("],\"attachments\":[],").append(NO_MENTIONS).append("}");
        } else {
            // The bodies are already escaped, so the separator is the two characters
            // that mean a newline inside a JSON string, not an actual newline.
            sb.append("{\"content\":\"");
            for (int i = 0; i < batch.size(); i++) {
                if (i > 0) sb.append("\\n");
                sb.append(batch.get(i).body);
            }
            sb.append("\",").append(NO_MENTIONS).append("}");
        }
        return sb.toString();
    }

    /** Send one batch, retrying transient failures and honouring rate limits. */
    private static void deliver(Destination dest, List<Payload> batch) throws InterruptedException {
        final String json = envelope(batch);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            waitUntilAllowed(dest);

            final DiscordWebhook.Response res = DiscordWebhook.post(dest.url, json);

            if (res.rateLimited()) {
                Counters.rateLimited();
                // Told to back off explicitly — wait it out and retry the SAME message
                // without consuming an attempt, since nothing was wrong with it.
                final long waitMs = clampWait(res.retryAfterMs());
                log().warning("[DiscordWebhook] Rate limited by Discord on webhook ..."
                        + shortId(dest.url) + " — retrying " + batch.size()
                        + " message(s) in " + waitMs + "ms ("
                        + dest.queue.size() + " queued for it).");
                sleep(waitMs);
                attempt--;
                continue;
            }

            if (res.success()) {
                // Per event, not per request: send_rate answers "how busy is this
                // server", and a batch of ten is ten logged events.
                Counters.sent(batch.size());
                applyRateLimitHints(dest, res);
                return;
            }

            if (res.retryable()) {
                if (attempt < MAX_ATTEMPTS) {
                    final long backoff = clampWait(500L * (1L << (attempt - 1))); // 0.5s, 1s, 2s…
                    sleep(backoff);
                    continue;
                }
                for (int i = 0; i < batch.size(); i++) Counters.failed();
                log().warning("[DiscordWebhook] Giving up on " + batch.size()
                        + " message(s) after " + MAX_ATTEMPTS
                        + " attempts (last status " + res.status() + ").");
                return;
            }

            // 4xx that isn't 429: bad/deleted webhook, malformed payload — retrying
            // can't help. But a rejection of a batch says nothing about which embed
            // Discord objected to, and sending them again one at a time costs one
            // wasted request to keep the other nine: without this, batching would
            // widen the blast radius of one over-long description from one lost
            // message to ten. A 404 fails them all again, which is correct — the
            // webhook is gone — and each counts its own outcome, so nothing is
            // tallied twice.
            if (batch.size() > 1) {
                log().warning("[DiscordWebhook] Discord rejected a batch of " + batch.size()
                        + " with HTTP " + res.status() + " — resending them individually to"
                        + " find the one it objected to.");
                for (Payload one : batch) {
                    deliver(dest, java.util.Collections.singletonList(one));
                }
                return;
            }

            Counters.failed();
            if (res.status() == 404) {
                Counters.notFound();
                // The failure that dominates the live metrics, and the one console has
                // demonstrably failed to surface.
                OpAlert.deadWebhook("..." + shortId(dest.url));
            }
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
