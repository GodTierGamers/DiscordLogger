package com.discordlogger.log;

import com.discordlogger.webhook.DiscordWebhook;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class Log {
    private static JavaPlugin plugin;
    private static String webhookUrl;
    private static DateTimeFormatter timeFmt;

    private Log(){}

    public static void init(JavaPlugin pl, String url, String timePattern) {
        plugin = pl;
        webhookUrl = url;
        // Build formatter; fall back safely if config pattern is invalid
        try {
            timeFmt = DateTimeFormatter.ofPattern(timePattern);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("[DiscordLogger] Invalid time format in config: " + timePattern + " — using [HH:mm:ss dd:MM:yyyy]");
            timeFmt = DateTimeFormatter.ofPattern("[HH:mm:ss dd:MM:yyyy]");
        }
    }

    private static String ts() {
        return LocalDateTime.now().format(timeFmt);
    }

    public static void event(String category, String message) {
        String line = "`" + ts() + "` - **" + category + "**: " + message;
        plugin.getLogger().info(line);
        DiscordWebhook.sendAsync(plugin, webhookUrl, line);
    }

    public static String mdEscape(String s) {
        if (s == null) return "";
        return s
                .replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("~", "\\~");
    }

}
