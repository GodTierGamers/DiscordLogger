package com.discordlogger.listener.player;

import com.discordlogger.listener.player.PlayerBucket.Liquid;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which buckets are worth a message.
 *
 * <p>Matching is on the material's name because the names moved in 1.13, and the
 * plugin runs on both sides of that split from one jar.
 */
class PlayerBucketLiquidTest {

    @Test
    @DisplayName("lava and water are recognised")
    void theTwoThatMatter() {
        assertEquals(Liquid.LAVA, PlayerBucket.liquidOf(Material.LAVA_BUCKET));
        assertEquals(Liquid.WATER, PlayerBucket.liquidOf(Material.WATER_BUCKET));
    }

    @Test
    @DisplayName("a bucket that places nothing is not logged at all")
    void everythingElseIsIgnored() {
        // Milk, a fish and powder snow destroy nothing, so there is no griefing to
        // record -- and no toggle covers them, so logging them would be unfilterable.
        assertNull(PlayerBucket.liquidOf(Material.MILK_BUCKET));
        assertNull(PlayerBucket.liquidOf(Material.BUCKET));
        assertNull(PlayerBucket.liquidOf(null));
    }

    @Test
    @DisplayName("each liquid names its config key in full, so the validator can find it")
    void configKeysAreLiterals() {
        assertEquals("log.player.bucket.lava", Liquid.LAVA.enabledKey);
        assertEquals("log.player.bucket.water", Liquid.WATER.enabledKey);
        assertTrue(Liquid.LAVA.icon().endsWith("/buckets/lava.png"));
        assertTrue(Liquid.WATER.icon().endsWith("/buckets/water.png"));
    }
}
