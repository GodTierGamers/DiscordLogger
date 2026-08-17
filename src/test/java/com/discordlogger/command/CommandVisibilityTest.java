package com.discordlogger.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tab-completion filter. Pure decisions only — the event itself needs a server.
 */
class CommandVisibilityTest {

    private static final String NS = "discordlogger:";
    private static final Set<String> LABELS = Set.of("discordlogger", "dlogger", "dlog");

    private static boolean noise(String entry, boolean usable) {
        return CommandVisibility.isNoise(entry, NS, LABELS, usable);
    }

    @Test
    @DisplayName("the plugin:command duplicates go, for everyone")
    void namespacedDuplicatesAreDropped() {
        // The reported symptom: these three were offered beside the real commands.
        for (String e : new String[]{
                "discordlogger:discordlogger", "discordlogger:dlogger", "discordlogger:dlog"}) {
            assertTrue(noise(e, true), e + " should be hidden from an op");
            assertTrue(noise(e, false), e + " should be hidden from a normal player");
        }
    }

    @Test
    @DisplayName("the real commands survive for someone who can use them")
    void realCommandsSurviveForPrivileged() {
        assertFalse(noise("discordlogger", true));
        assertFalse(noise("dlogger", true));
        assertFalse(noise("dlog", true));
    }

    @Test
    @DisplayName("a player who can run nothing is not offered the command")
    void hiddenEntirelyWithoutPermission() {
        assertTrue(noise("discordlogger", false));
        assertTrue(noise("dlogger", false));
        assertTrue(noise("dlog", false));
    }

    @Test
    @DisplayName("other plugins are left completely alone")
    void otherPluginsUntouched() {
        // Editing another plugin's entries makes its behaviour unexplainable from
        // its own source, which is worse than the noise this fixes.
        for (String e : new String[]{
                "essentials:home", "minecraft:kill", "worldedit:/set", "home", "kill"}) {
            assertFalse(noise(e, true), e);
            assertFalse(noise(e, false), e);
        }
    }

    @Test
    @DisplayName("an unrelated command under our namespace is left alone")
    void onlyDeclaredLabelsAreMatched() {
        // Matching the whole namespace rather than the declared labels would hide
        // anything another plugin registered there.
        assertFalse(noise("discordlogger:somethingelse", true));
        assertFalse(noise("discordlogger:somethingelse", false));
    }

    @Test
    @DisplayName("matching ignores case")
    void caseInsensitive() {
        assertTrue(noise("DiscordLogger:DLog", true));
        assertTrue(noise("DLOGGER", false));
    }

    @Test
    @DisplayName("a null entry is not an error")
    void nullIsSafe() {
        assertFalse(noise(null, true));
    }
}
