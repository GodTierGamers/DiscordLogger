package com.discordlogger.filter;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

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

    private record Snapshot(
            Set<String> ignoredNames,
            Set<UUID> ignoredUuids,
            Set<String> ignoredCommands,
            Set<String> ignoredWorlds,
            List<String> chatPatterns,
            String exemptPermission
    ) {
        static Snapshot empty() {
            return new Snapshot(Set.of(), Set.of(), Set.of(), Set.of(), List.of(), "");
        }
    }

    /** Re-read the filter config. Called from applyRuntimeConfig, so /reload picks it up. */
    public static void reload(JavaPlugin plugin) {
        final Set<String> names = new LinkedHashSet<>();
        final Set<UUID> uuids = new LinkedHashSet<>();

        // One list accepts both names and UUIDs: asking an admin which one they have
        // is friction, and the two are trivially distinguishable.
        for (String entry : plugin.getConfig().getStringList("filters.ignored_players")) {
            if (entry == null || entry.isBlank()) continue;
            final String trimmed = entry.trim();
            try {
                uuids.add(UUID.fromString(trimmed));
            } catch (IllegalArgumentException notAUuid) {
                names.add(trimmed.toLowerCase(Locale.ROOT));
            }
        }

        final Set<String> commands = new LinkedHashSet<>();
        for (String entry : plugin.getConfig().getStringList("filters.ignored_commands")) {
            if (entry == null || entry.isBlank()) continue;
            commands.add(normaliseCommand(entry));
        }

        final Set<String> worlds = new LinkedHashSet<>();
        for (String entry : plugin.getConfig().getStringList("filters.ignored_worlds")) {
            if (entry == null || entry.isBlank()) continue;
            worlds.add(entry.trim().toLowerCase(Locale.ROOT));
        }

        final List<String> patterns = plugin.getConfig()
                .getStringList("filters.ignored_chat_containing").stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();

        final String perm = plugin.getConfig().getString("filters.exempt_permission", "").trim();

        current = new Snapshot(names, uuids, commands, worlds, patterns, perm);

        final int total = names.size() + uuids.size() + commands.size()
                + worlds.size() + patterns.size();
        if (total > 0 || !perm.isEmpty()) {
            plugin.getLogger().info("Log filters active: " + commands.size() + " command(s), "
                    + (names.size() + uuids.size()) + " player(s), " + worlds.size() + " world(s), "
                    + patterns.size() + " chat pattern(s)"
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
     * @param raw the command as typed, with or without a leading slash and arguments
     */
    public static boolean blocksCommand(String raw) {
        if (raw == null || raw.isBlank()) return false;
        return current.ignoredCommands().contains(normaliseCommand(raw));
    }

    /** True when a chat line contains any configured pattern. */
    public static boolean blocksChat(String message) {
        if (message == null || message.isEmpty()) return false;
        final String haystack = message.toLowerCase(Locale.ROOT);
        for (String needle : current.chatPatterns()) {
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
