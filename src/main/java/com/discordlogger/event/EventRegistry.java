package com.discordlogger.event;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/** Central place to register Bukkit listeners and fire lifecycle events. */
public final class EventRegistry {
    private final Plugin plugin;

    public EventRegistry(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Register Bukkit listeners here as we add them (PlayerJoin, etc.). */
    public void registerAll() {
        PluginManager pm = plugin.getServer().getPluginManager();
        // (none yet – we’ll add Player/Server listeners next)
        // pm.registerEvents(new PlayerJoinListener(plugin), plugin);
        // pm.registerEvents(new ServerCommandListener(plugin), plugin);
    }

    /** Server start routed through its own class, honoring config. */
    public void fireServerStart() {
        if (plugin.getConfig().getBoolean("log.server.start", true)) {
            ServerStart.handle(plugin);
        }
    }

    /** Server stop routed through its own class, honoring config. */
    public void fireServerStop() {
        if (plugin.getConfig().getBoolean("log.server.stop", true)) {
            ServerStop.handle(plugin);
        }
    }
}
