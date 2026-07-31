package com.discordlogger.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A real upgrade, end to end: a customised old config in, the new schema out.
 *
 * <p>This is the test that would catch the failure that actually matters — an
 * upgrade that quietly discards what someone configured and replaces it with
 * defaults. It runs against the genuinely shipped {@code config.yml}, so it keeps
 * testing the real thing as the schema moves.
 */
class ConfigMigratorUpgradeTest {

    private static String shipped;
    private static int current;

    @BeforeAll
    static void loadShippedConfig() throws Exception {
        shipped = Files.readString(Path.of("src/main/resources/config.yml"));
        current = ConfigMigrator.extractVersion(shipped);
    }

    /** A v9 config someone actually customised — the point is that none of this is default. */
    private static final String CUSTOMISED_V9 = String.join("\n",
            "webhook:", "  url: \"https://discord.com/api/webhooks/123/SECRET\"",
            "format:", "  time: \"[HH:mm]\"", "  name: \"Survival\"", "  nicknames: false",
            "embeds:", "  enabled: true", "  author: \"My Server\"",
            "  colors:",
            "    player:", "      join: \"#123456\"", "      chat: \"#654321\"",
            "    moderation:", "      whitelist: \"#ABCDEF\"",
            "log:",
            "  player:", "    join: true", "    quit: false", "    chat: false",
            "    command: true", "    death: true", "    advancement: true",
            "    teleport: true", "    gamemode: true",
            "  server:", "    command: true", "    start: true", "    stop: true",
            "    explosion: false",
            "  moderation:", "    ban: true", "    unban: true", "    kick: true",
            "    op: true", "    deop: true", "    whitelist_toggle: true",
            "    whitelist_edit: true",
            "# CONFIG VERSION V9, SHIPPED WITH v2.1.6", "");

