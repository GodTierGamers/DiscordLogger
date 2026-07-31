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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Migrating list values.
 *
 * <p>The migrator replaced scalars only. Every value in the config happened to be a
 * scalar until {@code filters:} arrived, and a list hitting the scalar path did not
 * fail loudly — it wrote {@code ignored_commands:"[login, register, …]"}, which is
 * not valid YAML and left the original items orphaned below it. A user upgrading
 * would have had their config broken and their deny-list lost.
 *
 * <p>That matters more than most config keys: the deny-list is what stops
 * {@code /login} posting a password to Discord.
 */
class ConfigMigratorListTest {

    private static String shipped;
    private static int current;

    @BeforeAll
    static void load() throws Exception {
        shipped = Files.readString(Path.of("src/main/resources/config.yml"));
        current = ConfigMigrator.extractVersion(shipped);
    }

    private static Map<?, ?> filtersOf(String yaml) {
        return (Map<?, ?>) ((Map<?, ?>) new Yaml().load(yaml)).get("filters");
    }

    @Test
    @DisplayName("a customised deny-list survives migration")
    void customEntriesArePreserved() {
        String user = shipped.replace("    - login\n", "    - login\n    - mypluginsecret\n");
        String out = ConfigMigrator.migrateText(shipped, user, current, current);

        @SuppressWarnings("unchecked")
        List<String> commands = (List<String>) filtersOf(out).get("ignored_commands");
        assertTrue(commands.contains("mypluginsecret"),
                "an entry the admin added must not be reverted to the shipped defaults");
        assertTrue(commands.contains("login"), "the shipped entries must still be there too");
    }

    @Test
    @DisplayName("a list the user filled from empty is carried over")
    void filledFromEmpty() {
        String user = shipped.replace("  ignored_players: []",
                "  ignored_players:\n    - Notch\n    - 00000000-0000-0000-0009-01f2a3b4c5d6");
        String out = ConfigMigrator.migrateText(shipped, user, current, current);

        @SuppressWarnings("unchecked")
        List<String> players = (List<String>) filtersOf(out).get("ignored_players");
        assertEquals(List.of("Notch", "00000000-0000-0000-0009-01f2a3b4c5d6"), players);
    }

    @Test
    @DisplayName("an empty list stays inline rather than becoming a quoted string")
    void emptyListsStayEmpty() {
        String out = ConfigMigrator.migrateText(shipped, shipped, current, current);
        assertEquals(List.of(), filtersOf(out).get("ignored_worlds"));
        assertTrue(out.contains("ignored_worlds: []"),
                "an empty list should read as [] the way it was written");
    }

    @Test
    @DisplayName("migrating an unchanged config is byte-identical")
    void identityMigrationIsStable() {
        // The regression that caught the quoting bug: rendering every item as a
        // quoted string rewrote a plain "- login" into a quoted one on every upgrade, churning
        // a file nobody had edited.
        String out = ConfigMigrator.migrateText(shipped, shipped, current, current);
        assertEquals(shipped.stripTrailing(), out.stripTrailing());
    }

    @Test
    @DisplayName("the result is still valid YAML with comments intact")
    void outputRemainsValid() {
        String user = shipped.replace("    - login\n", "    - login\n    - custom\n");
        String out = ConfigMigrator.migrateText(shipped, user, current, current);

        new Yaml().load(out);   // throws if the list splice produced invalid YAML
        assertEquals(shipped.lines().filter(l -> l.strip().startsWith("#")).count(),
                out.lines().filter(l -> l.strip().startsWith("#")).count(),
                "splicing a list must not disturb the surrounding comments");
    }

    @Test
    @DisplayName("entries YAML would read as booleans or numbers stay strings")
    void reservedWordsAreQuoted() {
        // A command called "no" or "on" is unlikely but legal, and unquoted it would
        // come back as a boolean — silently no longer matching anything.
        String user = shipped.replace("    - login\n", "    - login\n    - \"no\"\n    - \"3\"\n");
        String out = ConfigMigrator.migrateText(shipped, user, current, current);

        @SuppressWarnings("unchecked")
        List<Object> commands = (List<Object>) filtersOf(out).get("ignored_commands");
        assertTrue(commands.contains("no"), "should still be the string no, not the boolean false");
        assertTrue(commands.contains("3"), "should still be the string 3, not the number 3");
    }
}
