package com.discordlogger.util;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;

/**
 * Registers listeners whose event does not exist on every supported server.
 *
 * <h2>The problem this solves</h2>
 *
 * <p>The plugin supports servers from 1.8.8 up, and two events it listens to arrived
 * later: {@code PlayerAdvancementDoneEvent} in 1.12 (advancements did not exist at
 * all before then — 1.8 had achievements, a different event that 1.12 removed) and
 * {@code PlayerCommandSendEvent} in 1.13. A listener class naming a missing event
 * cannot even be loaded on an old server: registering it throws
 * {@link NoClassDefFoundError} out of {@code onEnable}, taking the whole plugin with
 * it.
 *
 * <h2>Why the reflection stops here</h2>
 *
 * <p>Only the <em>instantiation</em> goes through reflection. The listener classes
 * themselves are ordinary compiled code, built against the 1.13 API like everything
 * else, so their handler bodies keep full type checking and stay unit-testable —
 * {@code CommandVisibilityTest} covers exactly that logic today and continues to.
 *
 * <p>That is the whole reason this is a loader rather than a reflective listener.
 * Doing it the other way — probing the API inside each handler — would have put the
 * feature logic on a path no compiler and no test can reach, and advancement logging
 * is a feature every current server relies on. Silent breakage there reads as
 * "advancements stopped appearing", which nobody reports.
 *
 * <p>CI proves the rest of the plugin really is 1.8.8-clean: the {@code compat-floor}
 * job compiles against the floor with these two classes excluded, so any other file
 * reaching for a newer API fails there.
 */
public final class Compat {

    /**
     * The gated wiring, declared once.
     *
     * <p>These live here rather than inline at the call sites so that
     * {@code CompatTest} can assert the values actually in use. Inline literals would
     * let a rename update the call site and the test independently, which is the one
     * way this could still break silently.
     */
    public static final String ADVANCEMENT_EVENT =
            "org.bukkit.event.player.PlayerAdvancementDoneEvent";
    public static final String ADVANCEMENT_LISTENER =
            "com.discordlogger.listener.player.PlayerAdvancement";
    public static final String COMMAND_SEND_EVENT =
            "org.bukkit.event.player.PlayerCommandSendEvent";
    public static final String COMMAND_SEND_LISTENER =
            "com.discordlogger.command.CommandVisibility";
    public static final String BLOCK_EXPLODE_EVENT =
            "org.bukkit.event.block.BlockExplodeEvent";
    public static final String BLOCK_EXPLODE_LISTENER =
            "com.discordlogger.listener.server.BlockExplosion";

    private Compat() {}

    /** Whether this server provides {@code className}. */
    public static boolean hasClass(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /**
     * Builds {@code listenerClass} if {@code requiredEventClass} exists here.
     *
     * @return the listener, or {@code null} when the server is too old for it — the
     *         caller skips registration and the feature is simply absent, which is
     *         the honest outcome for an event the server will never fire.
     */
    public static Listener listenerIfPresent(String requiredEventClass,
                                             String listenerClass,
                                             Class<?>[] signature,
                                             Object... args) {
        if (!hasClass(requiredEventClass)) return null;
        try {
            final Class<?> type = Class.forName(listenerClass);
            final Constructor<?> ctor = type.getConstructor(signature);
            return (Listener) ctor.newInstance(args);
        } catch (Exception | LinkageError e) {
            // The event exists but the listener would not load. Never worth taking
            // the plugin down over one optional log category.
            return null;
        }
    }

    /** Convenience for the common {@code (JavaPlugin)} constructor. */
    public static Listener listenerIfPresent(String requiredEventClass,
                                             String listenerClass,
                                             JavaPlugin plugin) {
        return listenerIfPresent(requiredEventClass, listenerClass,
                new Class<?>[] { JavaPlugin.class }, plugin);
    }
}
