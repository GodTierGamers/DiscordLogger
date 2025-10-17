package com.discordlogger.event;

import com.discordlogger.listener.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

public final class EventRegistry {
    private final Plugin plugin;

    public EventRegistry(Plugin plugin) { this.plugin = plugin; }

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

        // Moderation events
        pm.registerEvents(new Ban(plugin), plugin);
        pm.registerEvents(new Unban(plugin), plugin);
        pm.registerEvents(new Kick(plugin), plugin);
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
