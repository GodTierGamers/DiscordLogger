package com.discordlogger.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * {@link Vanish} reads Bukkit metadata, which needs a server — so what is pinned here
 * is the contract that holds without one: it never throws, and it never guesses.
 */
class VanishTest {

    @Test
    @DisplayName("a null player is not vanished, and does not explode")
    void nullPlayerIsSafe() {
        // Reached whenever a listener fires for something that is not a player.
        assertFalse(Vanish.isVanished(null));
    }
}
