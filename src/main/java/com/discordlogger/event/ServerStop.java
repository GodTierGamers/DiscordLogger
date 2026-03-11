package com.discordlogger.event;

import com.discordlogger.log.Log;
import com.discordlogger.util.Icons;
import org.bukkit.plugin.Plugin;

public final class ServerStop {
    private ServerStop() {}

    public static void handle(Plugin plugin) {
        // Category string drives the color key -> "server_stop"
        Log.eventWithThumb("Server Stop", "Server Stopped", Icons.SERVER);
    }
}