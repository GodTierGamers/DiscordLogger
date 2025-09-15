package com.discordlogger.event;

import com.discordlogger.listener.PlayerChat;
import com.discordlogger.listener.PlayerCommand;
import com.discordlogger.listener.PlayerDeath;
import com.discordlogger.listener.PlayerJoin;
import com.discordlogger.listener.PlayerQuit;
import com.discordlogger.listener.ServerCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class EventRegistry {
    private final JavaPlugin plugin;

    public EventRegistry(JavaPlugin plugin) { this.plugin = plugin; }

    public void registerAll() {
        PluginManager pm = plugin.getServer().getPluginManager();

        // Player events
        pm.registerEvents(new PlayerJoin(plugin), plugin);
        pm.registerEvents(new PlayerQuit(plugin), plugin);
        pm.registerEvents(new PlayerChat(plugin), plugin);
        pm.registerEvents(new PlayerCommand(plugin), plugin);
        pm.registerEvents(new PlayerDeath(plugin), plugin);

        // Server events
        pm.registerEvents(new ServerCommand(plugin), plugin);
    }

    public void fireServerStart() {
        if (plugin.getConfig().getBoolean("log.server.start", true)) {
            ServerStart.handle(plugin);
        }
    }

    public void fireServerStop() {
        if (plugin.getConfig().getBoolean("log.server.stop", true)) {
            ServerStop.handle(plugin);
        }
    }
}
