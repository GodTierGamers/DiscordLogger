package com.discordlogger.command;

import com.discordlogger.log.Log;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parsing for the hand-composed test embed. */
class MockEmbedTest {

    @Test
    @DisplayName("a bare category still works, unchanged")
    void bareCategory() {
        MockEmbed m = MockEmbed.parse("player_join", "Console");
        assertEquals("player_join", m.category());
        assertTrue(m.isPlain(), "no detail given, so the plain one-liner should be used");
    }

    @Test
    @DisplayName("no arguments at all falls back to the server category")
    void noArgs() {
        assertEquals("server", MockEmbed.parse("", "Console").category());
    }

    @Test
    @DisplayName("a full embed parses every part")
    void fullEmbed() {
        MockEmbed m = MockEmbed.parse(
                "player_death | player=Notch | title=Player Death "
              + "| desc=Notch fell from a high place | field=Cause:Fall", "Console");
        assertEquals("player_death", m.category());
        assertEquals("Notch", m.author());
        assertEquals("Player Death", m.title());
        assertEquals("Notch fell from a high place", m.description());
        assertEquals(1, m.fields().size());
        assertEquals("Cause", m.fields().get(0).name);
        assertEquals("Fall", m.fields().get(0).value);
        assertFalse(m.isPlain());
    }

    @Test
    @DisplayName("an invented player gets an avatar from their name")
    void avatarFromName() {
        // The point of the feature: several fake accounts produce visibly different
        // heads with nothing to host or look up.
        assertEquals("https://mc-heads.net/avatar/Notch/256",
                MockEmbed.parse("x | player=Notch", "Console").thumbnail());
    }

    @Test
    @DisplayName("an explicit avatar overrides the derived one")
    void avatarOverride() {
        assertEquals("https://example.test/a.png",
                MockEmbed.parse("x | player=Notch | avatar=https://example.test/a.png",
                        "Console").thumbnail());
    }

    @Test
    @DisplayName("a field value may contain colons")
    void valueKeepsItsColons() {
        // Coordinates and timestamps both do, and losing them to the delimiter would
        // rule out most of what is worth screenshotting.
        Log.Field f = MockEmbed.field("Coords:x: 123, y: 64");
        assertEquals("Coords", f.name);
        assertEquals("x: 123, y: 64", f.value);
        assertFalse(f.inline);
    }

    @Test
    @DisplayName("a trailing :inline marks the field inline")
    void inlineFlag() {
        Log.Field f = MockEmbed.field("Coords:123, 64, -90:inline");
        assertEquals("123, 64, -90", f.value);
        assertTrue(f.inline);
    }

    @Test
    @DisplayName("malformed parts are skipped, never thrown on")
    void malformedIgnored() {
        assertNull(MockEmbed.field("nocolon"));
        assertNull(MockEmbed.field(":novalue"));
        MockEmbed m = MockEmbed.parse("x | garbage | =novalue | unknown=thing", "Console");
        assertEquals("x", m.category());
        assertTrue(m.fields().isEmpty());
    }

    @Test
    @DisplayName("with no player, the sender is the author")
    void fallbackAuthor() {
        assertEquals("Lachlan", MockEmbed.parse("x | title=Hi", "Lachlan").author());
    }
}
