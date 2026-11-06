package com.discordlogger.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v13 changed two shipped defaults. Migration must not apply either of them to a
 * config that already has the key — a value equal to the OLD default is still the
 * user's value, not an absence to be filled in.
 */
class MigrateToV13Test {

    private static final String V12_USER =
            "config-version: 12\n"
            + "log:\n"
            + "  server:\n"
            + "    explosion:\n"
            + "      enabled: true\n"
            + "filters:\n"
            + "  ignored_teleport_causes:\n"
            + "    - EXIT_BED\n"
            + "# CONFIG VERSION V12\n";

    private static final String V13_DEFAULT =
            "config-version: 13\n"
            + "log:\n"
            + "  server:\n"
            + "    explosion:\n"
            + "      enabled: false\n"
            + "    lag:\n"
            + "      enabled: true\n"
            + "filters:\n"
            + "  ignored_teleport_causes:\n"
            + "    - EXIT_BED\n"
            + "    - PLUGIN\n"
            + "# CONFIG VERSION V13\n";

    @Test
    @DisplayName("explosion stays on for someone who already had it on")
    void explosionDefaultIsNotApplied() {
        final String out = ConfigMigrator.migrateText(V13_DEFAULT, V12_USER, 12, 13);
        assertTrue(out.contains("explosion:\n      enabled: true"),
                "the new default overwrote the user's value:\n" + out);
    }

    @Test
    @DisplayName("a teleport list the user kept is theirs, PLUGIN included or not")
    void teleportListIsNotExtended() {
        final String out = ConfigMigrator.migrateText(V13_DEFAULT, V12_USER, 12, 13);
        assertTrue(out.contains("- EXIT_BED"), out);
        assertTrue(!out.contains("- PLUGIN"),
                "PLUGIN was added to a list the user already had:\n" + out);
    }

    @Test
    @DisplayName("keys the user does not have arrive at their v13 defaults")
    void newKeysArrive() {
        final String out = ConfigMigrator.migrateText(V13_DEFAULT, V12_USER, 12, 13);
        assertTrue(out.contains("lag:\n      enabled: true"), out);
    }

    @Test
    @DisplayName("someone who turned explosion off stays off")
    void explicitFalseSurvives() {
        final String off = V12_USER.replace("enabled: true", "enabled: false");
        final String out = ConfigMigrator.migrateText(V13_DEFAULT, off, 12, 13);
        assertTrue(out.contains("explosion:\n      enabled: false"), out);
    }
}
