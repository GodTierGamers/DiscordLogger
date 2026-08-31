package com.discordlogger.listener.player;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Achievement names as a reader sees them.
 *
 * <p>The enum name is all the API gives -- there is no display string on
 * {@code org.bukkit.Achievement} -- so the wording in Discord is derived from it, and
 * these pin that derivation. They also pin the shape of the synthesised key, because
 * a server's {@code ignored_advancements} list is matched against it and a change
 * here would quietly stop those entries matching.
 */
class PlayerAchievementTest {

    @ParameterizedTest
    @CsvSource({
            "mine_wood,           Mine Wood",
            "build_workbench,     Build Workbench",
            "acquire_iron,        Acquire Iron",
            "the_end,             The End",
            "diamonds_to_you,     Diamonds To You",
            "overkill,            Overkill",
            "open_inventory,      Open Inventory",
    })
    @DisplayName("enum names read as words")
    void titlesReadAsWords(String path, String expected) {
        assertEquals(expected, PlayerAchievement.prettyTitle(path));
    }

    @Test
    @DisplayName("degenerate input comes back unchanged rather than empty")
    void degenerateInput() {
        assertEquals("", PlayerAchievement.prettyTitle(""));
        assertNotNull(PlayerAchievement.prettyTitle("_"));
    }

    @Test
    @DisplayName("every Achievement constant produces non-empty wording")
    void everyConstantHasWording() {
        // Same guarantee PlayerDeathCauseTest gives for damage causes: nothing in the
        // enum may end up reported as a blank field.
        for (org.bukkit.Achievement a : org.bukkit.Achievement.values()) {
            final String path = a.name().toLowerCase(java.util.Locale.ROOT);
            final String pretty = PlayerAchievement.prettyTitle(path);
            assertNotNull(pretty, a.name());
            org.junit.jupiter.api.Assertions.assertFalse(
                    pretty.trim().isEmpty(), a + " produced blank wording");
        }
    }
}
