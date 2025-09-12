package com.discordlogger.listener;

import com.discordlogger.log.Log;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public final class PlayerQuit implements Listener {
    private final Plugin plugin;

    public PlayerQuit(Plugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        if (!plugin.getConfig().getBoolean("log.player.quit", true)) return;
        final String name = Log.mdEscape(e.getPlayer().getName());
        final String thumb = Log.playerAvatarUrl(e.getPlayer().getUniqueId());
        Log.eventWithThumb("Player Quit", name + " left the server", thumb);
    }
}
