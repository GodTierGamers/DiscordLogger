package com.discordlogger.acceptance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every setting the plugin ships is accounted for.
 *
 * <h2>What this is for</h2>
 *
 * <p>"Everything is tested" decays the moment someone adds a setting, and it decays
 * silently: the suite still passes, and nothing anywhere says the new key has never
 * been driven. This makes that impossible. Each key in the shipped config.yml is
 * either covered by a case or explicitly listed as pending with a reason, and a key in
 * neither fails the build.
 *
 * <p>So the failure mode is inverted. Adding a setting without a test is now a red
 * build with the key named, rather than a claim of full coverage that quietly stopped
 * being true.
 *
 * <p>Pending entries are deliberately allowed. Recording honestly that eighty-two keys
 * exist and forty are driven is worth more than pretending otherwise, and the count is
 * visible in the failure text as it shrinks.
 */
class ConfigCoverageTest {

    private static final Path REGISTRY =
            Path.of("src", "test", "resources", "baseline", "config-coverage.json");
    private static final Path SHIPPED_CONFIG =
            Path.of("..", "src", "main", "resources", "config.yml");

    /** Every settable leaf in the shipped config, dotted. */
    private static Map<String, String> shippedKeys() throws Exception {
        final Map<String, Object> cfg;
        try (InputStream in = Files.newInputStream(SHIPPED_CONFIG)) {
            cfg = new Yaml().load(in);
        }
        final Map<String, String> out = new TreeMap<>();
        flatten(cfg, "", out);
        out.remove("config-version");
        return out;
    }

    private static void flatten(Object node, String path, Map<String, String> out) {
        if (node instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) node).entrySet()) {
                final String key = path.isEmpty()
                        ? String.valueOf(e.getKey()) : path + "." + e.getKey();
                flatten(e.getValue(), key, out);
            }
        } else {
            out.put(path, node instanceof List ? "list"
                    : node == null ? "null" : node.getClass().getSimpleName());
        }
    }

    /** The keys the registry knows about, and which of them have a case. */
    private static Set<String> registered(boolean withCaseOnly) throws Exception {
        final String json = Files.readString(REGISTRY, StandardCharsets.UTF_8);
        // Scoped to the body of "keys" rather than the whole file. Matching the file
        // meant the wrapper "keys": { matched the same pattern and swallowed the first
        // entry inside it, which reported exactly one key as missing -- a failure that
        // looked like a gap in the registry rather than a bug in the reader.
        final int start = json.indexOf("\"keys\"");
        if (start < 0) throw new IllegalStateException("registry has no \"keys\" object");
        final int open = json.indexOf('{', start);
        int depth = 0;
        int end = open;
        for (; end < json.length(); end++) {
            if (json.charAt(end) == '{') depth++;
            else if (json.charAt(end) == '}' && --depth == 0) break;
        }
        final String body = json.substring(open + 1, end);

        final Set<String> out = new LinkedHashSet<>();
        final Matcher m = Pattern.compile(
                "\"([a-z_.0-9]+)\"\\s*:\\s*\\{([^}]*)}").matcher(body);
        while (m.find()) {
            final boolean hasCase = m.group(2).contains("\"case\"");
            if (!withCaseOnly || hasCase) out.add(m.group(1));
        }
        return out;
    }

    @Test
    @DisplayName("every shipped setting is either covered or explicitly pending")
    void everyKeyIsAccountedFor() throws Exception {
        final Map<String, String> shipped = shippedKeys();
        final Set<String> known = registered(false);

        final List<String> unaccounted = new ArrayList<>();
        for (String key : shipped.keySet()) {
            if (!known.contains(key)) unaccounted.add(key + "  (" + shipped.get(key) + ")");
        }
        assertEquals(List.of(), unaccounted,
                "these settings ship but are not in the coverage registry, so nothing "
                        + "drives them and nothing says so:\n  "
                        + String.join("\n  ", unaccounted)
                        + "\n\nAdd each to acceptance/src/test/resources/baseline/"
                        + "config-coverage.json, with a case if it is driven or a pending "
                        + "reason if it is not yet.");
    }

    @Test
    @DisplayName("the registry does not name settings that no longer ship")
    void registryHasNoGhosts() throws Exception {
        // A key removed from config.yml but left here would count towards coverage
        // forever while testing nothing.
        final Map<String, String> shipped = shippedKeys();
        final List<String> ghosts = new ArrayList<>();
        for (String key : registered(false)) {
            if (!shipped.containsKey(key)) ghosts.add(key);
        }
        assertEquals(List.of(), ghosts,
                "the registry lists settings the plugin no longer ships: " + ghosts);
    }

    @Test
    @DisplayName("coverage is reported, so the number is visible rather than assumed")
    void reportCoverage() throws Exception {
        final int total = shippedKeys().size();
        final int driven = registered(true).size();
        // Not an assertion on the number: this suite is being built, and a threshold
        // would either be met trivially or block the work that raises it. It prints, so
        // the figure appears in every run rather than being something people believe.
        System.out.printf("%n  config coverage: %d of %d settings driven (%.0f%%)%n",
                driven, total, 100.0 * driven / total);
        assertTrue(total > 0, "no settings found in the shipped config");
    }
}
