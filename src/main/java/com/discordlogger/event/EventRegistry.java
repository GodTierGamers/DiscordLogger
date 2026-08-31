package com.discordlogger.event;

import com.discordlogger.util.Compat;
import org.bukkit.event.Listener;
import com.discordlogger.listener.custom.CustomCommandLog;
import com.discordlogger.listener.player.*;
import com.discordlogger.listener.server.*;
import com.discordlogger.listener.moderation.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class EventRegistry {
    private final JavaPlugin plugin;

    public EventRegistry(JavaPlugin plugin) { this.plugin = plugin; }

    public void registerAll() {
        PluginManager pm = plugin.getServer().getPluginManager();

        // Player events
        // Inert on 1.20+, where the server distinguishes /kill from a void death.
        pm.registerEvents(new KillCommandTracker(), plugin);
        pm.registerEvents(new PlayerJoin(plugin), plugin);
        pm.registerEvents(new PlayerQuit(plugin), plugin);
        pm.registerEvents(new PlayerChat(plugin), plugin);
        pm.registerEvents(new PlayerCommand(plugin), plugin);
        pm.registerEvents(new PlayerDeath(plugin), plugin);
        // PlayerAdvancementDoneEvent arrived in 1.12; before that advancements did not
        // exist. Naming the class directly here would stop the plugin loading at all
        // on an older server -- see util.Compat.
        final Listener advancement = Compat.listenerIfPresent(
                Compat.ADVANCEMENT_EVENT, Compat.ADVANCEMENT_LISTENER, plugin);
        if (advancement != null) pm.registerEvents(advancement, plugin);

        // The other half of the same feature: achievements existed from 1.8 and the API
        // outlived them until 1.15. On 1.12 to 1.14 both are registered and only the
        // advancement one fires, which costs nothing.
        final Listener achievement = Compat.listenerIfPresent(
                Compat.ACHIEVEMENT_EVENT, Compat.ACHIEVEMENT_LISTENER, plugin);
        if (achievement != null) pm.registerEvents(achievement, plugin);
        pm.registerEvents(new PlayerTeleport(plugin), plugin);
        pm.registerEvents(new PlayerGamemode(plugin), plugin);

        // Admin-defined command rules (log.custom.*)
        pm.registerEvents(new CustomCommandLog(plugin), plugin);

        // Server events
        pm.registerEvents(new ServerCommand(plugin), plugin);
        pm.registerEvents(new Explosion(plugin), plugin);

        // BlockExplodeEvent arrived in 1.8.3. Entity explosions above work on 1.8.0,
        // which is why the two are separate classes rather than one.
        final Listener blockExplosion = Compat.listenerIfPresent(
                Compat.BLOCK_EXPLODE_EVENT, Compat.BLOCK_EXPLODE_LISTENER, plugin);
        if (blockExplosion != null) pm.registerEvents(blockExplosion, plugin);

        // Moderation events
        pm.registerEvents(new Ban(plugin), plugin);
        pm.registerEvents(new Unban(plugin), plugin);
        pm.registerEvents(new Kick(plugin), plugin);
        pm.registerEvents(new Op(plugin), plugin);
        pm.registerEvents(new Deop(plugin), plugin);
        pm.registerEvents(new Whitelist(plugin), plugin);
    }

    public void fireServerStart() {
        if (plugin.getConfig().getBoolean("log.server.start.enabled", true)) {
            ServerStart.handle(plugin);
        }
    }

    public void fireServerStop() {
        if (plugin.getConfig().getBoolean("log.server.stop.enabled", true)) {
            ServerStop.handle(plugin);
        }
    }
}
