package com.discordlogger.event;

import com.discordlogger.log.Log;
import org.bukkit.plugin.Plugin;

public final class ServerStart {
    private ServerStart() {}

    public static void handle(Plugin plugin) {
        Log.event("Server", "Server Started");
    }
}
