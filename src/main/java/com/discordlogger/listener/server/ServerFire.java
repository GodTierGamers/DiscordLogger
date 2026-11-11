package com.discordlogger.listener.server;

import com.discordlogger.filter.Filters;
import com.discordlogger.lang.Lang;
import com.discordlogger.log.Log;
import com.discordlogger.util.Names;
import com.discordlogger.util.Places;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Fire being lit, and what lit it.
 *
 * <p>Under {@code server} rather than {@code player} for the same reason an
 * explosion is: three of the eight causes have no player at all. Lightning, lava and
 * spreading fire are things the world does, and a listener that assumed a player
 * would have to invent one.
 *
 * <p><strong>Causes are matched by name, never by enum constant.</strong>
 * {@code IgniteCause.ARROW} arrived in 1.15, so naming it would stop this compiling
 * at the 1.8 floor — and a flaming arrow on an older server simply reports as
 * {@code FLINT_AND_STEEL}, which is the server's own answer rather than a guess.
 */
public final class ServerFire implements Listener {
    private static final String ICON_BASE =
            "https://discordlogger.godtiergamers.xyz/assets/icons/fire/";

    /**
     * The causes with an icon of their own. Anything a future version adds falls
     * back to the fire texture, which is never wrong for an event about fire.
     */
    private static final Set<String> ICONS = Collections.unmodifiableSet(new HashSet<>(
            java.util.Arrays.asList("FLINT_AND_STEEL", "FIREBALL", "LIGHTNING",
                                    "EXPLOSION", "ENDER_CRYSTAL", "ARROW")));

    /** The two that animate, because the texture they come from animates. */
    private static final Set<String> ANIMATED = Collections.unmodifiableSet(new HashSet<>(
            java.util.Arrays.asList("LAVA", "SPREAD")));

    private final JavaPlugin plugin;
    public ServerFire(JavaPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (!plugin.getConfig().getBoolean("log.server.fire.enabled", false)) return;

        final String cause = e.getCause() == null ? "UNKNOWN" : e.getCause().name();
        if (Filters.blocksIgnite(cause)) return;

        final Block b = e.getBlock();
        if (Filters.blocksWorld(b.getWorld().getName())) return;

        final Player player = e.getPlayer();
        if (player != null && Filters.blocksPlayer(player)) return;

        final List<Log.Field> fields = new ArrayList<>();
        fields.add(new Log.Field(Lang.text("discord.fire.cause-field"), pretty(cause), true));
        fields.add(new Log.Field(Lang.text("discord.fire.where-field"), Places.of(b), true));
        fields.add(new Log.Field(Lang.text("discord.fire.lit-by-field"), litBy(e, player)));

        Log.eventFieldsWithThumb(
                "server_fire",
                Lang.text("discord.fire.title"),
                null,
                fields,
                iconFor(cause)
        );
    }

    /**
     * Who or what lit it.
     *
     * <p>{@code getPlayer} is the player who struck the flint; {@code getIgnitingEntity}
     * is the ghast whose fireball landed. Both can be absent, and "the world" is a
     * truthful answer for lightning in a way that a blank field is not.
     */
    private String litBy(BlockIgniteEvent e, Player player) {
        if (player != null) return Names.display(player, plugin);
        final Entity source = e.getIgnitingEntity();
        if (source != null) return pretty(source.getType().name());
        return Lang.text("discord.fire.the-world");
    }

    /** The icon for a cause, falling back to the fire texture for anything unknown. */
    static String iconFor(String cause) {
        final String c = cause == null ? "" : cause.toUpperCase(Locale.ROOT);
        if (ANIMATED.contains(c)) return ICON_BASE + c.toLowerCase(Locale.ROOT) + ".gif";
        if (ICONS.contains(c)) return ICON_BASE + c.toLowerCase(Locale.ROOT) + ".png";
        return ICON_BASE + "spread.gif";
    }

    /** {@code FLINT_AND_STEEL} as {@code Flint and steel}. */
    static String pretty(String enumName) {
        if (enumName == null || enumName.isEmpty()) return "Unknown";
        final String s = enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
