package com.discordlogger.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v13 has to be distinguishable from v12 by its keys alone.
 *
 * <p>Without a marker the two are shape-identical, {@code SchemaDetector} reports 12
 * for a file declaring 13, and the shipped config logs a version disagreement on
 * every single start.
 */
class SchemaV13Test {

    private static Map<String, Object> keysOf(String... paths) {
        final Map<String, Object> flat = new HashMap<>();
        for (final String p : paths) flat.put(p, Boolean.TRUE);
        return flat;
    }

    /** Enough v12 shape that the detector has something to fall back to. */
    private static final String[] V12 = {
        "log.server.explosion.history.enabled",
        "log.server.explosion.history.expiry_time",
    };

    @Test
    @DisplayName("any one v13 key is decisive")
    void eachMarkerIsEnough() {
        final String[] markers = {
            "log.player.first_join.enabled",
            "log.player.sign.enabled",
            "log.player.book.enabled",
            "log.player.bucket.enabled",
            "log.player.item_frame.enabled",
            "log.server.plugin.enabled",
            "log.server.lag.enabled",
        };
        for (final String marker : markers) {
            final Map<String, Object> flat = keysOf(V12);
            flat.put(marker, Boolean.TRUE);
            assertEquals(13, SchemaDetector.infer(flat),
                    marker + " alone should identify a v13 file");
        }
    }

    @Test
    @DisplayName("a v12 file is still v12")
    void v12StaysV12() {
        assertEquals(12, SchemaDetector.infer(keysOf(V12)));
    }

    @Test
    @DisplayName("the shipped config declares the schema its keys actually are")
    void shippedConfigAgreesWithItself() throws Exception {
        final String text;
        try (java.io.InputStream in = getClass().getResourceAsStream("/config.yml")) {
            text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        assertTrue(text.contains("config-version: 13"), "config-version key");
        assertTrue(text.contains("CONFIG VERSION V13"), "trailer");
    }
}
