package com.discordlogger.custom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code log.custom} must be an empty BLOCK mapping, never a flow one.
 *
 * <p>Shipping {@code custom: {}} made the obvious edit impossible: YAML will not accept
 * block children under a flow mapping, so
 *
 * <pre>
 *   custom: {}
 *     rank_change:
 *       enabled: true
 * </pre>
 *
 * is a parse error. Worse, the flow syntax someone reaches for instead —
 * {@code custom: { rank_change, enabled: true }} — <em>parses cleanly</em> and produces
 * a rule named {@code rank_change} with no body plus three stray siblings, so the
 * feature silently does nothing and the config looks fine.
 */
class CustomConfigShapeTest {

    private static String shipped() throws Exception {
        return Files.readString(Path.of("src/main/resources/config.yml"));
    }

    @Test
    @DisplayName("log.custom is not a flow mapping")
    void notAFlowMapping() throws Exception {
        assertFalse(shipped().contains("custom: {}"),
                "custom: {} cannot take block children — ship a bare 'custom:' instead");
    }

    @Test
    @DisplayName("log.custom is present and ready for children")
    void presentAsABlockKey() throws Exception {
        assertTrue(shipped().contains("\n  custom:\n"),
                "config.yml must ship 'custom:' with no value, so rules can be added under it");
    }
}
