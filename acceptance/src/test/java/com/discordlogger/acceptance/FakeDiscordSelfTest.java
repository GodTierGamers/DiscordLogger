package com.discordlogger.acceptance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The harness proving itself before it is trusted to judge the plugin.
 *
 * <p>Everything else in this suite rests on one assumption: that a JVM told to use a
 * proxy and a truststore will send a genuine {@code https://discord.com} request here
 * instead of to Discord. If that were ever silently untrue, every other test would go
 * green by never sending anything at all -- the worst possible failure, since it looks
 * exactly like success.
 *
 * <p>So this speaks to the fake through {@link HttpURLConnection} configured the same
 * way the plugin's own HTTP layer is, and asserts the request arrived intact.
 */
class FakeDiscordSelfTest {

    @Test
    @DisplayName("a genuine discord.com POST is captured, body and headers intact")
    void capturesARealDiscordPost(@TempDir Path tmp) throws Exception {
        try (FakeDiscord discord = FakeDiscord.start(tmp)) {
            System.setProperty("https.proxyHost", "127.0.0.1");
            System.setProperty("https.proxyPort", String.valueOf(discord.proxyPort()));

            final String payload = "{\"content\":null,\"embeds\":[{\"title\":\"Player Join\"}]}";

            final HttpsURLConnection c =
                    (HttpsURLConnection) new URL(discord.webhookUrl()).openConnection();
            // Trust configured on this connection rather than through system properties.
            // javax.net.ssl.trustStore is read once, when the default SSLContext first
            // initialises, so setting it here would be silently ignored the moment any
            // earlier test had already made an HTTPS call. The server subprocess gets it
            // as a launch flag, where the ordering problem cannot arise.
            c.setSSLSocketFactory(discord.clientSocketFactory());
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("User-Agent", "DiscordLogger (test)");
            c.setDoOutput(true);
            final byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = c.getOutputStream()) { out.write(bytes); }

            assertEquals(204, c.getResponseCode(), "the fake should answer as Discord does");

            final FakeDiscord.Recorded r = discord.awaitPost(10, TimeUnit.SECONDS);
            assertEquals("POST", r.method);
            assertTrue(r.path.startsWith("/api/webhooks/"), "path was " + r.path);
            assertEquals("discord.com", r.header("host"),
                    "the plugin must believe it is talking to Discord");
            assertTrue(r.bodyContains("Player Join"), "body was " + r.body);
        } finally {
            System.clearProperty("https.proxyHost");
            System.clearProperty("https.proxyPort");
        }
    }

    @Test
    @DisplayName("assertSilent passes when nothing is sent")
    void silenceIsDetectable(@TempDir Path tmp) throws Exception {
        // The other half of every toggle test: proving a disabled category sends nothing
        // is only meaningful if this can tell silence from a broken harness.
        try (FakeDiscord discord = FakeDiscord.start(tmp)) {
            discord.assertSilent(300, TimeUnit.MILLISECONDS);
        }
    }
}
