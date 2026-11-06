package com.discordlogger.listener.server;

import com.discordlogger.lang.Lang;
import com.discordlogger.log.Log;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Plugins enabled or disabled while the server is running.
 *
 * <p>Near-zero volume and squarely an audit event: outside a restart, a plugin
 * changing state means somebody did it, and that is worth a line in the staff
 * channel.
 *
 * <p>Startup and shutdown are deliberately excluded. Every plugin on the server
 * fires one of these during boot, which would post dozens of messages nobody asked
 * for and bury the server-start message in them.
 */
public final class ServerPlugin implements Listener {
    /**
     * A command block, animated, because nothing in Minecraft means "plugin" and a
     * command block is the closest thing that reads as server machinery.
     */
    private static final String THUMB_PLUGIN =
            "https://discordlogger.godtiergamers.xyz/assets/icons/plugin.gif";

    private final JavaPlugin plugin;

    /**
     * Until the server says it is done starting, every enable is part of boot.
     * Set once {@code EventRegistry} fires the start message, which happens after
     * the server has loaded everything.
     */
    private static volatile boolean running = false;

    public ServerPlugin(JavaPlugin plugin) { this.plugin = plugin; }

    public static void serverIsUp() { running = true; }
    public static void serverIsStopping() { running = false; }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnable(PluginEnableEvent e) {
        post(e.getPlugin(), "discord.plugin.enabled-title");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDisable(PluginDisableEvent e) {
        post(e.getPlugin(), "discord.plugin.disabled-title");
    }

    private void post(Plugin changed, String titleKey) {
        if (!running) return;
        if (!plugin.getConfig().getBoolean("log.server.plugin.enabled", true)) return;
        if (changed == null) return;

        final List<Log.Field> fields = new ArrayList<>();
        fields.add(new Log.Field(Lang.text("discord.plugin.plugin-field"),
                Log.mdEscape(changed.getName()), true));
        fields.add(new Log.Field(Lang.text("discord.plugin.version-field"),
                Log.mdEscape(changed.getDescription().getVersion()), true));

        Log.eventFieldsWithThumb(
                "server_plugin", Lang.text(titleKey), null, fields, THUMB_PLUGIN);
    }
}
