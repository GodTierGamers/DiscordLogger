package com.discordlogger.event;

import com.discordlogger.log.Log;
import org.bukkit.plugin.Plugin;

public final class ServerStop {
    private ServerStop() {}

    private static final String THUMB_SERVER = "https://cdn-icons-png.flaticon.com/512/1411/1411887.png";

    public static void handle(Plugin plugin) {
        // Category string drives the color key -> "server_stop"
        Log.eventWithThumb("Server Stop", "Server Stopped", THUMB_SERVER);
    }
}
