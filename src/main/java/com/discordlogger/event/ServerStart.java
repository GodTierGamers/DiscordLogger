package com.discordlogger.event;

import com.discordlogger.log.Log;
import org.bukkit.plugin.Plugin;

public final class ServerStart {
    private ServerStart() {}

    private static final String THUMB_SERVER = "https://cdn-icons-png.flaticon.com/512/1411/1411887.png";

    public static void handle(Plugin plugin) {
        // Category string drives the color key -> "server_start"
        Log.eventWithThumb("Server Start", "Server Started", THUMB_SERVER);
    }
}
