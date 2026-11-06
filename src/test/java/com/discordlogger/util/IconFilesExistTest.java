package com.discordlogger.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every icon the plugin can ask Discord for is a file the site actually serves.
 *
 * <p>A missing one is not an error anywhere: the POST succeeds, Discord fetches the
 * URL, gets a 404 and renders the embed with no thumbnail. Nobody files a bug for a
 * picture that was never there, so this is the only thing that would notice.
 *
 * <p>It reads the names out of the source rather than listing them here, because a
 * list kept by hand is the same problem one level up.
 */
class IconFilesExistTest {

    private static final Path SOURCE = Paths.get("src/main/java/com/discordlogger");
    private static final Path ICONS  = Paths.get("docs/assets/icons");

    /** A full URL written out, e.g. the lag clock or the shared server block. */
    private static final Pattern DIRECT =
            Pattern.compile("assets/icons/([A-Za-z0-9_./-]+\\.(?:png|gif))");

    /** A folder constant plus a leaf, e.g. ICON_BASE + "tnt.png". */
    private static final Pattern JOINED =
            Pattern.compile("ICON_BASE\\s*\\+\\s*\"([A-Za-z0-9_./-]+\\.(?:png|gif))\"");

    /** The folder a joined name hangs off, declared once per listener. */
    private static final Pattern BASE =
            Pattern.compile("ICON_BASE\\s*=\\s*\\n?\\s*\"[^\"]*assets/icons/([A-Za-z0-9_/-]*)\"");

    @Test
    @DisplayName("every icon the source names exists under docs/assets/icons")
    void everyReferencedIconIsOnDisk() throws IOException {
        final Set<String> wanted = new LinkedHashSet<>();

        try (Stream<Path> files = Files.walk(SOURCE)) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
                final String src = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);

                final Matcher direct = DIRECT.matcher(src);
                while (direct.find()) wanted.add(direct.group(1));

                final Matcher base = BASE.matcher(src);
                final String folder = base.find() ? base.group(1) : "";
                final Matcher joined = JOINED.matcher(src);
                while (joined.find()) wanted.add(folder + joined.group(1));
            }
        }

        // The two sets ServerFire maps causes through, which are names, not literals.
        for (String cause : causesOf("ICONS")) {
            wanted.add("fire/" + cause.toLowerCase(Locale.ROOT) + ".png");
        }
        for (String cause : causesOf("ANIMATED")) {
            wanted.add("fire/" + cause.toLowerCase(Locale.ROOT) + ".gif");
        }

        assertFalse(wanted.isEmpty(), "found no icon references at all — the patterns have rotted");

        final List<String> missing = new ArrayList<>();
        for (String name : wanted) {
            if (!Files.isRegularFile(ICONS.resolve(name))) missing.add(name);
        }
        assertTrue(missing.isEmpty(),
                "referenced but not served: " + missing + " (a 404 shows as a blank thumbnail)");
    }

    /** The constants listed in one of {@code ServerFire}'s cause sets. */
    private static List<String> causesOf(String field) throws IOException {
        final String src = new String(Files.readAllBytes(
                SOURCE.resolve("listener/server/ServerFire.java")), StandardCharsets.UTF_8);
        final Matcher block = Pattern.compile(
                field + "\\s*=[^;]*?asList\\(([^)]*)\\)", Pattern.DOTALL).matcher(src);
        assertTrue(block.find(), "could not read ServerFire's " + field + " set");

        final List<String> out = new ArrayList<>();
        final Matcher each = Pattern.compile("\"([A-Z_]+)\"").matcher(block.group(1));
        while (each.find()) out.add(each.group(1));
        assertFalse(out.isEmpty(), field + " parsed as empty");
        return out;
    }
}
