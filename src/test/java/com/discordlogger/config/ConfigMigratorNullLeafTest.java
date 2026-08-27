package com.discordlogger.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A key with no value must survive migration.
 *
 * <p>{@code log.custom:} is legal YAML meaning "an empty section, add entries here",
 * and it flattens to a null leaf. Rendering that null produced {@code custom:null} —
 * and YAML with no space after the colon is a plain <em>scalar</em>, not a mapping, so
 * everything below it stopped parsing. Migration corrupted the file it exists to
 * preserve, and only on configs that had reached the version introducing the key.
 */
class ConfigMigratorNullLeafTest {

    private static final String DEFAULT_TEXT = """
            log:
              player:
                join:
                  enabled: true
              custom:

            # CONFIG VERSION V11, SHIPPED WITH v9.9.9
            """;

    @Test
    @DisplayName("a null-valued key round-trips as valid YAML")
    void nullLeafStaysParseable() {
        final String user = DEFAULT_TEXT.replace("enabled: true", "enabled: false");
        final String out = ConfigMigrator.migrateText(DEFAULT_TEXT, user, 11, 11);

        final Map<?, ?> parsed = new Yaml().load(out);
        assertNotNull(parsed, "migrated output must still be parseable YAML");

        final Map<?, ?> log = (Map<?, ?>) parsed.get("log");
        assertTrue(log.containsKey("custom"), "the empty section must survive");
        assertEquals(null, log.get("custom"), "and must stay empty, not become the string \"null\"");
    }

    @Test
    @DisplayName("the user's real values still transplant across it")
    void valuesAroundItStillMove() {
        final String user = DEFAULT_TEXT.replace("enabled: true", "enabled: false");
        final String out = ConfigMigrator.migrateText(DEFAULT_TEXT, user, 11, 11);

        final Map<?, ?> parsed = new Yaml().load(out);
        final Map<?, ?> log = (Map<?, ?>) parsed.get("log");
        final Map<?, ?> join = (Map<?, ?>) ((Map<?, ?>) log.get("player")).get("join");
        assertEquals(false, join.get("enabled"),
                "a null leaf elsewhere must not stop real values transplanting");
    }

    @Test
    @DisplayName("the line is left exactly as the default wrote it")
    void lineIsUntouched() {
        final String out = ConfigMigrator.migrateText(DEFAULT_TEXT, DEFAULT_TEXT, 11, 11);
        assertTrue(out.contains("\n  custom:\n"),
                "the shipped line should pass through unchanged, got:\n" + out);
    }
}
