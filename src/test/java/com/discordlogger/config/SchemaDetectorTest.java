package com.discordlogger.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Recognising a config by its keys rather than by what it claims.
 *
 * <p>The schema was declared only by a comment on the last line, which is the least
 * durable thing in a config file. When it went missing the migrator returned
 * UNKNOWN, skipped migration, and the plugin then read a file whose shape it did
 * not match — every option silently falling back to its default, with nothing in
 * the log to explain it.
 */
class SchemaDetectorTest {

    private static Map<String, Object> flat(String yaml) {
        return ConfigMigrator.flattenYaml(new Yaml().load(yaml));
    }

    private static String shipped() throws Exception {
        return Files.readString(Path.of("src/main/resources/config.yml"));
    }

    @Test
    @DisplayName("the shipped config is recognised as its own schema")
    void recognisesTheShippedConfig() throws Exception {
        final String text = shipped();
        assertEquals(ConfigMigrator.extractVersion(text), SchemaDetector.infer(flat(text)),
                "the shipped file's declared version and its actual shape must agree — "
                        + "if they do not, one of them was updated without the other");
    }

    @Test
    @DisplayName("a config with no version marker at all is still identified")
    void survivesLosingEveryMarker() throws Exception {
        // Both markers gone: the trailer deleted and the key removed. This is the case
        // the whole class exists for.
        String stripped = shipped()
                .lines()
                .filter(l -> !l.contains("CONFIG VERSION"))
                .filter(l -> !l.startsWith("config-version:"))
                .reduce("", (a, b) -> a + b + "\n");

        assertEquals(shippedVersion(), SchemaDetector.infer(flat(stripped)),
                "with no declaration anywhere, the keys still say what this file is");
    }

    @Test
    @DisplayName("older schemas are identified by the key that introduced them")
    void identifiesEachHistoricalSchema() {
        assertEquals(9, SchemaDetector.infer(flat(
                "webhook:\n  url: ''\nformat:\n  time: x\nembeds:\n  author: a\n  colors:\n"
                        + "    server:\n      explosion: '#fff'\n")));
        assertEquals(8, SchemaDetector.infer(flat(
                "webhook:\n  url: ''\nformat:\n  time: x\nembeds:\n  author: a\n  colors:\n"
                        + "    player:\n      gamemode: '#fff'\n")));
        assertEquals(7, SchemaDetector.infer(flat(
                "webhook:\n  url: ''\nformat:\n  time: x\nembeds:\n  author: a\n  colors:\n"
                        + "    moderation:\n      ban: '#fff'\n")));
        assertEquals(6, SchemaDetector.infer(flat(
                "webhook:\n  url: ''\nformat:\n  time: x\nembeds:\n  author: a\n  colors:\n"
                        + "    ban: '#fff'\n")));
        assertEquals(3, SchemaDetector.infer(flat(
                "webhook:\n  url: ''\nformat:\n  time: x\nembeds:\n  author: a\n")));
        assertEquals(2, SchemaDetector.infer(flat("webhook:\n  url: ''\nformat:\n  time: x\n")));
    }

    @Test
    @DisplayName("a v9 config is not mistaken for v10")
    void doesNotOverreadOlderConfigs() {
        // v9 events are bare booleans; v10 made them sections. Getting this wrong
        // would skip the migration that restructures them.
        String v9 = "webhook:\n  url: ''\nformat:\n  time: x\nembeds:\n  author: a\n  colors:\n"
                + "    server:\n      explosion: '#fff'\nlog:\n  player:\n    join: true\n";
        assertEquals(9, SchemaDetector.infer(flat(v9)));
        assertNotEquals(10, SchemaDetector.infer(flat(v9)));
    }

    @Test
    @DisplayName("deleting an ordinary option does not change the detected schema")
    void toleratesMissingOptions() throws Exception {
        // An admin who deletes options they do not use must not have their config
        // treated as an older schema and rewritten.
        String trimmed = shipped().replace("  nicknames: true\n", "")
                                  .replace("      show_coords: false", "");
        assertEquals(shippedVersion(), SchemaDetector.infer(flat(trimmed)));
    }

    @Test
    @DisplayName("something that is not a config at all is not claimed")
    void ignoresUnrelatedYaml() {
        assertEquals(SchemaDetector.UNKNOWN, SchemaDetector.infer(flat("name: SomePlugin\nversion: 1.0\n")));
        assertEquals(SchemaDetector.UNKNOWN, SchemaDetector.infer(Map.of()));
        assertEquals(SchemaDetector.UNKNOWN, SchemaDetector.infer(null));
    }

    /**
     * The schema the plugin currently ships, read from the file rather than typed.
     *
     * <p>These assertions mean "the shipped config still identifies as itself", which is
     * true at every schema. Writing the number in made them fail on the bump that added
     * v11 — a green suite reporting a problem that did not exist, which is the kind of
     * failure that teaches people to edit tests without reading them.
     */
    private static int shippedVersion() throws Exception {
        return ConfigMigrator.extractVersion(shipped());
    }
}
