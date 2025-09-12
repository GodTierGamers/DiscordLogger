package com.discordlogger.listener;

import com.discordlogger.log.Log;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.Plugin;

public final class ServerCommand implements Listener {
    private final Plugin plugin;

    public ServerCommand(Plugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent e) {
        if (!plugin.getConfig().getBoolean("log.server.command", true)) return;
        final String who = e.getSender().getName(); // "Server" for console
        Log.event("Server Command", who + " ran: /" + e.getCommand());
    }
}
