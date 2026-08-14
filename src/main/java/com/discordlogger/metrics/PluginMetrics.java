package com.discordlogger.metrics;

import com.discordlogger.update.BuildInfo;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Anonymous usage metrics via <a href="https://bstats.org">bStats</a>.
 *
 * <p>Opting out is handled by bStats itself through {@code plugins/bStats/config.yml},
 * which covers every bStats plugin on the server at once — that's where admins
 * already expect the switch to be, so this plugin deliberately adds no config key
 * of its own. (A key would also open config schema v10, which should be opened by
 * a deliberate schema change rather than by metrics.)
 *
 * <p>The charts exist to answer questions that actually change decisions:
 * <ul>
 *   <li><b>Config schema</b> — when is it safe to stop supporting an old schema?
 *       The generator freezes a bundle per schema forever, so this is the number
 *       that says whether anyone would notice.</li>
 *   <li><b>Release channel</b> — is the nightly channel used enough to be worth
 *       maintaining?</li>
 *   <li><b>Output mode</b> — embeds vs plain text, i.e. where formatting effort
 *       is worth spending.</li>
 *   <li><b>Enabled events</b> — which log types are actually switched on.</li>
 * </ul>
 */
public final class PluginMetrics {

    /** bStats plugin id for DiscordLogger (bstats.org/plugin/bukkit/DiscordLogger). */
    private static final int PLUGIN_ID = 33026;

    private static final Pattern SCHEMA_RE =
            Pattern.compile("CONFIG\\s+VERSION\\s+(V\\d+)", Pattern.CASE_INSENSITIVE);

    private PluginMetrics() {}

    /** Call once from onEnable, after {@link BuildInfo#load}. */
    public static void start(JavaPlugin plugin) {
        // Source builds DO report, and the release_channel chart separates them as
        // "dev". They used to be excluded on the theory that a developer's machine
        // would skew every chart, but that reasoning does not survive contact with
        // how bStats identifies a server: the id lives in plugins/bStats/config.yml,
        // per server directory, so fifty rebuild-and-restart cycles against one test
        // server are one server, not fifty. Excluding them instead understated the
        // server count and made "how many people build from source" unanswerable --
        // a question that can only be answered by counting.
        //
        // The cost is real but small: a test server with deliberately odd settings
        // lands in the config-shaped charts like any other unusual server. Filtering
        // by channel is what that chart is for.

        try {
            final Metrics metrics = new Metrics(plugin, PLUGIN_ID);

            metrics.addCustomChart(new SimplePie("config_schema", () -> configSchema(plugin)));
            metrics.addCustomChart(new SimplePie("release_channel", BuildInfo::channel));
            metrics.addCustomChart(new SimplePie("output_mode", () ->
                    plugin.getConfig().getBoolean("embeds.enabled", true) ? "Embeds" : "Plain text"));
            metrics.addCustomChart(new org.bstats.charts.AdvancedPie("enabled_events", () -> enabledEvents(plugin)));

        } catch (Throwable t) {
            // Metrics must never be the reason a server fails to start.
            plugin.getLogger().fine("Metrics could not start: " + t.getMessage());
        }
    }

    /**
     * The schema of the config actually on disk, read from its trailer — not the
     * schema this build ships. Those differ exactly when a server hasn't restarted
     * into a migration yet, and the on-disk value is the one worth measuring.
     */
    private static String configSchema(JavaPlugin plugin) {
        try {
            final File file = new File(plugin.getDataFolder(), "config.yml");
            if (!file.isFile()) return "Unknown";
            final String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            final Matcher m = SCHEMA_RE.matcher(text);
            return m.find() ? m.group(1).toUpperCase() : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }

    /**
     * Every enabled {@code log.<category>.<event>} toggle, counted once each.
     * Reads the live config rather than a hardcoded list, so events added later
     * appear here without touching this class.
     */
    private static Map<String, Integer> enabledEvents(JavaPlugin plugin) {
        final Map<String, Integer> counts = new HashMap<>();
        final ConfigurationSection log = plugin.getConfig().getConfigurationSection("log");
        if (log == null) return counts;

        for (String category : log.getKeys(false)) {
            final ConfigurationSection section = log.getConfigurationSection(category);
            if (section == null) continue;
            for (String event : section.getKeys(false)) {
                // Schema v10 made each event a section (enabled + color); older
                // shapes stored a bare boolean. Read both so a config that failed
                // to migrate still reports something truthful.
                final ConfigurationSection eventSec = section.getConfigurationSection(event);
                final boolean on = eventSec != null
                        ? eventSec.getBoolean("enabled", false)
                        : section.getBoolean(event, false);
                if (on) {
                    counts.put(category + "." + event, 1);
                }
            }
        }
        return counts;
    }
}
