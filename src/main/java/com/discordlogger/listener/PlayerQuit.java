package com.discordlogger.listener;

import com.discordlogger.log.Log;
import com.discordlogger.util.Names;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerQuit implements Listener {
    private final JavaPlugin plugin;

    public PlayerQuit(JavaPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent e) {
        if (!plugin.getConfig().getBoolean("log.player.quit", true)) return;

        String who = Names.display(e.getPlayer(), plugin); // falls back to cache if needed
        String msg = who + " left the server";
        Log.eventWithThumb("Player Quit", msg, Log.playerAvatarUrl(e.getPlayer().getUniqueId()));

        Names.remove(e.getPlayer());
    }
}
