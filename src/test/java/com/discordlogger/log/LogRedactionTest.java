package com.discordlogger.log;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeping webhook URLs out of Discord.
 *
 * <p>A webhook URL is a bearer credential — anyone holding it can post to that
 * channel. Command logging echoes whatever was typed, so without redaction,
 * {@code /discordlogger webhook <url>} would publish the new URL to the channel
 * the plugin is currently posting to, which when changing webhooks is the old one.
 */
class LogRedactionTest {

    private static final String ID = "123456789012345678";
    private static final String TOKEN = "AbCdEf-gH1jK_lMnOpQrStUvWxYz";

    @ParameterizedTest(name = "{0} is treated as a webhook host")
    @ValueSource(strings = {
            "https://discord.com",
            "https://discordapp.com",
            "https://ptb.discord.com",
            "https://canary.discord.com",
    })
    void masksEveryDiscordHost(String host) {
        String url = host + "/api/webhooks/" + ID + "/" + TOKEN;
        assertEquals(host + "/api/webhooks/" + ID + "/***", Log.redactWebhooks(url));
    }

    @Test
    @DisplayName("the id survives so the channel is still identifiable")
    void keepsTheIdDropsTheToken() {
        String out = Log.redactWebhooks("/discordlogger webhook https://discord.com/api/webhooks/"
                + ID + "/" + TOKEN);
        assertTrue(out.contains(ID), "the channel id is not a secret and is useful in an audit trail");
        assertFalse(out.contains(TOKEN), "the token is the credential and must never be logged");
    }

    @Test
    @DisplayName("a URL embedded in a longer message is still masked")
    void masksUrlsMidSentence() {
        assertEquals("set it to https://discord.com/api/webhooks/" + ID + "/*** just now",
                Log.redactWebhooks("set it to https://discord.com/api/webhooks/"
                        + ID + "/" + TOKEN + " just now"));
    }

    @Test
    @DisplayName("ordinary commands pass through untouched")
    void leavesEverythingElseAlone() {
        assertEquals("/gamemode creative Steve", Log.redactWebhooks("/gamemode creative Steve"));
        assertEquals("/say hello #general", Log.redactWebhooks("/say hello #general"));
        assertEquals("", Log.redactWebhooks(""));
        assertEquals(null, Log.redactWebhooks(null));
    }

    @Test
    @DisplayName("only real Discord webhook URLs are accepted")
    void validatesWebhookUrls() {
        assertTrue(Log.isValidWebhookUrl("https://discord.com/api/webhooks/" + ID + "/" + TOKEN));
        assertTrue(Log.isValidWebhookUrl("https://canary.discord.com/api/webhooks/1/x"));

        assertFalse(Log.isValidWebhookUrl("https://evil.example/api/webhooks/1/x"),
                "a lookalike host must not be accepted — it would send every log off-site");
        assertFalse(Log.isValidWebhookUrl("http://discord.com/api/webhooks/1/x"),
                "plain HTTP would put the token on the wire in clear");
        assertFalse(Log.isValidWebhookUrl(""));
        assertFalse(Log.isValidWebhookUrl(null));
    }

    // ------------------------------------------------- startup webhook validation

    @Test
    @DisplayName("a deleted webhook is reported, and says how to fix it")
    void deletedWebhookIsLoud() {
        String msg = Log.probeMessage("webhook.url", 404);
        assertNotNull(msg);
        assertTrue(msg.contains("no longer exists"), msg);
        assertTrue(msg.contains("/discordlogger webhook"), msg);
    }

    @Test
    @DisplayName("an unreachable Discord does not accuse the webhook")
    void unreachableDoesNotCryWolf() {
        // The failure mode this guards: warning that a perfectly good webhook is dead
        // every time the network blips teaches admins to ignore the warning that matters.
        for (int status : new int[]{0, 500, 502, 503}) {
            String msg = Log.probeMessage("webhook.url", status);
            assertNotNull(msg, "status " + status);
            assertTrue(msg.contains("says nothing about whether the webhook is valid"),
                    "status " + status + ": " + msg);
        }
    }

    @Test
    @DisplayName("a healthy webhook produces no output at all")
    void healthyIsSilent() {
        assertNull(Log.probeMessage("webhook.url", 200));
        assertNull(Log.probeMessage("webhook.url", 204));
    }

    @Test
    @DisplayName("no probe message ever contains a webhook URL")
    void probeMessagesNeverLeakTheUrl() {
        // The message names the config path, never the credential it holds.
        for (int status : new int[]{0, 200, 401, 403, 404, 500}) {
            String msg = Log.probeMessage("log.player.join.webhook", status);
            if (msg == null) continue;
            assertFalse(msg.contains("discord.com"), msg);
            assertFalse(msg.contains("https://"), msg);
        }
    }
}
