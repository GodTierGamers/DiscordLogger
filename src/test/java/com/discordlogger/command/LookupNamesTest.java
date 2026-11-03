package com.discordlogger.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Turning the stored UUIDs back into something a person can read.
 *
 * <p>The file stores UUIDs so a rename cannot rot a record; these cover the other half
 * of that trade, including the case where the UUID cannot be resolved at all — which is
 * the normal case here, since there is no server in a unit test.
 */
class LookupNamesTest {

    private static final String STEVE = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
    private static final String ALEX  = "853c80ef-3c37-49fd-aa49-938b674adae6";

    @Test
    @DisplayName("an unresolvable UUID still identifies somebody")
    void fallsBackToAShortId() {
        // Dropping the entry would read as "nobody was there", which is the one answer
        // that must never be invented. Eight characters is enough to match against
        // another row or to look up by hand.
        final String out = Lookup.names(STEVE + "@4.2");
        assertEquals("069a79f4 (4.2m)", out);
    }

    @Test
    @DisplayName("several players keep their order and their distances")
    void listsEveryone() {
        final String out = Lookup.names(STEVE + "@4.2 " + ALEX + "@11.8");
        assertEquals("069a79f4 (4.2m), 853c80ef (11.8m)", out);
    }

    @Test
    @DisplayName("an empty list is empty, not the word \"none\"")
    void emptyStaysEmpty() {
        // The caller decides whether to print anything at all, so this must not invent
        // a placeholder that then appears in the middle of a detail line.
        assertEquals("", Lookup.names(""));
        assertEquals("", Lookup.names("   "));
        assertEquals("", Lookup.names(null));
    }

    @Test
    @DisplayName("a hand-edited or pre-UUID entry is passed through rather than dropped")
    void toleratesWhatItFinds() {
        // Older rows held display names, and the file is plain text an admin may edit.
        assertEquals("Steve (4.2m)", Lookup.names("Steve@4.2"));
        assertTrue(Lookup.names("Steve").contains("Steve"), "no distance is still a name");
    }
}