    private Map<?, ?> upgradeFromV9() {
        String out = ConfigMigrator.migrateText(shipped, CUSTOMISED_V9, 9, current);
        return (Map<?, ?>) new Yaml().load(out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> event(Map<?, ?> root, String group, String name) {
        Map<?, ?> log = (Map<?, ?>) root.get("log");
        Map<?, ?> section = (Map<?, ?>) log.get(group);
        return (Map<String, Object>) section.get(name);
    }

    @Test
    @DisplayName("disabled events stay disabled through the shape change")
    void togglesSurvive() {
        Map<?, ?> r = upgradeFromV9();
        assertEquals(false, event(r, "player", "quit").get("enabled"));
        assertEquals(false, event(r, "player", "chat").get("enabled"));
        assertEquals(false, event(r, "server", "explosion").get("enabled"));
        assertEquals(true, event(r, "player", "join").get("enabled"));
        assertEquals(true, event(r, "moderation", "ban").get("enabled"));
    }

    @Test
    @DisplayName("customised colours move to their event; untouched ones take the new default")
    void coloursMoveBesideTheirToggle() {
        Map<?, ?> r = upgradeFromV9();
        assertEquals("#123456", event(r, "player", "join").get("color"));
        assertEquals("#654321", event(r, "player", "chat").get("color"));
        // v9's "whitelist" colour belongs to the whitelist_edit toggle.
        assertEquals("#ABCDEF", event(r, "moderation", "whitelist_edit").get("color"));
        // Not customised in the v9 file, so it must come from the new defaults.
        assertEquals("#ED4245", event(r, "player", "quit").get("color"));
    }

    @Test
    @DisplayName("a sub-option added to the open schema takes its default, not a stale value")
    void newSubOptionsDefaultSafely() {
        // show_coords did not exist in v9, so an upgrading user must land on the
        // shipped default. It defaults to false because a death message with
        // coordinates tells the whole channel where the body and its loot are.
        Map<?, ?> r = upgradeFromV9();
        assertEquals(false, event(r, "player", "death").get("show_coords"));
    }

    @Test
    @DisplayName("an existing sub-option choice is preserved across a migration")
    void subOptionChoiceSurvives() {
        String opted = shipped.replace("      show_coords: false", "      show_coords: true");
        String out = ConfigMigrator.migrateText(shipped, opted, current, current);
        Map<?, ?> r = (Map<?, ?>) new Yaml().load(out);
        assertEquals(true, event(r, "player", "death").get("show_coords"),
                "a user who opted in must not be silently opted back out");
    }

    @Test
    @DisplayName("per-event webhooks default to empty, meaning the main webhook")
    void routingDefaultsToTheMainWebhook() {
        // A v9 user had one webhook and expects to keep having one. Every event must
        // arrive with an empty route rather than anything inherited or invented.
        Map<?, ?> r = upgradeFromV9();
        Map<?, ?> log = (Map<?, ?>) r.get("log");
        for (Object groupName : log.keySet()) {
            Map<?, ?> group = (Map<?, ?>) log.get(groupName);
            for (Object eventName : group.keySet()) {
                Map<?, ?> ev = (Map<?, ?>) group.get(eventName);
                assertTrue(ev.containsKey("webhook"),
                        groupName + "." + eventName + " should carry a webhook key");
                assertEquals("", ev.get("webhook"),
                        groupName + "." + eventName + " must default to the main webhook");
            }
        }
    }

    @Test
    @DisplayName("a configured per-event webhook survives a migration")
    void routingChoiceSurvives() {
        String routed = shipped.replaceFirst(
                "      webhook: \"\"",
                "      webhook: \"https://discord.com/api/webhooks/1/STAFF\"");
        String out = ConfigMigrator.migrateText(shipped, routed, current, current);
        assertTrue(out.contains("https://discord.com/api/webhooks/1/STAFF"),
                "a channel someone deliberately routed must not be reset by an upgrade");
    }

    @Test
    @DisplayName("settings outside the log tree survive untouched")
    void unrelatedSettingsSurvive() {
        Map<?, ?> r = upgradeFromV9();
        assertEquals("https://discord.com/api/webhooks/123/SECRET",
                ((Map<?, ?>) r.get("webhook")).get("url"));
        assertEquals("My Server", ((Map<?, ?>) r.get("embeds")).get("author"));
        assertEquals(false, ((Map<?, ?>) r.get("format")).get("nicknames"));
        assertEquals("Survival", ((Map<?, ?>) r.get("format")).get("name"));
    }

    @Test
    @DisplayName("the old colours tree is gone, not left behind alongside the new one")
    void oldStructureIsNotCarriedOver() {
        assertFalse(((Map<?, ?>) upgradeFromV9().get("embeds")).containsKey("colors"));
    }

    @Test
    @DisplayName("the result is the new schema's file, comments and all")
    void outputKeepsTheShippedFileIntact() {
        String out = ConfigMigrator.migrateText(shipped, CUSTOMISED_V9, 9, current);

        assertTrue(out.strip().endsWith("(x-release-please-version)"),
                "the schema trailer must survive, or the next upgrade cannot detect the version");
        assertEquals(commentLines(shipped), commentLines(out),
                "migration must not drop comments — the file is documentation as much as config");
        assertEquals(shipped.split("\n", -1).length, out.split("\n", -1).length,
                "migration rewrites values in place; it must not add or remove lines");
    }

    @Test
    @DisplayName("a config that changed nothing round-trips to the shipped file exactly")
    void defaultConfigIsUnchangedByMigration() {
        // Same schema in and out, no customisation: the only difference should be
        // none at all. Guards against a rename rule firing when it should not.
        String out = ConfigMigrator.migrateText(shipped, shipped, current, current);
        assertEquals(shipped.stripTrailing(), out.stripTrailing());
    }

    @Test
    @DisplayName("every customised value in the old config lands somewhere")
    void nothingIsSilentlyDropped() {
        Map<String, Object> user = ConfigMigrator.flattenYaml(new Yaml().load(CUSTOMISED_V9));
        Map<String, Object> defaults = ConfigMigrator.flattenYaml(new Yaml().load(shipped));

        List<String> lost = user.keySet().stream()
                .filter(k -> ConfigMigrator.resolvePath(k, defaults, 9, current) == null)
                .toList();

        assertEquals(List.of(), lost,
                "these v9 keys have nowhere to go in the current schema, so the user's "
                        + "choices for them would be replaced by defaults");
    }

    private static long commentLines(String text) {
        return text.lines().filter(l -> l.strip().startsWith("#")).count();
    }
}
