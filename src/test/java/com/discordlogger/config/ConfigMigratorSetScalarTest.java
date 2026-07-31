package com.discordlogger.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writing a single value back into config.yml without wrecking the file.
 *
 * <p>{@code /discordlogger webhook} needs this. The obvious implementation —
 * {@code getConfig().set()} then {@code saveConfig()} — re-serialises the whole
 * file through Bukkit's YAML writer and drops every comment, including the
 * {@code CONFIG VERSION V<n>} trailer. A config saved that way looks like it has
 * no schema at all, which breaks migration for that user permanently.
 */
class ConfigMigratorSetScalarTest {

    @TempDir
    Path dir;

    private Path config;
    private String before;

    @BeforeEach
    void copyShippedConfig() throws Exception {
        before = Files.readString(Path.of("src/main/resources/config.yml"));
        config = dir.resolve("config.yml");
        Files.writeString(config, before);
    }

    @Test
    @DisplayName("the value is written and the file still parses")
    void writesTheValue() throws Exception {
        String url = "https://discord.com/api/webhooks/123456789/TOKEN";
        assertTrue(ConfigMigrator.setScalar(config.toFile(), "webhook.url", url));

        Map<?, ?> parsed = (Map<?, ?>) new Yaml().load(Files.readString(config));
        assertEquals(url, ((Map<?, ?>) parsed.get("webhook")).get("url"));
    }

    @Test
    @DisplayName("exactly one line changes — nothing else in the file is touched")
    void changesOnlyTheTargetLine() throws Exception {
        ConfigMigrator.setScalar(config.toFile(), "webhook.url", "https://discord.com/api/webhooks/1/T");

        List<String> was = List.of(before.split("\n", -1));
        List<String> now = List.of(Files.readString(config).split("\n", -1));

        assertEquals(was.size(), now.size(), "line count must not change");
        long differing = java.util.stream.IntStream.range(0, was.size())
                .filter(i -> !was.get(i).equals(now.get(i)))
                .count();
        assertEquals(1, differing, "only webhook.url's line should differ");
    }

    @Test
    @DisplayName("comments, banners and the schema trailer all survive")
    void preservesEverythingElse() throws Exception {
        ConfigMigrator.setScalar(config.toFile(), "webhook.url", "https://discord.com/api/webhooks/1/T");
        String after = Files.readString(config);

        assertEquals(before.lines().filter(l -> l.strip().startsWith("#")).count(),
                after.lines().filter(l -> l.strip().startsWith("#")).count(),
                "comments are the documentation; losing them makes the file unusable by hand");
        assertTrue(after.strip().endsWith("(x-release-please-version)"),
                "the trailer must survive or ConfigMigrator can no longer detect the schema");
        assertEquals(ConfigMigrator.extractVersion(before), ConfigMigrator.extractVersion(after));
    }

    @Test
    @DisplayName("an unknown key reports failure instead of corrupting the file")
    void unknownKeyIsRejected() throws Exception {
        assertFalse(ConfigMigrator.setScalar(config.toFile(), "webhook.does_not_exist", "x"));
        assertEquals(before, Files.readString(config), "a failed write must leave the file alone");
    }

    @Test
    @DisplayName("a missing file fails rather than throwing")
    void missingFileIsHandled() {
        assertFalse(ConfigMigrator.setScalar(new File(dir.toFile(), "nope.yml"), "webhook.url", "x"));
    }

    @Test
    @DisplayName("a nested key deeper than the old schema's maximum still resolves")
    void writesDeeplyNestedKeys() throws Exception {
        // v10 toggles sit four levels down; the line finder predates that depth.
        assertTrue(ConfigMigrator.setScalar(config.toFile(), "log.player.join.enabled", false));

        Map<?, ?> parsed = (Map<?, ?>) new Yaml().load(Files.readString(config));
        Map<?, ?> log = (Map<?, ?>) parsed.get("log");
        Map<?, ?> player = (Map<?, ?>) log.get("player");
        assertEquals(false, ((Map<?, ?>) player.get("join")).get("enabled"));
    }
}
