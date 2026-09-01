package com.discordlogger.acceptance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every line in the shipped lang.yml is accounted for.
 *
 * <p>The same guard {@link ConfigCoverageTest} puts on config.yml, for the other file
 * admins edit. A line added to lang.yml is either swept or explicitly listed as pending
 * with a reason; a line in neither fails the build.
 *
 * <p>Without this, the honest answer to "is every line tested?" decays quietly with
 * every release that adds one, and nobody finds out until a line nobody drove turns out
 * to have been broken for months.
 */
class LangCoverageTest {

    private static final Path REGISTRY =
            Path.of("src", "test", "resources", "baseline", "lang-coverage.json");
    private static final Path LANG =
            Path.of("..", "src", "main", "resources", "lang.yml");

    @Test
    @DisplayName("every line in lang.yml is swept or listed as pending")
    void nothingIsUnaccountedFor() throws Exception {
        final Map<String, String> shipped =
                LangSweepTest.flatten(Files.readString(LANG, StandardCharsets.UTF_8));
        final Set<String> known = registered(false);

        final List<String> unaccounted = new ArrayList<>();
        for (String key : shipped.keySet()) {
            if (!known.contains(key)) unaccounted.add(key);
        }
        assertEquals(List.of(), unaccounted,
                "these lines are in lang.yml but in no sweep and on no pending list:\n  "
                        + String.join("\n  ", unaccounted)
                        + "\n\nAdd each to lang-coverage.json, with a case if it is driven "
                        + "or a pending reason if it is not.");
    }

    @Test
    @DisplayName("the registry names no line that has been removed")
    void noGhosts() throws Exception {
        final Map<String, String> shipped =
                LangSweepTest.flatten(Files.readString(LANG, StandardCharsets.UTF_8));
        final List<String> ghosts = new ArrayList<>();
        for (String key : registered(false)) {
            if (!shipped.containsKey(key)) ghosts.add(key);
        }
        assertEquals(List.of(), ghosts,
                "these lines are in the registry but no longer in lang.yml:\n  "
                        + String.join("\n  ", ghosts));
    }

    @Test
    @DisplayName("how much of lang.yml is driven")
    void report() throws Exception {
        final int total = registered(false).size();
        final int driven = registered(true).size();
        System.out.printf("%n  lang coverage: %d of %d lines driven (%.0f%%)%n",
                driven, total, 100.0 * driven / total);
        assertTrue(total > 0, "no lines found in the shipped lang.yml");
    }

    /** The keys the registry knows about, and which of them have a case. */
    private static Set<String> registered(boolean withCaseOnly) throws Exception {
        final String json = Files.readString(REGISTRY, StandardCharsets.UTF_8);
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
                "\"([a-zA-Z_.\\-0-9]+)\"\\s*:\\s*\\{([^}]*)}").matcher(body);
        while (m.find()) {
            if (!withCaseOnly || m.group(2).contains("\"case\"")) out.add(m.group(1));
        }
        return out;
    }
}
