package com.discordlogger.webhook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The exact JSON a death sends to Discord.
 *
 * <p>This is user-visible output with an agreed shape — title, a description naming
 * the player, a "Cause of Death" field always, and a "Coords" field only when the
 * server opted in. None of that is enforced by the compiler, so it is pinned here.
 */
class DeathEmbedPayloadTest {

    private static final int RED = 0xED4245;
    private static final String THUMB = "https://example.invalid/head.png";

    private static String[][] fields(String... nameValuePairs) {
        String[][] out = new String[nameValuePairs.length / 2][];
        for (int i = 0; i < out.length; i++) {
            out[i] = new String[]{nameValuePairs[i * 2], nameValuePairs[i * 2 + 1], "false"};
        }
        return out;
    }

    private static String deathPayload(boolean withCoords) {
        String[][] f = withCoords
                ? fields("Cause of Death", "Fell from a high place",
                         "Coords", "128, 71, -344 in world")
                : fields("Cause of Death", "Fell from a high place");
        return DiscordWebhook.buildEmbedJson(
                "Player Death", "Lachlan died", RED,
                "2026-07-31T06:00:00Z", "Server Logs", "DiscordLogger v2.2.0", THUMB, f);
    }

    @Test
    @DisplayName("the embed carries the title, description and cause field")
    void hasTitleDescriptionAndCause() {
        String json = deathPayload(false);
        assertTrue(json.contains("\"title\":\"Player Death\""), json);
        assertTrue(json.contains("\"description\":\"Lachlan died\""), json);
        assertTrue(json.contains("\"name\":\"Cause of Death\""), json);
        assertTrue(json.contains("\"value\":\"Fell from a high place\""), json);
    }

    @Test
    @DisplayName("Coords is absent unless the server opted in")
    void coordsOnlyWhenEnabled() {
        assertFalse(deathPayload(false).contains("Coords"),
                "show_coords is off by default; the field must not appear at all");
        assertTrue(deathPayload(true).contains("\"name\":\"Coords\""));
        assertTrue(deathPayload(true).contains("\"value\":\"128, 71, -344 in world\""));
    }

    @Test
    @DisplayName("Cause of Death comes before Coords")
    void causeIsListedFirst() {
        String json = deathPayload(true);
        assertTrue(json.indexOf("Cause of Death") < json.indexOf("\"name\":\"Coords\""),
                "cause is the headline detail and must lead");
    }

    @Test
    @DisplayName("the envelope is the shape Discord expects")
    void envelopeShape() {
        String json = deathPayload(true);
        assertTrue(json.startsWith("{\"content\":null,\"embeds\":[{"), json);
        assertTrue(json.endsWith("}],\"attachments\":[]}"), json);
        assertTrue(json.contains("\"author\":{\"name\":\"Server Logs\"}"), json);
        assertTrue(json.contains("\"color\":" + RED), json);
        // No trailing comma before a closing brace — Discord rejects the whole payload.
        assertFalse(json.contains(",}"), json);
        assertFalse(json.contains(",]"), json);
    }

    @Test
    @DisplayName("a player name with quotes cannot break the payload")
    void escapesFieldContent() {
        String json = DiscordWebhook.buildEmbedJson(
                "Player Death", "He said \"hi\" died", RED, null, null, null, null,
                fields("Cause of Death", "Slain by A\\B"));
        assertFalse(json.contains("\"hi\""), "raw quotes would terminate the JSON string early");
        assertTrue(json.contains("\\\"hi\\\""), json);
        assertTrue(json.contains("A\\\\B"), json);
    }

    @Test
    @DisplayName("no fields at all still produces valid JSON")
    void handlesEmptyFields() {
        String json = DiscordWebhook.buildEmbedJson(
                "Player Death", "Lachlan died", RED, null, null, null, null, new String[0][]);
        assertFalse(json.contains("\"fields\""));
        assertTrue(json.endsWith("}],\"attachments\":[]}"), json);
        assertFalse(json.contains(",}"), json);
    }
}
