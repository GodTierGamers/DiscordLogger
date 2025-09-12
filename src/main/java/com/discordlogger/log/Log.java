package com.discordlogger.log;

import com.discordlogger.webhook.DiscordWebhook;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Log {
    private static JavaPlugin plugin;
    private static String webhookUrl;
    private static DateTimeFormatter timeFmt;

    // Embeds config
    private static boolean embedsEnabled;
    private static String embedAuthor;                          // configurable
    private static final String EMBED_FOOTER = "DiscordLogger"; // hard-coded
    private static final String PLAYER_THUMB_TEMPLATE =
            "https://mc-heads.net/avatar/{uuid}/256";

    private static final Map<String, Integer> colorMap = new HashMap<>();
    private static int defaultColor = 0x5865F2;

    private Log(){}

    public static void init(JavaPlugin pl, String url, String timePattern) {
        plugin = pl;
        webhookUrl = url;

        try {
            timeFmt = DateTimeFormatter.ofPattern(timePattern);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("[DiscordLogger] Invalid time format in config: " + timePattern + " — using [HH:mm:ss dd:MM:yyyy]");
            timeFmt = DateTimeFormatter.ofPattern("[HH:mm:ss dd:MM:yyyy]");
        }

        // Embeds (author configurable; footer/thumbnail hard-coded)
        embedsEnabled = plugin.getConfig().getBoolean("embeds.enabled", false);
        embedAuthor   = plugin.getConfig().getString("embeds.author", "Server Logs");

        // Default colors
        colorMap.clear();
        colorMap.put("server",           hex("#43B581"));
        colorMap.put("player_join",      hex("#57F287"));
        colorMap.put("player_quit",      hex("#ED4245"));
        colorMap.put("player_chat",      hex("#5865F2"));
        colorMap.put("player_command",   hex("#FEE75C"));
        colorMap.put("server_command",   hex("#EB459E"));
        colorMap.put("player_death",     hex("#ED4245"));

        // Allow overrides via embeds.colors.*
        ConfigurationSection cs = plugin.getConfig().getConfigurationSection("embeds.colors");
        if (cs != null) {
            for (String k : cs.getKeys(false)) {
                String v = cs.getString(k);
                if (v != null && !v.isBlank()) {
                    colorMap.put(normalizeKey(k), hex(v));
                }
            }
        }
        defaultColor = colorMap.getOrDefault("server", defaultColor);
    }

    private static int hex(String s) {
        if (s == null) return defaultColor;
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);
        try { return (int) Long.parseLong(s, 16); }
        catch (NumberFormatException e) { return defaultColor; }
    }

    private static String normalizeKey(String k) {
        return k == null ? "" : k.trim().toLowerCase().replace(' ', '_');
    }

    private static int colorFor(String categoryKey) {
        return colorMap.getOrDefault(normalizeKey(categoryKey), defaultColor);
    }

    private static String ts() { return LocalDateTime.now().format(timeFmt); }

    /** Legacy plain-text line to console + Discord. */
    public static void plain(String message) {
        String line = ts() + " " + message;
        plugin.getLogger().info(line);
        DiscordWebhook.sendAsync(plugin, webhookUrl, line);
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

    /** Event logger (no thumbnail). Sends embed if enabled, else legacy line. */
    public static void event(String category, String message) {
        final String now = ts();
        if (embedsEnabled) {
            String consoleLine = "[" + now + "] " + category + ": " + message;
            plugin.getLogger().info(consoleLine);

            DiscordWebhook.sendEmbed(
                    plugin, webhookUrl,
                    /*title*/ category,
                    /*description*/ message,
                    /*color*/ colorFor(category),
                    /*timestampIso*/ OffsetDateTime.now(ZoneOffset.UTC).toString(),
                    /*author*/ embedAuthor,
                    /*footer*/ EMBED_FOOTER,
                    /*thumbnailUrl*/ null
            );
        } else {
            String line = "`" + now + "` - **" + category + "**: " + message;
            plugin.getLogger().info(line);
            DiscordWebhook.sendAsync(plugin, webhookUrl, line);
        }
    }

    /** Event logger with player thumbnail (avatar). */
    public static void eventWithThumb(String category, String message, String thumbnailUrl) {
        final String now = ts();
        if (embedsEnabled) {
            String consoleLine = "[" + now + "] " + category + ": " + message;
            plugin.getLogger().info(consoleLine);

            DiscordWebhook.sendEmbed(
                    plugin, webhookUrl,
                    /*title*/ category,
                    /*description*/ message,
                    /*color*/ colorFor(category),
                    /*timestampIso*/ OffsetDateTime.now(ZoneOffset.UTC).toString(),
                    /*author*/ embedAuthor,
                    /*footer*/ EMBED_FOOTER,
                    /*thumbnailUrl*/ thumbnailUrl
            );
        } else {
            String line = "`" + now + "` - **" + category + "**: " + message;
            plugin.getLogger().info(line);
            DiscordWebhook.sendAsync(plugin, webhookUrl, line);
        }
    }

    /** Build the hard-coded player avatar URL from UUID (Crafatar). */
    public static String playerAvatarUrl(UUID uuid) {
        if (uuid == null) return null;
        String noDash = uuid.toString().replace("-", "");
        return PLAYER_THUMB_TEMPLATE.replace("{uuid}", noDash);
    }
}
