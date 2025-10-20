package com.discordlogger.listener.server;

import com.discordlogger.log.Log;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.Plugin;

public final class ServerCommand implements Listener {
    private final Plugin plugin;

    private static final String THUMB_SERVER = "https://cdn-icons-png.flaticon.com/512/1411/1411887.png";

    public ServerCommand(Plugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent e) {
        if (!plugin.getConfig().getBoolean("log.server.command", true)) return;
        final String who = Log.mdEscape(e.getSender().getName()); // "Server" for console
        final String cmd = Log.mdEscape("/" + e.getCommand());
        Log.eventWithThumb("Server Command", who + " ran: " + cmd, THUMB_SERVER);
    }
}
