package com.discordlogger.event;

import com.discordlogger.log.Log;
import org.bukkit.plugin.Plugin;

public final class ServerStop {
    private ServerStop() {}

    public static void handle(Plugin plugin) {
        Log.event("Server", "Server Stopped");
    }
}
