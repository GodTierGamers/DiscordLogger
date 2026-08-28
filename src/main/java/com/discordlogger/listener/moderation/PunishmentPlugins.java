package com.discordlogger.listener.moderation;

import org.bukkit.BanList;
import org.bukkit.Bukkit;

import java.util.List;

/**
 * Whether this server's punishments actually reach Bukkit's ban list.
 *
 * <h2>The silent failure this exists to end</h2>
 *
 * <p>{@link Ban} and {@link Unban} confirm a punishment landed by asking
 * {@code Bukkit.getBanList()} on the next tick, which is right on a vanilla server and
 * wrong everywhere else. LiteBans, LibertyBans, AdvancedBan, BanManager and CMI keep
 * their own databases and never touch that list, so the check returns false, the
 * listener logs nothing, and the admin sees a moderation channel that simply never
 * mentions bans. Nothing errors. Nothing warns.
 *
 * <p>bStats found 4 of 25 reporting servers in exactly that state.
 *
 * <h2>What replaces the check</h2>
 *
 * <p>When one of these is installed the verification is skipped and the command is
 * logged on its own. That is a real trade and worth naming: the permission gate still
 * applies, so an unprivileged attempt is still ignored, but a ban the plugin itself
 * rejected — already banned, unknown player, bad duration — would now be logged as
 * though it succeeded.
 *
 * <p>It is the right trade because the alternative is what these servers have now,
 * which is nothing at all. An occasional over-report is visible and correctable; a
 * moderation log that silently omits every ban is neither.
 */
public final class PunishmentPlugins {

    /** Plugins that store punishments outside Bukkit's ban list. */
    private static final List<String> NAMES = List.of(
            "LiteBans", "LibertyBans", "AdvancedBan", "BanManager", "CMI");

    private static volatile Boolean present;

    private PunishmentPlugins() {}

    /**
     * True when a punishment plugin owns bans on this server.
     *
     * <p>Resolved once. Plugins cannot appear after startup, and this is consulted on
     * every ban command, so re-scanning the plugin list each time would be work done
     * repeatedly to reach a fixed answer.
     */
    public static boolean installed() {
        Boolean cached = present;
        if (cached != null) return cached;
        boolean found = false;
        try {
            for (String name : NAMES) {
                if (Bukkit.getPluginManager().getPlugin(name) != null) { found = true; break; }
            }
        } catch (Throwable noServerYet) {
            found = false;
        }
        present = found;
        return found;
    }

    /**
     * Whether a name is banned, checking IP bans too.
     *
     * <p>{@code /ban-ip} writes to {@link BanList.Type#IP}, which the listeners never
     * consulted — so an IP ban was a second way to punish someone and have it go
     * unlogged, independent of which plugins are installed.
     */
    public static boolean isBanned(String target) {
        try {
            if (Bukkit.getBanList(BanList.Type.NAME).isBanned(target)) return true;
            return Bukkit.getBanList(BanList.Type.IP).isBanned(target);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Clears the cached lookup. For tests. */
    static void reset() {
        present = null;
    }
}
