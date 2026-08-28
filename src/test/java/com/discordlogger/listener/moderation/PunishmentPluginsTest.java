package com.discordlogger.listener.moderation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lookups need a server, so what is pinned here is that they cannot take one down
 * — plus the two structural properties the fix depends on.
 */
class PunishmentPluginsTest {

    private static String src(String cls) throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/discordlogger/listener/moderation/" + cls + ".java"));
    }

    @Test
    @DisplayName("both lookups are safe with no server running")
    void safeWithoutAServer() {
        PunishmentPlugins.reset();
        assertDoesNotThrow(() -> PunishmentPlugins.installed());
        assertDoesNotThrow(() -> PunishmentPlugins.isBanned("Someone"));
        assertFalse(PunishmentPlugins.isBanned("Someone"));
    }

    @Test
    @DisplayName("IP bans are consulted, not just name bans")
    void checksIpBansToo() throws Exception {
        // /ban-ip writes to a different list. Checking only NAME meant an IP ban was a
        // second way to punish someone with nothing logged.
        final String s = src("PunishmentPlugins");
        assertTrue(s.contains("BanList.Type.IP"), "must consult the IP ban list");
        assertTrue(s.contains("BanList.Type.NAME"), "must still consult the name ban list");
    }

    @Test
    @DisplayName("neither listener still verifies against the raw ban list")
    void listenersUseTheHelper() throws Exception {
        // The whole bug was Ban/Unban asking Bukkit directly. If either goes back to
        // that, servers running LiteBans silently stop logging bans again.
        for (String cls : new String[]{"Ban", "Unban"}) {
            final String s = src(cls);
            assertFalse(s.contains("Bukkit.getBanList("),
                    cls + " must go through PunishmentPlugins, not Bukkit directly");
            assertTrue(s.contains("PunishmentPlugins."), cls + " must use the helper");
        }
    }
}
