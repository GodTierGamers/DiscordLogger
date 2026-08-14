package com.discordlogger.metrics;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runtime tallies the metrics charts read, and nothing else reads.
 *
 * <p>Kept separate from {@link PluginMetrics} so the send path has one cheap,
 * dependency-free thing to call. Every counter is an {@link AtomicLong} because
 * {@code WebhookQueue} increments them from its worker threads while bStats reads
 * them from its own scheduler.
 *
 * <h2>Deltas, not totals</h2>
 *
 * <p>Reads are destructive — {@code take*} returns what has accumulated since the
 * last read and resets to zero. bStats plots a line chart by summing each
 * submission across every server, so a running total would double-count itself on
 * every report and draw a curve that only ever climbs.
 *
 * <h2>Why the volume counter is not exposed as a number</h2>
 *
 * <p>Failures, drops and rate limits describe whether the plugin is working, and
 * are reported as counts. Message volume describes how busy someone's server is,
 * which is a fact about their community rather than about this plugin — so it is
 * bucketed before it leaves the process, and the raw figure never appears in a
 * chart. Same information for the decision it informs ("does anyone log fast
 * enough to need batching"), without the per-server detail.
 */
public final class Counters {

    private static final AtomicLong SENT = new AtomicLong();
    private static final AtomicLong FAILED = new AtomicLong();
    private static final AtomicLong DROPPED = new AtomicLong();
    private static final AtomicLong RATE_LIMITED = new AtomicLong();
    private static final AtomicLong NOT_FOUND = new AtomicLong();

    /** Commands used at least once since the last report — presence, never a tally. */
    private static final Set<String> COMMANDS_USED =
            Collections.synchronizedSet(new LinkedHashSet<>());

    private Counters() {}

    public static void sent()        { SENT.incrementAndGet(); }
    public static void failed()      { FAILED.incrementAndGet(); }
    public static void dropped()     { DROPPED.incrementAndGet(); }
    public static void rateLimited() { RATE_LIMITED.incrementAndGet(); }
    public static void notFound()    { NOT_FOUND.incrementAndGet(); }

    /** Record that a subcommand ran. The invoker is never recorded. */
    public static void commandUsed(String name) {
        if (name != null && !name.isBlank()) COMMANDS_USED.add(name);
    }

    // bStats line charts take an Integer; these deltas cannot realistically
    // overflow one between reports, but clamp rather than wrap if they ever did.
    static Integer takeFailedInt()      { return clamp(FAILED.getAndSet(0)); }
    static Integer takeDroppedInt()     { return clamp(DROPPED.getAndSet(0)); }
    static Integer takeRateLimitedInt() { return clamp(RATE_LIMITED.getAndSet(0)); }
    static Integer takeNotFoundInt()    { return clamp(NOT_FOUND.getAndSet(0)); }

    static int clamp(long v) {
        return v > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0, v);
    }

    /**
     * Messages sent since the last read, as a rate band rather than a number.
     *
     * <p>bStats reports roughly every 30 minutes, so the raw delta is a
     * half-hourly figure; the bands are named for the hourly rate they imply
     * because that is how anyone would reason about "is this server busy".
     */
    static String takeSendRateBand() {
        final long perReport = SENT.getAndSet(0);
        if (perReport == 0) return "None";
        final long perHour = perReport * 2;
        if (perHour < 10) return "Under 10/hour";
        if (perHour < 100) return "10-100/hour";
        if (perHour < 1_000) return "100-1000/hour";
        return "Over 1000/hour";
    }

    static Set<String> takeCommandsUsed() {
        synchronized (COMMANDS_USED) {
            final Set<String> snapshot = new LinkedHashSet<>(COMMANDS_USED);
            COMMANDS_USED.clear();
            return snapshot;
        }
    }
}
