package com.discordlogger.util;

import com.discordlogger.lang.Lang;
import org.bukkit.Location;
import org.bukkit.block.Block;

/**
 * One wording for "where something happened", so the events that report a place
 * cannot drift apart from each other or from whatever the server owner translated
 * {@code discord.where-value} into.
 */
public final class Places {
    private Places() {}

    public static String of(Block b) {
        return of(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
    }

    public static String of(Location l) {
        return of(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    public static String of(String world, int x, int y, int z) {
        return Lang.text("discord.where-value",
                "world", world,
                "x", String.valueOf(x),
                "y", String.valueOf(y),
                "z", String.valueOf(z));
    }
}
