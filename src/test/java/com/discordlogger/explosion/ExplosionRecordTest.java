package com.discordlogger.explosion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The row format, and the lenience that keeps one bad line from hiding every good one. */
class ExplosionRecordTest {

    private static ExplosionRecord sample(String nearby) {
        return new ExplosionRecord(1_762_128_000_000L, "world", 128, 71, -344, 8,
                "Primed Tnt", 37, 0.30f, nearby);
    }

    @Test
    @DisplayName("a row survives being written and read back")
    void roundTrips() {
        final ExplosionRecord before = sample("Steve (4.2m)");
        final ExplosionRecord after = ExplosionRecord.fromCsv(before.toCsv());

        assertEquals(before.at(), after.at());
        assertEquals("world", after.world());
        assertEquals(128, after.x());
        assertEquals(71, after.y());
        assertEquals(-344, after.z());
        assertEquals(8, after.radius());
        assertEquals("Primed Tnt", after.source());
        assertEquals(37, after.blocks());
        assertEquals("Steve (4.2m)", after.nearby());
    }

    @Test
    @DisplayName("a comma in a player list or a world name does not break the row")
    void quotingHolds() {
        // The nearby list is built from display names and joined with commas, so it
        // contains the delimiter by construction -- this is the ordinary case, not a
        // contrived one.
        final ExplosionRecord before = sample("Steve (4.2m), Alex (11.8m)");
        final String csv = before.toCsv();
        assertTrue(csv.contains("\"Steve (4.2m), Alex (11.8m)\""), csv);

        final ExplosionRecord after = ExplosionRecord.fromCsv(csv);
        assertEquals("Steve (4.2m), Alex (11.8m)", after.nearby());

        // A nickname can hold a quote too, and a doubled quote is how CSV says one.
        final ExplosionRecord quoted = new ExplosionRecord(1L, "the \"end\"", 0, 0, 0, 6,
                "Creeper", 1, 1f, "He said \"hi\"");
        final ExplosionRecord back = ExplosionRecord.fromCsv(quoted.toCsv());
        assertEquals("the \"end\"", back.world());
        assertEquals("He said \"hi\"", back.nearby());
    }

    @Test
    @DisplayName("an unreadable line is skipped, not thrown")
    void malformedLinesAreSkipped() {
        // The file is plain text in a folder admins open. A lookup that throws on the
        // first odd line stops working the moment anybody edits it.
        assertNull(ExplosionRecord.fromCsv(null));
        assertNull(ExplosionRecord.fromCsv(""));
        assertNull(ExplosionRecord.fromCsv("   "));
        assertNull(ExplosionRecord.fromCsv(ExplosionRecord.HEADER), "the header is not a row");
        assertNull(ExplosionRecord.fromCsv("not,a,row"));
        assertNull(ExplosionRecord.fromCsv("later,world,1,2,3,4,Creeper,5,6,x"));
        assertNull(ExplosionRecord.fromCsv("1,world,1,2,3,4,Creeper,5"), "too few columns");
    }

    @Test
    @DisplayName("touches() answers whether the blast reached a place, in the right world")
    void touchesTheRightPlaces() {
        final ExplosionRecord r = sample("");   // radius 8 at 128,71,-344

        assertTrue(r.touches("world", 128, 71, -344, 0), "its own centre");
        assertTrue(r.touches("world", 134, 71, -344, 0), "6 away, inside 8");
        assertFalse(r.touches("world", 140, 71, -344, 0), "12 away, outside 8");
        assertTrue(r.touches("world", 140, 71, -344, 10), "outside 8 but inside the buffer");

        // A crater at the same coordinates in the nether is a different crater.
        assertFalse(r.touches("world_nether", 128, 71, -344, 0));
        assertTrue(r.touches("WORLD", 128, 71, -344, 0), "world names match case-insensitively");
        assertFalse(r.touches(null, 128, 71, -344, 0));
    }

    @Test
    @DisplayName("distance is measured in three dimensions")
    void distanceUsesY() {
        final ExplosionRecord r = sample("");
        // A blast directly below is not at distance zero, which matters for a lookup
        // standing on top of a hole in a floor.
        assertEquals(10.0, r.distanceTo(128, 81, -344), 0.0001);
        assertEquals(0.0, r.distanceTo(128, 71, -344), 0.0001);
    }
}
