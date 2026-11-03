package com.discordlogger.explosion;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * {@code explosions.csv} — every explosion, so one can be found by place later.
 *
 * <h2>One file, one thread</h2>
 *
 * <p>A single append-only CSV in the plugin's data folder. One writer thread owns it
 * and nothing else touches it, which is what makes the append and the prune safe
 * without a lock: they are two kinds of task on the same queue, so a rewrite can never
 * interleave with a write. Explosions fire on the main thread and the main thread never
 * waits for a disk here — the same reason {@code WebhookQueue} exists.
 *
 * <h2>Why the prune is cheap</h2>
 *
 * <p>Rows are appended in the order they happen, so the expired ones are always a
 * contiguous block at the <em>start</em> of the file. Pruning is therefore one forward
 * pass: skip rows while they are older than the cutoff, copy the remainder to a
 * temporary file, and move it over the original. Only the timestamp is read — the text
 * up to the first comma — so nothing else is parsed to decide what to keep, and when
 * the first row is still current (the ordinary case) the whole prune costs one line.
 *
 * <p><b>A row whose timestamp cannot be read is kept.</b> Deleting what cannot be
 * parsed would delete exactly the rows a bug wrote, which are the ones worth having.
 */
public final class ExplosionLog {

    /** The file, in the plugin's own folder beside config.yml. */
    static final String FILE_NAME = "explosions.csv";

    /**
     * Pending rows. Bounded for the same reason the webhook queue is: a disk that has
     * stopped accepting writes must cost a fixed amount of memory rather than the
     * server.
     */
    private static final int CAPACITY = 4096;

    /**
     * How often the scheduled task wakes. It does not prune every time — see
     * {@link #pruneIntervalMs}.
     */
    private static final long PRUNE_CHECK_TICKS = 20L * 60L * 5L;            // 5 minutes

    /** Long enough that startup is finished, short enough that a restart still prunes. */
    private static final long PRUNE_DELAY_TICKS = 20L * 60L;                 // 1 minute

    /** Never prune more often than this, however short the retention. */
    private static final long MIN_PRUNE_MS = 5L * 60_000L;

    /** Never leave it longer than this, however long the retention. */
    private static final long MAX_PRUNE_MS = 6L * 3_600_000L;

    private static final BlockingQueue<String> PENDING = new ArrayBlockingQueue<String>(CAPACITY);

    private static volatile JavaPlugin plugin;
    private static volatile File file;
    private static volatile Thread writer;
    private static volatile boolean running;
    private static volatile boolean warnedFull;
    private static volatile long lastPruneAt;

    private ExplosionLog() {}

    /** Start the writer. Safe to call again on reload. */
    public static synchronized void start(JavaPlugin pl) {
        plugin = pl;
        file = new File(pl.getDataFolder(), FILE_NAME);
        if (running) return;
        running = true;
        final Thread t = new Thread(ExplosionLog::runLoop, "DiscordLogger-ExplosionLog");
        // Not a daemon: a queued row should still reach the disk if the JVM is winding
        // down, and shutdown() bounds how long that can take.
        t.setDaemon(false);
        t.start();
        writer = t;
        schedulePrune(pl);
    }

    /** Queue one explosion. Never blocks the caller, never touches the disk here. */
    public static void append(ExplosionRecord record) {
        if (!running || record == null) return;
        if (!PENDING.offer(record.toCsv())) {
            if (!warnedFull) {
                warnedFull = true;
                log().warning("[ExplosionLog] " + FILE_NAME + " is not keeping up ("
                        + CAPACITY + " rows pending) — dropping explosion records until it "
                        + "drains. The disk may be full or read-only.");
            }
            return;
        }
        warnedFull = false;
    }

