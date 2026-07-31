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

    /** What the on-disk config turned out to be, relative to the one in this JAR. */
    public enum Status {
        /** No config existed; the shipped default was written out. */
        FRESH_INSTALL,
        /** On-disk schema matches this build. Nothing to do. */
        UP_TO_DATE,
        /** On-disk schema was older and has been migrated forward. */
        UPGRADED,
        /**
         * On-disk schema is NEWER than this build understands — the server was
         * downgraded, or a config was copied from a newer install. Deliberately
         * left untouched: migrating "forward" to an older schema would silently
         * throw away settings the user wrote against the newer one.
         */
        AHEAD,
        /** A version trailer was missing or unparseable at one end. */
        UNKNOWN
    }

    /** Outcome plus the two schema numbers, for messaging. Either may be null when UNKNOWN. */
    public record Result(Status status, Integer installed, Integer shipped) {
        public boolean migrated() { return status == Status.UPGRADED; }
    }

    /**
     * The migrate/leave-alone/complain decision, as a pure function of the two
     * schema numbers. Split out from the file handling so it can be exercised
     * directly — the surrounding method needs a running server, this does not.
     */
    public static Status decide(Integer installed, Integer shipped) {
        if (installed == null || shipped == null) return Status.UNKNOWN;
        if (installed.equals(shipped)) return Status.UP_TO_DATE;
        return shipped > installed ? Status.UPGRADED : Status.AHEAD;
    }

    /** The config schema number baked into this JAR. Null if the trailer is missing. */
    public static Integer shippedVersion(JavaPlugin plugin, String resourcePath) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) return null;
            return extractVersion(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return null;
        }
    }

    public static Result migrateIfVersionChanged(JavaPlugin plugin, String resourcePath, File userFile) {
        try {
            if (userFile == null) userFile = new File(plugin.getDataFolder(), "config.yml");

            // Load default config text (verbatim from JAR)
            final String defaultText;
            try (InputStream in = plugin.getResource(resourcePath)) {
                if (in == null) {
                    plugin.getLogger().warning("Default resource not found: " + resourcePath);
                    return new Result(Status.UNKNOWN, null, null);
                }
                defaultText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            final Integer newVer = extractVersion(defaultText);

            // If user file missing → write default and return (fresh install; no migration)
            if (!userFile.exists()) {
                Files.createDirectories(userFile.getParentFile().toPath());
                Files.writeString(userFile.toPath(), defaultText, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                return new Result(Status.FRESH_INSTALL, newVer, newVer);
            }

            // Read user's current config (verbatim)
            final String userText = Files.readString(userFile.toPath(), StandardCharsets.UTF_8);
            final Integer oldVer = extractVersion(userText);

            // Migrate ONLY forward. A config newer than this build is left exactly as
            // it is: rewriting it against an older shipped default would drop whatever
            // keys the newer schema added, which is data loss, not a migration.
            final Status decision = decide(oldVer, newVer);
            if (decision != Status.UPGRADED) {
                return new Result(decision, oldVer, newVer);
            }

            // Parse both YAMLs to find scalar leaves to transplant
            Map<String, Object> defMap = flattenYaml(new Yaml().load(defaultText));
            Map<String, Object> usrMap = flattenYaml(new Yaml().load(userText));

            // Start from default lines; we will replace values in-place (preserves comments)
            List<String> defLines = Arrays.asList(defaultText.split("\r?\n", -1));
            List<String> newLines = new ArrayList<>(defLines);

            // Transplant user values for keys that still exist in defaults, following
            // any rename the new schema introduced. Without this step a schema that
            // relocates keys silently resets every setting the user ever changed:
            // the old path is absent from the new defaults, so the value is dropped
            // and the default wins. That is indistinguishable, to the user, from the
            // plugin ignoring their config.
            plugin.getLogger().info("Migrating config schema v" + oldVer + " -> v" + newVer
                    + " (one step at a time, so renames from every intermediate version apply)");

            for (Map.Entry<String, Object> e : usrMap.entrySet()) {
                String target = resolvePath(e.getKey(), defMap, oldVer, newVer);
                if (target == null) continue;  // genuinely removed -> keep the default
                replaceLeafValueInDefault(newLines, defLines, target, e.getValue());
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
            return new Result(Status.UPGRADED, oldVer, newVer);

        } catch (Exception ex) {
            plugin.getLogger().severe("Config migration failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return new Result(Status.UNKNOWN, null, null);
        }
    }

    /**
     * Maps a path from the user's config onto its equivalent in the new defaults,
     * applying each schema step in turn rather than jumping straight to the target.
     *
     * <p>Stepping matters because renames compose. Going v6 to v10 in one hop, only
     * the v9-to-v10 renames would be recognised, and a v6 user's colours — which were
     * flat ({@code embeds.colors.player_join}) before v7 nested them — would match
     * nothing and be silently replaced by defaults. Walking 6→7→8→9→10 renames the
     * key at each step, so it arrives in a shape the final schema recognises.
     *
     * <p>Schema history, from the shipped config's own git history:
     * <pre>
     *   v2→v3, v3→v4, v4→v5, v5→v6   pure additions, nothing moved
     *   v6→v7   colours went flat → nested, moderation colours gained their group
     *   v7→v8, v8→v9                 pure additions, nothing moved
     *   v9→v10  colours moved beside their toggle; toggles gained ".enabled"
     * </pre>
     *
     * @return the path in the target schema, or null if the key is genuinely gone
     */
    static String resolvePath(String path, Map<String, Object> defMap, int from, int to) {
        String current = path;
        for (int v = from; v < to; v++) {
            current = step(v, current);
            if (current == null) return null;   // dropped by that step
        }
        return defMap.containsKey(current) ? current : null;
    }

    /** One schema step: a path as written in schema {@code from}, renamed for {@code from + 1}. */
    private static String step(int from, String path) {
        switch (from) {
            case 6:  return v6ToV7(path);
            case 9:  return v9ToV10(path);
            // Every other step only ADDED keys, so existing paths carry over as-is.
            // A step that starts moving keys must get a case here, or upgrades from
            // before it will quietly lose those settings.
            default: return path;
        }
    }

    /** v6→v7: embed colours went from flat keys to a nested tree grouped by category. */
    private static String v6ToV7(String path) {
        if (!path.startsWith("embeds.colors.")) return path;
        String key = path.substring("embeds.colors.".length());

        // "player_join" -> "player.join"  (also server_command, etc.)
        int us = key.indexOf('_');
        if (us > 0) {
            String group = key.substring(0, us);
            if (group.equals("player") || group.equals("server")) {
                return "embeds.colors." + group + "." + key.substring(us + 1);
            }
        }
        // Moderation colours were bare in v6: "ban" -> "moderation.ban".
        if (key.equals("ban") || key.equals("unban") || key.equals("kick")) {
            return "embeds.colors.moderation." + key;
        }
        // v6's "embeds.colors.server" was a single fallback colour; v7 turned that
        // name into a section, so the scalar has no successor.
        if (key.equals("server")) return null;
        return path;
    }

    /** v9→v10: each event became a section holding its own toggle and colour. */
    private static String v9ToV10(String path) {
        // log.<group>.<event>  ->  log.<group>.<event>.enabled
        if (path.startsWith("log.") && path.chars().filter(ch -> ch == '.').count() == 2) {
            return path + ".enabled";
        }
        // embeds.colors.<group>.<event>  ->  log.<group>.<event>.color
        if (path.startsWith("embeds.colors.")) {
            String rest = path.substring("embeds.colors.".length());
            int dot = rest.indexOf('.');
            if (dot <= 0) return null;
            String group = rest.substring(0, dot);
            String event = rest.substring(dot + 1);
            // v9 called the whitelist-entries colour "whitelist"; the toggle it now
            // lives under is "whitelist_edit".
            if ("moderation".equals(group) && "whitelist".equals(event)) {
                event = "whitelist_edit";
            }
            return "log." + group + "." + event + ".color";
        }
        return path;
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
