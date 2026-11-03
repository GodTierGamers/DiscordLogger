package com.discordlogger.explosion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The prune, which is the only code here that deletes anything.
 *
 * <p>It leans on rows being appended in time order, so the expired ones are a block at
 * the start of the file. These tests are what stop that assumption becoming a quiet
 * data-loss bug if the writer ever changes.
 */
class ExplosionLogPruneTest {

    private static File write(Path dir, String... lines) throws IOException {
        final Path f = dir.resolve("explosions.csv");
        final StringBuilder sb = new StringBuilder();
        sb.append(ExplosionRecord.HEADER).append('\n');
        for (String line : lines) sb.append(line).append('\n');
        Files.write(f, sb.toString().getBytes(StandardCharsets.UTF_8));
        return f.toFile();
    }

    /** A real row, in the shipped column order -- not a shape only this test believes in. */
    private static String row(long at, int x) {
        return at + ",world," + x + ",71,-344,8,Primed Tnt,37,0.30,";
    }

    @Test
    @DisplayName("rows older than the cutoff go, the rest stay in order")
    void dropsThePrefix(@TempDir Path dir) throws IOException {
        final File f = write(dir, row(100, 1), row(200, 2), row(300, 3), row(400, 4));

        assertEquals(2, ExplosionLog.rewriteWithout(f, 300L));

        final List<String> left = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        assertEquals(ExplosionRecord.HEADER, left.get(0), "the header is rewritten");
        assertEquals(3, left.size());
        assertTrue(left.get(1).startsWith("300,"));
        assertTrue(left.get(2).startsWith("400,"));
    }

    @Test
    @DisplayName("nothing expired means nothing is rewritten")
    void noOpWhenCurrent(@TempDir Path dir) throws IOException {
        final File f = write(dir, row(500, 1), row(600, 2));
        final long before = f.lastModified();

        assertEquals(0, ExplosionLog.rewriteWithout(f, 100L));

        // The ordinary case on every server: the cheapest possible prune is one that
        // reads a line and stops.
        assertEquals(before, f.lastModified(), "the file must not be touched");
        assertFalse(dir.resolve("explosions.csv.tmp").toFile().exists(),
                "no leftover temporary file");
    }

    @Test
    @DisplayName("a row whose timestamp cannot be read is kept")
    void unreadableRowsSurvive(@TempDir Path dir) throws IOException {
        // Deleting what cannot be parsed would delete exactly the rows a bug wrote.
        final File f = write(dir, "garbage line", row(900, 2));

        assertEquals(0, ExplosionLog.rewriteWithout(f, 500L));
        assertTrue(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)
                .contains("garbage line"));
    }

    @Test
    @DisplayName("expiring everything leaves a valid, empty file rather than no file")
    void emptyingKeepsTheHeader(@TempDir Path dir) throws IOException {
        final File f = write(dir, row(100, 1), row(200, 2));

        assertEquals(2, ExplosionLog.rewriteWithout(f, 1_000L));

        final List<String> left = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        assertEquals(1, left.size(), "just the header");
        assertEquals(ExplosionRecord.HEADER, left.get(0));
        // A missing file would read as "logging never worked" rather than "nothing is
        // old enough to still be here".
        assertTrue(f.isFile());
    }

    @Test
    @DisplayName("the timestamp is read without parsing the rest of the row")
    void timestampIsCheapToRead() {
        assertEquals(1_762_128_000_000L, ExplosionLog.timestampOf(row(1_762_128_000_000L, 1)));
        assertEquals(-1L, ExplosionLog.timestampOf("no comma here"));
        assertEquals(-1L, ExplosionLog.timestampOf(",world,1"));
        assertEquals(-1L, ExplosionLog.timestampOf(ExplosionRecord.HEADER));
        assertEquals(-1L, ExplosionLog.timestampOf(null));
    }

    @Test
    @DisplayName("the prune cadence follows the retention window, clamped at both ends")
    void cadenceFollowsTheWindow() {
        // Half the window, so a row is never far past its expiry on disk.
        assertEquals(3L * 3_600_000L, ExplosionLog.pruneIntervalMs("6h"));
        assertEquals(15L * 60_000L, ExplosionLog.pruneIntervalMs("30m"));
        assertEquals(60L * 60_000L, ExplosionLog.pruneIntervalMs("2h"));

        // Clamped low: a one-minute retention must not have the pruner rewriting the
        // file every thirty seconds.
        assertEquals(5L * 60_000L, ExplosionLog.pruneIntervalMs("1m"));

        // Clamped high: half of a year is not a useful interval, and "keep forever"
        // still wakes occasionally in case the setting changes under it.
        assertEquals(6L * 3_600_000L, ExplosionLog.pruneIntervalMs("1y"));
        assertEquals(6L * 3_600_000L, ExplosionLog.pruneIntervalMs("30d"));
        assertEquals(6L * 3_600_000L, ExplosionLog.pruneIntervalMs("0"));
    }

    @Test
    @DisplayName("the window check keeps a row it cannot date")
    void windowFailsOpen() {
        assertTrue(ExplosionLog.withinWindow(row(900, 1), 500L));
        assertFalse(ExplosionLog.withinWindow(row(100, 1), 500L));
        assertTrue(ExplosionLog.withinWindow("garbage", 500L),
                "an undateable row is considered rather than silently skipped");
    }
}
