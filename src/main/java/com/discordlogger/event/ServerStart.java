package com.discordlogger.event;

import com.discordlogger.log.Log;
import com.discordlogger.util.Icons;
import org.bukkit.plugin.Plugin;

public final class ServerStart {
    private ServerStart() {}

    public static void handle(Plugin plugin) {
        // Category string drives the color key -> "server_start"
        Log.eventWithThumb("Server Start", "Server Started", Icons.SERVER);
    }
}