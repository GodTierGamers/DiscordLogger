package com.discordlogger.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * Whether the server currently considers a player an operator or whitelisted, asked by
 * name.
 *
 * <h2>Why not just call isOp() on an OfflinePlayer</h2>
 *
 * <p>Because on a server older than about 1.13 it answers no for a player who has never
 * joined, whatever the truth is.
 *
 * <p>The moderation listeners see a command before it runs, so they take an
 * {@link OfflinePlayer} then and re-check it a tick later to confirm the command
 * worked. On those versions {@code Bukkit.getOfflinePlayer(String)} looks the name up
 * in the server's user cache, and for a player nobody has ever seen there is nothing
 * there -- so it hands back a profile carrying the name and no UUID at all. The
 * operator list is keyed by UUID. A profile with none never matches it, and never
 * starts matching it either, because the object was built before the command populated
 * the cache and is not refreshed afterwards.
 *
 * <p>The visible effect was that opping, deopping or whitelisting somebody who had not
 * joined yet went unlogged on 1.8 through 1.12 while working perfectly on current
 * versions. Pre-opping an admin before their first login is exactly when that matters,
 * and exactly when it silently did nothing.
 *
 * <p>Asking the server for its own lists and matching on the name sidesteps the profile
 * entirely. Both lists are small -- they are staff, not players -- so walking them costs
 * nothing worth measuring, and both methods have existed unchanged since 1.8.
 */
public final class Roster {

    private Roster() {}

    /** Whether the server's operator list currently holds this name. */
    public static boolean isOp(String name) {
        return holds(Bukkit.getOperators(), name);
    }

    /** Whether the server's whitelist currently holds this name. */
    public static boolean isWhitelisted(String name) {
        return holds(Bukkit.getWhitelistedPlayers(), name);
    }

    private static boolean holds(Iterable<? extends OfflinePlayer> list, String name) {
        if (Strings.isBlank(name) || list == null) return false;
        for (OfflinePlayer p : list) {
            // A profile in these lists can carry a UUID and no name, so this is checked
            // rather than assumed.
            if (p != null && name.equalsIgnoreCase(p.getName())) return true;
        }
        return false;
    }
}
