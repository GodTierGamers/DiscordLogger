package com.discordlogger.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    @DisplayName("no version, no server, no network: still compatible")
    void failsOpenWithoutAServer() {
        assertTrue(ServerCompat.canRun(""));
        assertTrue(ServerCompat.canRun(null));
    }
}
