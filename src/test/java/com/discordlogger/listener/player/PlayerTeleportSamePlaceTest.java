package com.discordlogger.listener.player;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A teleport that does not move the player is not logged.
 *
 * <p>On 1.8 a single {@code /kill} fires a burst of teleports whose from and to are
 * the same block, cause UNKNOWN, distance 0.0. They filled a 1000-message send queue
 * faster than it could drain, and none of the existing filters stopped them:
 * {@code minimum_teleport_distance} defaults to 0, which disables it, and UNKNOWN is
 * not in the default ignored causes. So this is dropped unconditionally rather than
 * left to configuration.
 */
class PlayerTeleportSamePlaceTest {

    /** A World that answers getName() and nothing else. */
    private static World world(String name) {
        final InvocationHandler h = (proxy, method, args) -> {
            switch (method.getName()) {
                case "getName": return name;
                case "equals":  return proxy == args[0];
                case "hashCode": return name.hashCode();
                case "toString": return "World(" + name + ")";
                default: return null;
            }
        };
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(), new Class<?>[]{World.class}, h);
    }

    @Test
    @DisplayName("same world, same block is not a teleport")
    void samePlaceIsDropped() {
        final World w = world("world");
        assertTrue(PlayerTeleport.isSamePlace(
                new Location(w, -252, 66, 252), new Location(w, -252, 66, 252)));
    }

    @Test
    @DisplayName("a fraction of a block is still the same block")
    void subBlockMovementIsDropped() {
        // The exact case from the report: a respawn nudges the player within one block.
        final World w = world("world");
        assertTrue(PlayerTeleport.isSamePlace(
                new Location(w, -252.4, 66.0, 252.9), new Location(w, -252.1, 66.4, 252.2)));
    }

    @Test
    @DisplayName("a real move is still logged")
    void realMovementIsKept() {
        final World w = world("world");
        assertFalse(PlayerTeleport.isSamePlace(
                new Location(w, -252, 66, 252), new Location(w, -252, 66, 253)));
        assertFalse(PlayerTeleport.isSamePlace(
                new Location(w, 0, 66, 0), new Location(w, 300, 66, 300)));
    }

    @Test
    @DisplayName("same coordinates in a different world is a real teleport")
    void worldChangeIsKept() {
        // Nether portals land you at matching coordinates in another world. That is a
        // teleport and must survive.
        assertFalse(PlayerTeleport.isSamePlace(
                new Location(world("world"), 10, 66, 10),
                new Location(world("world_nether"), 10, 66, 10)));
    }

    @Test
    @DisplayName("nulls do not suppress anything")
    void nullsAreNotSamePlace() {
        final World w = world("world");
        assertFalse(PlayerTeleport.isSamePlace(null, new Location(w, 0, 0, 0)));
        assertFalse(PlayerTeleport.isSamePlace(new Location(w, 0, 0, 0), null));
        assertFalse(PlayerTeleport.isSamePlace(null, null));
    }
}
