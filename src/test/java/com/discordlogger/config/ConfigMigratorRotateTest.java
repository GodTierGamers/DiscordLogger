package com.discordlogger.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The last step of a migration: swapping the upgraded file in without losing the old one.
 *
 * <p>This is the most destructive moment in the plugin. It used to warn when the backup
 * failed and then replace the original anyway, so a locked {@code config.old.yml} or a
 * folder that would not allow the rename destroyed settings nobody could get back — and
 * then logged that a backup had been saved.
 *
 * <p>It survived because the rotation could only be reached through a method that needs a
 * running server. It is static and plugin-free now, so these cost nothing to run.
 */
class ConfigMigratorRotateTest {

    private static Path write(Path p, String text) throws IOException {
        Files.write(p, text.getBytes(StandardCharsets.UTF_8));
        return p;
    }

    private static String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the upgraded file lands and the original is kept beside it")
    void happyPath(@TempDir Path dir) throws Exception {
        final Path user = write(dir.resolve("config.yml"), "ORIGINAL");
        final Path fresh = write(dir.resolve("config.new.yml"), "UPGRADED");
        final Path backup = dir.resolve("config.old.yml");

        ConfigMigrator.rotate(user, fresh, backup);

        assertEquals("UPGRADED", read(user));
        assertEquals("ORIGINAL", read(backup));
        assertFalse(Files.exists(fresh), "the staging file should have been moved, not copied");
    }

    @Test
    @DisplayName("a backup that cannot be taken leaves the original exactly as it was")
    void backupFailureChangesNothing(@TempDir Path dir) throws Exception {
        final Path user = write(dir.resolve("config.yml"), "ORIGINAL");
        final Path fresh = write(dir.resolve("config.new.yml"), "UPGRADED");

        // A non-empty directory where the backup wants to go: Files.move cannot
        // replace it, which is the portable stand-in for a locked or unwritable file.
        final Path backup = Files.createDirectory(dir.resolve("config.old.yml"));
        write(backup.resolve("occupied"), "x");

        assertThrows(IOException.class, () -> ConfigMigrator.rotate(user, fresh, backup));

        // The whole point: nothing was touched, so the caller can abort and the
        // server keeps running on a config the user still has.
        assertEquals("ORIGINAL", read(user));
        assertEquals("UPGRADED", read(fresh));
    }

    @Test
    @DisplayName("if the swap fails after the backup, the original is put back")
    void replaceFailurePutsTheOriginalBack(@TempDir Path dir) throws Exception {
        final Path user = write(dir.resolve("config.yml"), "ORIGINAL");
        final Path fresh = dir.resolve("config.new.yml");   // never created
        final Path backup = dir.resolve("config.old.yml");

        assertThrows(IOException.class, () -> ConfigMigrator.rotate(user, fresh, backup));

        // A folder holding a backup and no config.yml would start the server on
        // shipped defaults -- recoverable, but only by someone who notices.
        assertTrue(Files.exists(user), "config.yml was left missing");
        assertEquals("ORIGINAL", read(user));
    }
}
