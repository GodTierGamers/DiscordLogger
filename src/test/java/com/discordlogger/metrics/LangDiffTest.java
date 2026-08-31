package com.discordlogger.metrics;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What counts as a reworded lang key.
 *
 * <p>Reported from a live server: bStats put a server in the "50 or more keys changed"
 * bucket when a single message had been edited. Absent keys were being counted as
 * changed, so the chart was largely measuring how many keys the plugin had added since
 * that server's lang.yml was written.
 */
class LangDiffTest {

    private static YamlConfiguration yaml(String s) {
        final YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(s);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return y;
    }

    private static final String BUNDLED =
            "chat:\n"
          + "  reload-ok: \"reloaded\"\n"
          + "  regen-done: \"rebuilt\"\n"
          + "  added-later: \"new in a later release\"\n"
          + "discord:\n"
          + "  player-chat: \"{player}: {message}\"\n";

    @Test
    @DisplayName("a key the user's file lacks is not a change")
    void absentKeysAreNotChanges() {
        // The reported bug: this file predates "added-later" and has edited nothing.
        final List<String> changed = PluginMetrics.changedKeys(
                yaml("chat:\n  reload-ok: \"reloaded\"\n  regen-done: \"rebuilt\"\n"
                   + "discord:\n  player-chat: \"{player}: {message}\"\n"),
                yaml(BUNDLED));
        assertEquals(List.of(), changed,
                "an older lang.yml resolves absent keys from the bundled fallback, so it "
                        + "behaves identically to one carrying the defaults");
    }

    @Test
    @DisplayName("one edited message counts as exactly one")
    void oneEditIsOne() {
        final List<String> changed = PluginMetrics.changedKeys(
                yaml("chat:\n  reload-ok: \"MY OWN WORDING\"\n  regen-done: \"rebuilt\"\n"
                   + "discord:\n  player-chat: \"{player}: {message}\"\n"),
                yaml(BUNDLED));
        assertEquals(List.of("chat.reload-ok"), changed);
    }

    @Test
    @DisplayName("an untouched copy reports nothing")
    void identicalReportsNothing() {
        assertEquals(List.of(), PluginMetrics.changedKeys(yaml(BUNDLED), yaml(BUNDLED)));
    }

    @Test
    @DisplayName("config-version is never a change, and nulls are safe")
    void versionIgnoredAndNullsSafe() {
        final List<String> changed = PluginMetrics.changedKeys(
                yaml("config-version: 99\n" + BUNDLED), yaml("config-version: 11\n" + BUNDLED));
        assertTrue(changed.isEmpty(), "schema version is not a reworded message: " + changed);
        assertEquals(List.of(), PluginMetrics.changedKeys(null, yaml(BUNDLED)));
        assertEquals(List.of(), PluginMetrics.changedKeys(yaml(BUNDLED), null));
    }
}
