package com.discordlogger.listener.player;

import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every way the server can kill a player has words for it.
 *
 * <p>This exists because thirteen of the thirty-three damage causes — including the
 * one behind {@code /kill} — silently fell through to "Cause of Death: Died". That
 * is invisible until someone dies that particular way, so the gap is closed by
 * walking the API's own enum rather than by listing the ones anyone thought of.
 */
class PlayerDeathCauseTest {

    @ParameterizedTest
    @EnumSource(DamageCause.class)
    @DisplayName("every damage cause the API declares has phrasing")
    void everyCauseIsHandled(DamageCause cause) {
        assertNotNull(PlayerDeath.causeText(cause),
                cause + " has no phrasing, so a player dying this way would be reported "
                        + "as a bare \"Died\". Add a case for it.");
    }

    @Test
    @DisplayName("a future Paper release adding a cause fails here, not in production")
    void causeTextIsExhaustive() {
        List<String> unhandled = new ArrayList<>();
        for (DamageCause cause : DamageCause.values()) {
            if (PlayerDeath.causeText(cause) == null) unhandled.add(cause.name());
        }
        assertEquals(List.of(), unhandled,
                "these damage causes have no phrasing and would report as \"Died\"");
    }

    @Test
    @DisplayName("/kill reads as a command, not as a mystery")
    void killCommandIsNamed() {
        // The reported case: /kill produced "Cause of Death: Died".
        //
        // Looked up by NAME rather than written as DamageCause.KILL, because the
        // constant does not exist before 1.20 and the plugin is compiled against
        // its oldest supported API. Naming it directly is a compile error there --
        // which is exactly the trap this whole file exists to catch, one level up:
        // the production code has always resolved causes by name at runtime, so it
        // was only ever the test that could not travel backwards.
        assertCauseReads("KILL", "Killed by command");
        assertCauseReads("SUICIDE", "Killed by command");
    }

    /** Asserts phrasing for a cause named at runtime, skipping if this API lacks it. */
    private static void assertCauseReads(String constant, String expected) {
        DamageCause cause;
        try {
            cause = DamageCause.valueOf(constant);
        } catch (IllegalArgumentException absentOnThisVersion) {
            return;
        }
        assertEquals(expected, PlayerDeath.causeText(cause));
    }

    @Test
    @DisplayName("phrasing reads as a standalone field value")
    void phrasingIsStandalone() {
        for (DamageCause cause : DamageCause.values()) {
            String text = PlayerDeath.causeText(cause);
            assertTrue(Character.isUpperCase(text.charAt(0)),
                    cause + " -> \"" + text + "\" should start capitalised; it is a field value, "
                            + "not the middle of a sentence");
            assertTrue(text.equals(text.strip()) && !text.isBlank(), cause + " -> \"" + text + "\"");
            assertTrue(text.length() <= 64, cause + " -> \"" + text + "\" is too long for a field");
        }
    }

    @Test
    @DisplayName("no cause yields null, so the caller can fall back")
    void nullCauseIsHandled() {
        assertNull(PlayerDeath.causeText(null));
    }
}
