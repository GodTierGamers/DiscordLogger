package com.discordlogger.listener.player;

import com.discordlogger.filter.Filters;
import com.discordlogger.lang.Lang;
import com.discordlogger.log.Log;
import com.discordlogger.util.Names;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * A player's very first join, in addition to the ordinary join message.
 *
 * <p>Deliberately a second message rather than a replacement: {@code player.join}
 * is enabled on 99% of reporting servers, and silently swallowing the join someone
 * already expects in order to show a different one is the kind of change that reads
 * as a bug. Turning this off leaves join exactly as it was.
 */
public final class PlayerFirstJoin implements Listener {
    private final JavaPlugin plugin;
    public PlayerFirstJoin(JavaPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (!plugin.getConfig().getBoolean("log.player.first_join.enabled", true)) return;

        final Player p = e.getPlayer();
        // The server records the player the moment they connect, so this is only ever
        // true for someone it has never seen.
        if (p.hasPlayedBefore()) return;
        if (Filters.blocksPlayer(p) || Filters.blocksWorld(p.getWorld().getName())) return;

        final List<Log.Field> fields = new ArrayList<>();
        fields.add(new Log.Field(Lang.text("discord.first-join.player-field"), Names.display(p, plugin)));
        fields.add(new Log.Field(Lang.text("discord.first-join.online-field"), String.valueOf(
                p.getServer().getOnlinePlayers().size()), true));

        Log.eventFieldsWithThumb(
                "player_first_join",
                Lang.text("discord.first-join.title"),
                null,
                fields,
                Log.playerAvatarUrl(p.getUniqueId())
        );
    }
}
