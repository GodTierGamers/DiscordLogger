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
    public static final class Result {
        private final Status status;
        private final Integer installed;
        private final Integer shipped;

        public Result(Status status, Integer installed, Integer shipped) {
            this.status = status;
            this.installed = installed;
            this.shipped = shipped;
        }

        public Status status()     { return status; }
        public Integer installed() { return installed; }
        public Integer shipped()   { return shipped; }

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

    /**
     * Produces the migrated config text: the new schema's file, with the user's
     * values transplanted onto it.
     *
     * <p>Split out from {@link #migrateIfVersionChanged} so the part that can lose
     * someone's settings is a pure function of two strings — no server, no files.
     * What remains around it is only I/O and file rotation.
     *
     * @param defaultText the config bundled with this build (the target schema)
     * @param userText    the config currently on disk
     * @param fromVersion the schema {@code userText} is written in
     * @param toVersion   the schema {@code defaultText} is written in
     */
    static String migrateText(String defaultText, String userText, int fromVersion, int toVersion) {
        Map<String, Object> defMap = flattenYaml(new Yaml().load(defaultText));
        Map<String, Object> usrMap = flattenYaml(new Yaml().load(userText));

        // Start from the default's lines and replace values in place, which is what
        // preserves the comments, banners and trailer.
        List<String> defLines = Arrays.asList(defaultText.split("\r?\n", -1));
        List<String> newLines = new ArrayList<>(defLines);

        // Follow any rename the new schema introduced. Without this a schema that
        // relocates keys silently resets every setting the user ever changed: the old
        // path is absent from the new defaults, so the value is dropped and the
        // default wins — indistinguishable, to the user, from the plugin ignoring
        // their config.
        // Two passes. Scalars are replaced in place, which keeps every line index
        // aligned with defLines. Lists change the line count, so they are collected
        // and spliced afterwards from the bottom up — applying them top-down would
        // invalidate every index after the first splice.
        final class ListEdit {
            private final int from;
            private final int to;
            private final List<String> block;

            ListEdit(int from, int to, List<String> block) {
                this.from = from;
                this.to = to;
                this.block = block;
            }

            int from() { return from; }
            int to() { return to; }
            List<String> block() { return block; }
        }
        final List<ListEdit> listEdits = new ArrayList<>();

        for (Map.Entry<String, Object> e : usrMap.entrySet()) {
            String target = resolvePath(e.getKey(), defMap, fromVersion, toVersion);
            if (target == null) continue;  // genuinely removed -> keep the default

            if (e.getValue() instanceof List<?>) {
                final List<?> userList = (List<?>) e.getValue();
                final int[] span = listSpanInDefault(defLines, target);
                if (span != null) {
                    listEdits.add(new ListEdit(span[0], span[1],
                            renderList(defLines.subList(span[0], span[1]), userList)));
                }
                continue;
            }
            replaceLeafValueInDefault(newLines, defLines, target, e.getValue());
        }

        listEdits.sort((a, b) -> Integer.compare(b.from(), a.from()));
        for (ListEdit edit : listEdits) {
            newLines.subList(edit.from(), edit.to()).clear();
            newLines.addAll(edit.from(), edit.block());
        }

        return String.join("\n", newLines);
    }

    /**
     * The line range a list value occupies in the defaults, as {@code [from, toExclusive)}.
     *
     * <p>Handles both forms the config uses: an inline empty list ({@code key: []}),
     * which is one line, and a block list whose items follow on their own lines.
     *
     * @return the span, or null if the key is not a list here
     */
    private static int[] listSpanInDefault(List<String> lines, String path) {
        final LeafPos pos = findLeafLine(lines, path);
        if (pos == null) return null;

        final String keyLine = lines.get(pos.index());
        final int colon = keyLine.indexOf(':', pos.keyIndent());
        if (colon < 0) return null;

        final String after = stripInlineComment(keyLine.substring(colon + 1)).trim();
        if (!after.isEmpty()) {
            // Inline: "key: []" or "key: [a, b]". One line either way.
            return after.startsWith("[") ? new int[]{pos.index(), pos.index() + 1} : null;
        }

        int end = pos.index() + 1;
        while (end < lines.size()) {
            final String line = lines.get(end);
            if (line.isBlank()) break;
            if (leadingSpaces(line) <= pos.keyIndent()) break;
            if (!line.strip().startsWith("-")) break;
            end++;
        }
        return new int[]{pos.index(), end};
    }

    /**
     * The user's list, written under the default's own key line and indentation.
     *
     * <p>Inline comments on the default's items are carried over to any item with the
     * same value. Without that, a shipped list like
     * {@code - EXIT_BED   # standing up from a bed} loses its explanation the first
     * time the file is migrated, and the config quietly becomes less readable on every
     * upgrade even for someone who changed nothing.
     */
    private static List<String> renderList(List<String> defaultBlock, List<?> values) {
        final String keyLine = defaultBlock.get(0);
        final int keyIndent = leadingSpaces(keyLine);

        // value -> the comment that followed it in the shipped file
        final Map<String, String> comments = new LinkedHashMap<>();
        for (int i = 1; i < defaultBlock.size(); i++) {
            final String line = defaultBlock.get(i);
            final String trimmed = line.strip();
            if (!trimmed.startsWith("-")) continue;
            final String afterDash = trimmed.substring(1);
            final int hash = findUnquotedHash(afterDash, 0);
            if (hash < 0) continue;
            final String value = afterDash.substring(0, hash).strip();
            if (value.isEmpty()) continue;
            // Keep the original spacing between the value and its comment, so an
            // unchanged file round-trips byte for byte rather than being reindented.
            final int valueEnd = afterDash.indexOf(value) + value.length();
            comments.put(value, afterDash.substring(valueEnd));
        }
        final int colon = keyLine.indexOf(':', keyIndent);
        final String comment = inlineCommentOf(keyLine.substring(colon + 1));
        final String key = keyLine.substring(0, colon + 1);

        final List<String> out = new ArrayList<>();
        if (values.isEmpty()) {
            out.add(key + " []" + comment);
            return out;
        }
        out.add(key + comment);
        final String indent = " ".repeat(keyIndent + 2);
        for (Object v : values) {
            final String rendered = renderListItem(v);
            final String itemComment = comments.get(rendered);
            out.add(indent + "- " + rendered + (itemComment == null ? "" : itemComment));
        }
        return out;
    }

    /**
     * A list item, quoted only when YAML would otherwise read it as something else.
     *
     * <p>{@code renderScalar} quotes every string, which is right for a value being
     * substituted into an existing line but wrong here: it would rewrite a hand-
     * written {@code - login} as {@code - "login"} on every migration, producing a
     * diff for a file nobody edited. Quoting stays for the cases that need it — an
     * entry like {@code no} or {@code 3} would otherwise come back as a boolean or a
     * number rather than the command name someone typed.
     */
    private static String renderListItem(Object v) {
        if (!(v instanceof String)) return renderScalar(v);
        final String s = (String) v;
        if (s.isEmpty() || !s.equals(s.strip())) return renderScalar(s);
        if (PLAIN_SAFE.matcher(s).matches() && !YAML_RESERVED.matcher(s).matches()) return s;
        return renderScalar(s);
    }

    /** Conservative: letters, digits and the punctuation a command or name actually uses. */
    private static final Pattern PLAIN_SAFE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.:@/-]*");

    /** Words YAML 1.1 resolves to a boolean or null, plus anything numeric. */
    private static final Pattern YAML_RESERVED = Pattern.compile(
            "(?i)y|n|yes|no|true|false|on|off|null|~|[+-]?\\d+(\\.\\d+)?");

    /** Everything before an unquoted '#', or the whole string when there is none. */
    private static String stripInlineComment(String s) {
        final int hash = findUnquotedHash(s, 0);
        return hash < 0 ? s : s.substring(0, hash);
    }

    /** The inline comment including its leading space, or "" when absent. */
    private static String inlineCommentOf(String s) {
        final int hash = findUnquotedHash(s, 0);
        return hash < 0 ? "" : " " + s.substring(hash).trim();
    }

    /**
     * Rewrites one scalar in config.yml in place, leaving every other byte alone.
     *
     * <p>Deliberately not {@code plugin.getConfig().set(...)} + {@code saveConfig()}:
     * that re-serialises the whole file through Bukkit's YAML writer, which drops
     * every comment — the banners, the per-option explanations, and, fatally, the
     * {@code CONFIG VERSION V<n>} trailer this class reads to decide whether to
     * migrate. A config saved that way would look like it had no schema at all.
     *
     * @return true if the key was found and rewritten
     */
    public static boolean setScalar(File configFile, String path, Object value) {
        try {
            List<String> lines = new ArrayList<>(
                    Arrays.asList(Files.readString(configFile.toPath(), StandardCharsets.UTF_8)
                            .split("\r?\n", -1)));
            List<String> reference = List.copyOf(lines);
            if (findLeafLine(reference, path) == null) return false;

            replaceLeafValueInDefault(lines, reference, path, value);
            Files.writeString(configFile.toPath(), String.join("\n", lines),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /** "lang.yml" -> "lang". Used to name the rotated files after their source. */
    private static String stripExtension(String fileName) {
        final int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
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
            final Map<String, Object> defFlat = flattenYaml(new Yaml().load(defaultText));
            final Integer newVer = detectVersion(defaultText, defFlat, m -> {});

            // If user file missing → write default and return (fresh install; no migration)
            if (!userFile.exists()) {
                Files.createDirectories(userFile.getParentFile().toPath());
                Files.writeString(userFile.toPath(), defaultText, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                return new Result(Status.FRESH_INSTALL, newVer, newVer);
            }

            // Read user's current config (verbatim)
            final String userText = Files.readString(userFile.toPath(), StandardCharsets.UTF_8);
            Map<String, Object> userFlat;
            try {
                userFlat = flattenYaml(new Yaml().load(userText));
            } catch (Exception malformed) {
                plugin.getLogger().warning(userFile.getName() + " could not be parsed: "
                        + malformed.getMessage());
                userFlat = new LinkedHashMap<>();
            }
            final Integer oldVer = detectVersion(userText, userFlat, plugin.getLogger()::warning);

            // Migrate ONLY forward. A config newer than this build is left exactly as
            // it is: rewriting it against an older shipped default would drop whatever
            // keys the newer schema added, which is data loss, not a migration.
            final Status decision = decide(oldVer, newVer);
            if (decision != Status.UPGRADED) {
                return new Result(decision, oldVer, newVer);
            }

            plugin.getLogger().info("Migrating " + userFile.getName() + " schema v" + oldVer + " -> v" + newVer
                    + " (one step at a time, so renames from every intermediate version apply)");

            final String merged = migrateText(defaultText, userText, oldVer, newVer);

            // Derived from the file being migrated, not hardcoded: this class now runs
            // for lang.yml as well, and a fixed "config.old.yml" would have had the
            // second migration overwrite the first one's backup.
            final String base = stripExtension(userFile.getName());

            File newFile = new File(userFile.getParentFile(), base + ".new.yml");
            Files.writeString(newFile.toPath(), merged, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            // Rotate: <name>.yml -> <name>.old.yml, new -> <name>.yml
            File oldFile = new File(userFile.getParentFile(), base + ".old.yml");
            try {
                Files.move(userFile.toPath(), oldFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not move " + userFile.getName()
                        + " to " + oldFile.getName() + ": " + ex.getMessage());
            }
            Files.move(newFile.toPath(), userFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            plugin.getLogger().info(userFile.getName() + " updated automatically from version "
                    + oldVer + " to " + newVer);
            plugin.getLogger().info("Previous file saved as " + oldFile.getName());
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
        // Never carried over. The freshly written file already declares the schema it
        // is; transplanting the user's old number would label a v10 file as v9 and
        // make the next start try to migrate it again.
        if ("config-version".equals(path)) return null;

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
    static Map<String, Object> flattenYaml(Object root) {
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

        // A key with NO value at all -- "custom:" -- flattens to a null leaf. Writing
        // renderScalar(null) produced "custom:null", and YAML with no space after the
        // colon is a plain SCALAR, not a mapping: the whole block below it stopped
        // parsing and migration corrupted the file it was meant to preserve.
        //
        // Leaving the default's own line untouched is also the right answer on its
        // merits. A null leaf carries nothing to transplant, so there is no user value
        // being dropped here -- only a rewrite that could never improve on what the
        // shipped default already says.
        if (userVal == null) return;

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

    /**
     * The schema a config file actually is.
     *
     * <p>Three sources, in decreasing durability:
     *
     * <ol>
     *   <li><b>Its shape</b> — which keys exist. Cannot be wrong, because it is not a
     *       claim about the file, it <i>is</i> the file. Used as the arbiter.</li>
     *   <li>The <b>{@code config-version} key</b>. Survives comment stripping and sits
     *       at the top of the file rather than the bottom, so it is far harder to
     *       lose by accident than the trailer it replaces.</li>
     *   <li>The <b>trailer comment</b>. Kept because every v9-and-earlier config in
     *       existence has one and no key, so it is the only marker on the files that
     *       most need upgrading.</li>
     * </ol>
     *
     * <p>When a declaration disagrees with the shape, the shape wins and the mismatch
     * is reported. The realistic cause is someone pasting an older config over a newer
     * one, or hand-editing the number; in both cases the keys are what the plugin has
     * to read, so they are what migration must be based on.
     */
    static Integer detectVersion(String text, Map<String, Object> flat, java.util.function.Consumer<String> warn) {
        final Integer declared = declaredVersion(text, flat);
        final int inferred = SchemaDetector.infer(flat);

        if (inferred == SchemaDetector.UNKNOWN) return declared;
        if (declared == null) return inferred;

        if (declared != inferred) {
            warn.accept("config.yml says it is schema v" + declared + ", but its keys are v"
                    + inferred + ". Going with v" + inferred + " — the keys are what the plugin "
                    + "actually reads. This usually means an older config was pasted over a "
                    + "newer one, or the version was edited by hand.");
        }
        return inferred;
    }

    /** The version the file claims: the config-version key first, else the trailer. */
    private static Integer declaredVersion(String text, Map<String, Object> flat) {
        final Object key = flat == null ? null : flat.get("config-version");
        if (key instanceof Number) return ((Number) key).intValue();
        if (key instanceof String) {
            final String str = (String) key;
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException ignored) {
                // Falls through to the trailer.
            }
        }
        return extractVersion(text);
    }

    static Integer extractVersion(String text) {
        if (text == null) return null;
        Matcher m = VERSION_RE.matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    /** Simple struct for leaf position. */
    private static final class LeafPos {
        private final int index;
        private final int keyIndent;

        LeafPos(int index, int keyIndent) {
            this.index = index;
            this.keyIndent = keyIndent;
        }

        int index() { return index; }
        int keyIndent() { return keyIndent; }
    }
}
