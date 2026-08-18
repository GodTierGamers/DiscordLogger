package com.discordlogger.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link Placeholders} needs a running server to expand anything, so what is pinned
 * here is the contract that holds without one: it is inert unless switched on, and it
 * never throws. Both matter more than the expansion itself — this sits in the send
 * path of every player event, and the two-thirds of servers with no PlaceholderAPI
 * must not pay for it.
 */
class PlaceholdersTest {

    @Test
    @DisplayName("with PlaceholderAPI absent, text passes through untouched")
    void inertWhenUnavailable() {
        // reload() has never run in a unit test, so `enabled` is false — the state
        // every server without PlaceholderAPI is in, permanently.
        assertEquals("%vault_prefix% Steve joined",
                Placeholders.apply(null, "%vault_prefix% Steve joined"));
    }

    @Test
    @DisplayName("null and empty input are handled, not thrown on")
    void nullSafe() {
        assertNull(Placeholders.apply(null, null));
        assertEquals("", Placeholders.apply(null, ""));
    }

    @Test
    @DisplayName("text with no percent sign is returned without any work")
    void noPercentShortCircuits() {
        // The common case on every message: skipping the reflective call entirely
        // keeps this off the hot path rather than merely cheap on it.
        final String plain = "Steve joined the server";
        assertEquals(plain, Placeholders.apply(null, plain));
    }
}
