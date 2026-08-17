package com.discordlogger.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where an old key lands in the current schema.
 *
 * <p>Migration steps one schema at a time because renames compose. Colours were
 * flat ({@code embeds.colors.player_join}) until v7 nested them, then moved beside
 * their toggle in v10. Resolving v6 straight to v10 would apply only the last
 * step's renames, so a v6 user's colours would match nothing and be replaced by
 * defaults — the exact loss the schema trailer exists to prevent.
 */
class ConfigMigratorPathTest {

    /** The live schema's flattened defaults — every resolved path must exist in here. */
    private static Map<String, Object> defaults;
    private static int current;

    @BeforeAll
    static void loadShippedConfig() throws Exception {
        String shipped = Files.readString(Path.of("src/main/resources/config.yml"));
        defaults = ConfigMigrator.flattenYaml(new Yaml().load(shipped));
        current = ConfigMigrator.extractVersion(shipped);
    }

    @ParameterizedTest(name = "v{1}: {0} -> {2}")
    @CsvSource({
            // v6 wrote colours flat; v7 nested them; v10 moved them beside the toggle.
            "embeds.colors.player_join,          6, log.player.join.color",
            "embeds.colors.server_command,       6, log.server.command.color",
            "embeds.colors.player_death,         6, log.player.death.color",
            // v6 moderation colours had no group at all.
            "embeds.colors.ban,                  6, log.moderation.ban.color",
            "embeds.colors.unban,                6, log.moderation.unban.color",
            "embeds.colors.kick,                 6, log.moderation.kick.color",
            // v7 and v8 already nested, so only the v10 move applies.
            "embeds.colors.player.teleport,      7, log.player.teleport.color",
            "embeds.colors.moderation.deop,      8, log.moderation.deop.color",
            "embeds.colors.player.gamemode,      8, log.player.gamemode.color",
            // v9 is the single-step case.
            "embeds.colors.player.join,          9, log.player.join.color",
            "log.player.join,                    9, log.player.join.enabled",
            "log.server.explosion,               9, log.server.explosion.enabled",
    })
    void renamesCompose(String oldPath, int from, String expected) {
        assertEquals(expected, ConfigMigrator.resolvePath(oldPath, defaults, from, current));
    }

    @Test
    @DisplayName("v9's 'whitelist' colour belongs to the whitelist_edit toggle")
    void whitelistColourFindsItsRenamedToggle() {
        // The colour key and the toggle key never matched: v9 had a colour called
        // "whitelist" but a toggle called "whitelist_edit". v10 merged them.
        assertEquals("log.moderation.whitelist_edit.color",
                ConfigMigrator.resolvePath("embeds.colors.moderation.whitelist", defaults, 9, current));
    }

    @Test
    @DisplayName("every v9 event toggle survives the upgrade")
    void allV9TogglesResolve() {
        String[][] groups = {
                {"player", "join", "quit", "chat", "command", "death", "advancement", "teleport", "gamemode"},
                {"server", "command", "start", "stop", "explosion"},
                {"moderation", "ban", "unban", "kick", "op", "deop", "whitelist_toggle", "whitelist_edit"},
        };
        for (String[] group : groups) {
            for (int i = 1; i < group.length; i++) {
                String path = "log." + group[0] + "." + group[i];
                assertEquals(path + ".enabled",
                        ConfigMigrator.resolvePath(path, defaults, 9, current),
                        path + " must carry over; if it does not, that user's setting is lost");
            }
        }
    }

    @ParameterizedTest(name = "{0} is untouched by any step")
    @ValueSource(strings = {"webhook.url", "format.time", "format.name", "format.nicknames",
            "embeds.enabled", "embeds.author"})
    void keysThatNeverMovedPassStraightThrough(String path) {
        // From the oldest schema on record, so every intermediate step is exercised.
        assertEquals(path, ConfigMigrator.resolvePath(path, defaults, 2, current));
    }

    @Test
    @DisplayName("a key that genuinely no longer exists resolves to nothing")
    void removedKeysAreNotForcedSomewhere() {
        // v6's embeds.colors.server was a single fallback colour; v7 turned that
        // same name into a section, so the scalar has no successor. Inventing a
        // destination would be worse than dropping it.
        assertNull(ConfigMigrator.resolvePath("embeds.colors.server", defaults, 6, current));
        assertNull(ConfigMigrator.resolvePath("log.player.nonsense", defaults, 9, current));
        assertNull(ConfigMigrator.resolvePath("embeds.colors.nope.nope", defaults, 9, current));
    }

    @Test
    @DisplayName("every resolved path actually exists in the shipped defaults")
    void resolvedPathsAreReal() {
        for (String flat : new String[]{"player_join", "player_quit", "player_chat",
                "player_command", "player_death", "server_command", "ban", "unban", "kick"}) {
            String resolved = ConfigMigrator.resolvePath("embeds.colors." + flat, defaults, 6, current);
            assertNotNull(resolved, flat + " (a v6 colour) must land somewhere");
            // resolvePath only returns paths present in defMap, so this is belt and
            // braces against a future change loosening that.
            org.junit.jupiter.api.Assertions.assertTrue(defaults.containsKey(resolved),
                    resolved + " is not a real key in the shipped config");
        }
    }

    @Test
    @DisplayName("v10 settings survive the step to v11")
    void v10SettingsReachV11() {
        // v11 only ADDS filters.respect_vanish, so every v10 path must arrive unchanged.
        // A step that renames nothing still needs proving: step() defaulting to identity
        // is what makes "only develop the delta" true, and a stray case would break it.
        for (String path : new String[]{
                "webhook.url", "embeds.enabled", "format.nicknames",
                "log.player.join.enabled", "log.player.join.color",
                "log.moderation.ban.enabled", "filters.ignored_commands",
                "filters.minimum_explosion_blocks"}) {
            assertEquals(path, ConfigMigrator.resolvePath(path, defaults, 10, current),
                    path + " must survive v10 -> v11 untouched");
        }
    }

    @Test
    @DisplayName("the new filter exists in the shipped defaults")
    void respectVanishIsShipped() {
        assertTrue(defaults.containsKey("filters.respect_vanish"),
                "config.yml must ship the key the Java reads");
    }
}
