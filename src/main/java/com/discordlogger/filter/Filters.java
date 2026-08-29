package com.discordlogger.filter;

import com.discordlogger.util.Strings;
import com.discordlogger.util.Vanish;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Decides what never reaches Discord, regardless of which events are enabled.
 *
 * <p>Event toggles are all-or-nothing: command logging is either on or off. That is
 * too blunt for two things a server genuinely needs. Some commands must never be
 * logged at all — {@code /login} and {@code /register} carry passwords in plain
 * text, and {@code /msg} is private by definition — and some accounts should be
 * invisible, such as staff alt accounts or bots whose activity would drown the log.
 *
 * <p>Filtering happens in the listener rather than in {@link com.discordlogger.log.Log},
 * because that is where the player, world and raw command still exist. By the time
 * a message reaches {@code Log} it is a rendered string, and deciding from that
 * would mean matching against prose.
 *
 * <p>The config is read into an immutable snapshot on load and swapped atomically,
 * so a reload cannot be observed half-applied by an async sender.
 *
 * <p><b>Moderation events are deliberately not filtered by player.</b>
 * {@code ignored_players} means "this account's own activity is not logged" — its
 * joins, chat, commands, deaths. A ban or a kick is not that player's activity, it
 * is a record of staff action, and suppressing it is how an audit trail quietly
 * loses the entries that matter most. Someone silencing a bot account still wants
 * to know if that account gets banned.
 */
public final class Filters {

    private static volatile Snapshot current = Snapshot.empty();

    private Filters() {}

    /**
     * The filter configuration as of the last reload.
     *
     * <p><b>Every field is final and this class is never mutated.</b> That was free when
     * this was a record and has to be maintained by hand now: {@code current} is swapped
     * in one volatile write, and a reader that could observe a half-updated Snapshot
     * would be able to filter against a mix of the old config and the new. Adding a
     * non-final field here reintroduces exactly that, silently.
     *
     * <p>The collections are not defensively copied because every caller passes one it
     * has just built and then discards. Reusing a caller's mutable collection after
     * construction would defeat the immutability above just as effectively.
     */
    private static final class Snapshot {
        private final Set<String> ignoredNames;
        private final Set<UUID> ignoredUuids;
        private final Set<String> ignoredCommands;
        private final Set<String> onlyCommands;
        private final Set<String> ignoredWorlds;
        private final List<String> chatPatterns;
        private final int minChatLength;
        private final List<String> ignoredAdvancements;
        private final boolean logRecipeAdvancements;
        private final Set<String> ignoredTeleportCauses;
        private final double minTeleportDistance;
        private final Set<String> ignoredDeathCauses;
        private final Set<String> ignoredExplosionSources;
        private final int minExplosionBlocks;
        private final String exemptPermission;
        private final boolean respectVanish;

        Snapshot(Set<String> ignoredNames,
                 Set<UUID> ignoredUuids,
                 Set<String> ignoredCommands,
                 Set<String> onlyCommands,
                 Set<String> ignoredWorlds,
                 List<String> chatPatterns,
                 int minChatLength,
                 List<String> ignoredAdvancements,
                 boolean logRecipeAdvancements,
                 Set<String> ignoredTeleportCauses,
                 double minTeleportDistance,
                 Set<String> ignoredDeathCauses,
                 Set<String> ignoredExplosionSources,
                 int minExplosionBlocks,
                 String exemptPermission,
                 boolean respectVanish) {
            this.ignoredNames = ignoredNames;
            this.ignoredUuids = ignoredUuids;
            this.ignoredCommands = ignoredCommands;
            this.onlyCommands = onlyCommands;
            this.ignoredWorlds = ignoredWorlds;
            this.chatPatterns = chatPatterns;
            this.minChatLength = minChatLength;
            this.ignoredAdvancements = ignoredAdvancements;
            this.logRecipeAdvancements = logRecipeAdvancements;
            this.ignoredTeleportCauses = ignoredTeleportCauses;
            this.minTeleportDistance = minTeleportDistance;
            this.ignoredDeathCauses = ignoredDeathCauses;
            this.ignoredExplosionSources = ignoredExplosionSources;
            this.minExplosionBlocks = minExplosionBlocks;
            this.exemptPermission = exemptPermission;
            this.respectVanish = respectVanish;
        }

