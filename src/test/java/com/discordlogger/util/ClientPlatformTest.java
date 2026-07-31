package com.discordlogger.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bedrock detection, specifically its fallback.
 *
 * <p>Floodgate's API is the primary route and cannot be exercised here — it needs
 * Floodgate on the classpath and a running server. What is testable, and what
 * actually runs on the vast majority of servers, is the UUID-shape fallback. It
 * relies on undocumented Floodgate behaviour, so the exact bit pattern it keys on
 * is worth pinning: if someone "tidies" the check into something subtly different,
 * every Bedrock player silently stops being flagged.
 */
class ClientPlatformTest {

    @Test
    @DisplayName("a Floodgate UUID has zero most-significant bits")
    void detectsFloodgateShape() {
        assertTrue(ClientPlatform.looksLikeFloodgateUuid(
                UUID.fromString("00000000-0000-0000-0009-01f2a3b4c5d6")));
        assertTrue(ClientPlatform.looksLikeFloodgateUuid(new UUID(0L, 12345L)));
    }

    @Test
    @DisplayName("an ordinary Java UUID is not mistaken for Bedrock")
    void ignoresRealJavaUuids() {
        // A genuine Mojang UUID is version 4 random; the version nibble alone puts
        // a non-zero byte in the most significant half.
        assertFalse(ClientPlatform.looksLikeFloodgateUuid(
                UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")));
        assertFalse(ClientPlatform.looksLikeFloodgateUuid(UUID.randomUUID()));
    }

    @Test
    @DisplayName("a thousand random UUIDs produce no false positives")
    void randomUuidsNeverCollide() {
        for (int i = 0; i < 1000; i++) {
            assertFalse(ClientPlatform.looksLikeFloodgateUuid(UUID.randomUUID()),
                    "a real player would be wrongly labelled Bedrock");
        }
    }

    @Test
    @DisplayName("the all-zero UUID counts, and null does not")
    void handlesEdges() {
        assertTrue(ClientPlatform.looksLikeFloodgateUuid(new UUID(0L, 0L)));
        assertFalse(ClientPlatform.looksLikeFloodgateUuid(null));
        assertFalse(ClientPlatform.isBedrock(null));
    }

    @Test
    @DisplayName("with no Floodgate present, detection still answers without throwing")
    void worksWithoutFloodgateInstalled() {
        // The static initialiser must survive Floodgate being absent — it is absent
        // here, which is exactly the common case on a real server.
        assertFalse(ClientPlatform.floodgateApiAvailable(),
                "Floodgate is not a test dependency; if this passes, the probe is wrong");
        assertTrue(ClientPlatform.isBedrock(new UUID(0L, 42L)),
                "the fallback must still work when the API is unavailable");
        assertFalse(ClientPlatform.isBedrock(UUID.randomUUID()));
    }
}
