package com.discordlogger.log;

import com.discordlogger.webhook.DiscordWebhook;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
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

    // Footer text (dynamic: "DiscordLogger v<version>")
    private static final String EMBED_FOOTER_BASE = "DiscordLogger";
    private static volatile String embedFooterText = EMBED_FOOTER_BASE;

    private static final String PLAYER_THUMB_TEMPLATE =
            "https://mc-heads.net/avatar/{uuid}/256";

    // colorMap is replaced atomically: a fully-built map is assigned in one
    // volatile write, so async threads never see a half-populated map.
    private static volatile Map<String, Integer> colorMap = new HashMap<>();
    private static volatile int defaultColor = 0x5865F2;

    private Log() {}

    /** Initialize runtime config. Safe to call even if url is invalid; we'll run degraded. */
    public static void init(JavaPlugin pl, String url, String timePattern) {
        plugin = pl;

        // determine readiness & store webhook (store null when not ready)
        ready = isValidWebhookUrl(url);
        webhookUrl = ready ? url : null;

        // compute footer text with plugin version (from plugin.yml -> ${project.version})
        try {
            String ver = plugin.getDescription().getVersion();
            embedFooterText = (ver != null && !ver.isBlank())
                    ? EMBED_FOOTER_BASE + " v" + ver
                    : EMBED_FOOTER_BASE;
        } catch (Exception ignored) {
            embedFooterText = EMBED_FOOTER_BASE;
        }

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

        // Allow overrides via embeds.colors.*
        // Supports both:
        // - flat:   embeds.colors.player_join: "#...."
        // - nested: embeds.colors.player.join: "#...."
        int currentDefault = cm.getOrDefault("server", baseDefaultColor);
        ConfigurationSection base = plugin.getConfig().getConfigurationSection("embeds.colors");
        if (base != null) {
            for (String k : base.getKeys(false)) {
                Object child = base.get(k);
                if (child instanceof ConfigurationSection) {
                    ConfigurationSection group = (ConfigurationSection) child;
                    for (String sk : group.getKeys(false)) {
                        String v = group.getString(sk);
                        if (v != null && !v.isBlank()) {
                            cm.put(normalizeKey(k + "_" + sk), hex(v, currentDefault));
                            cm.put(normalizeKey(sk), hex(v, currentDefault));
                        }
                    }
                } else {
                    String v = base.getString(k);
                    if (v != null && !v.isBlank()) {
                        cm.put(normalizeKey(k), hex(v, currentDefault));
                    }
                }
            }
        }

        // Atomic assignment: async threads either see the old complete map or the
        // new complete map — never a half-built one.
        defaultColor = cm.getOrDefault("server", baseDefaultColor);
        colorMap = cm;
    }

    // ---- Public helpers (used by other components like UpdateChecker) ----
    public static boolean isReady()        { return ready; }
    public static boolean embedsEnabled()  { return embedsEnabledFlag; }
    public static String embedAuthor()     { return embedAuthorName; }

    // ---- Internal utilities ----

    private static boolean isValidWebhookUrl(String url) {
        if (url == null || url.isBlank()) return false;
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

    private static int colorFor(String categoryKey) {
        return colorMap.getOrDefault(normalizeKey(categoryKey), defaultColor);
    }

    private static String ts() { return LocalDateTime.now().format(timeFmt); }

    /** Server name segment for plain-text messages. */
    private static String nameSegment() {
        if (plainServerName == null || plainServerName.isBlank()) return "";
        return " [" + mdEscape(plainServerName) + "]";
    }

    /** Minimal Markdown escape for names/messages. */
    public static String mdEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("~", "\\~");
    }

    // ---- Public logging API ----

    /** Plain one-off line (keeps prefix for consistency). */
    public static void plain(String message) {
        String line = "`" + ts() + "`" + nameSegment() + " " + message;
        plugin.getLogger().info(line);
        if (ready) {
            DiscordWebhook.sendAsync(plugin, webhookUrl, line);
        }
    }

    /** Event logger (no thumbnail). Sends EMBED if enabled, else plain line. */
    public static void event(String category, String message) {
        final String now = ts();
        if (embedsEnabledFlag) {
            plugin.getLogger().info("[" + now + "] " + category + ": " + message);
            if (ready) {
                DiscordWebhook.sendEmbed(
                        plugin, webhookUrl,
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
            if (ready) DiscordWebhook.sendAsync(plugin, webhookUrl, line);
        }
    }

    /** Event logger with player thumbnail (avatar). */
    public static void eventWithThumb(String category, String message, String thumbnailUrl) {
        final String now = ts();
        if (embedsEnabledFlag) {
            plugin.getLogger().info("[" + now + "] " + category + ": " + message);
            if (ready) {
                DiscordWebhook.sendEmbed(
                        plugin, webhookUrl,
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
            if (ready) DiscordWebhook.sendAsync(plugin, webhookUrl, line);
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
     * - category: used for color lookup (embeds.colors.<category>)
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
        final String now = ts();

        // Console echo
        StringBuilder console = new StringBuilder();
        console.append("[").append(now).append("] ")
                .append(title == null || title.isBlank() ? category : title).append(": ");
        if (fields != null && !fields.isEmpty()) {
            boolean first = true;
            for (Field f : fields) {
                if (!first) console.append(" | ");
                console.append(f.name).append(" ")
                        .append(f.value == null || f.value.isBlank() ? "N/A" : mdEscape(f.value));
                first = false;
            }
        }
        plugin.getLogger().info(console.toString());

        if (!ready) return;

        if (embedsEnabledFlag) {
            DiscordWebhook.sendEmbedWithFields(
                    plugin,
                    webhookUrl,
                    /*title*/        (title == null || title.isBlank()) ? category : title,
                    /*description*/  "",
                    /*color*/        colorFor(category),
                    /*timestampIso*/ OffsetDateTime.now(ZoneOffset.UTC).toString(),
                    /*author*/       (author == null || author.isBlank()) ? embedAuthorName : author,
                    /*footer*/       embedFooterText,
                    /*thumbnailUrl*/ thumbnailUrl,
                    /*fields*/       toFieldsArray(fields)
            );
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("`").append(now).append("`").append(nameSegment())
                    .append(" - **").append(category).append("**: ")
                    .append(title == null || title.isBlank() ? "" : title + "\n");
            if (fields != null) {
                for (Field f : fields) {
                    sb.append("- ").append(f.name).append(" ")
                            .append(f.value == null || f.value.isBlank() ? "N/A" : mdEscape(f.value))
                            .append("\n");
                }
            }
            DiscordWebhook.sendAsync(plugin, webhookUrl, sb.toString().trim());
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
            arr[i][1] = (f.value == null || f.value.isBlank()) ? "N/A" : f.value;
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
                webhookUrl,
                title,
                description,
                color,
                timestampIso,
                author,
                (footer == null || footer.isBlank()) ? embedFooterText : footer,
                null,
                new String[][]{
                        {"Current Version", currentVersion, "false"},
                        {"New Version",     newVersion,     "false"}
                }
        );
    }
}