package com.discordlogger.listener.server;

import com.discordlogger.filter.Filters;
import com.discordlogger.lang.Lang;
import com.discordlogger.log.Log;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.Plugin;

public final class ServerCommand implements Listener {
    private final Plugin plugin;

    private static final String THUMB_SERVER = "https://discordlogger.godtiergamers.xyz/assets/icons/server.png";

    public ServerCommand(Plugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent e) {
        if (!plugin.getConfig().getBoolean("log.server.command.enabled", true)) return;
        // Console has no player, so only the command filter applies.
        if (Filters.blocksCommand(e.getCommand())) return;
        final String who = Log.mdEscape(e.getSender().getName()); // "Server" for console
        final String cmd = Log.mdEscape(Log.redactWebhooks("/" + e.getCommand()));
        Log.eventWithThumb("Server Command",
                Lang.text("discord.server-command", "sender", who, "command", cmd), THUMB_SERVER);
    }
}