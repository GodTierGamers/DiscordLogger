package com.discordlogger.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The custom-log names are the only strings in the config an ADMIN chooses, and the
 * privacy contract says nothing describing a specific server leaves it. These pin the
 * two halves of that: the walk skips the group, and what is reported is a count.
 */
class CustomLogPrivacyTest {

    private static String metricsSource() throws Exception {
        return Files.readString(
                Path.of("src/main/java/com/discordlogger/metrics/PluginMetrics.java"));
    }

    @Test
    @DisplayName("the event walk skips log.custom")
    void walkSkipsCustom() throws Exception {
        // enabled_events, routed_events and colors_customised all run through
        // forEachEvent. Without the skip, a rule named after a server's own staff
        // process would be published to bStats as a chart slice.
        assertTrue(metricsSource().contains("if (CUSTOM.equals(category)) continue;"),
                "forEachEvent must skip the admin-named group");
    }

    @Test
    @DisplayName("the custom chart reports a bucket, never a name")
    void chartIsABucket() throws Exception {
        String src = metricsSource();
        assertTrue(src.contains("bucket(customLogCount(plugin))"),
                "custom_logs must report a bucketed count");
        // The supplier must not be able to reach the keys themselves.
        assertFalse(src.contains("new AdvancedPie(\"custom_logs\""),
                "an advanced pie would carry one slice per rule NAME");
    }
}
