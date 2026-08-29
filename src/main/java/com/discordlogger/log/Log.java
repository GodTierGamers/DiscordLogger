package com.discordlogger.log;

import com.discordlogger.util.Strings;

import com.discordlogger.webhook.DiscordWebhook;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Log {
    // All fields volatile: init() runs on the main thread; logging methods are
    // called from async scheduler threads. Without volatile, the JVM is free to
    // serve stale cached values to reader threads.
    private static volatile JavaPlugin plugin;
    private static volatile String webhookUrl;
    private static volatile DateTimeFormatter timeFmt;
    private static volatile String plainServerName;

    // Only send to Discord when true (valid webhook)
    private static volatile boolean ready;

    // Embed config (single source of truth)
    private static volatile boolean embedsEnabledFlag;
    private static volatile String embedAuthorName;

    // Footer text. Deliberately just the plugin name -- see Log.init.
    private static final String EMBED_FOOTER_BASE = "DiscordLogger";
    private static volatile String embedFooterText = EMBED_FOOTER_BASE;

    private static final String PLAYER_THUMB_TEMPLATE =
            "https://mc-heads.net/avatar/{uuid}/256";

    // colorMap is replaced atomically: a fully-built map is assigned in one
    // volatile write, so async threads never see a half-populated map.
    private static volatile Map<String, Integer> colorMap = new HashMap<>();

    /**
     * Category key -> the webhook that category posts to, when it overrides the
     * global one. Built and swapped atomically alongside colorMap, for the same
     * reason: async senders must never observe a half-populated map.
     */
    private static volatile Map<String, String> webhookMap = new HashMap<>();
    private static volatile int defaultColor = 0x5865F2;

    private Log() {}

    /** Initialize runtime config. Safe to call even if url is invalid; we'll run degraded. */
    public static void init(JavaPlugin pl, String url, String timePattern) {
        plugin = pl;

        // determine readiness & store webhook (store null when not ready)
        ready = isValidWebhookUrl(url);
        webhookUrl = ready ? url : null;

        // The footer names the plugin, not the build. A version in every embed dates
        // the screenshots in both listings the moment a release ships, and tells the
        // reader nothing they can act on -- anyone who needs it has /discordlogger
        // status, which reports the channel too.
        embedFooterText = EMBED_FOOTER_BASE;

        // plain-text prefix (proxy/server name)
        plainServerName = plugin.getConfig().getString("format.name", "");

        // timestamp format
        try {
            timeFmt = DateTimeFormatter.ofPattern(timePattern);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid time format in config: " + timePattern + " — using [HH:mm:ss dd:MM:yyyy]");
            timeFmt = DateTimeFormatter.ofPattern("[HH:mm:ss dd:MM:yyyy]");
        }

        // Embeds (author configurable; footer/thumbnail hard-coded)
        embedsEnabledFlag = plugin.getConfig().getBoolean("embeds.enabled", false);
        embedAuthorName   = plugin.getConfig().getString("embeds.author", "Server Logs");

        // Build the color map into a local variable first, then assign atomically.
        // This ensures async threads never observe a partially-populated map.
        int baseDefaultColor = 0x5865F2;
        Map<String, Integer> cm = new HashMap<>();

        // Player
        cm.put("player_join",        hex("#57F287", baseDefaultColor)); // green
        cm.put("player_quit",        hex("#ED4245", baseDefaultColor)); // red
        cm.put("player_chat",        hex("#5865F2", baseDefaultColor)); // blurple
        cm.put("player_command",     hex("#FEE75C", baseDefaultColor)); // yellow
        cm.put("player_death",       hex("#ED4245", baseDefaultColor)); // red
        cm.put("player_advancement", hex("#2ECC71", baseDefaultColor)); // green
        cm.put("player_teleport",    hex("#3498DB", baseDefaultColor)); // blue
        cm.put("player_gamemode",    hex("#9B59B6", baseDefaultColor)); // purple

        // Server
        cm.put("server_start",     hex("#43B581", baseDefaultColor)); // green
        cm.put("server_stop",      hex("#ED4245", baseDefaultColor)); // red
        cm.put("server_command",   hex("#EB459E", baseDefaultColor)); // pink
        cm.put("server_explosion", hex("#E74C3C", baseDefaultColor)); // red

        // Moderation
        cm.put("ban",              hex("#FF0000", baseDefaultColor)); // red
        cm.put("unban",            hex("#FF0000", baseDefaultColor)); // red
        cm.put("kick",             hex("#FF0000", baseDefaultColor)); // red
        cm.put("op",               hex("#FF0000", baseDefaultColor)); // red
        cm.put("deop",             hex("#FF0000", baseDefaultColor)); // red
        cm.put("whitelist_toggle", hex("#1ABC9C", baseDefaultColor)); // teal
        cm.put("whitelist",        hex("#16A085", baseDefaultColor)); // dark teal

        // Fallback base category
        cm.put("server", hex("#43B581", baseDefaultColor));

        // Overrides now live beside the toggle they belong to, as
        //     log.<group>.<event>.color
        // rather than the separate embeds.colors tree v9 used (schema v10).
        // A v9-shaped config reaches here only if migration failed, in which case
        // the event is a plain boolean with no section, and the built-in default
        // above stands.
        int currentDefault = cm.getOrDefault("server", baseDefaultColor);
        Map<String, String> wm = new HashMap<>();
        ConfigurationSection logSec = plugin.getConfig().getConfigurationSection("log");
        if (logSec != null) {
            for (String group : logSec.getKeys(false)) {
                ConfigurationSection groupSec = logSec.getConfigurationSection(group);
                if (groupSec == null) continue;
                for (String event : groupSec.getKeys(false)) {
                    ConfigurationSection eventSec = groupSec.getConfigurationSection(event);
                    if (eventSec == null) continue;   // v9-shaped boolean
                    String v = eventSec.getString("color");
                    if (v == null || Strings.isBlank(v)) continue;
                    int c = hex(v, currentDefault);

                    cm.put(normalizeKey(group + "_" + event), c);

                    final String hook = eventSec.getString("webhook");
                    if (hook != null && !Strings.isBlank(hook) && isValidWebhookUrl(hook.trim())) {
                        wm.put(normalizeKey(group + "_" + event), hook.trim());
                        if ("moderation".equals(group)) {
                            wm.put(normalizeKey(event), hook.trim());
                            if ("whitelist_edit".equals(event)) wm.put("whitelist", hook.trim());
                        }
                    } else if (hook != null && !Strings.isBlank(hook)) {
                        plugin.getLogger().warning("log." + group + "." + event
                                + ".webhook is not a valid Discord webhook URL — that event will "
                                + "use the main webhook instead.");
                    }

                    // Moderation listeners pass bare categories ("ban", "kick"),
                    // unlike player/server which pass "<group> <event>". Only
                    // moderation gets the unqualified alias, so that player.command
                    // and server.command cannot overwrite each other under "command".
                    if ("moderation".equals(group)) {
                        cm.put(normalizeKey(event), c);
                        // The whitelist-edit listener's category is "whitelist".
                        if ("whitelist_edit".equals(event)) cm.put("whitelist", c);
                    }
                }
            }
        }

        // Atomic assignment: async threads either see the old complete map or the
        // new complete map — never a half-built one.
        defaultColor = cm.getOrDefault("server", baseDefaultColor);
        colorMap = cm;
        webhookMap = wm;

        if (!wm.isEmpty()) {
            plugin.getLogger().info("Per-event webhook routing active for "
                    + wm.size() + " categor" + (wm.size() == 1 ? "y" : "ies") + ".");
        }
    }

    /**
     * Confirms every configured webhook still exists, once, at startup.
     *
     * <p>Without this, a webhook deleted in Discord is discovered by the first event
     * that 404s — which can be hours later, looks like the plugin breaking, and loses
     * everything in between. It happened: 345 events went into a dead webhook across
     * three and a half hours before anything surfaced, and the only warning went to
     * console, where nobody was watching.
     *
     * <p><b>Never logs a URL.</b> A webhook URL is a bearer credential, so failures
     * name the config path that holds it and nothing else — enough to fix it, useless
     * to anyone reading the log.
     *
     * <p>Deduplicated by URL: routing many categories to one channel is the common
     * case, and probing per category would fire a burst of identical requests at
     * Discord on every boot for no extra information.
     */
    public static void validateWebhooksAsync() {
        final JavaPlugin pl = plugin;
        if (!ready || pl == null) return;

        // webhookFor(null) rather than webhookUrl: the field is only ever read through
        // that accessor, and LogRoutingTest enforces it. Reaching past it here would
        // have been harmless (this enumerates destinations, it does not send) but the
        // guard cannot tell the two apart, and a rule with exceptions stops being one.
        final Map<String, String> byUrl = new LinkedHashMap<>();
        final String main = webhookFor(null);
        if (main != null && !Strings.isBlank(main)) byUrl.put(main, "webhook.url");
        for (Map.Entry<String, String> e : webhookMap.entrySet()) {
            byUrl.putIfAbsent(e.getValue(), "log." + e.getKey() + ".webhook");
        }
        if (byUrl.isEmpty()) return;

        pl.getServer().getScheduler().runTaskAsynchronously(pl, () -> {
            for (Map.Entry<String, String> e : byUrl.entrySet()) {
                describeProbe(pl, e.getValue(), DiscordWebhook.probe(e.getKey()));
            }
        });
    }

    /**
     * Turns a probe status into console output, or into nothing.
     *
     * <p>Split out so the wording is testable without a server. The distinction that
     * matters: 404 is the admin's problem and must be loud, while an unreachable
     * Discord proves nothing about the webhook and must not cry wolf — a plugin that
     * warns about a working webhook during a network blip teaches people to ignore it.
     */
    static void describeProbe(JavaPlugin pl, String where, int status) {
        final String msg = probeMessage(where, status);
        if (msg == null) return;
        if (status == 404 || status == 401 || status == 403) pl.getLogger().warning(msg);
        else pl.getLogger().fine(msg);
    }

    /** The message for a probe result, or null when the result is unremarkable. */
    static String probeMessage(String where, int status) {
        if (status == 404) {
            return "The webhook set in " + where + " no longer exists in Discord (404)."
                    + " Everything routed there will be discarded until it is replaced."
                    + " Set a new one with /discordlogger webhook <url>, or edit config.yml"
                    + " and run /discordlogger reload.";
        }
        if (status == 401 || status == 403) {
            return "Discord rejected the webhook set in " + where + " (HTTP " + status
                    + "). It may have been regenerated.";
        }
        if (status == 0 || status >= 500) {
            return "Could not reach Discord to check " + where + " (status " + status
                    + "). This says nothing about whether the webhook is valid.";
        }
        return null;
    }

    // ---- Public helpers (used by other components like UpdateChecker) ----
    public static boolean isReady()        { return ready; }
    public static boolean embedsEnabled()  { return embedsEnabledFlag; }
    public static String embedAuthor()     { return embedAuthorName; }

    // ---- Internal utilities ----

    public static boolean isValidWebhookUrl(String url) {
        if (url == null || Strings.isBlank(url)) return false;
        return url.startsWith("https://discord.com/api/webhooks/")
                || url.startsWith("https://discordapp.com/api/webhooks/")
                || url.startsWith("https://ptb.discord.com/api/webhooks/")
                || url.startsWith("https://canary.discord.com/api/webhooks/");
    }

    private static int hex(String s, int fallback) {
        if (s == null) return fallback;
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);
        try { return (int) Long.parseLong(s, 16); }
        catch (NumberFormatException e) { return fallback; }
    }

    // Overload used during color-map construction before defaultColor is finalised
    private static int hex(String s) {
        return hex(s, defaultColor);
    }

    private static String normalizeKey(String k) {
        if (k == null) return "";
        return k.trim()
                .toLowerCase()
                .replace(' ', '_')
                .replace('.', '_')
                .replace('-', '_')
                .replace('/', '_');
    }

    /**
     * Where this category posts. Falls back to the main webhook, so an event with
     * no override behaves exactly as it did before routing existed.
     */
    private static String webhookFor(String categoryKey) {
        final String routed = webhookMap.get(normalizeKey(categoryKey));
        return (routed != null && !Strings.isBlank(routed)) ? routed : webhookUrl;
    }

    // NOTE: webhookUrl must be read ONLY through webhookFor above. Every send site
    // resolves its destination that way, including the ones that always want the
    // main webhook (they pass null). This is enforced by
    // LogRoutingTest.everySendSiteResolvesARoute, because the first version of
    // routing missed exactly one send site — the fields embed — and the result was
    // that quit routed correctly while death and gamemode silently did not.

    private static int colorFor(String categoryKey) {
        return colorMap.getOrDefault(normalizeKey(categoryKey), defaultColor);
    }

    private static String ts() { return LocalDateTime.now().format(timeFmt); }

    /** Server name segment for plain-text messages. */
    private static String nameSegment() {
        if (plainServerName == null || Strings.isBlank(plainServerName)) return "";
        return " [" + mdEscape(plainServerName) + "]";
    }

    /** Minimal Markdown escape for names/messages. */
    /**
     * Masks the secret half of any Discord webhook URL in text bound for Discord.
     *
     * <p>A webhook URL is a bearer credential: anyone holding it can post to that
     * channel. Command logging echoes whatever was typed, so without this,
     * {@code /discordlogger webhook <url>} would publish the new URL to the
     * channel — quite possibly the OLD webhook, i.e. the one being moved away
     * from. Redacting the token rather than suppressing the command keeps the
     * audit trail: you still see that someone changed it, just not to what.
     */
    public static String redactWebhooks(String s) {
        if (s == null || s.isEmpty()) return s;
        return WEBHOOK_URL_RE.matcher(s).replaceAll("$1/***");
    }

    private static final java.util.regex.Pattern WEBHOOK_URL_RE =
            java.util.regex.Pattern.compile(
                    "(https://(?:ptb\\.|canary\\.)?discord(?:app)?\\.com/api/webhooks/\\d+)/\\S+");

    public static String mdEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("~", "\\~");
    }

    // ---- Public logging API ----

    /**
     * Plain one-off line (keeps prefix for consistency). Belongs to no event, so it
     * always goes to the main webhook rather than any per-event route.
     */
    public static void plain(String message) {
        String line = "`" + ts() + "`" + nameSegment() + " " + message;
        plugin.getLogger().info(line);
        if (ready) {
            DiscordWebhook.sendAsync(plugin, webhookFor(null), line);
        }
    }

    /** Event logger (no thumbnail). Sends EMBED if enabled, else plain line. */
    public static void event(String category, String message) {
        final String now = ts();
        if (embedsEnabledFlag) {
            plugin.getLogger().info("[" + now + "] " + category + ": " + message);
            if (ready) {
                DiscordWebhook.sendEmbed(
                        plugin, webhookFor(category),
                        /*title*/        category,
                        /*description*/  message,
                        /*color*/        colorFor(category),
                        /*timestampIso*/ OffsetDateTime.now(ZoneOffset.UTC).toString(),
                        /*author*/       embedAuthorName,
                        /*footer*/       embedFooterText,
                        /*thumbnailUrl*/ null
                );
            }
        } else {
            String line = "`" + now + "`" + nameSegment() + " - **" + category + "**: " + message;
            plugin.getLogger().info(line);
            if (ready) DiscordWebhook.sendAsync(plugin, webhookFor(category), line);
        }
    }

    /** Event logger with player thumbnail (avatar). */
    public static void eventWithThumb(String category, String message, String thumbnailUrl) {
        final String now = ts();
        if (embedsEnabledFlag) {
            plugin.getLogger().info("[" + now + "] " + category + ": " + message);
            if (ready) {
                DiscordWebhook.sendEmbed(
                        plugin, webhookFor(category),
                        /*title*/        category,
                        /*description*/  message,
                        /*color*/        colorFor(category),
                        /*timestampIso*/ OffsetDateTime.now(ZoneOffset.UTC).toString(),
                        /*author*/       embedAuthorName,
                        /*footer*/       embedFooterText,
                        /*thumbnailUrl*/ thumbnailUrl
                );
            }
        } else {
            String line = "`" + now + "`" + nameSegment() + " - **" + category + "**: " + message;
            plugin.getLogger().info(line);
            if (ready) DiscordWebhook.sendAsync(plugin, webhookFor(category), line);
        }
    }

    /** Simple value object for embed fields. */
    public static final class Field {
        public final String name;
        public final String value;
        public final boolean inline;

        public Field(String name, String value) {
            this(name, value, false);
        }
        public Field(String name, String value, boolean inline) {
            this.name = name;
            this.value = value;
            this.inline = inline;
        }
    }

    /**
     * General-purpose event sender for structured embeds with fields.
     * - category: used for color lookup (log.<group>.<event>.color)
     * - title: embed title (e.g. "Player Ban")
     * - author: author name (null -> use embeds.author from config)
     * - fields: list of field name/value pairs (inline respected)
     * - thumbnailUrl: optional image (e.g. player head)
     */
    public static void eventFieldsWithThumb(String category,
                                            String title,
                                            String author,
                                            List<Field> fields,
                                            String thumbnailUrl) {
        eventFieldsWithThumb(category, title, "", author, fields, thumbnailUrl);
    }

    /**
     * As above, with a description line between the title and the fields.
     *
     * <p>Overload rather than a sixth parameter on the original: every existing
     * caller wants an empty description, and threading a null through thirteen
     * call sites to say "unchanged" is noise.
     */
    public static void eventFieldsWithThumb(String category,
                                            String title,
                                            String description,
                                            String author,
                                            List<Field> fields,
                                            String thumbnailUrl) {
        final String now = ts();

        // Console echo
        StringBuilder console = new StringBuilder();
        console.append("[").append(now).append("] ")
                .append(title == null || Strings.isBlank(title) ? category : title).append(": ");
        if (description != null && !Strings.isBlank(description)) {
            console.append(description);
            if (fields != null && !fields.isEmpty()) console.append(" | ");
        }
        if (fields != null && !fields.isEmpty()) {
            boolean first = true;
            for (Field f : fields) {
                if (!first) console.append(" | ");
                console.append(f.name).append(" ")
                        .append(f.value == null || Strings.isBlank(f.value) ? "N/A" : mdEscape(f.value));
                first = false;
            }
        }
        plugin.getLogger().info(console.toString());

        if (!ready) return;

        if (embedsEnabledFlag) {
            DiscordWebhook.sendEmbedWithFields(
                    plugin,
                    webhookFor(category),
                    /*title*/        (title == null || Strings.isBlank(title)) ? category : title,
                    /*description*/  description == null ? "" : description,
                    /*color*/        colorFor(category),
                    /*timestampIso*/ OffsetDateTime.now(ZoneOffset.UTC).toString(),
                    /*author*/       (author == null || Strings.isBlank(author)) ? embedAuthorName : author,
                    /*footer*/       embedFooterText,
                    /*thumbnailUrl*/ thumbnailUrl,
                    /*fields*/       toFieldsArray(fields)
            );
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("`").append(now).append("`").append(nameSegment())
                    .append(" - **").append(category).append("**: ")
                    .append(title == null || Strings.isBlank(title) ? "" : title + "\n");
            if (description != null && !Strings.isBlank(description)) {
                sb.append(description).append("\n");
            }
            if (fields != null) {
                for (Field f : fields) {
                    sb.append("- ").append(f.name).append(" ")
                            .append(f.value == null || Strings.isBlank(f.value) ? "N/A" : mdEscape(f.value))
                            .append("\n");
                }
            }
            DiscordWebhook.sendAsync(plugin, webhookFor(category), sb.toString().trim());
        }
    }

    /** Convenience wrapper: default embed author, no thumbnail. */
    public static void eventFields(String category, String title, List<Field> fields) {
        eventFieldsWithThumb(category, title, embedAuthorName, fields, null);
    }

    private static String[][] toFieldsArray(List<Field> fields) {
        if (fields == null || fields.isEmpty()) return new String[0][0];
        String[][] arr = new String[fields.size()][3];
        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            arr[i][0] = f.name;
            arr[i][1] = (f.value == null || Strings.isBlank(f.value)) ? "N/A" : f.value;
            arr[i][2] = Boolean.toString(f.inline);
        }
        return arr;
    }

    /** Build the player avatar URL from UUID (mc-heads.net). */
    public static String playerAvatarUrl(UUID uuid) {
        if (uuid == null) return null;
        return PLAYER_THUMB_TEMPLATE.replace("{uuid}", uuid.toString().replace("-", ""));
    }

    /** Send the "Plugin Updates" embed with fields (used by UpdateChecker). */
    public static void sendUpdateEmbed(String title,
                                       String description,
                                       int color,
                                       String timestampIso,
                                       String author,
                                       String footer,
                                       String currentVersion,
                                       String newVersion) {
        plugin.getLogger().info("[" + ts() + "] " + title + ": " + mdEscape(description));

        if (!ready) return;

        DiscordWebhook.sendEmbedWithFields(
                plugin,
                webhookFor(null),
                title,
                description,
                color,
                timestampIso,
                author,
                (footer == null || Strings.isBlank(footer)) ? embedFooterText : footer,
                null,
                new String[][]{
                        {"Current Version", currentVersion, "false"},
                        {"New Version",     newVersion,     "false"}
                }
        );
    }
}