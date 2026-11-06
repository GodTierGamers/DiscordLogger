package com.discordlogger.listener.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cause-to-icon mapping, which is the one part of fire logging that is pure.
 *
 * <p>It is tested because the mapping is by string: the enum has eight constants
 * today and {@code ARROW} only arrived in 1.15, so the listener can never name them
 * and the compiler can never check this.
 */
class ServerFireIconTest {

    @Test
    @DisplayName("every cause with its own texture gets it")
    void knownCausesMapToTheirOwnIcon() {
        assertTrue(ServerFire.iconFor("FLINT_AND_STEEL").endsWith("/fire/flint_and_steel.png"));
        assertTrue(ServerFire.iconFor("FIREBALL").endsWith("/fire/fireball.png"));
        assertTrue(ServerFire.iconFor("LIGHTNING").endsWith("/fire/lightning.png"));
        assertTrue(ServerFire.iconFor("EXPLOSION").endsWith("/fire/explosion.png"));
        assertTrue(ServerFire.iconFor("ENDER_CRYSTAL").endsWith("/fire/ender_crystal.png"));
        assertTrue(ServerFire.iconFor("ARROW").endsWith("/fire/arrow.png"));
    }

    @Test
    @DisplayName("the two animated textures are served as GIFs")
    void animatedCausesUseTheGif() {
        // Fire and lava animate in the game, and Discord animates a GIF thumbnail.
        assertTrue(ServerFire.iconFor("LAVA").endsWith("/fire/lava.gif"));
        assertTrue(ServerFire.iconFor("SPREAD").endsWith("/fire/spread.gif"));
    }

    @Test
    @DisplayName("a cause a future version adds still gets a picture of fire")
    void unknownCausesFallBack() {
        // ARROW is the precedent: it did not exist before 1.15, and the next one
        // will not exist before the version that adds it.
        assertTrue(ServerFire.iconFor("SOMETHING_NEW").endsWith("/fire/spread.gif"));
        assertTrue(ServerFire.iconFor(null).endsWith("/fire/spread.gif"));
    }

    @Test
    @DisplayName("the cause is shown as words, not as an enum constant")
    void causeIsReadable() {
        assertEquals("Flint and steel", ServerFire.pretty("FLINT_AND_STEEL"));
        assertEquals("Lightning", ServerFire.pretty("LIGHTNING"));
        assertEquals("Unknown", ServerFire.pretty(null));
        assertEquals("Unknown", ServerFire.pretty(""));
    }
}
