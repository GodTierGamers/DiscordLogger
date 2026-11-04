package com.discordlogger.log;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The avatar URL, and the cache-busting window that keeps it honest.
 *
 * <p>Discord re-hosts embed thumbnails through its own proxy, keyed on the URL. A
 * permanent URL for a changeable skin meant whichever head Discord cached first was the
 * head it served forever — reported in #71 and again in #258, eleven months apart.
 *
 * <p>These tests exist mainly so the {@code ?c=} parameter cannot be removed quietly.
 * It looks like a query string that does nothing, and to mc-heads it is one; to Discord
 * it is the whole fix.
 */
class PlayerAvatarUrlTest {

    private static final UUID JEB = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6");
    private static final long HOUR = 3_600_000L;

    @Test
    @DisplayName("the URL carries the dashless UUID, the size and a cache window")
    void urlShape() {
        final String url = Log.playerAvatarUrl(JEB);

        assertTrue(url.startsWith("https://mc-heads.net/avatar/"), url);
        assertTrue(url.contains("853c80ef3c3749fdaa49938b674adae6"),
                "mc-heads wants the UUID without dashes: " + url);
        assertTrue(url.contains("/256"), url);
        assertTrue(url.contains("?c="), "the cache buster is the fix, not decoration: " + url);
    }

    @Test
    @DisplayName("no UUID, no URL")
    void nullIsNull() {
        assertNull(Log.playerAvatarUrl(null));
    }

    @Test
    @DisplayName("the window holds steady for six hours, then moves")
    void windowAdvancesEverySixHours() {
        final long base = 1_762_128_000_000L;

        // Steady within the window: a value that changed per call would re-fetch every
        // head on every message, which is defeating the cache rather than expiring it.
        assertEquals(Log.cacheBucket(base), Log.cacheBucket(base + HOUR));
        assertEquals(Log.cacheBucket(base), Log.cacheBucket(base + 5 * HOUR));

        // Six hours is mc-heads' own max-age, so Discord is never asked to hold an
        // image for longer than the origin calls it fresh.
        assertNotEquals(Log.cacheBucket(base), Log.cacheBucket(base + 6 * HOUR));
        assertEquals(Log.cacheBucket(base) + 1, Log.cacheBucket(base + 6 * HOUR));
        assertEquals(Log.cacheBucket(base) + 4, Log.cacheBucket(base + 24 * HOUR));
    }

    @Test
    @DisplayName("two players in the same window still get different URLs")
    void playersDoNotCollide() {
        // The window is shared; the identity is not. If the UUID ever stopped varying
        // the parameter would happily serve one player's head for everybody.
        final String a = Log.playerAvatarUrl(JEB);
        final String b = Log.playerAvatarUrl(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"));
        assertNotEquals(a, b);
    }
}
