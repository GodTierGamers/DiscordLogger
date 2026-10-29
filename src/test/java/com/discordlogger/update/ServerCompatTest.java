package com.discordlogger.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parsing and the fail-open contract, both testable without a server or a network. */
class ServerCompatTest {

    /**
     * Modrinth's real field order: game_versions BEFORE version_number.
     *
     * <p>This fixture used to have them the other way round, matching what the parser
     * assumed rather than what the API sends. Every test passed while the live check
     * returned the wrong release's versions -- it proved the parser agreed with itself.
     * Copied from an actual /v2/project/discordlogger/version response.
     */
    private static final String PAYLOAD = """
            [
              {"game_versions":["1.20.6","1.21","1.21.1"],"loaders":["paper"],"version_number":"2.3.1"},
              {"game_versions":["1.19","1.20","1.21"],"loaders":["paper"],"version_number":"2.3.0"},
              {"game_versions":["1.19.4"],"loaders":["paper"],"version_number":"2.2.0"}
            ]
            """;

    /** The other order, which Modrinth does not send but which must still parse. */
    private static final String PAYLOAD_NUMBER_FIRST = """
            [
              {"version_number":"2.3.1","game_versions":["1.20.6","1.21","1.21.1"]},
              {"version_number":"2.3.0","game_versions":["1.19","1.20","1.21"]}
            ]
            """;

    /**
     * A changelog carrying the punctuation the scan relies on.
     *
     * <p>Braces and brackets inside quoted text must not move an object boundary, and
     * a release body is written by a person, so this is not a contrived input.
     */
    private static final String PAYLOAD_AWKWARD_CHANGELOG = """
            [
              {"changelog":"Fixed {braces} and [brackets] and a quote \\" here",
               "game_versions":["1.21.4"],"version_number":"2.4.1"},
              {"changelog":"older","game_versions":["1.19"],"version_number":"2.4.0"}
            ]
            """;

    @Test
    @DisplayName("picks the game versions belonging to the asked-for release")
    void parsesTheRightBlock() {
        assertEquals(Set.of("1.20.6", "1.21", "1.21.1"), ServerCompat.parse(PAYLOAD, "2.3.1"));
        assertEquals(Set.of("1.19", "1.20", "1.21"), ServerCompat.parse(PAYLOAD, "2.3.0"));
        assertEquals(Set.of("1.19.4"), ServerCompat.parse(PAYLOAD, "2.2.0"));
    }

    @Test
    @DisplayName("reads the release's own versions, not the next release's")
    void doesNotRunOnIntoTheFollowingRelease() {
        // The bug this exists for: armed on the right version_number, then returned the
        // game_versions that came after it -- which belonged to the next object. It made
        // 2.4.0 look like it supported 1.19+, so every server from 1.8 to 1.18 was told
        // the release it had been waiting for could not run on it.
        assertEquals(Set.of("1.20.6", "1.21", "1.21.1"), ServerCompat.parse(PAYLOAD, "2.3.1"));
        assertTrue(ServerCompat.parse(PAYLOAD, "2.3.1").contains("1.20.6"),
                "must not return 2.3.0's list for 2.3.1");
    }

    @Test
    @DisplayName("a Paper build number is not part of the Minecraft version")
    void buildSuffixIsStripped() {
        // The line a real server printed: "does not list support for Minecraft
        // 26.2.build.71". That string is in no listing anywhere, so the check could
        // never match it and silenced the update on every modern Paper server.
        assertEquals("26.2", ServerCompat.numericPrefix("26.2.build.71"));
        assertEquals("1.21.1", ServerCompat.numericPrefix("1.21.1"));
        assertEquals("1.8", ServerCompat.numericPrefix("1.8"));
        assertEquals("1.20.4", ServerCompat.numericPrefix("1.20.4-pre1".split("-")[0]));
        // Nothing numeric to keep: handed back untouched so the caller fails open.
        assertEquals("weird", ServerCompat.numericPrefix("weird"));
        assertEquals("", ServerCompat.numericPrefix(""));
        assertNull(ServerCompat.numericPrefix(null));
    }