        Set<String> ignoredNames() { return ignoredNames; }
        Set<UUID> ignoredUuids() { return ignoredUuids; }
        Set<String> ignoredCommands() { return ignoredCommands; }
        Set<String> onlyCommands() { return onlyCommands; }
        Set<String> ignoredWorlds() { return ignoredWorlds; }
        List<String> chatPatterns() { return chatPatterns; }
        int minChatLength() { return minChatLength; }
        List<String> ignoredAdvancements() { return ignoredAdvancements; }
        boolean logRecipeAdvancements() { return logRecipeAdvancements; }
        Set<String> ignoredTeleportCauses() { return ignoredTeleportCauses; }
        double minTeleportDistance() { return minTeleportDistance; }
        Set<String> ignoredDeathCauses() { return ignoredDeathCauses; }
        Set<String> ignoredExplosionSources() { return ignoredExplosionSources; }
        int minExplosionBlocks() { return minExplosionBlocks; }
        String exemptPermission() { return exemptPermission; }
        boolean respectVanish() { return respectVanish; }

        static Snapshot empty() {
            return new Snapshot(Collections.<String>emptySet(), Collections.<UUID>emptySet(),
                    Collections.<String>emptySet(), Collections.<String>emptySet(),
                    Collections.<String>emptySet(), Collections.<String>emptyList(), 0,
                    Collections.<String>emptyList(), false, Collections.<String>emptySet(), 0d,
                    Collections.<String>emptySet(), Collections.<String>emptySet(), 0, "", true);
        }
    }

    /** Reads a list of enum-ish names into a normalised set: upper case, underscores. */
    private static Set<String> constantSet(JavaPlugin plugin, String path) {
        final Set<String> out = new LinkedHashSet<>();
        for (String entry : plugin.getConfig().getStringList(path)) {
            if (entry == null || Strings.isBlank(entry)) continue;
            out.add(entry.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
        }
        return out;
    }

    /** Re-read the filter config. Called from applyRuntimeConfig, so /reload picks it up. */
    public static void reload(JavaPlugin plugin) {
        final Set<String> names = new LinkedHashSet<>();
        final Set<UUID> uuids = new LinkedHashSet<>();

        // One list accepts both names and UUIDs: asking an admin which one they have
        // is friction, and the two are trivially distinguishable.
        for (String entry : plugin.getConfig().getStringList("filters.ignored_players")) {
            if (entry == null || Strings.isBlank(entry)) continue;
            final String trimmed = entry.trim();
            try {
                uuids.add(UUID.fromString(trimmed));
            } catch (IllegalArgumentException notAUuid) {
                names.add(trimmed.toLowerCase(Locale.ROOT));
            }
        }

        final Set<String> commands = new LinkedHashSet<>();
        for (String entry : plugin.getConfig().getStringList("filters.ignored_commands")) {
            if (entry == null || Strings.isBlank(entry)) continue;
            commands.add(normaliseCommand(entry));
        }

        final Set<String> worlds = new LinkedHashSet<>();
        for (String entry : plugin.getConfig().getStringList("filters.ignored_worlds")) {
            if (entry == null || Strings.isBlank(entry)) continue;
            worlds.add(entry.trim().toLowerCase(Locale.ROOT));
        }

        final List<String> patterns = plugin.getConfig()
                .getStringList("filters.ignored_chat_containing").stream()
                .filter(s -> s != null && !Strings.isBlank(s))
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());

        final Set<String> onlyCommands = new LinkedHashSet<>();
        for (String entry : plugin.getConfig().getStringList("filters.only_log_commands")) {
            if (entry == null || Strings.isBlank(entry)) continue;
            onlyCommands.add(normaliseCommand(entry));
        }

        final List<String> advancements = plugin.getConfig()
                .getStringList("filters.ignored_advancements").stream()
                .filter(a -> a != null && !Strings.isBlank(a))
                .map(a -> a.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());

        final String perm = plugin.getConfig().getString("filters.exempt_permission", "").trim();

        current = new Snapshot(
                names, uuids, commands, onlyCommands, worlds, patterns,
                Math.max(0, plugin.getConfig().getInt("filters.minimum_chat_length", 0)),
                advancements,
                plugin.getConfig().getBoolean("filters.log_recipe_advancements", false),
                constantSet(plugin, "filters.ignored_teleport_causes"),
                Math.max(0d, plugin.getConfig().getDouble("filters.minimum_teleport_distance", 0d)),
                constantSet(plugin, "filters.ignored_death_causes"),
                constantSet(plugin, "filters.ignored_explosion_sources"),
                Math.max(0, plugin.getConfig().getInt("filters.minimum_explosion_blocks", 0)),
                perm,
                plugin.getConfig().getBoolean("filters.respect_vanish", true));

        final Snapshot snap = current;
        final int rules = snap.ignoredNames().size() + snap.ignoredUuids().size()
                + snap.ignoredCommands().size() + snap.onlyCommands().size()
                + snap.ignoredWorlds().size() + snap.chatPatterns().size()
                + snap.ignoredAdvancements().size() + snap.ignoredTeleportCauses().size()
                + snap.ignoredDeathCauses().size() + snap.ignoredExplosionSources().size();
        if (rules > 0 || !perm.isEmpty()) {
            plugin.getLogger().info("Log filters active: " + rules + " rule(s)"
                    + (snap.onlyCommands().isEmpty() ? ""
                       : ", command allow-list of " + snap.onlyCommands().size())
                    + (perm.isEmpty() ? "" : ", exempt permission '" + perm + "'") + ".");
        }
    }

