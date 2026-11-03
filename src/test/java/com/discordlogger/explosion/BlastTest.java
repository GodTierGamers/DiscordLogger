package com.discordlogger.explosion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The radius stored with a record, which decides whether a lookup says "reached here". */
class BlastTest {

    @Test
    @DisplayName("a more specific name wins over the substring inside it")
    void namesResolveInTheRightOrder() {
        // TNT_MINECART contains "TNT" and DRAGON_FIREBALL contains "FIREBALL", so the
        // order of these checks is load-bearing rather than cosmetic.
        assertEquals(8, Blast.floorFor("TNT_MINECART"));
        assertEquals(8, Blast.floorFor("PRIMED_TNT"));
        assertEquals(2, Blast.floorFor("DRAGON_FIREBALL"));
        assertEquals(2, Blast.floorFor("FIREBALL"));
        assertEquals(2, Blast.floorFor("SMALL_FIREBALL"));
    }

    @Test
    @DisplayName("every source the icons know about has a radius too")
    void knownSources() {
        assertEquals(6, Blast.floorFor("CREEPER"));
        assertEquals(12, Blast.floorFor("END_CRYSTAL"));
        assertEquals(12, Blast.floorFor("ENDER_CRYSTAL"), "the pre-1.20 name");
        assertEquals(10, Blast.floorFor("RESPAWN_ANCHOR"));
        assertEquals(10, Blast.floorFor("RED_BED"));
        assertEquals(10, Blast.floorFor("BED"), "the legacy flat name");
        assertEquals(2, Blast.floorFor("WITHER_SKULL"));
        assertEquals(Blast.DEFAULT_FLOOR, Blast.floorFor("SOMETHING_NEW"));
        assertEquals(Blast.DEFAULT_FLOOR, Blast.floorFor(null));
        assertEquals(6, Blast.floorFor("creeper"), "case does not matter");
    }

    @Test
    @DisplayName("what the blast really did beats what its type usually does")
    void measuredWins() {
        // A charged creeper reaches further than a creeper's floor, and the block list
        // is the only place that fact exists.
        assertEquals(11, Blast.radiusFor(10.4, "CREEPER"));
        assertEquals(9, Blast.radiusFor(8.1, "PRIMED_TNT"));
    }

    @Test
    @DisplayName("an explosion that broke nothing still has a reach")
    void floorCatchesTheEmptyBlastList() {
        // A creeper that killed someone in a stone corridor can destroy no blocks at
        // all. Stored as radius 0 it could never be found from the spot it happened,
        // which is the entire point of the log.
        assertEquals(6, Blast.radiusFor(0.0, "CREEPER"));
        assertEquals(8, Blast.radiusFor(0.0, "TNT"));
        // Nonsense in, the floor out -- not merely "something positive", which the floor
        // guarantees for every input and so asserts nothing.
        assertEquals(6, Blast.radiusFor(-1.0, "CREEPER"));
    }

    @Test
    @DisplayName("the search default is far wider than any blast")
    void searchRadiusIsDeliberatelyWide() {
        // Somebody at the edge of a crater is not inside the blast that made it, so the
        // widest blast there is must still fit well inside the default search. This one
        // can fail -- raising END_CRYSTAL's floor past 50 would break it.
        assertTrue(Blast.DEFAULT_SEARCH_RADIUS > Blast.floorFor("END_CRYSTAL"),
                "the widest blast must still fit well inside the default search");
    }
}
