package com.discordlogger.config;

import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfigMigrator {
    private ConfigMigrator(){}

    // Trailer marker, e.g. "# CONFIG VERSION V4, SHIPPED WITH V2.1.0"
    private static final Pattern VERSION_RE =
            Pattern.compile("CONFIG\\s+VERSION\\s+V(\\d+)", Pattern.CASE_INSENSITIVE);

    /** Returns true if a migration happened (files rotated). */
    public static boolean migrateIfVersionChanged(JavaPlugin plugin, String resourcePath, File userFile) {
        try {
            if (userFile == null) userFile = new File(plugin.getDataFolder(), "config.yml");

            // Load default config text (verbatim from JAR)
            final String defaultText;
            try (InputStream in = plugin.getResource(resourcePath)) {
                if (in == null) {
                    plugin.getLogger().warning("Default resource not found: " + resourcePath);
                    return false;
                }
                defaultText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            final Integer newVer = extractVersion(defaultText);

            // If user file missing → write default and return (fresh install; no migration)
            if (!userFile.exists()) {
                Files.createDirectories(userFile.getParentFile().toPath());
                Files.writeString(userFile.toPath(), defaultText, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                return false;
            }

            // Read user's current config (verbatim)
            final String userText = Files.readString(userFile.toPath(), StandardCharsets.UTF_8);
            final Integer oldVer = extractVersion(userText);

            // Guard: migrate ONLY when both versions exist and differ
            if (newVer == null || oldVer == null || newVer.equals(oldVer)) {
                // Do nothing if versions are the same or if we can’t detect either version
                return false;
            }

            // Parse both YAMLs to find scalar leaves to transplant
            Map<String, Object> defMap = flattenYaml(new Yaml().load(defaultText));
            Map<String, Object> usrMap = flattenYaml(new Yaml().load(userText));

            // Start from default lines; we will replace values in-place (preserves comments)
            List<String> defLines = Arrays.asList(defaultText.split("\r?\n", -1));
            List<String> newLines = new ArrayList<>(defLines);

            // Transplant user values for keys that still exist in defaults
            for (Map.Entry<String, Object> e : usrMap.entrySet()) {
                String path = e.getKey();
                if (!defMap.containsKey(path)) continue; // key removed/renamed in new defaults -> keep default
                replaceLeafValueInDefault(newLines, defLines, path, e.getValue());
            }

            // Write config.new.yml
            File newFile = new File(userFile.getParentFile(), "config.new.yml");
            Files.writeString(newFile.toPath(), String.join("\n", newLines), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            // Rotate: config.yml -> config.old.yml, new -> config.yml
            File oldFile = new File(userFile.getParentFile(), "config.old.yml");
            try {
                Files.move(userFile.toPath(), oldFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not move config.yml to config.old.yml: " + ex.getMessage());
            }
            Files.move(newFile.toPath(), userFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            plugin.getLogger().info("Config updated automatically from version " + oldVer + " to " + newVer);
            plugin.getLogger().info("Previous file saved as config.old.yml");
            return true;

        } catch (Exception ex) {
            plugin.getLogger().severe("Config migration failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return false;
        }
    }

    // ------- helpers -------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> flattenYaml(Object root) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!(root instanceof Map)) return out;
        walk("", (Map<String, Object>) root, out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void walk(String prefix, Map<String, Object> node, Map<String, Object> out) {
        for (Map.Entry<String, Object> e : node.entrySet()) {
            String key = e.getKey();
            Object val = e.getValue();
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (val instanceof Map) {
                walk(path, (Map<String, Object>) val, out);
            } else {
                out.put(path, val);
            }
        }
    }

    /** Replace a scalar leaf's value in the default text lines (preserving inline comments). */
    /** Replace a scalar leaf's value in the default text lines (preserving inline comments). */
    private static void replaceLeafValueInDefault(
            java.util.List<String> newLines,
            java.util.List<String> defLines,
            String path,
            Object userVal
    ) {
        LeafPos pos = findLeafLine(defLines, path);
        if (pos == null) return;

        String line = newLines.get(pos.index);

        // find the colon after the key at the known indent
        int colon = line.indexOf(':', pos.keyIndent);
        if (colon < 0) return;

        // skip spaces after colon to find value start
        int i = colon + 1;
        while (i < line.length() && line.charAt(i) == ' ') i++;
        int valueStart = i;

        // find end-of-value: first unquoted '#' (start of inline comment) or EOL
        int valueEnd = findUnquotedHash(line, valueStart);
        if (valueEnd < 0) valueEnd = line.length(); // no inline comment

        // Keep everything before the value as-is
        String before = line.substring(0, valueStart);

        // The rest (either inline comment starting at '#', or empty)
        String after = line.substring(valueEnd);

        // Ensure a single space before an inline comment, so values don't touch '#'
        String sep = after.startsWith("#") ? " " : "";

        String rendered = renderScalar(userVal);
        newLines.set(pos.index, before + rendered + sep + after);
    }


    /** Find index of the first '#' that is NOT inside single/double quotes; return -1 if none. */
    private static int findUnquotedHash(String s, int from) {
        boolean inSingle = false, inDouble = false;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && !inSingle) inDouble = !inDouble;
            else if (c == '\'' && !inDouble) inSingle = !inSingle;
            else if (c == '#' && !inSingle && !inDouble) return i;
        }
        return -1;
    }

    /** Render a YAML scalar: booleans/numbers bare; strings quoted (safe). */
    private static String renderScalar(Object v) {
        if (v == null) return "null";
        if (v instanceof Boolean) return ((Boolean) v) ? "true" : "false";
        if (v instanceof Number)  return v.toString();
        String s = v.toString();
        // always quote strings to be safe; escape inner quotes
        return "\"" + s.replace("\"", "\\\"") + "\"";
    }


    /** Locate the exact leaf line in default lines for a dotted path (scalar only). */
    private static LeafPos findLeafLine(List<String> lines, String dottedPath) {
        String[] segs = dottedPath.split("\\.");
        int depth = 0;
        int i = 0;
        int expectedIndent = 0;

        while (depth < segs.length - 1) {
            String key = segs[depth];
            int foundAt = findSectionHeader(lines, i, expectedIndent, key);
            if (foundAt < 0) return null;
            i = foundAt + 1;
            expectedIndent += 2;
            depth++;
        }
        String leafKey = segs[segs.length - 1];
        // search for "leafKey:" at expectedIndent
        for (int idx = i; idx < lines.size(); idx++) {
            String ln = lines.get(idx);
            if (ln.strip().isEmpty() || ln.strip().startsWith("#")) continue;
            int ind = leadingSpaces(ln);
            if (ind < expectedIndent) break;      // out of section
            if (ind > expectedIndent) continue;   // deeper child
            String trimmed = ln.strip();
            if (trimmed.startsWith(leafKey + ":")) {
                return new LeafPos(idx, expectedIndent);
            }
        }
        return null;
    }

    /** Find "key:" at given indent, scanning from 'from' index. */
    private static int findSectionHeader(List<String> lines, int from, int indent, String key) {
        for (int i = from; i < lines.size(); i++) {
            String ln = lines.get(i);
            if (ln.strip().isEmpty() || ln.strip().startsWith("#")) continue;
            int ind = leadingSpaces(ln);
            if (ind < indent) return -1;
            if (ind != indent) continue;
            String trimmed = ln.strip();
            if (trimmed.startsWith(key + ":")) return i;
        }
        return -1;
    }

    private static int leadingSpaces(String s) {
        int i = 0; while (i < s.length() && s.charAt(i) == ' ') i++; return i;
    }

    private static Integer extractVersion(String text) {
        if (text == null) return null;
        Matcher m = VERSION_RE.matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    /** Simple struct for leaf position. */
    private record LeafPos(int index, int keyIndent) {}
}
