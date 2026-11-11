package com.discordlogger.listener.moderation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When an unban counts as having happened.
 *
 * <p>The rule used to be "Bukkit's ban list changed from banned to not", which is
 * unreachable on a server where LiteBans or similar owns bans: those plugins keep
 * their own database and never write to Bukkit's list, so the before-reading is
 * always false and the condition could never be true. Unbans were never logged on
 * those servers at all — the same hole {@link Ban} was fixed for, left open here.
 */
class UnbanVerificationTest {

    @Test
    @DisplayName("without a punishment plugin, the ban list has to actually change")
    void vanillaNeedsTheListToChange() {
        assertTrue(Unban.unbanTookEffect(false, true, false), "banned, then not");
        assertFalse(Unban.unbanTookEffect(false, true, true), "still banned — the command failed");
        assertFalse(Unban.unbanTookEffect(false, false, false), "was never banned");
    }

    @Test
    @DisplayName("with a punishment plugin, the command is taken at face value")
    void punishmentPluginTakesTheCommand() {
        // The regression this exists for: Bukkit's list reads false both times on
        // these servers, which is exactly the case that used to log nothing.
        assertTrue(Unban.unbanTookEffect(true, false, false),
                "an unban on a LiteBans-style server must still be logged");
        assertTrue(Unban.unbanTookEffect(true, true, false));
    }
}
