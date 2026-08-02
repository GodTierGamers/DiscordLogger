package com.discordlogger.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Command matching, and the shipped deny-list.
 *
 * <p>The matching is the security-relevant part. A deny-list that "msg" defeats by
 * typing "/essentials:msg" is worse than no deny-list, because the admin believes
 * private messages are excluded when they are not — and the same reasoning applies
 * to {@code /login}, which carries a password in plain text.
 *
 * <p>{@code blocksPlayer} and {@code blocksWorld} need a live server and config, so
 * they are exercised on a test server rather than here; what is pinned here is the
 * pure normalisation they all route through, plus the shipped defaults.
 */
class FiltersTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "'/msg hello there',            msg",
            "'msg hello there',             msg",
            "'/MSG Hello',                  msg",
            "'/essentials:msg hello',       msg",
            "'/minecraft:tell someone hi',  tell",
            "'/login hunter2',              login",
            "'  /login   hunter2  ',        login",
            "'/register pw pw',             register",
            "'/gamemode creative',          gamemode",
            "'/',                           ''",
    })
    void reducesACommandToItsIdentifyingWord(String raw, String expected) {
        assertEquals(expected, Filters.normaliseCommand(raw));
    }

    @Test
    @DisplayName("a plugin qualifier cannot be used to slip past the list")
    void qualifiedFormsNormaliseIdentically() {
        // The bypass this guards: an admin denies "msg", a player types
        // "/essentials:msg" and the message is logged anyway.
        for (String variant : List.of("/msg hi", "/essentials:msg hi", "/ESSENTIALS:MSG hi")) {
            assertEquals("msg", Filters.normaliseCommand(variant), variant);
        }
        for (String variant : List.of("/login pw", "/authme:login pw", "/AuthMe:Login pw")) {
            assertEquals("login", Filters.normaliseCommand(variant), variant);
        }
    }

    @ParameterizedTest(name = "shipped default denies /{0}")
    @ValueSource(strings = {"login", "register", "changepassword", "unregister",
                            "msg", "tell", "whisper", "w", "r", "reply"})
    @DisplayName("the shipped config denies the commands that leak")
    void shippedDefaultsCoverLeakyCommands(String command) throws Exception {
        assertTrue(shippedIgnoredCommands().contains(command),
                "config.yml should deny /" + command + " by default — command logging posts "
                        + "the full line, so this one would publish a password or a private message");
    }

    @Test
    @DisplayName("the shipped deny-list is normalised already, so it matches what players type")
    void shippedEntriesAreInMatchableForm() throws Exception {
        for (String entry : shippedIgnoredCommands()) {
            assertEquals(entry, Filters.normaliseCommand(entry),
                    "'" + entry + "' in config.yml does not normalise to itself, so it would "
                            + "never match anything a player types");
        }
    }

    @Test
    @DisplayName("the other filter lists ship empty, so nothing is silently hidden")
    void nonCommandFiltersShipEmpty() throws Exception {
        Map<?, ?> filters = shippedFilters();
        assertEquals(List.of(), filters.get("ignored_players"));
        assertEquals(List.of(), filters.get("ignored_worlds"));
        assertEquals(List.of(), filters.get("ignored_chat_containing"));
        assertEquals("", filters.get("exempt_permission"),
                "an exempt permission that defaulted to something real would hide activity "
                        + "on every server that happens to grant it");
    }

    @Test
    @DisplayName("blank and malformed input never matches")
    void handlesJunk() {
        assertFalse(Filters.blocksCommand(null));
        assertFalse(Filters.blocksCommand(""));
        assertFalse(Filters.blocksCommand("   "));
        assertFalse(Filters.blocksChat(null));
        assertFalse(Filters.blocksChat(""));
        assertFalse(Filters.blocksWorld(null));
        assertFalse(Filters.blocksPlayer(null));
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> shippedFilters() throws Exception {
        Map<String, Object> root = (Map<String, Object>) new Yaml()
                .load(Files.readString(Path.of("src/main/resources/config.yml")));
        return (Map<?, ?>) root.get("filters");
    }

    @SuppressWarnings("unchecked")
    private static List<String> shippedIgnoredCommands() throws Exception {
        return (List<String>) shippedFilters().get("ignored_commands");
    }
}
