package com.discordlogger.metrics;

import com.discordlogger.update.BuildInfo;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.DrilldownPie;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Anonymous usage metrics via <a href="https://bstats.org">bStats</a>.
 *
 * <p>Opting out is handled by bStats itself through {@code plugins/bStats/config.yml},
 * which covers every bStats plugin on the server at once — that's where admins
 * already expect the switch to be, so this plugin deliberately adds no config key
 * of its own.
 *
 * <h2>What is measured, and what is not</h2>
 *
 * <p>Every chart here reports <b>configuration state, plugin presence, or server
 * software</b>. Nothing reports a player. No names, no UUIDs, no IP addresses, no
 * message content, no coordinates, no world names. The question these answer is
 * "what do people do with this plugin", never "who is on this server".
 *
 * <p>Values are deliberately <b>booleans and buckets rather than counts</b> wherever
 * a count would work. "Two filters differ from default" answers the question;
 * "47 chat messages filtered this hour" starts describing a specific community's
 * behaviour without naming anyone. The first is measurement, the second is residue.
 *
 * <h2>Why each group exists</h2>
 * <ul>
 *   <li><b>Environment</b> — which server software, Minecraft version and schema
 *       combinations exist in the wild. The schema one decides when a frozen
 *       generator bundle can finally be retired.</li>
 *   <li><b>Companion plugins</b> — whether the integrations being considered
 *       (proxy support, PlaceholderAPI, punishment plugins, vanish) have an
 *       audience, and how many servers are silently getting nothing from
 *       moderation logging because their punishments bypass the vanilla ban list.</li>
 *   <li><b>Feature usage</b> — which of the fourteen filters, the routing, and the
 *       language file are actually used, versus shipped and ignored.</li>
 *   <li><b>Config lifecycle</b> — did setup ever complete, was the config built by
 *       the website generator, and which old schema did it migrate from.</li>
 * </ul>
 */
public final class PluginMetrics {

    /** bStats plugin id for DiscordLogger (bstats.org/plugin/bukkit/DiscordLogger). */
    private static final int PLUGIN_ID = 33026;

    private static final Pattern SCHEMA_RE =
            Pattern.compile("CONFIG\\s+VERSION\\s+(V\\d+)", Pattern.CASE_INSENSITIVE);

    private static final String YES = "Yes";
    private static final String NO = "No";
    private static final String UNKNOWN = "Unknown";

    /**
     * Punishment plugins that keep their own database instead of Bukkit's ban list.
     * On a server running any of these, the moderation listeners verify a ban by
     * checking {@code Bukkit.getBanList()}, find nothing, and log nothing — so this
     * chart measures how many installs silently get no moderation logging at all.
     */
    private static final List<String> PUNISHMENT_PLUGINS =
            List.of("LiteBans", "LibertyBans", "AdvancedBan", "BanManager", "CMI");

    /** Vanish implementations. A vanished admin joining is currently announced anyway. */
    private static final List<String> VANISH_PLUGINS =
            List.of("PremiumVanish", "SuperVanish", "Essentials", "CMI");

    private PluginMetrics() {}

