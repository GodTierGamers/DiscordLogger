package com.discordlogger.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The decisions inside the metrics that are worth being sure about.
 *
 * <p>Most of this class reads Bukkit state and cannot be exercised without a
 * server. These three can, and they are the ones where a wrong answer would
 * matter: a misclassified command filter hides the case worth knowing about, an
 * exact count where a bucket belongs is a privacy regression rather than a bug,
 * and a lang key falling into the wrong section quietly makes a chart lie.
 */
class PluginMetricsTest {

    // ---------------------------------------------------------- command filter

    private static final List<String> SHIPPED =
            List.of("login", "register", "changepassword", "unregister",
                    "msg", "tell", "whisper", "w", "r", "reply");

    @Test
    @DisplayName("an untouched deny-list reads as Default")
    void defaultList() {
        assertEquals("Default", PluginMetrics.commandFilterState(SHIPPED, SHIPPED));
    }

    @Test
    @DisplayName("adding commands reads as Extended, not Reduced")
    void extendedList() {
        final List<String> mine = new java.util.ArrayList<>(SHIPPED);
        mine.add("vote");
        assertEquals("Extended", PluginMetrics.commandFilterState(mine, SHIPPED));
    }

    @Test
    @DisplayName("removing a shipped command reads as Reduced — the case that matters")
    void reducedList() {
        // This is the whole point of the chart. /login is in the deny-list because
        // command logging posts the line exactly as typed; taking it out publishes
        // passwords to Discord. Reporting that as anything else would hide it.
        final List<String> mine = new java.util.ArrayList<>(SHIPPED);
        mine.remove("login");
        assertEquals("Reduced", PluginMetrics.commandFilterState(mine, SHIPPED));
    }

    @Test
    @DisplayName("a list that is longer but missing a shipped entry is still Reduced")
    void reducedBeatsExtended() {
        // Size alone must not decide it: someone can add five commands and remove
        // /login, and the removal is the part worth reporting.
        final List<String> mine = new java.util.ArrayList<>(SHIPPED);
        mine.remove("login");
        mine.addAll(List.of("vote", "buy", "shop", "kit", "warp"));
        assertNotEquals("Extended", PluginMetrics.commandFilterState(mine, SHIPPED));
        assertEquals("Reduced", PluginMetrics.commandFilterState(mine, SHIPPED));
    }

    @Test
    @DisplayName("an emptied list is called Emptied, not Reduced")
    void emptiedList() {
        assertEquals("Emptied", PluginMetrics.commandFilterState(List.of(), SHIPPED));
        assertEquals("Emptied", PluginMetrics.commandFilterState(null, SHIPPED));
    }

    // ------------------------------------------------------------------ buckets

    @Test
    @DisplayName("counts are reported as ranges, never as the number itself")
    void bucketsNeverLeakTheCount() {
        assertEquals("None", PluginMetrics.bucket(0));
        assertEquals("1-5", PluginMetrics.bucket(1));
        assertEquals("1-5", PluginMetrics.bucket(5));
        assertEquals("6-20", PluginMetrics.bucket(6));
        assertEquals("6-20", PluginMetrics.bucket(20));
        assertEquals("21-50", PluginMetrics.bucket(21));
        assertEquals("50+", PluginMetrics.bucket(51));
        assertEquals("50+", PluginMetrics.bucket(4_000));

        // The guarantee, stated as a test: no bucket is ever the bare number, so a
        // chart cannot become a fingerprint by accident.
        for (int n : new int[]{1, 7, 33, 99}) {
            assertNotEquals(String.valueOf(n), PluginMetrics.bucket(n),
                    "bucket(" + n + ") returned the exact count");
        }
    }

    // ------------------------------------------------------------ lang sections

    @Test
    @DisplayName("lang keys land in the section a reader would expect")
    void langSections() {
        assertEquals("chat", PluginMetrics.langSection("chat.reload-ok"));
        assertEquals("discord", PluginMetrics.langSection("discord.player-join"));
        assertEquals("death embed", PluginMetrics.langSection("discord.death.cause-field"));
        assertEquals("death causes", PluginMetrics.langSection("discord.death.causes.fall"));
        assertEquals("other", PluginMetrics.langSection("config-version"));
        assertEquals("other", PluginMetrics.langSection(null));
    }

    @Test
    @DisplayName("death causes are not swallowed by the broader discord prefix")
    void deathCausesBeatDiscordPrefix() {
        // Ordering trap: every death key also starts with "discord.", so a naive
        // chain reports all 33 causes as plain "discord" and the chart loses the
        // distinction it exists to draw.
        assertNotEquals("discord", PluginMetrics.langSection("discord.death.causes.void"));
        assertNotEquals("discord", PluginMetrics.langSection("discord.death.description"));
    }

    // ------------------------------------------------------------------ proxyMode

    @Test
    @DisplayName("a default Paper server is not reported as proxied")
    void defaultPaperServerIsNotProxied() {
        // The regression this pins: proxies.bungee-cord.online-mode ships as true
        // everywhere, so reading it made every stock Paper server claim BungeeCord.
        assertEquals("None", PluginMetrics.proxyModeOf(false, false));
    }

    @Test
    @DisplayName("modern Velocity forwarding is named exactly")
    void velocityIsNamed() {
        assertEquals("Velocity", PluginMetrics.proxyModeOf(false, true));
    }

    @Test
    @DisplayName("the spigot.yml flag cannot tell BungeeCord from legacy Velocity")
    void bungeeFlagStaysAmbiguous() {
        assertEquals("BungeeCord or Velocity", PluginMetrics.proxyModeOf(true, false));
    }

    @Test
    @DisplayName("modern Velocity wins over the ambiguous flag")
    void velocityBeatsTheAmbiguousFlag() {
        assertEquals("Velocity", PluginMetrics.proxyModeOf(true, true));
    }
}
