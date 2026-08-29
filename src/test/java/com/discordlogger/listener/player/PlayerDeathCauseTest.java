package com.discordlogger.listener.player;

import com.discordlogger.lang.Lang;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /**
     * Causes that exist on servers newer than the API this compiles against.
     *
     * <p>The plugin compiles against the oldest API it supports, so
     * {@code DamageCause.values()} only knows the causes that existed then — the two
     * tests above went from thirty cases to twenty-eight the moment the build moved
     * to 1.13, and neither noticed. Nothing broke at runtime, because the phrasing is
     * looked up by {@code cause.name()} rather than by enum constant, but the CI
     * guarantee those tests exist to provide had quietly stopped covering the newest
     * causes — which are exactly the ones most likely to be missing.
     *
     * <p>So they are listed by name instead. This has to be extended whenever
     * Minecraft adds a damage cause, which is the same obligation as before; the
     * difference is that forgetting now fails here rather than showing a player
     * "Cause of Death: Died".
     */
    private static final List<String> CAUSES_NEWER_THAN_THE_COMPILE_FLOOR = List.of(
            "FREEZE",       // 1.17
            "SONIC_BOOM");  // 1.19

    @Test
    @DisplayName("causes added after the compile floor still have phrasing")
    void causesNewerThanTheApiAreHandled() {
        final List<String> missing = new ArrayList<>();
        for (String name : CAUSES_NEWER_THAN_THE_COMPILE_FLOOR) {
            final String key = "discord.death.causes."
                    + name.toLowerCase(Locale.ROOT).replace('_', '-');
            if (!Lang.has(key)) missing.add(name);
        }
        assertEquals(List.of(), missing,
                "these causes exist on servers newer than the compiled API and have no "
                        + "phrasing, so a player dying this way would be reported as \"Died\"");
    }

    @Test
    @DisplayName("the hardcoded list only names causes the compiled API lacks")
    void theListDoesNotDuplicateTheEnum() {
        // If the compile floor is later raised past one of these, the enum covers it
        // again and the entry here is dead weight -- say so rather than let it rot.
        for (String name : CAUSES_NEWER_THAN_THE_COMPILE_FLOOR) {
            boolean inEnum = false;
            for (DamageCause c : DamageCause.values()) {
                if (c.name().equals(name)) { inEnum = true; break; }
            }
            assertFalse(inEnum, name + " is in the compiled API now, so the enum-driven "
                    + "tests already cover it. Remove it from the hardcoded list.");
        }
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