    /**
     * True when nothing about this player should ever be logged.
     *
     * <p>Covers the name list, the UUID list, and the exempt permission. The
     * permission is checked last because it is the only one that touches the
     * permissions plugin.
     */
    public static boolean blocksPlayer(Player player) {
        if (player == null) return false;
        final Snapshot snap = current;

        if (snap.ignoredUuids().contains(player.getUniqueId())) return true;
        if (snap.ignoredNames().contains(player.getName().toLowerCase(Locale.ROOT))) return true;

        // Vanish is checked live rather than held in the snapshot, because unlike every
        // other filter here it is not configuration -- it is state that changes while
        // the server runs. ignored_players cannot express it for the same reason.
        if (snap.respectVanish() && Vanish.isVanished(player)) return true;

        final String perm = snap.exemptPermission();
        return !perm.isEmpty() && player.hasPermission(perm);
    }

    /** True when this world's events should never be logged. */
    public static boolean blocksWorld(String worldName) {
        if (worldName == null) return false;
        return current.ignoredWorlds().contains(worldName.toLowerCase(Locale.ROOT));
    }

    /**
     * True when this command should never be logged.
     *
     * <p>{@code only_log_commands} is an allow-list and wins outright when set: a
     * server that wants nothing but moderation commands should not also have to
     * enumerate every command it does not want. The deny-list still applies inside
     * it, so a command can be allow-listed by prefix and then excluded.
     *
     * @param raw the command as typed, with or without a leading slash and arguments
     */
    public static boolean blocksCommand(String raw) {
        if (raw == null || Strings.isBlank(raw)) return false;
        final Snapshot snap = current;
        final String word = normaliseCommand(raw);

        if (!snap.onlyCommands().isEmpty() && !snap.onlyCommands().contains(word)) return true;
        return snap.ignoredCommands().contains(word);
    }