    /** Call once from onEnable, after {@link BuildInfo#load}. */
    public static void start(JavaPlugin plugin) {
        // Source builds DO report, and release_channel separates them as "dev".
        // They used to be excluded on the theory that a developer's machine would
        // skew every chart, but bStats identifies a server by an id in
        // plugins/bStats/config.yml — per server directory — so fifty rebuild cycles
        // against one test server are one server, not fifty. Excluding them only
        // understated the totals and made "how many build from source" unanswerable.
        try {
            final Metrics metrics = new Metrics(plugin, PLUGIN_ID);

            // ---------------------------------------------------------------- environment
            metrics.addCustomChart(new SimplePie("release_channel", BuildInfo::channel));
            metrics.addCustomChart(new SimplePie("server_fork", PluginMetrics::serverFork));
            metrics.addCustomChart(new SimplePie("config_schema", () -> configSchema(plugin)));
            metrics.addCustomChart(new DrilldownPie("mc_version_by_schema",
                    () -> drilldown(minecraftVersion(), configSchema(plugin))));
            metrics.addCustomChart(new DrilldownPie("mc_version_by_java",
                    () -> drilldown(minecraftVersion(), javaMajor())));

            // ----------------------------------------------------------- companion plugins
            metrics.addCustomChart(new SimplePie("proxy_mode", PluginMetrics::proxyMode));
            metrics.addCustomChart(new SimplePie("online_mode",
                    () -> Bukkit.getOnlineMode() ? "Online" : "Offline"));
            metrics.addCustomChart(new SimplePie("floodgate",
                    () -> present(installed("floodgate") || installed("Geyser-Spigot"))));
            metrics.addCustomChart(new SimplePie("placeholderapi",
                    () -> present(installed("PlaceholderAPI"))));
            metrics.addCustomChart(new SimplePie("coreprotect",
                    () -> present(installed("CoreProtect"))));
            metrics.addCustomChart(new SimplePie("punishment_plugin",
                    () -> firstInstalled(PUNISHMENT_PLUGINS)));
            metrics.addCustomChart(new SimplePie("vanish_plugin",
                    () -> firstInstalled(VANISH_PLUGINS)));

            // -------------------------------------------------------------- config health
            metrics.addCustomChart(new SimplePie("webhook_configured",
                    () -> present(isWebhookSet(plugin))));
            metrics.addCustomChart(new SimplePie("config_origin", () -> configOrigin(plugin)));
            metrics.addCustomChart(new SimplePie("migrated_from", () -> migratedFrom(plugin)));
            metrics.addCustomChart(new SimplePie("config_ahead_of_build",
                    () -> yesNo(configAheadOfBuild(plugin))));

            // -------------------------------------------------------------- feature usage
            metrics.addCustomChart(new SimplePie("output_mode", () ->
                    plugin.getConfig().getBoolean("embeds.enabled", true) ? "Embeds" : "Plain text"));
            metrics.addCustomChart(new AdvancedPie("enabled_events", () -> enabledEvents(plugin)));
            metrics.addCustomChart(new AdvancedPie("enabled_options", () -> enabledOptions(plugin)));
            metrics.addCustomChart(new SimplePie("colors_customised",
                    () -> yesNo(anyDiffers(plugin, "log", "color"))));
            metrics.addCustomChart(new SimplePie("routing_used", () -> routingUsed(plugin)));
            metrics.addCustomChart(new AdvancedPie("routed_events", () -> routedEvents(plugin)));
            metrics.addCustomChart(new AdvancedPie("filters_modified", () -> filtersModified(plugin)));
            metrics.addCustomChart(new SimplePie("command_filter_state",
                    () -> commandFilterState(plugin)));

            // ---------------------------------------------------------------- lang.yml
            metrics.addCustomChart(new SimplePie("lang_customised",
                    () -> yesNo(langChanged(plugin) > 0)));
            metrics.addCustomChart(new SimplePie("lang_keys_changed",
                    () -> bucket(langChanged(plugin))));
            metrics.addCustomChart(new AdvancedPie("lang_sections_changed",
                    () -> langSectionsChanged(plugin)));

        } catch (Throwable t) {
            // Metrics must never be the reason a server fails to start.
            plugin.getLogger().fine("Metrics could not start: " + t.getMessage());
        }
    }

    // ------------------------------------------------------------------ environment

    /**
     * The server implementation name — Paper, Purpur, Pufferfish, Leaf, and so on.
     *
     * <p>bStats reports "Server Software" already, but forks frequently identify as
     * their upstream there. Issue #161 arrived from Leaf, which is exactly the kind
     * of fork that would otherwise be invisible.
     */
    private static String serverFork() {
        try {
            final String name = Bukkit.getName();
            return (name == null || name.isBlank()) ? UNKNOWN : name;
        } catch (Throwable t) {
            return UNKNOWN;
        }
    }

    /** "1.21.11" from whatever shape this server reports its version in. */
    private static String minecraftVersion() {
        try {
            final Matcher m = Pattern.compile("\\(MC: ([^)]+)\\)").matcher(Bukkit.getVersion());
            if (m.find()) return m.group(1).trim();
            return Bukkit.getBukkitVersion().split("-")[0];
        } catch (Throwable t) {
            return UNKNOWN;
        }
    }