    /** Drain what is queued and stop. Called from {@code onDisable}. */
    public static void shutdown() {
        running = false;
        final Thread t = writer;
        if (t == null) return;
        try {
            t.join(2_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (t.isAlive()) t.interrupt();
        writer = null;
    }

    // ------------------------------------------------------------------ reading

    /**
     * Explosions near a point, nearest first.
     *
     * <p>Call this off the main thread — it reads a file that may hold a month of
     * rows. The timestamp is checked before anything else is parsed, so rows outside
     * the window cost one {@code indexOf} and no allocation.
     *
     * @param world  the world to match, case-insensitively
     * @param radius how far to look, in blocks
     * @param since  epoch millis; rows older than this are skipped, 0 for no limit
     * @param type   a case-insensitive substring of the source, or null for any
     * @param limit  how many results to return at most
     */
    /** What a lookup found: the rows to show, and how many matched in total. */
    public static final class Results {
        private final List<ExplosionRecord> shown;
        private final int total;
        Results(List<ExplosionRecord> shown, int total) { this.shown = shown; this.total = total; }
        public List<ExplosionRecord> shown() { return shown; }
        /** Every match, not just the ones returned — so a truncated list can say so. */
        public int total() { return total; }
    }

    public static Results near(String world, int x, int y, int z,
                               int radius, long since, String type, int limit) {
        final File f = file;
        if (f == null || !f.isFile()) return new Results(Collections.<ExplosionRecord>emptyList(), 0);

        // Never return a row the config says is already expired. Pruning is scheduled,
        // so between passes the file still holds rows past their window -- and a
        // lookup that showed them would make expiry_time read as a lie. Applying the
        // cutoff here makes the setting true the moment it changes, and leaves pruning
        // as purely a question of disk space.
        since = Math.max(since, expiryFloor());

        final long r = radius;
        final long r2 = r * r;
        final String wanted = type == null ? null : type.toLowerCase(java.util.Locale.ROOT);
        final List<ExplosionRecord> hits = new ArrayList<ExplosionRecord>();

        BufferedReader in = null;
        try {
            in = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8);
            String line;
            while ((line = in.readLine()) != null) {
                if (since > 0L && !withinWindow(line, since)) continue;
                final ExplosionRecord rec = ExplosionRecord.fromCsv(line);
                if (rec == null) continue;
                if (!rec.world().equalsIgnoreCase(world == null ? "" : world)) continue;
                if (wanted != null && !rec.source().toLowerCase(java.util.Locale.ROOT).contains(wanted)) {
                    continue;
                }
                final long dx = rec.x() - x;
                final long dy = rec.y() - y;
                final long dz = rec.z() - z;
                if (dx * dx + dy * dy + dz * dz > r2) continue;
                hits.add(rec);
            }
        } catch (IOException unreadable) {
            log().warning("[ExplosionLog] Could not read " + FILE_NAME + ": " + unreadable);
            return new Results(Collections.<ExplosionRecord>emptyList(), 0);
        } finally {
            closeQuietly(in);
        }

        // Nearest first: the explosion that made the hole somebody is standing in is
        // the one they are asking about, and it is rarely the most recent.
        final int px = x;
        final int py = y;
        final int pz = z;
        Collections.sort(hits, new Comparator<ExplosionRecord>() {
            @Override public int compare(ExplosionRecord a, ExplosionRecord b) {
                final int byDistance = Double.compare(a.distanceTo(px, py, pz),
                                                      b.distanceTo(px, py, pz));
                // A tie is two blasts at the same spot, so the newer one first.
                return byDistance != 0 ? byDistance : Long.compare(b.at(), a.at());
            }
        });
        final int total = hits.size();
        final List<ExplosionRecord> shown = total > limit
                ? new ArrayList<ExplosionRecord>(hits.subList(0, limit)) : hits;
        return new Results(shown, total);
    }

    /** The oldest timestamp {@code expiry_time} still allows, or 0 when it is unset. */
    static long expiryFloor() {
        final JavaPlugin pl = plugin;
        if (pl == null) return 0L;
        final long cutoff = Expiry.cutoff(configuredExpiry(pl), System.currentTimeMillis());
        return cutoff == Expiry.NEVER ? 0L : cutoff;
    }

    /**
     * Whether a row's timestamp is at or after {@code since}, read without parsing
     * the rest of it. An unreadable timestamp counts as in-window, so the row is
     * considered rather than silently skipped.
     */
    static boolean withinWindow(String line, long since) {
        final long at = timestampOf(line);
        return at < 0L || at >= since;
    }

    /** The leading timestamp, or -1 when the line does not start with one. */
    static long timestampOf(String line) {
        if (line == null) return -1L;
        final int comma = line.indexOf(',');
        if (comma <= 0) return -1L;
        try {
            return Long.parseLong(line.substring(0, comma).trim());
        } catch (NumberFormatException notARow) {
            return -1L;
        }
    }

    /** How many rows the file holds, for {@code /discordlogger status}-style readouts. */
    public static long size() {
        final File f = file;
        return (f == null || !f.isFile()) ? 0L : f.length();
    }

    // ------------------------------------------------------------------ writing

    private static void runLoop() {
        while (true) {
            String row;
            try {
                if (running) {
                    row = PENDING.poll(250, TimeUnit.MILLISECONDS);
                    if (row == null) continue;
                } else {
                    row = PENDING.poll();
                    if (row == null) return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            // Everything already queued goes out in one open-write-close rather than
            // one per row: a TNT chain is dozens of records in the same tick.
            final List<String> batch = new ArrayList<String>();
            batch.add(row);
            PENDING.drainTo(batch, 512);
            try {
                writeRows(batch);
            } catch (IOException unwritable) {
                log().warning("[ExplosionLog] Could not write " + FILE_NAME + ": " + unwritable);
            } catch (RuntimeException unexpected) {
                // A single bad row must never kill the writer.
                log().warning("[ExplosionLog] Unexpected error writing a record: " + unexpected);
            }
        }
    }

    private static void writeRows(List<String> rows) throws IOException {
        final File f = file;
        if (f == null) return;
        final File parent = f.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("could not create " + parent);
        }
        final boolean fresh = !f.isFile() || f.length() == 0L;

        BufferedWriter out = null;
        try {
            out = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            if (fresh) {
                out.write(ExplosionRecord.HEADER);
                out.newLine();
            }
            for (int i = 0; i < rows.size(); i++) {
                out.write(rows.get(i));
                out.newLine();
            }
        } finally {
            if (out != null) {
                try { out.close(); } catch (IOException ignored) { /* nothing left to do */ }
            }
        }
    }

    // ------------------------------------------------------------------ pruning

    /**
     * How long to leave between prunes, given what is configured.
     *
     * <p>Half the retention window, so a row is never more than about 50% past its
     * expiry on disk, clamped at both ends: a {@code 30m} retention would otherwise
     * have the pruner rewriting the file constantly, and a {@code 1y} one would have it
     * doing nothing useful every six hours. Before {@code expiry_time} accepted minutes
     * a fixed six hours was fine; the moment it did, a short window became a promise
     * the file did not keep.
     */
    static long pruneIntervalMs(String configured) {
        final long window = Expiry.parseMillis(configured);
        if (window == Expiry.NEVER) return MAX_PRUNE_MS;
        return Math.max(MIN_PRUNE_MS, Math.min(MAX_PRUNE_MS, window / 2L));
    }

    /** What {@code expiry_time} is set to, read live so a reload takes effect. */
    public static String configuredExpiry(JavaPlugin pl) {
        return pl.getConfig().getString(
                "log.server.explosion.history.expiry_time", Expiry.DEFAULT);
    }

    private static void schedulePrune(JavaPlugin pl) {
        try {
            pl.getServer().getScheduler().runTaskTimerAsynchronously(pl, new Runnable() {
                @Override public void run() {
                    // The task wakes on a fixed short period and decides for itself,
                    // so changing expiry_time takes effect without rescheduling.
                    final long now = System.currentTimeMillis();
                    if (now - lastPruneAt < pruneIntervalMs(configuredExpiry(pl))) return;
                    pruneNow();
                }
            }, PRUNE_DELAY_TICKS, PRUNE_CHECK_TICKS);
        } catch (Throwable noScheduler) {
            // A server too early in its startup to take a task, or a test harness with
            // no scheduler at all. The next start schedules it again.
            log().fine("[ExplosionLog] Could not schedule the pruner: " + noScheduler);
        }
    }

    /**
     * Drops rows older than {@code expiry_time}. Safe to call from any thread.
     *
     * <p>Reads the config live, like every other setting, so a reload changes it with
     * no restart.
     */
    public static void pruneNow() {
        final JavaPlugin pl = plugin;
        final File f = file;
        lastPruneAt = System.currentTimeMillis();
        if (pl == null || f == null || !f.isFile()) return;

        final String configured = configuredExpiry(pl);
        final long cutoff = Expiry.cutoff(configured, System.currentTimeMillis());
        if (cutoff == Expiry.NEVER) return;

        try {
            final int dropped = rewriteWithout(f, cutoff);
            if (dropped > 0) {
                log().info("[ExplosionLog] Removed " + dropped + " explosion(s) older than "
                        + Expiry.describe(configured) + " from " + FILE_NAME + ".");
            }
        } catch (IOException failed) {
            log().warning("[ExplosionLog] Could not prune " + FILE_NAME + ": " + failed);
        }
    }

    /**
     * Copies everything at or after the cutoff to a new file and moves it into place.
     *
     * <p>Returns early without writing anything when the first row is already current,
     * which is what makes the ordinary prune free. The temporary file is written beside
     * the original and moved over it, so a crash mid-prune leaves the original intact
     * rather than a half-copied log.
     */
    static int rewriteWithout(File target, long cutoff) throws IOException {
        final Path path = target.toPath();
        final Path tmp = path.resolveSibling(FILE_NAME + ".tmp");

        int dropped = 0;
        BufferedReader in = null;
        BufferedWriter out = null;
        try {
            in = Files.newBufferedReader(path, StandardCharsets.UTF_8);
            String line;
            String firstKept = null;
            while ((line = in.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("at,")) continue;
                final long at = timestampOf(line);
                // Unreadable timestamps are kept: see the class comment.
                if (at >= 0L && Expiry.expired(at, cutoff)) {
                    dropped++;
                    continue;
                }
                firstKept = line;
                break;
            }
            if (dropped == 0) return 0;

            out = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            out.write(ExplosionRecord.HEADER);
            out.newLine();
            if (firstKept != null) {
                out.write(firstKept);
                out.newLine();
                // Rows are appended in time order, so everything after the first kept
                // row is newer than it and cannot have expired. No further checks.
                while ((line = in.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    out.write(line);
                    out.newLine();
                }
            }
        } finally {
            closeQuietly(in);
            if (out != null) {
                try { out.close(); } catch (IOException ignored) { /* nothing left to do */ }
            }
        }

        if (dropped > 0) {
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException notAtomic) {
                // Not every filesystem offers an atomic move; a plain replace is still
                // a single operation from the reader's point of view.
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return dropped;
    }

    private static void closeQuietly(BufferedReader in) {
        if (in != null) {
            try { in.close(); } catch (IOException ignored) { /* nothing left to do */ }
        }
    }

    private static java.util.logging.Logger log() {
        final JavaPlugin p = plugin;
        return (p != null) ? p.getLogger() : java.util.logging.Logger.getLogger("DiscordLogger");
    }
}
