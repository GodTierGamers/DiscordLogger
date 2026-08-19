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
        MockEmbed m = MockEmbed.parse("player_join");
        assertEquals("player_join", m.category());
        assertTrue(m.isPlain(), "no detail given, so the plain one-liner should be used");
    }

    @Test
    @DisplayName("no arguments at all falls back to the server category")
    void noArgs() {
        assertEquals("server", MockEmbed.parse("").category());
    }

    @Test
    @DisplayName("a full embed parses every part")
    void fullEmbed() {
        MockEmbed m = MockEmbed.parse(
                "player_death player=\"Notch\" title=\"Player Death\" "
              + "desc=\"Notch fell from a high place\" field=\"Cause:Fall\"");
        assertEquals("player_death", m.category());
        assertNull(m.author(), "a real event leaves the author to embeds.author");
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
                MockEmbed.parse("x player=\"Notch\"").thumbnail());
    }

    @Test
    @DisplayName("an explicit avatar overrides the derived one")
    void avatarOverride() {
        assertEquals("https://example.test/a.png",
                MockEmbed.parse("x player=Notch avatar=https://example.test/a.png").thumbnail());
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
        MockEmbed m = MockEmbed.parse("x unknown=\"thing\"");
        assertEquals("x", m.category());
        assertTrue(m.fields().isEmpty());
    }

    @Test
    @DisplayName("author= overrides embeds.author; player= never does")
    void fallbackAuthor() {
        assertEquals("Lachlan", MockEmbed.parse("x title=\"Hi\" author=\"Lachlan\"").author());
    }

    @Test
    @DisplayName("a quoted value keeps its spaces")
    void quotedValueKeepsSpaces() {
        MockEmbed m = MockEmbed.parse("x desc=\"Notch fell from a high place\"");
        assertEquals("Notch fell from a high place", m.description());
    }

    @Test
    @DisplayName("an unquoted value still works when it has no spaces")
    void unquotedStillWorks() {
        assertEquals("https://mc-heads.net/avatar/Notch/256",
                MockEmbed.parse("x player=Notch").thumbnail());
    }

    @Test
    @DisplayName("several quoted values in one line stay separate")
    void multipleQuoted() {
        MockEmbed m = MockEmbed.parse(
                "player_ban player=\"Bad Actor\" title=\"Player Banned\" "
              + "field=\"Reason:Griefing spawn\" field=\"By:Lachlan:inline\"");
        assertNull(m.author());
        assertEquals("Player Banned", m.title());
        assertEquals(2, m.fields().size());
        assertEquals("Griefing spawn", m.fields().get(0).value);
        assertEquals("Lachlan", m.fields().get(1).value);
        assertTrue(m.fields().get(1).inline);
    }

    @Test
    @DisplayName("an unterminated quote takes the rest of the line rather than failing")
    void unterminatedQuote() {
        // Half-typed input should still render something to look at.
        assertEquals("Notch fell", MockEmbed.parse("x desc=\"Notch fell").description());
    }

    @Test
    @DisplayName("curly quotes work, since phones and docs produce them")
    void curlyQuotes() {
        MockEmbed m = MockEmbed.parse("x title=\u201CPlayer Death\u201D");
        assertEquals("Player Death", m.title());
    }

    @Test
    @DisplayName("a name with a space still resolves an avatar")
    void spacedNameAvatar() {
        assertEquals("https://mc-heads.net/avatar/Bad%20Actor/256",
                MockEmbed.parse("x player=\"Bad Actor\"").thumbnail());
    }
}