    @Test
    @DisplayName("field order inside the object does not matter")
    void bothFieldOrdersParse() {
        assertEquals(Set.of("1.20.6", "1.21", "1.21.1"),
                ServerCompat.parse(PAYLOAD_NUMBER_FIRST, "2.3.1"));
        assertEquals(Set.of("1.19", "1.20", "1.21"),
                ServerCompat.parse(PAYLOAD_NUMBER_FIRST, "2.3.0"));
    }

    @Test
    @DisplayName("punctuation inside a changelog does not move the object boundary")
    void quotedTextIsSkipped() {
        assertEquals(Set.of("1.21.4"),
                ServerCompat.parse(PAYLOAD_AWKWARD_CHANGELOG, "2.4.1"));
        assertEquals(Set.of("1.19"),
                ServerCompat.parse(PAYLOAD_AWKWARD_CHANGELOG, "2.4.0"));
    }

    @Test
    @DisplayName("an unknown release yields nothing, which fails open")
    void unknownReleaseIsEmpty() {
        // Empty means "cannot establish", and canRun treats that as compatible. A
        // checker that goes quiet because an API changed shape is worse than one that
        // occasionally recommends a build the admin declines.
        assertTrue(ServerCompat.parse(PAYLOAD, "9.9.9").isEmpty());
        assertTrue(ServerCompat.parse("not json at all", "2.3.1").isEmpty());
        assertTrue(ServerCompat.parse(null, "2.3.1").isEmpty());
    }

    @Test
    @DisplayName("a 26.3 server is offered a release only once that release lists 26.3")
    void twentySixThreeIsDecidedByTheList() {
        final Set<String> withIt = Set.of("1.21.11", "26.1", "26.1.2", "26.2", "26.3");
        final Set<String> withoutIt = Set.of("1.21.11", "26.1", "26.1.2", "26.2");

        assertTrue(ServerCompat.listed(withIt, "26.3"));
        assertFalse(ServerCompat.listed(withoutIt, "26.3"),
                "a release whose list stops at 26.2 must not be offered to a 26.3 server");

        // The whole path a real server takes: Bukkit.getBukkitVersion() hands back
        // "26.3.build.230-R0.1-SNAPSHOT", the dash is cut, then the build segments go.
        assertTrue(ServerCompat.listed(withIt, ServerCompat.numericPrefix("26.3.build.230")));
        assertFalse(ServerCompat.listed(withoutIt, ServerCompat.numericPrefix("26.3.build.230")));
    }

    @Test
    @DisplayName("a listing naming the minor line covers its patches, but 26 is not a line")
    void parentLineIsAcceptedOnlyWhereItExists() {
        assertTrue(ServerCompat.listed(Set.of("1.20"), "1.20.4"));
        assertTrue(ServerCompat.listed(Set.of("26.1"), "26.1.2"));
        // 26.x numbers the year, so the parent of "26.3" is the bare "26" -- which is
        // no Minecraft version and appears in no listing. A 26.3 server therefore gets
        // no free pass from a release that stops at 26.2, unlike a 1.20.4 server
        // reading a list that names 1.20.
        assertFalse(ServerCompat.listed(Set.of("26.2"), "26.3"));
    }

    @Test
    @DisplayName("a version_number carrying build metadata is not matched by the bare tag")
    void buildMetadataInTheListingFailsOpen() {
        // 2.4.1 is published to Modrinth as "2.4.1+bukkit" on purpose, to put the
        // installs with the broken reader down the fail-open branch. That rescue works
        // only because the lookup is an exact match, so pin it: if this ever starts
        // matching loosely, those installs go quiet again.
        final String listing =
                "[{\"game_versions\":[\"26.2\"],\"version_number\":\"2.4.1+bukkit\"}]";
        assertTrue(ServerCompat.parse(listing, "2.4.1").isEmpty());
        assertEquals(Set.of("26.2"), ServerCompat.parse(listing, "2.4.1+bukkit"));
    }

    @Test
    @DisplayName("no version, no server, no network: still compatible")
    void failsOpenWithoutAServer() {
        assertTrue(ServerCompat.canRun(""));
        assertTrue(ServerCompat.canRun(null));
    }
}
