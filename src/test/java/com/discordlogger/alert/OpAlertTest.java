package com.discordlogger.alert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Delivery needs a server, so what is pinned here is the contract that holds without
 * one — and the two properties that decide whether this feature helps or gets muted.
 */
class OpAlertTest {

    private static String source() throws Exception {
        return Files.readString(
                Path.of("src/main/java/com/discordlogger/alert/OpAlert.java"));
    }

    @Test
    @DisplayName("alerting before init, or with no server, is not an error")
    void safeBeforeInit() {
        // Called from webhook worker threads, which can outlive onDisable.
        OpAlert.reset();
        assertDoesNotThrow(() -> OpAlert.deadWebhook("...abc123"));
        assertDoesNotThrow(() -> OpAlert.queueFull("...abc123"));
    }

    @Test
    @DisplayName("no alert can contain a webhook URL")
    void neverLeaksAUrl() throws Exception {
        // Chat is visible to anyone with the permission, and the URL is a bearer
        // credential. Callers pass a short id; the class must never build a full URL.
        final String src = source();
        assertFalse(src.contains("discord.com/api/webhooks"), src);
        assertFalse(src.contains("dest.url"), "must take the caller's short id, not the URL");
    }

    @Test
    @DisplayName("alerts are rate limited, or staff will mute them")
    void isRateLimited() throws Exception {
        // A dead webhook fails on EVERY event. Unthrottled, this would be worse than
        // the silence it replaces.
        final String src = source();
        assertTrue(src.contains("QUIET_MS"), "must have a cooldown between alerts");
        assertTrue(src.contains("compareAndSet"),
                "two workers failing at once must produce one alert, not two");
    }

    @Test
    @DisplayName("delivery hops to the main thread")
    void deliversOnMainThread() throws Exception {
        // Bukkit's player list is not safe to walk from a webhook worker thread.
        assertTrue(source().contains("runTask("),
                "must schedule onto the main thread before touching online players");
    }
}
