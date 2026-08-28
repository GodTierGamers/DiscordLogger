package com.discordlogger.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parsing and the fail-open contract, both testable without a server or a network. */
class ServerCompatTest {

    private static final String PAYLOAD = """
            [
              {"version_number":"2.3.1","game_versions":["1.20.6","1.21","1.21.1"]},
              {"version_number":"2.3.0","game_versions":["1.19","1.20","1.21"]},
              {"version_number":"2.2.0","game_versions":["1.19.4"]}
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
