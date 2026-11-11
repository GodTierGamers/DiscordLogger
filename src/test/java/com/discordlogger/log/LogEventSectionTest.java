package com.discordlogger.log;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Colour and webhook are independent settings, and one must never switch the other off.
 *
 * <p>They did not used to be: the webhook was read after a {@code continue} that fired
 * when {@code color} was missing or blank, so clearing a colour to fall back to the
 * built-in one silently stopped routing that event anywhere but the main webhook. No
 * warning, and nothing a reader of the config could have predicted.
 */
class LogEventSectionTest {

    private static final String HOOK = "https://discord.com/api/webhooks/123456789/TOKEN";

    private final Map<String, Integer> colours = new HashMap<>();
    private final Map<String, String> routes = new HashMap<>();
    private final List<String> warnings = new ArrayList<>();

    private void read(String group, String event, String yaml) {
        final YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(yaml);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        final ConfigurationSection sec = y.getConfigurationSection("event");
        Log.readEventSection(group, event, sec, 0x5865F2, colours, routes, warnings::add);
    }

    @Test
    @DisplayName("a blank colour leaves the event's webhook routing alone")
    void blankColourKeepsTheRoute() {
        read("player", "death", "event:\n  color: \"\"\n  webhook: \"" + HOOK + "\"\n");

        // The regression: this used to be empty.
        assertEquals(HOOK, routes.get("player_death"));
        // And the colour correctly falls back to the built-in default.
        assertFalse(colours.containsKey("player_death"));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    @DisplayName("a missing colour key behaves the same way")
    void missingColourKeepsTheRoute() {
        read("server", "explosion", "event:\n  webhook: \"" + HOOK + "\"\n");

        assertEquals(HOOK, routes.get("server_explosion"));
        assertFalse(colours.containsKey("server_explosion"));
    }

    @Test
    @DisplayName("both set still works, and moderation keeps its bare aliases")
    void bothSettingsTogether() {
        read("moderation", "whitelist_edit",
                "event:\n  color: \"#123456\"\n  webhook: \"" + HOOK + "\"\n");

        assertEquals(0x123456, colours.get("moderation_whitelist_edit"));
        assertEquals(0x123456, colours.get("whitelist_edit"));
        assertEquals(0x123456, colours.get("whitelist"));
        assertEquals(HOOK, routes.get("moderation_whitelist_edit"));
        assertEquals(HOOK, routes.get("whitelist_edit"));
        assertEquals(HOOK, routes.get("whitelist"));
    }

    @Test
    @DisplayName("an invalid webhook warns and routes nowhere, colour unaffected")
    void invalidWebhookStillWarns() {
        read("player", "chat", "event:\n  color: \"#ABCDEF\"\n  webhook: \"not-a-url\"\n");

        assertFalse(routes.containsKey("player_chat"));
        assertEquals(0xABCDEF, colours.get("player_chat"));
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("log.player.chat.webhook"), warnings.get(0));
    }
}
