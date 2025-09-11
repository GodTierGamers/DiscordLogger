package com.discordlogger.event;

import com.discordlogger.log.Log;
import org.bukkit.plugin.Plugin;

/** Dedicated class for "Server Stop". */
public final class ServerStop {
    private ServerStop() {}

    public static void handle(Plugin plugin) {
        Log.plain("SERVER STOP");
    }
}
