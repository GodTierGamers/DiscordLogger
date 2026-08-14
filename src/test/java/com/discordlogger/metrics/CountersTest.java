package com.discordlogger.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two properties the runtime counters have to hold.
 *
 * <p>Reads are destructive on purpose — bStats sums each submission across every
 * server, so reporting a running total would re-count everything already reported
 * and draw a line that only ever climbs. And the send counter must leave the
 * process as a band, because message volume describes someone's community rather
 * than this plugin.
 */
class CountersTest {

    @Test
    @DisplayName("reading a counter resets it, so charts show activity not totals")
    void readsAreDestructive() {
        Counters.takeFailedInt();               // clear anything from another test
        Counters.failed();
        Counters.failed();
        assertEquals(2, Counters.takeFailedInt());
        assertEquals(0, Counters.takeFailedInt(),
                "a second read must report nothing new, or every report double-counts");
    }

    @Test
    @DisplayName("send volume leaves as a band, never as the number itself")
    void sendRateIsBanded() {
        Counters.takeSendRateBand();
        assertEquals("None", Counters.takeSendRateBand());

        for (int i = 0; i < 3; i++) Counters.sent();
        final String band = Counters.takeSendRateBand();
        assertEquals("Under 10/hour", band);

        // The guarantee: whatever the tally was, it is not what gets reported.
        for (int i = 0; i < 250; i++) Counters.sent();
        assertEquals("100-1000/hour", Counters.takeSendRateBand());
    }

    @Test
    @DisplayName("commands report which ran, never how many times")
    void commandsArePresenceOnly() {
        Counters.takeCommandsUsed();
        Counters.commandUsed("reload");
        Counters.commandUsed("reload");
        Counters.commandUsed("regen");

        final Set<String> used = Counters.takeCommandsUsed();
        assertEquals(Set.of("reload", "regen"), used,
                "a set, so running reload fifty times is indistinguishable from once");
        assertTrue(Counters.takeCommandsUsed().isEmpty(), "reads must reset");
    }

    @Test
    @DisplayName("a delta beyond Integer range clamps rather than wrapping negative")
    void clampsRatherThanWrapping() {
        assertEquals(Integer.MAX_VALUE, Counters.clamp(Long.MAX_VALUE));
        assertEquals(0, Counters.clamp(-1));
    }
}
