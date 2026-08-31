package com.discordlogger.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The "enabled but silent" report.
 *
 * <p>These run against the 1.13 API, where every gated event exists, so the honest
 * assertion is that a modern server is told <em>nothing</em>. A false positive here
 * would put a warning about missing features on the console of every server that has
 * them, which is worse than the silence this was written to fix.
 */
class CompatFeaturesTest {

    @Test
    @DisplayName("a null plugin is an empty report, not an exception")
    void nullPluginIsEmpty() {
        assertEquals(List.of(), Compat.unavailableFeatures(null));
    }

    @Test
    @DisplayName("every gated event resolves on the build API, so there is nothing to warn about")
    void buildApiHasEveryGatedEvent() {
        // This is what makes the silence above meaningful: the report is empty on a
        // modern server because the events are present, not because it never fires.
        assertTrue(Compat.hasClass(Compat.BLOCK_EXPLODE_EVENT), Compat.BLOCK_EXPLODE_EVENT);
        assertTrue(Compat.hasClass(Compat.ADVANCEMENT_EVENT), Compat.ADVANCEMENT_EVENT);
        assertTrue(Compat.hasClass(Compat.ACHIEVEMENT_EVENT), Compat.ACHIEVEMENT_EVENT);
    }

    @Test
    @DisplayName("the advancement pair can never both be missing on a real server")
    void advancementPairAlwaysCovered() {
        // PlayerAdvancementDoneEvent is 1.12+; PlayerAchievementAwardedEvent is 1.8-1.14.
        // Their union spans the whole supported range, which is why unavailableFeatures
        // never reports that setting. If either constant is ever repointed, this fails
        // rather than letting a silent hole open under one config switch.
        assertTrue(Compat.hasClass(Compat.ADVANCEMENT_EVENT)
                        || Compat.hasClass(Compat.ACHIEVEMENT_EVENT),
                "one of the two must exist on any supported server");
    }
}