    /**
     * True when this advancement should not be logged.
     *
     * <p>Entries match the full key ({@code minecraft:husbandry/plant_seed}) and
     * support a trailing {@code *}. The wildcard matters because advancements are
     * grouped by tab, so "stop logging the farming ones" should be one line rather
     * than twenty.
     *
     * @param key       the full namespaced key
     * @param path      the key's path, e.g. {@code husbandry/plant_seed}
     */
    public static boolean blocksAdvancement(String key, String path) {
        final Snapshot snap = current;

        // Recipe unlocks and tab roots fire constantly and mean nothing to a reader.
        // Kept as a filter rather than hardcoded so a server that genuinely wants
        // them can have them, but off by default because almost nobody does.
        if (!snap.logRecipeAdvancements() && path != null
                && (path.startsWith("recipes/") || path.startsWith("recipe/")
                    || path.endsWith("/root") || path.equals("story/root"))) {
            return true;
        }

        if (key == null || snap.ignoredAdvancements().isEmpty()) return false;
        final String lower = key.toLowerCase(Locale.ROOT);
        for (String entry : snap.ignoredAdvancements()) {
            if (entry.endsWith("*")) {
                if (lower.startsWith(entry.substring(0, entry.length() - 1))) return true;
            } else if (lower.equals(entry)) {
                return true;
            }
        }
        return false;
    }

    /** True when a teleport should not be logged, by its cause or how short it was. */
    public static boolean blocksTeleport(String cause, Double distance) {
        final Snapshot snap = current;
        if (cause != null && snap.ignoredTeleportCauses()
                .contains(cause.toUpperCase(Locale.ROOT))) return true;

        // A distance of null means the two points are in different worlds, which is
        // never "a short hop" and so is never filtered by distance.
        return distance != null && snap.minTeleportDistance() > 0d
                && distance < snap.minTeleportDistance();
    }

    /** True when a death with this damage cause should not be logged. */
    public static boolean blocksDeath(String damageCause) {
        return damageCause != null
                && current.ignoredDeathCauses().contains(damageCause.toUpperCase(Locale.ROOT));
    }

    /**
     * True when an explosion should not be logged.
     *
     * @param source        the entity type that exploded, or null for a block
     * @param affectedBlocks how many blocks it destroyed
     */
    public static boolean blocksExplosion(String source, int affectedBlocks) {
        final Snapshot snap = current;
        if (source != null && snap.ignoredExplosionSources()
                .contains(source.toUpperCase(Locale.ROOT))) return true;
        return snap.minExplosionBlocks() > 0 && affectedBlocks < snap.minExplosionBlocks();
    }

    /** True when a chat line contains any configured pattern, or is too short to be worth logging. */
    public static boolean blocksChat(String message) {
        if (message == null || message.isEmpty()) return false;
        final Snapshot snap = current;

        if (snap.minChatLength() > 0 && Strings.strip(message).length() < snap.minChatLength()) return true;

        final String haystack = message.toLowerCase(Locale.ROOT);
        for (String needle : snap.chatPatterns()) {
            if (haystack.contains(needle)) return true;
        }
        return false;
    }

    /**
     * Reduces a typed command to the word that identifies it.
     *
     * <p>Drops the leading slash, everything from the first space, and any plugin
     * qualifier — {@code /essentials:msg hello} and {@code /MSG hello} both reduce to
     * {@code msg}. Without the qualifier step, a deny-list entry of "msg" would be
     * trivially bypassed by typing the fully-qualified form, which is exactly the
     * kind of hole that makes a security-flavoured filter worthless.
     */
    static String normaliseCommand(String raw) {
        String s = raw.trim();
        if (s.startsWith("/")) s = s.substring(1);

        final int space = s.indexOf(' ');
        if (space >= 0) s = s.substring(0, space);

        final int colon = s.indexOf(':');
        if (colon >= 0 && colon < s.length() - 1) s = s.substring(colon + 1);

        return s.toLowerCase(Locale.ROOT);
    }
}
