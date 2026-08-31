package com.discordlogger.lang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every user-facing string, frozen at the wording that was reviewed.
 *
 * <h2>Why a snapshot and not a rule</h2>
 *
 * <p>No rule can tell "Fell from a high place" from "Fell from a high palce". Whether a
 * sentence is right is a human judgement, so it is made once and then held: these are
 * the strings as approved from production use of the plugin, and this fails when any of
 * them changes.
 *
 * <p>Comparing the plugin's output against {@code lang.yml} instead would prove nothing,
 * because that is where the output comes from. A typo introduced there would match
 * itself perfectly. Frozen separately, a changed string has to be looked at again.
 *
 * <p>Failing here is not "you did something wrong". It means a shipped string moved, and
 * the diff needs an owner's eye before it ships. Re-approving is one command, and the
 * message below says which.
 */
class ApprovedStringsTest {

    private static final Path BASELINE =
            Path.of("src", "test", "resources", "baseline", "approved-strings.json");

    /** Flattens lang.yml the same way the baseline was built. */
    private static Map<String, String> shippedStrings() throws Exception {
        final Map<String, Object> lang;
        try (InputStream in = ApprovedStringsTest.class.getResourceAsStream("/lang.yml")) {
            lang = new Yaml().load(in);
        }
        final Map<String, String> out = new TreeMap<>();
        flatten(lang, "", out);
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
        } else if (node instanceof String) {
            out.put(path, (String) node);
        }
    }

    /** The approved strings, read without a JSON library to keep the plugin dependency-free. */
    private static Map<String, String> approved() throws Exception {
        final String json = Files.readString(BASELINE, StandardCharsets.UTF_8);
        final Map<String, String> out = new LinkedHashMap<>();
        for (String section : new String[]{"discord", "chat"}) {
            final int start = json.indexOf("\"" + section + "\": {");
            if (start < 0) continue;
            int depth = 0;
            int i = json.indexOf('{', start);
            final int from = i;
            for (; i < json.length(); i++) {
                if (json.charAt(i) == '{') depth++;
                else if (json.charAt(i) == '}' && --depth == 0) break;
            }
            final String body = json.substring(from, i);
            final var m = Pattern.compile(
                    "\"([^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(body);
            while (m.find()) {
                out.put(m.group(1), m.group(2)
                        .replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\"));
            }
        }
        return out;
    }

    @Test
    @DisplayName("no shipped string has changed since it was approved")
    void stringsMatchTheApprovedBaseline() throws Exception {
        final Map<String, String> shipped = shippedStrings();
        final Map<String, String> approved = approved();
        assertTrue(approved.size() > 50, "the baseline looks empty: " + approved.size());

        final List<String> drift = new ArrayList<>();
        for (Map.Entry<String, String> e : approved.entrySet()) {
            final String now = shipped.get(e.getKey());
            if (now == null) {
                drift.add("REMOVED  " + e.getKey() + "\n    was: " + e.getValue());
            } else if (!now.equals(e.getValue())) {
                drift.add("CHANGED  " + e.getKey()
                        + "\n    was: " + e.getValue()
                        + "\n    now: " + now);
            }
        }
        for (String key : shipped.keySet()) {
            if (!approved.containsKey(key) && (key.startsWith("discord.") || key.startsWith("chat."))) {
                drift.add("ADDED    " + key + "\n    now: " + shipped.get(key));
            }
        }

        assertEquals(List.of(), drift,
                "shipped wording has moved away from what was approved.\n\n"
                        + String.join("\n", drift)
                        + "\n\nIf these changes are intended, re-approve them with:\n"
                        + "    python3 scripts/approve-strings.py\n"
                        + "and include the updated baseline in the same commit.");
    }

    @Test
    @DisplayName("no approved string carries an unresolved placeholder or stray tag")
    void approvedStringsAreWellFormed() throws Exception {
        // Mechanically decidable faults, which stay wrong under any wording. These are
        // checked against the approved text rather than the output, so they are caught
        // at the source instead of once per event that renders them.
        final List<String> faults = new ArrayList<>();
        for (Map.Entry<String, String> e : approved().entrySet()) {
            final String key = e.getKey();
            final String value = e.getValue();

            if (value.trim().isEmpty()) faults.add(key + " is empty");
            if (value.contains("null")) faults.add(key + " contains the literal \"null\": " + value);
            // Only mid-sentence. Leading spaces are indentation -- chat.help-entry
            // indents each command under its header, and flagging that would be a rule
            // objecting to a deliberate layout rather than finding a fault.
            final String visible = value.replaceAll("</?[a-z][a-z0-9_#:-]*>", "");
            if (visible.strip().contains("  ")) {
                faults.add(key + " has a double space mid-sentence: " + value);
            }
            if (value.contains("::")) faults.add(key + " has a doubled colon: " + value);

            // Discord takes plain text. A MiniMessage tag there is posted literally.
            if (key.startsWith("discord.") && Pattern.compile("</?[a-z][a-z0-9_#:-]*>")
                    .matcher(value).find()) {
                faults.add(key + " carries a MiniMessage tag, which Discord shows literally: " + value);
            }
        }
        assertEquals(List.of(), faults, String.join("\n", faults));
    }
}
