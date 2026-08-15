package com.discordlogger.listener.player;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Parsing for the /kill correlation.
 *
 * <p>The window and the map need a live server, but the parsing is where the
 * mistakes are: a plugin prefix, a selector, or another plugin's command that
 * merely begins with the same letters. Getting any of those wrong mislabels a
 * genuine void death as a command kill, which is worse than the bug this fixes.
 */
class KillCommandTrackerTest {

    @Test
    @DisplayName("a named target is returned as-is")
    void namedTarget() {
        assertEquals("Steve", KillCommandTracker.targetOf("/kill Steve"));
        assertEquals("Steve", KillCommandTracker.targetOf("kill Steve"));
        assertEquals("Steve", KillCommandTracker.targetOf("  /kill   Steve  "));
    }

    @Test
    @DisplayName("a bare /kill means the sender")
    void bareKillMeansSelf() {
        assertEquals("", KillCommandTracker.targetOf("/kill"));
        assertEquals("", KillCommandTracker.targetOf("/kill   "));
    }

    @Test
    @DisplayName("a selector matches whoever dies in the window")
    void selectorsBecomeWildcard() {
        for (String sel : new String[]{"@a", "@p", "@s", "@e[type=player]"}) {
            assertEquals("*", KillCommandTracker.targetOf("/kill " + sel), sel);
        }
    }

    @Test
    @DisplayName("a plugin prefix is the same command")
    void pluginPrefixStripped() {
        assertEquals("Steve", KillCommandTracker.targetOf("/minecraft:kill Steve"));
        assertEquals("Steve", KillCommandTracker.targetOf("/essentials:kill Steve"));
        assertEquals("", KillCommandTracker.targetOf("/minecraft:kill"));
    }

    @Test
    @DisplayName("commands that merely start with 'kill' are not /kill")
    void doesNotMatchOtherCommands() {
        // The trap: prefix-matching here would attribute someone's void death to
        // whatever /killall or /killchest happened to run a moment earlier.
        assertNull(KillCommandTracker.targetOf("/killall"));
        assertNull(KillCommandTracker.targetOf("/killchest Steve"));
        assertNull(KillCommandTracker.targetOf("/kills"));
        assertNull(KillCommandTracker.targetOf("/skill Steve"));
    }

    @Test
    @DisplayName("non-commands and rubbish return null rather than throwing")
    void handlesJunk() {
        assertNull(KillCommandTracker.targetOf(null));
        assertNull(KillCommandTracker.targetOf(""));
        assertNull(KillCommandTracker.targetOf("/"));
        assertNull(KillCommandTracker.targetOf("   "));
        assertNull(KillCommandTracker.targetOf("/gamemode creative"));
    }

    @Test
    @DisplayName("the tracker is inert on servers that report KILL themselves")
    void inactiveWhereTheServerKnows() {
        // Compiled against 1.19.4, where DamageCause.KILL does not exist -- so the
        // correlation is ACTIVE here. On a 1.20+ server the same code sees KILL and
        // switches itself off, which is the property that stops it guessing where
        // the server already has the answer.
        boolean killExists;
        try {
            org.bukkit.event.entity.EntityDamageEvent.DamageCause.valueOf("KILL");
            killExists = true;
        } catch (IllegalArgumentException e) {
            killExists = false;
        }
        assertEquals(!killExists, KillCommandTracker.ACTIVE,
                "the tracker must be active exactly when the server cannot tell them apart");
        assertFalse(KillCommandTracker.wasKilledByCommand(null),
                "a null victim must never be reported as killed by command");
    }
}
