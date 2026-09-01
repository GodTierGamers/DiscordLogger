package com.discordlogger.util;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the wiring that reflection put out of the compiler's reach.
 *
 * <p>{@link Compat} builds two listeners by name, so renaming either class, moving
 * its package or changing its constructor no longer breaks the build — it makes the
 * lookup return null at runtime and the feature silently stop registering. For
 * advancement logging that surfaces as "advancements stopped appearing in Discord",
 * which is not something anyone reports. These tests put a compile-time-ish check
 * back: they run against the 1.13 API, where both events exist, so the names and
 * signatures are verified on every build.
 */
class CompatTest {

    // Read from the call sites rather than restated, so a rename that misses
    // EventRegistry or DiscordLogger fails here instead of at runtime.
    private static final String ADVANCEMENT_EVENT = Compat.ADVANCEMENT_EVENT;
    private static final String ADVANCEMENT_LISTENER = Compat.ADVANCEMENT_LISTENER;
    private static final String COMMAND_SEND_EVENT = Compat.COMMAND_SEND_EVENT;
    private static final String COMMAND_SEND_LISTENER = Compat.COMMAND_SEND_LISTENER;
    private static final String BLOCK_EXPLODE_EVENT = Compat.BLOCK_EXPLODE_EVENT;
    private static final String BLOCK_EXPLODE_LISTENER = Compat.BLOCK_EXPLODE_LISTENER;
    private static final String ACHIEVEMENT_EVENT = Compat.ACHIEVEMENT_EVENT;
    private static final String ACHIEVEMENT_LISTENER = Compat.ACHIEVEMENT_LISTENER;

    @Test
    @DisplayName("the event classes the gates look for exist on the build API")
    void gatedEventsResolve() {
        // If these ever fail, the string in EventRegistry/DiscordLogger is wrong and
        // the feature would never register on any server.
        assertTrue(Compat.hasClass(ADVANCEMENT_EVENT), ADVANCEMENT_EVENT);
        assertTrue(Compat.hasClass(COMMAND_SEND_EVENT), COMMAND_SEND_EVENT);
        assertTrue(Compat.hasClass(BLOCK_EXPLODE_EVENT), BLOCK_EXPLODE_EVENT);
        assertTrue(Compat.hasClass(ACHIEVEMENT_EVENT), ACHIEVEMENT_EVENT);
    }

    @Test
    @DisplayName("both gated listeners exist under the names Compat looks up")
    void gatedListenersResolve() throws Exception {
        final Class<?> adv = Class.forName(ADVANCEMENT_LISTENER);
        final Class<?> vis = Class.forName(COMMAND_SEND_LISTENER);
        assertTrue(Listener.class.isAssignableFrom(adv), ADVANCEMENT_LISTENER + " is a Listener");
        assertTrue(Listener.class.isAssignableFrom(vis), COMMAND_SEND_LISTENER + " is a Listener");
        final Class<?> exp = Class.forName(BLOCK_EXPLODE_LISTENER);
        assertTrue(Listener.class.isAssignableFrom(exp), BLOCK_EXPLODE_LISTENER + " is a Listener");
        final Class<?> ach = Class.forName(ACHIEVEMENT_LISTENER);
        assertTrue(Listener.class.isAssignableFrom(ach), ACHIEVEMENT_LISTENER + " is a Listener");
    }

    @Test
    @DisplayName("their constructors still match the signatures the call sites pass")
    void gatedConstructorsMatch() throws Exception {
        // The exact signatures used in EventRegistry and DiscordLogger. A parameter
        // added here compiles fine and then fails only at runtime, so assert it.
        final Constructor<?> adv = Class.forName(ADVANCEMENT_LISTENER)
                .getConstructor(JavaPlugin.class);
        assertNotNull(adv);

        final Class<?> commands = Class.forName("com.discordlogger.command.Commands");
        final Constructor<?> vis = Class.forName(COMMAND_SEND_LISTENER)
                .getConstructor(JavaPlugin.class, commands);
        assertNotNull(vis);

        final Constructor<?> exp = Class.forName(BLOCK_EXPLODE_LISTENER)
                .getConstructor(JavaPlugin.class);
        assertNotNull(exp);

        final Constructor<?> ach = Class.forName(ACHIEVEMENT_LISTENER)
                .getConstructor(JavaPlugin.class);
        assertNotNull(ach);
    }

    @Test
    @DisplayName("a server without the event gets null, not an exception")
    void absentEventYieldsNull() {
        assertFalse(Compat.hasClass("org.bukkit.event.player.NoSuchEventHere"));
        assertNull(Compat.listenerIfPresent(
                "org.bukkit.event.player.NoSuchEventHere", ADVANCEMENT_LISTENER,
                new Class<?>[] { JavaPlugin.class }, (Object) null));
    }

    @Test
    @DisplayName("a listener that cannot be built is null, not a thrown plugin failure")
    void unbuildableListenerYieldsNull() {
        // The event is present but the listener class is not: must not propagate, or
        // onEnable dies over one optional log category.
        assertNull(Compat.listenerIfPresent(ADVANCEMENT_EVENT, "com.discordlogger.NotAClass",
                new Class<?>[] { JavaPlugin.class }, (Object) null));
    }
}
