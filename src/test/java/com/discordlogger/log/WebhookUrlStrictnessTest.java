package com.discordlogger.log;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validation accepts less than redaction hides, and this pins that direction.
 *
 * <p>It used to be the other way round: four {@code startsWith} checks accepted
 * anything after the prefix, while redaction required a numeric id. Every case
 * below passed validation and failed redaction, and {@link #tokenNeverSurvives()}
 * is the one that mattered — the token reached the channel verbatim.
 */
class WebhookUrlStrictnessTest {

    private static final String TOKEN = "AbCdEf_Gh-IjKlMnOpQrStUvWxYz0123456789";
    private static final String ID = "123456789012345678";

    @Test
    @DisplayName("a prefix with no id and no token is not a webhook")
    void barePrefixRejected() {
        assertFalse(Log.isValidWebhookUrl("https://discord.com/api/webhooks/"));
        assertFalse(Log.isValidWebhookUrl("https://discord.com/api/webhooks"));
    }

    @Test
    @DisplayName("ids are snowflakes, so letters are not an id")
    void nonNumericIdRejected() {
        assertFalse(Log.isValidWebhookUrl("https://discord.com/api/webhooks/abc/" + TOKEN));
    }

    @Test
    @DisplayName("an id with no token is a truncated copy-paste, not a webhook")
    void idWithoutTokenRejected() {
        assertFalse(Log.isValidWebhookUrl("https://discord.com/api/webhooks/" + ID));
        assertFalse(Log.isValidWebhookUrl("https://discord.com/api/webhooks/" + ID + "/"));
    }

    @Test
    @DisplayName("a lookalike host is not Discord")
    void lookalikeHostRejected() {
        assertFalse(Log.isValidWebhookUrl(
                "https://discord.com.example.invalid/api/webhooks/" + ID + "/" + TOKEN));
        assertFalse(Log.isValidWebhookUrl(
                "https://notdiscord.com/api/webhooks/" + ID + "/" + TOKEN));
        assertFalse(Log.isValidWebhookUrl(
                "http://discord.com/api/webhooks/" + ID + "/" + TOKEN), "plain http");
    }

    @Test
    @DisplayName("a token never survives redaction, however malformed the URL")
    void tokenNeverSurvives() {
        final String[] urls = {
            "https://discord.com/api/webhooks/" + ID + "/" + TOKEN,
            "https://discord.com/api/webhooks/abc/" + TOKEN,
            "https://canary.discord.com/api/webhooks/00/" + TOKEN,
            "https://discordapp.com/api/webhooks/-1/" + TOKEN,
        };
        for (final String url : urls) {
            final String logged = Log.redactWebhooks("/discordlogger webhook " + url);
            assertFalse(logged.contains(TOKEN), "token survived redaction in: " + url);
        }
    }

    @Test
    @DisplayName("every shape Discord actually issues is still accepted")
    void realUrlsStillAccepted() {
        assertTrue(Log.isValidWebhookUrl("https://discord.com/api/webhooks/" + ID + "/" + TOKEN));
        assertTrue(Log.isValidWebhookUrl("https://discordapp.com/api/webhooks/" + ID + "/" + TOKEN));
        assertTrue(Log.isValidWebhookUrl("https://ptb.discord.com/api/webhooks/" + ID + "/" + TOKEN));
        assertTrue(Log.isValidWebhookUrl("https://canary.discord.com/api/webhooks/" + ID + "/" + TOKEN));
    }

    @Test
    @DisplayName("a pasted URL may carry a trailing slash, a query or whitespace")
    void forgivingWhereItCostsNothing() {
        assertTrue(Log.isValidWebhookUrl("https://discord.com/api/webhooks/" + ID + "/" + TOKEN + "/"));
        assertTrue(Log.isValidWebhookUrl("https://discord.com/api/webhooks/" + ID + "/" + TOKEN + "?wait=true"));
        assertTrue(Log.isValidWebhookUrl("  https://discord.com/api/webhooks/" + ID + "/" + TOKEN + "  "));
    }
}
