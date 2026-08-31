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
            // adventure-platform-bukkit documents 1.8.8 and up, and the plugin runs
            // from 1.8.0. In practice it works on 1.8.0 too -- verified on CraftBukkit
            // 1.8, where hex, gradients, hover and click all render once Gson is on the
            // classpath -- so this is a safety net rather than the expected path. It is
            // kept because "works today, below its supported range" is not a promise the
            // library made, and see send() for what happens when it breaks mid-flight.
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
     * <p>Three tiers, in order: Adventure when it works, legacy section codes when it
     * does not, and silence only if even that fails. What makes the middle tier reachable
     * is catching {@link Throwable} rather than {@link Exception}.
     *
     * <p>That distinction was found the hard way on CraftBukkit 1.8.
     * {@code BukkitAudiences.create} succeeded, so this looked healthy, and the failure
     * came later inside Adventure's own send path as a {@link NoClassDefFoundError} for
     * a Gson class the server does not expose. An Error is not an Exception, so it went
     * straight past the old catch, out of the command handler, and printed "Unhandled
     * exception executing command" instead of a reload confirmation. Errors are exactly
     * what a missing class on an old server looks like, so they are the case this most
     * needs to survive.
     *
     * <p>The first such failure also switches Adventure off for the rest of the run. It
     * will not fix itself, and retrying it per message would repeat the cost and the
     * stack trace on every line the plugin sends.
     */
    public static void send(CommandSender to, Component message) {
        if (to == null || message == null) return;

        final BukkitAudiences a = audiences;
        if (a != null) {
            try {
                a.sender(to).sendMessage(message);
                return;
            } catch (Throwable adventureFailed) {
                audiences = null;
                try {
                    a.close();
                } catch (Throwable ignored) {
                    // Already failing; closing is best effort.
                }
            }
        }

        try {
            to.sendMessage(LegacyComponentSerializer.legacySection().serialize(message));
        } catch (Throwable ignored) {
            // A chat line is never worth propagating out of a command handler or an
            // event listener.
        }
    }
}
