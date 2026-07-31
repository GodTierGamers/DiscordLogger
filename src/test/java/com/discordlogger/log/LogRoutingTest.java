package com.discordlogger.log;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every message must resolve its destination through {@code webhookFor}.
 *
 * <p>Per-event routing shipped with one send site missed — the fields embed, used by
 * deaths, gamemode changes and every moderation event — while the plain-embed path
 * was converted. The result was that {@code quit} routed correctly and {@code death}
 * silently went to the main webhook instead. Nothing failed; the messages simply
 * arrived in the wrong channel, which no unit test was watching for and no user
 * would report as a crash.
 *
 * <p>Asserting on the source is unusual, but the property here is structural: it is
 * about which expression appears at a call site, and there is no runtime seam that
 * exposes it without a live server. The repo already checks source this way in
 * {@code scripts/validate-config-generator.py}.
 */
class LogRoutingTest {

    private static final Path LOG_SOURCE =
            Path.of("src/main/java/com/discordlogger/log/Log.java");

    @Test
    @DisplayName("no send site passes the raw webhook field")
    void everySendSiteResolvesARoute() throws Exception {
        final List<String> lines = Files.readAllLines(LOG_SOURCE);
        final List<String> offenders = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            if (line.strip().startsWith("//") || line.strip().startsWith("*")) continue;
            if (!line.contains("webhookUrl")) continue;

            // The three legitimate mentions: the declaration, the assignment in
            // init, and the fallback inside webhookFor itself.
            if (line.contains("private static volatile String webhookUrl")) continue;
            if (line.contains("webhookUrl = ready")) continue;
            if (line.contains("? routed : webhookUrl")) continue;

            offenders.add((i + 1) + ": " + line.strip());
        }

        assertEquals(List.of(), offenders,
                "these read webhookUrl directly instead of webhookFor(category), so they "
                        + "would ignore per-event routing and post to the main webhook");
    }

    @Test
    @DisplayName("every Discord send in Log goes through webhookFor")
    void allDispatchesUseTheResolver() throws Exception {
        final String src = Files.readString(LOG_SOURCE);

        // Count the calls that actually dispatch, and require each to be paired with
        // a resolver call. A new send site added without routing fails this.
        final long sends = countOccurrences(src, "DiscordWebhook.send");
        final long resolved = countOccurrences(src, "webhookFor(");

        assertTrue(sends > 0, "expected Log to dispatch to Discord at all");
        assertTrue(resolved >= sends,
                "found " + sends + " DiscordWebhook.send* call(s) but only " + resolved
                        + " webhookFor(...) call(s) — at least one send is not resolving a route");
    }

    private static long countOccurrences(String haystack, String needle) {
        long n = 0;
        int i = haystack.indexOf(needle);
        while (i >= 0) {
            n++;
            i = haystack.indexOf(needle, i + needle.length());
        }
        return n;
    }
}