    private static String javaMajor() {
        try {
            final String v = System.getProperty("java.version", "");
            final String major = v.startsWith("1.") ? v.substring(2, 3) : v.split("[.\\-+]")[0];
            return major.isBlank() ? UNKNOWN : major;
        } catch (Throwable t) {
            return UNKNOWN;
        }
    }

    // ----------------------------------------------------------- companion plugins

    private static boolean installed(String name) {
        try {
            return Bukkit.getPluginManager().getPlugin(name) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** The first of {@code names} that is installed, or "None". */
    private static String firstInstalled(List<String> names) {
        for (String n : names) {
            if (installed(n)) return n;
        }
        return "None";
    }

    /**
     * Whether this backend sits behind a proxy.
     *
     * <p>Worth having as its own chart because {@code online_mode} alone cannot
     * distinguish a proxied backend from a Geyser server or a cracked one — all
     * three run offline. Crossed against floodgate, the offline population resolves.
     */
    private static String proxyMode() {
        try {
            if (Bukkit.spigot().getConfig().getBoolean("settings.bungeecord", false)) {
                return "BungeeCord or Velocity";
            }
            final File paperGlobal = new File("config/paper-global.yml");
            if (paperGlobal.isFile()) {
                final YamlConfiguration y = YamlConfiguration.loadConfiguration(paperGlobal);
                if (y.getBoolean("proxies.velocity.enabled", false)) return "Velocity";
                if (y.getBoolean("proxies.bungee-cord.online-mode", false)) return "BungeeCord";
            }
            return "None";
        } catch (Throwable t) {
            return UNKNOWN;
        }
    }

    // --------------------------------------------------------------- config health

    private static boolean isWebhookSet(JavaPlugin plugin) {
        final String url = plugin.getConfig().getString("webhook.url", "");
        return url != null && url.startsWith("https://");
    }

    /**
     * Where this config came from, read from the trailer the file carries: shipped
     * with the JAR, built by the website generator, or downloaded whole from the
     * docs site. The only measure of whether the generator earns its keep.
     */
    private static String configOrigin(JavaPlugin plugin) {
        final String trailer = trailerOf(new File(plugin.getDataFolder(), "config.yml"));
        if (trailer == null) return UNKNOWN;
        final String t = trailer.toUpperCase();
        if (t.contains("GENERATED ON WEBSITE")) return "Website generator";
        if (t.contains("DOWNLOADED FROM WEBSITE")) return "Website download";
        if (t.contains("SHIPPED WITH")) return "Shipped with plugin";
        return UNKNOWN;
    }

    /**
     * The schema the previous config was on, read from the backup the migrator
     * leaves behind. Says which old schemas are still being upgraded from, which is
     * what decides when a frozen generator bundle can stop being maintained.
     */
    private static String migratedFrom(JavaPlugin plugin) {
        final File old = new File(plugin.getDataFolder(), "config.old.yml");
        if (!old.isFile()) return "Never migrated";
        final String schema = schemaIn(old);
        return schema == null ? UNKNOWN : schema;
    }

    /** A config newer than the running build — i.e. someone downgraded the plugin. */
    private static boolean configAheadOfBuild(JavaPlugin plugin) {
        try {
            final String onDisk = configSchema(plugin);
            final String shipped = shippedSchema(plugin);
            if (onDisk == null || shipped == null) return false;
            return schemaNumber(onDisk) > schemaNumber(shipped);
        } catch (Throwable t) {
            return false;
        }
    }

    private static int schemaNumber(String v) {
        try {
            return Integer.parseInt(v.replaceAll("\\D", ""));
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * The schema of the config actually on disk, read from its trailer — not the
     * schema this build ships. Those differ exactly when a server hasn't restarted
     * into a migration yet, and the on-disk value is the one worth measuring.
     */
    private static String configSchema(JavaPlugin plugin) {
        final String s = schemaIn(new File(plugin.getDataFolder(), "config.yml"));
        return s == null ? UNKNOWN : s;
    }

    private static String shippedSchema(JavaPlugin plugin) {
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in == null) return null;
            final Matcher m = SCHEMA_RE.matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            return m.find() ? m.group(1).toUpperCase() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String schemaIn(File file) {
        try {
            if (!file.isFile()) return null;
            final Matcher m = SCHEMA_RE.matcher(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            return m.find() ? m.group(1).toUpperCase() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String trailerOf(File file) {
        try {
            if (!file.isFile()) return null;
            final List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            for (int i = lines.size() - 1; i >= 0; i--) {
                final String line = lines.get(i).trim();
                if (!line.isEmpty()) return line;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    // --------------------------------------------------------------- feature usage

    /**
     * Every enabled {@code log.<category>.<event>} toggle, counted once each.
     * Reads the live config rather than a hardcoded list, so events added later
     * appear here without touching this class.
     */
    private static Map<String, Integer> enabledEvents(JavaPlugin plugin) {
        final Map<String, Integer> counts = new HashMap<>();
        forEachEvent(plugin, (path, section) -> {
            final boolean on = section != null && section.getBoolean("enabled", false);
            if (on) counts.put(path, 1);
        });
        return counts;
    }

    /** Per-event sub-options, plus the format toggles that sit outside {@code log}. */
    private static Map<String, Integer> enabledOptions(JavaPlugin plugin) {
        final Map<String, Integer> counts = new HashMap<>();
        if (plugin.getConfig().getBoolean("log.player.death.show_coords", false)) {
            counts.put("death coordinates", 1);
        }
        if (plugin.getConfig().getBoolean("log.player.join.show_platform", true)) {
            counts.put("Bedrock indicator", 1);
        }
        if (plugin.getConfig().getBoolean("format.nicknames", true)) {
            counts.put("nicknames", 1);
        }
        return counts;
    }

    /** How many distinct destinations this server splits its logs across. */
    private static String routingUsed(JavaPlugin plugin) {
        final int extra = routedEvents(plugin).size();
        if (extra == 0) return "One webhook";
        if (extra <= 2) return "Two or three webhooks";
        return "Four or more webhooks";
    }

    /** Which events carry their own webhook. The URLs themselves are never read. */
    private static Map<String, Integer> routedEvents(JavaPlugin plugin) {
        final Map<String, Integer> counts = new HashMap<>();
        forEachEvent(plugin, (path, section) -> {
            final String hook = section == null ? null : section.getString("webhook", "");
            if (hook != null && !hook.isBlank()) counts.put(path, 1);
        });
        return counts;
    }

    /** Which filters differ from what the plugin ships — never their contents. */
    private static Map<String, Integer> filtersModified(JavaPlugin plugin) {
        final Map<String, Integer> counts = new HashMap<>();
        final YamlConfiguration bundled = bundledConfig(plugin);
        final ConfigurationSection live = plugin.getConfig().getConfigurationSection("filters");
        if (bundled == null || live == null) return counts;

        for (String key : live.getKeys(false)) {
            final Object mine = live.get(key);
            final Object theirs = bundled.get("filters." + key);
            if (!Objects.equals(String.valueOf(mine), String.valueOf(theirs))) {
                counts.put(key, 1);
            }
        }
        return counts;
    }

    /**
     * Whether the command deny-list still protects what it ships to protect.
     *
     * <p>{@code /login}, {@code /register} and {@code /msg} are in it by default
     * because command logging posts the line exactly as typed. A server that has
     * emptied or shortened that list is publishing passwords and private messages
     * to Discord, and this is the only way to find out how often that happens.
     */
    private static String commandFilterState(JavaPlugin plugin) {
        final YamlConfiguration bundled = bundledConfig(plugin);
        if (bundled == null) return UNKNOWN;
        final List<String> mine = plugin.getConfig().getStringList("filters.ignored_commands");
        final List<String> theirs = bundled.getStringList("filters.ignored_commands");

        return commandFilterState(mine, theirs);
    }

    /**
     * The classification itself, split out so it can be tested without a server.
     *
     * <p>"Reduced" is the answer that matters: it means at least one command the
     * plugin ships in the deny-list has been taken out, and command logging posts
     * lines exactly as typed. Getting this backwards would hide the single case
     * worth knowing about.
     */
    static String commandFilterState(List<String> mine, List<String> shipped) {
        if (mine == null || mine.isEmpty()) return "Emptied";
        if (shipped == null || shipped.isEmpty()) return UNKNOWN;
        if (!mine.containsAll(shipped)) return "Reduced";
        return mine.size() > shipped.size() ? "Extended" : "Default";
    }

    /** True when any {@code log.*.*.<leaf>} differs from the shipped default. */
    private static boolean anyDiffers(JavaPlugin plugin, String root, String leaf) {
        final YamlConfiguration bundled = bundledConfig(plugin);
        if (bundled == null) return false;
        final boolean[] differs = {false};
        forEachEvent(plugin, (path, section) -> {
            if (differs[0] || section == null) return;
            final Object mine = section.get(leaf);
            final Object theirs = bundled.get(root + "." + path + "." + leaf);
            if (mine != null && !Objects.equals(String.valueOf(mine), String.valueOf(theirs))) {
                differs[0] = true;
            }
        });
        return differs[0];
    }

    // -------------------------------------------------------------------- lang.yml

    /** How many messages differ from the shipped wording. Never which text. */
    private static int langChanged(JavaPlugin plugin) {
        return langDiff(plugin).size();
    }

    /** Which top-level sections were reworded — chat, discord, or the death causes. */
    private static Map<String, Integer> langSectionsChanged(JavaPlugin plugin) {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (String key : langDiff(plugin)) {
            counts.merge(langSection(key), 1, Integer::sum);
        }
        return counts;
    }

    /** Which part of lang.yml a key belongs to. Split out to be testable. */
    static String langSection(String key) {
        if (key == null) return "other";
        if (key.startsWith("discord.death.causes.")) return "death causes";
        if (key.startsWith("discord.death.")) return "death embed";
        if (key.startsWith("discord.")) return "discord";
        if (key.startsWith("chat.")) return "chat";
        return "other";
    }

    /** The set of lang keys whose value differs from the bundled default. */
    private static List<String> langDiff(JavaPlugin plugin) {
        final List<String> changed = new java.util.ArrayList<>();
        try {
            final File onDisk = new File(plugin.getDataFolder(), "lang.yml");
            if (!onDisk.isFile()) return changed;
            final YamlConfiguration mine = YamlConfiguration.loadConfiguration(onDisk);
            final YamlConfiguration theirs = bundledYaml(plugin, "lang.yml");
            if (theirs == null) return changed;

            for (String key : theirs.getKeys(true)) {
                if (theirs.isConfigurationSection(key)) continue;
                if (key.equals("config-version")) continue;
                if (!Objects.equals(mine.get(key), theirs.get(key))) changed.add(key);
            }
        } catch (Throwable ignored) {
            // A malformed lang.yml is the user's problem, not a reason to fail metrics.
        }
        return changed;
    }

    // ---------------------------------------------------------------------- shared

    private interface EventVisitor {
        void accept(String path, ConfigurationSection section);
    }

    /** Walks {@code log.<category>.<event>}, handing each one its section. */
    private static void forEachEvent(JavaPlugin plugin, EventVisitor visitor) {
        final ConfigurationSection log = plugin.getConfig().getConfigurationSection("log");
        if (log == null) return;
        for (String category : log.getKeys(false)) {
            final ConfigurationSection section = log.getConfigurationSection(category);
            if (section == null) continue;
            for (String event : section.getKeys(false)) {
                visitor.accept(category + "." + event, section.getConfigurationSection(event));
            }
        }
    }

    private static YamlConfiguration bundledConfig(JavaPlugin plugin) {
        return bundledYaml(plugin, "config.yml");
    }

    private static YamlConfiguration bundledYaml(JavaPlugin plugin, String resource) {
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) return null;
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, Map<String, Integer>> drilldown(String outer, String inner) {
        final Map<String, Map<String, Integer>> map = new HashMap<>();
        final Map<String, Integer> entry = new HashMap<>();
        entry.put(inner, 1);
        map.put(outer, entry);
        return map;
    }

    private static String present(boolean b) {
        return b ? "Installed" : "Not installed";
    }

    private static String yesNo(boolean b) {
        return b ? YES : NO;
    }

    /** Buckets rather than an exact count — the shape of the answer, not a fingerprint. */
    static String bucket(int n) {
        if (n == 0) return "None";
        if (n <= 5) return "1-5";
        if (n <= 20) return "6-20";
        if (n <= 50) return "21-50";
        return "50+";
    }
}
