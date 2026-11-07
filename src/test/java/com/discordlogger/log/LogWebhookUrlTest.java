package com.discordlogger.log;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 
 * Tests the custom hostname allowlist, HTTPS requirement, endpoint structure and continued Discord support.
 */
class LogWebhookUrlTest {

    private static final Set<String> ALLOWED_HOSTS =
            Collections.singleton("chat.example.com");

    @Test
    void acceptsSelfHostedFluxerUrls() {
        assertTrue(Log.isValidWebhookUrl(
                "https://chat.example.com/webhooks/123456/secret",
                ALLOWED_HOSTS
        ));

        assertTrue(Log.isValidWebhookUrl(
                "https://chat.example.com/v1/webhooks/123456/secret",
                ALLOWED_HOSTS
        ));

        assertTrue(Log.isValidWebhookUrl(
                "https://chat.example.com/api/v1/webhooks/123456/secret",
                ALLOWED_HOSTS
        ));
    }

    @Test
    void rejectsUntrustedHosts() {
        assertFalse(Log.isValidWebhookUrl(
                "https://evil.example.com/api/v1/webhooks/123456/secret",
                ALLOWED_HOSTS
        ));
    }

    @Test
    void rejectsHttpUrls() {
        assertFalse(Log.isValidWebhookUrl(
                "http://chat.example.com/api/v1/webhooks/123456/secret",
                ALLOWED_HOSTS
        ));
    }

    @Test
    void rejectsInvalidWebhookPaths() {
        assertFalse(Log.isValidWebhookUrl(
                "https://chat.example.com/invalid/123456/secret",
                ALLOWED_HOSTS
        ));
    }

    @Test
    void retainsDiscordSupport() {
        assertTrue(Log.isValidWebhookUrl(
                "https://discord.com/api/webhooks/123456/secret",
                Collections.emptySet()
        ));
    }
}