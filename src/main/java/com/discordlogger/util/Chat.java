package com.discordlogger.util;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Sends a {@link Component} to a {@link CommandSender} on any server.
 *
 * <h2>Why this exists</h2>
 *
 * <p>{@code CommandSender.sendMessage(Component)} is a Paper method. The plugin now
 * compiles against Spigot so one JAR serves both, and on Spigot that overload is
 * simply not there — the only {@code sendMessage} is the {@code String} one.
 *
 * <p>Flattening to legacy section codes on the way out would have been the smaller
 * change, and it is the wrong one: {@code lang.yml} documents hex, gradients,
 * {@code click} and {@code hover} as available, and legacy codes can carry none of
 * those. Every server in the live metrics is 1.19 or newer, so that would be a
 * silent regression for the entire current audience in exchange for supporting
 * servers none of them are running. Adventure keeps the Component intact and
 * downsamples it only where the client genuinely cannot render it.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>{@link BukkitAudiences} holds listeners and must be closed, so {@link #init} is
 * called from {@code onEnable} and {@link #close} from {@code onDisable}. Sending
 * before init or after close is a no-op rather than an NPE: these are chat niceties
 * — a warning about a missing webhook, a reload confirmation — and a plugin that
 * blew up during startup teardown because it tried to explain itself would be worse
 * than one that stayed quiet.
 */
public final class Chat {

    private static volatile BukkitAudiences audiences;

    private Chat() {}

    public static void init(JavaPlugin plugin) {
        try {
            audiences = BukkitAudiences.create(plugin);
        } catch (Exception | LinkageError tooOld) {
            // adventure-platform-bukkit supports 1.8.8 and up, and the plugin now runs
            // from 1.8.0. Rather than assume it copes below its own supported range,
            // fall back to legacy section codes -- see send().
            audiences = null;
            plugin.getLogger().info("Adventure is unavailable on this server; "
                    + "chat messages will use legacy colour codes.");
        }
    }

    public static void close() {
        final BukkitAudiences a = audiences;
        audiences = null;
        if (a != null) a.close();
    }

    /**
     * Sends {@code message}, degrading rather than disappearing.
     *
     * <p>Three tiers, in order: Adventure when it came up, legacy section codes when
     * it did not, and silence only if even that throws. The middle tier is what makes
     * 1.8.0 through 1.8.7 honest — below Adventure's own supported range, a player
     * still gets a coloured, readable message. What it cannot carry is hover, click
     * and true hex, none of which a client that old can render anyway.
     */
    public static void send(CommandSender to, Component message) {
        if (to == null || message == null) return;
        final BukkitAudiences a = audiences;
        try {
            if (a != null) {
                a.sender(to).sendMessage(message);
            } else {
                to.sendMessage(LegacyComponentSerializer.legacySection().serialize(message));
            }
        } catch (Exception ignored) {
            // A chat line is never worth propagating an exception into a command
            // handler or an event listener.
        }
    }
}
