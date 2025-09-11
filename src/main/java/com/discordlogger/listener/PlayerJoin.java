package com.discordlogger.listener;

import com.discordlogger.log.Log;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

public final class PlayerJoin implements Listener {
    private final Plugin plugin;

    public PlayerJoin(Plugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (!plugin.getConfig().getBoolean("log.player.join", true)) return;
        final String name = e.getPlayer().getName();
        Log.event("Player Join", name + " joined the server");
    }
}
