package com.discordlogger.metrics;

import com.discordlogger.update.BuildInfo;
import com.discordlogger.util.Io;
import com.discordlogger.util.Strings;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.DrilldownPie;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
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
 *       (proxy support, PlaceholderAPI, punishment plugins, vanish, permissions) have an
 *       audience, and how many servers are silently getting nothing from
 *       moderation logging because their punishments bypass the vanilla ban list.</li>
 *   <li><b>Feature usage</b> — which of the fifteen filters, the routing, and the
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

    /** The one log group whose keys the admin names rather than this project. */
    private static final String CUSTOM = "custom";

    /**
     * Punishment plugins that keep their own database instead of Bukkit's ban list.
     * On a server running any of these, the moderation listeners verify a ban by
     * checking {@code Bukkit.getBanList()}, find nothing, and log nothing — so this
     * chart measures how many installs silently get no moderation logging at all.
     */
    private static final List<String> PUNISHMENT_PLUGINS =
            Collections.unmodifiableList(Arrays.asList(
                    "LiteBans", "LibertyBans", "AdvancedBan", "BanManager", "CMI"));

    /** Vanish implementations. A vanished admin joining is currently announced anyway. */
    private static final List<String> VANISH_PLUGINS =
            Collections.unmodifiableList(Arrays.asList(
                    "PremiumVanish", "SuperVanish", "Essentials", "CMI"));

    /**
     * Permission managers, in the order a server running two would want reported.
     *
     * <p>Measured because a LuckPerms integration is the one third-party hook worth
     * hard-wiring — permission grants are the highest-audit-value action on a server,
     * and unlike a ban they frequently arrive through paths no command sniffer sees:
     * the web editor, another plugin's API call, or a network-wide database sync. So
     * the feature cannot be approximated the way {@code /ban} can, and it needs
     * evidence before it is built. {@code punishment_plugin} came back {@code None}
     * on every server, which is the outcome this chart exists to find early.
     */
    private static final List<String> PERMISSION_PLUGINS =
            Collections.unmodifiableList(Arrays.asList(
                    "LuckPerms", "UltraPermissions", "PermissionsEx", "GroupManager",
                    "zPermissions", "PowerRanks"));

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
            metrics.addCustomChart(new SimplePie("config_schema", () -> configSchema(plugin)));
            metrics.addCustomChart(new DrilldownPie("mc_version_by_schema",
                    () -> drilldown(minecraftVersion(), configSchema(plugin))));
            metrics.addCustomChart(new DrilldownPie("mc_version_by_java",
                    () -> drilldown(minecraftVersion(), javaMajor())));

            // ----------------------------------------------------------- companion plugins
            metrics.addCustomChart(new SimplePie("proxy_mode", PluginMetrics::proxyMode));
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
            metrics.addCustomChart(new SimplePie("permission_plugin",
                    () -> firstInstalled(PERMISSION_PLUGINS)));

            // -------------------------------------------------------------- config health
            metrics.addCustomChart(new SimplePie("webhook_configured",
                    () -> configured(isWebhookSet(plugin))));
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
            // How MANY custom rules, never which -- the names are the admin's own words.
            metrics.addCustomChart(new SimplePie("custom_logs",
                    () -> bucket(customLogCount(plugin))));

            // ------------------------------------------------------------- reliability
            // Deltas since the last report, so the line charts show activity rather
            // than an ever-climbing total. These describe whether the plugin is
            // working, not what anyone's players are doing.
            metrics.addCustomChart(new SingleLineChart("send_failures", Counters::takeFailedInt));
            metrics.addCustomChart(new SingleLineChart("queue_drops", Counters::takeDroppedInt));
            metrics.addCustomChart(new SingleLineChart("rate_limit_waits", Counters::takeRateLimitedInt));
            metrics.addCustomChart(new SingleLineChart("dead_webhooks", Counters::takeNotFoundInt));
            // Volume is a fact about someone's community rather than about this
            // plugin, so it is banded before it leaves the process.
            metrics.addCustomChart(new SimplePie("send_rate", Counters::takeSendRateBand));
            metrics.addCustomChart(new AdvancedPie("commands_used", PluginMetrics::commandsUsed));

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

    /** Which subcommands ran since the last report. Presence, not a tally. */
    private static Map<String, Integer> commandsUsed() {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (String name : Counters.takeCommandsUsed()) counts.put(name, 1);
        return counts;
    }

    // ------------------------------------------------------------------ environment

    // NOTE: no server_fork or online_mode chart here. bStats 3.2.1 already collects
    // bukkitName (which IS Bukkit.getName(), so forks like Purpur and Leaf show up
    // there correctly) and onlineMode as default charts. Adding our own duplicated
    // the same values under a second name.

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
            return Strings.isBlank(major) ? UNKNOWN : major;
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
            boolean velocity = false;
            final File paperGlobal = new File("config/paper-global.yml");
            if (paperGlobal.isFile()) {
                velocity = YamlConfiguration.loadConfiguration(paperGlobal)
                        .getBoolean("proxies.velocity.enabled", false);
            }
            return proxyModeOf(bungeeForwardingEnabled(), velocity);
        } catch (Throwable t) {
            return UNKNOWN;
        }
    }

    /**
     * {@code settings.bungeecord} from spigot.yml, or false where there is no spigot.yml.
     *
     * <p>Reached by name because {@code Bukkit.spigot()} is Spigot's own extension and
     * does not exist on CraftBukkit. That single call was the only thing in the plugin
     * that could not compile against bare Bukkit, and keeping it off the compiler's
     * path is what lets CI prove the rest runs on all three platforms: Paper
     * implements Spigot implements Bukkit, so what compiles against the smallest of
     * them runs on every server that matters.
     *
     * <p>Only the {@code spigot()} hop is reflective. Its config is an ordinary Bukkit
     * {@link ConfigurationSection}, so the value is read with normal type checking.
     */
    static boolean bungeeForwardingEnabled() {
        try {
            final Object spigot = Bukkit.class.getMethod("spigot").invoke(null);
            final Object cfg = spigot.getClass().getMethod("getConfig").invoke(spigot);
            return (cfg instanceof ConfigurationSection)
                    && ((ConfigurationSection) cfg).getBoolean("settings.bungeecord", false);
        } catch (Throwable notSpigot) {
            // CraftBukkit, or a fork that dropped it. No spigot.yml means no legacy
            // BungeeCord forwarding to report.
            return false;
        }
    }

    /**
     * The proxy verdict, split out from the file reading so it can be tested.
     *
     * <p><strong>Do not reintroduce a check on {@code proxies.bungee-cord.online-mode}.</strong>
     * That key ships as {@code true} in every {@code paper-global.yml} and only means
     * anything when {@code settings.bungeecord} is already on, so reading it alone
     * reported "BungeeCord" for every default Paper server. The chart showed 100% of
     * servers proxied while bStats' own {@code onlineMode} put the ceiling at a
     * quarter — a proxied backend must run offline-mode, and most of these were online.
     *
     * @param bungeeFlag  {@code settings.bungeecord} from spigot.yml
     * @param velocityOn  {@code proxies.velocity.enabled} from paper-global.yml
     */
    static String proxyModeOf(boolean bungeeFlag, boolean velocityOn) {
        // Modern forwarding is the one unambiguous signal, so it wins outright.
        if (velocityOn) return "Velocity";
        // Velocity's legacy forwarding is configured through the same spigot.yml flag
        // BungeeCord uses, so this genuinely cannot tell them apart. Say so rather
        // than guessing — a wrong attribution is worse than a vague one.
        if (bungeeFlag) return "BungeeCord or Velocity";
        return "None";
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
            final Matcher m = SCHEMA_RE.matcher(Io.readString(in));
            return m.find() ? m.group(1).toUpperCase() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String schemaIn(File file) {
        try {
            if (!file.isFile()) return null;
            final Matcher m = SCHEMA_RE.matcher(Io.readString(file.toPath()));
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
            if (hook != null && !Strings.isBlank(hook)) counts.put(path, 1);
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
    /**
     * How many custom rules are defined. Bucketed by the caller, never named.
     *
     * <p>Answers "does anyone use this feature", which is the only thing worth knowing
     * and the most that can be known without describing a specific server's setup.
     */
    static int customLogCount(JavaPlugin plugin) {
        final ConfigurationSection sec =
                plugin.getConfig().getConfigurationSection("log." + CUSTOM);
        return sec == null ? 0 : sec.getKeys(false).size();
    }

    private static void forEachEvent(JavaPlugin plugin, EventVisitor visitor) {
        final ConfigurationSection log = plugin.getConfig().getConfigurationSection("log");
        if (log == null) return;
        for (String category : log.getKeys(false)) {
            // log.custom.* is named by the admin, so its keys are THEIR words -- a
            // server naming a rule after their own staff process would publish that
            // to bStats through enabled_events. Every other value here is a fixed
            // string this project chose; these are the only ones that are not, so
            // they are excluded from every walk and counted as a bucket instead.
            if (CUSTOM.equals(category)) continue;
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

    /**
     * Whether a setting has been filled in — not whether something is installed.
     *
     * <p>Distinct from {@link #present(boolean)} because the two answer different
     * questions and share no vocabulary. A webhook is configured or it isn't; it
     * is never "installed", and a chart reading "Not installed" invites the
     * reader to conclude the plugin isn't there at all.
     */
    private static String configured(boolean b) {
        return b ? "Configured" : "Not configured";
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
