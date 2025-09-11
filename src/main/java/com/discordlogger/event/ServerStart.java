package com.discordlogger.event;

import com.discordlogger.log.Log;
import org.bukkit.plugin.Plugin;

/** Dedicated class for "Server Start". */
public final class ServerStart {
    private ServerStart() {}

    public static void handle(Plugin plugin) {
        Log.plain("SERVER START");
    }
}
