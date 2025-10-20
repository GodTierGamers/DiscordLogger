package com.discordlogger.listener.player;

import com.discordlogger.log.Log;
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
        if (!plugin.getConfig().getBoolean("log.player.join", true)) return;

        // Delay slightly so nickname plugins can set displayName
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Names.capture(e.getPlayer()); // seed/update cache
            String who = Names.display(e.getPlayer(), plugin);
            String msg = who + " joined the server";
            Log.eventWithThumb("Player Join", msg, Log.playerAvatarUrl(e.getPlayer().getUniqueId()));
        }, 2L);
    }
}
