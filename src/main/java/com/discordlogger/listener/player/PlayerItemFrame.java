package com.discordlogger.listener.player;

import com.discordlogger.filter.Filters;
import com.discordlogger.lang.Lang;
import com.discordlogger.log.Log;
import com.discordlogger.util.Names;
import com.discordlogger.util.Places;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Item frames and paintings being broken.
 *
 * <p>{@code players_only} ships {@code true} and matters more than it looks. This
 * event fires for skeleton arrows, creeper blasts and anything else that clips a
 * frame, so logging every remover turns a rare, meaningful message into ambient
 * noise — and ambient noise is what servers in the metrics turn off.
 */
public final class PlayerItemFrame implements Listener {
    private final JavaPlugin plugin;
    public PlayerItemFrame(JavaPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        if (!plugin.getConfig().getBoolean("log.player.item_frame.enabled", true)) return;

        final Entity remover = e.getRemover();
        final boolean playersOnly =
                plugin.getConfig().getBoolean("log.player.item_frame.players_only", true);
        if (playersOnly && !(remover instanceof Player)) return;

        final Hanging hanging = e.getEntity();
        final Location loc = hanging.getLocation();
        if (Filters.blocksWorld(loc.getWorld().getName())) return;
        if (remover instanceof Player && Filters.blocksPlayer((Player) remover)) return;

        final List<Log.Field> fields = new ArrayList<>();
        fields.add(new Log.Field(Lang.text("discord.item-frame.broken-by-field"), describe(remover, plugin)));
        fields.add(new Log.Field(Lang.text("discord.item-frame.what-field"), pretty(hanging.getType().name()), true));
        fields.add(new Log.Field(Lang.text("discord.item-frame.where-field"), Places.of(loc), true));

        final String thumb = (remover instanceof Player)
                ? Log.playerAvatarUrl(((Player) remover).getUniqueId())
                : null;

        Log.eventFieldsWithThumb("player_item_frame", Lang.text("discord.item-frame.title"), null, fields, thumb);
    }

    private static String describe(Entity remover, JavaPlugin plugin) {
        if (remover instanceof Player) return Names.display((Player) remover, plugin);
        if (remover == null) return "Unknown";
        return pretty(remover.getType().name());
    }

    static String pretty(String enumName) {
        if (enumName == null || enumName.isEmpty()) return "Unknown";
        final String s = enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
