package com.discordlogger.event;


import com.discordlogger.listener.PlayerJoin;
import com.discordlogger.listener.PlayerQuit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/** Central place to register Bukkit listeners and fire lifecycle events. */
public final class EventRegistry {
    private final Plugin plugin;

    public EventRegistry(Plugin plugin) { this.plugin = plugin; }

    /** Register Bukkit listeners here as we add them. */
    public void registerAll() {
        PluginManager pm = plugin.getServer().getPluginManager();

        // Player events (kept under com.discordlogger.listener)
        pm.registerEvents(new PlayerJoin(plugin), plugin);
        pm.registerEvents(new PlayerQuit(plugin), plugin);

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
