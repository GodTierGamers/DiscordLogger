package com.discordlogger.listener.player;

import com.discordlogger.filter.Filters;
import com.discordlogger.lang.Lang;
import com.discordlogger.log.Log;
import com.discordlogger.util.ClientPlatform;
import com.discordlogger.util.Names;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerJoin implements Listener {
    private final JavaPlugin plugin;

    public PlayerJoin(JavaPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent e) {
        if (!plugin.getConfig().getBoolean("log.player.join.enabled", true)) return;
        if (Filters.blocksPlayer(e.getPlayer()) || Filters.blocksWorld(e.getPlayer().getWorld().getName())) return;

        // Delay slightly so nickname plugins can set displayName
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Names.capture(e.getPlayer()); // seed/update cache
            final String who = Names.display(e.getPlayer(), plugin);
            final java.util.UUID uuid = e.getPlayer().getUniqueId();
            final String msg = Lang.textFor(e.getPlayer(), "discord.player-join", "player", who);
            final String thumb = Log.playerAvatarUrl(uuid);

            // Only ever added for Bedrock. Nothing can prove a player IS Java --
            // with Geyser standalone they authenticate as ordinary Java accounts --
            // so an absent field means "nothing indicated Bedrock", and claiming
            // "Platform: Java" for everyone would be asserting more than we know.
            if (plugin.getConfig().getBoolean("log.player.join.show_platform", true)
                    && ClientPlatform.isBedrock(uuid)) {
                Log.eventFieldsWithThumb(
                        "Player Join",
                        "Player Join",
                        msg,
                        null,
                        java.util.Collections.singletonList(new Log.Field("Platform", "Bedrock")),
                        thumb
                );
            } else {
                Log.eventWithThumb("Player Join", msg, thumb);
            }
        }, 2L);
    }
}
