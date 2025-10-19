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
import java.util.List;

public final class Log {
    private static JavaPlugin plugin;
    private static String webhookUrl;
    private static DateTimeFormatter timeFmt;
    private static String plainServerName;

    // Only send to Discord when true (valid webhook)
    private static boolean ready;

    // Embed config (single source of truth)
    private static boolean embedsEnabledFlag;
    private static String embedAuthorName;

    // Footer text (dynamic: "DiscordLogger v<version>")
    private static final String EMBED_FOOTER_BASE = "DiscordLogger";
    private static String embedFooterText = EMBED_FOOTER_BASE;

    private static final String PLAYER_THUMB_TEMPLATE =
            "https://mc-heads.net/avatar/{uuid}/256";

    private static final Map<String, Integer> colorMap = new HashMap<>();
    private static int defaultColor = 0x5865F2;

    private Log() {}

    /** Initialize runtime config. Safe to call even if url is invalid; we’ll run degraded. */
    public static void init(JavaPlugin pl, String url, String timePattern) {
        plugin = pl;

        // determine readiness & store webhook (store null when not ready)
        ready = isLikelyDiscordWebhook(url);
        webhookUrl = ready ? url : null;

        // compute footer text with plugin version (from plugin.yml -> ${project.version})
        try {
            String ver = plugin.getDescription().getVersion();
            if (ver != null && !ver.isBlank()) {
                embedFooterText = EMBED_FOOTER_BASE + " v" + ver;
            } else {
                embedFooterText = EMBED_FOOTER_BASE;
            }
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

    // ---- Public helpers (used by other components like UpdateChecker) ----
    public static boolean isReady() { return ready; }
    public static boolean embedsEnabled() { return embedsEnabledFlag; }
    public static String embedAuthor() { return embedAuthorName; }

    // ---- Internal utilities ----
    private static boolean isLikelyDiscordWebhook(String url) {
        if (url == null || url.isBlank()) return false;
        return url.startsWith("https://discord.com/api/webhooks/")
                || url.startsWith("https://discordapp.com/api/webhooks/")
                || url.startsWith("https://ptb.discord.com/api/webhooks/")
                || url.startsWith("https://canary.discord.com/api/webhooks/");
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
            // Console echo only (clean text); send EMBED to Discord if ready
            String consoleLine = "[" + now + "] " + category + ": " + message;
            plugin.getLogger().info(consoleLine);

            if (ready) {
                DiscordWebhook.sendEmbed(
                        plugin, webhookUrl,
                        /*title*/ category,
                        /*description*/ message,
                        /*color*/ colorFor(category),
                        /*timestampIso*/ OffsetDateTime.now(ZoneOffset.UTC).toString(),
                        /*author*/ embedAuthorName,
                        /*footer*/ embedFooterText,   // << dynamic footer with version
                        /*thumbnailUrl*/ null
                );
            }
        } else {
            // Plain text path (includes optional server prefix)
            String line = "`" + now + "`" + nameSegment() + " - **" + category + "**: " + message;
            plugin.getLogger().info(line);
            if (ready) {
                DiscordWebhook.sendAsync(plugin, webhookUrl, line);
            }
        }
    }

    /** Event logger with player thumbnail (avatar). */
    public static void eventWithThumb(String category, String message, String thumbnailUrl) {
        final String now = ts();
        if (embedsEnabledFlag) {
            // Console echo only; send EMBED to Discord if ready
            String consoleLine = "[" + now + "] " + category + ": " + message;
            plugin.getLogger().info(consoleLine);

            if (ready) {
                DiscordWebhook.sendEmbed(
                        plugin, webhookUrl,
                        /*title*/ category,
                        /*description*/ message,
                        /*color*/ colorFor(category),
                        /*timestampIso*/ OffsetDateTime.now(ZoneOffset.UTC).toString(),
                        /*author*/ embedAuthorName,
                        /*footer*/ embedFooterText,   // << dynamic footer with version
                        /*thumbnailUrl*/ thumbnailUrl
                );
            }
        } else {
            // Plain text path (includes optional server prefix)
            String line = "`" + now + "`" + nameSegment() + " - **" + category + "**: " + message;
            plugin.getLogger().info(line);
            if (ready) {
                DiscordWebhook.sendAsync(plugin, webhookUrl, line);
            }
        }
    }

    /* =========================
       =   NEW: Fields support  =
       ========================= */

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
     * General-purpose event sender for "structured" embeds with fields.
     * Uses the same pipeline (author/footer/colors/timestamps) as other events.
     * - category: used for color (embeds.colors.<category>)
     * - title: embed title (e.g., "Player Ban")
     * - author: author name (null -> use embeds.author)
     * - fields: list of field name/value pairs (inline respected)
     * - thumbnailUrl: optional image (e.g., player head)
     */
    public static void eventFieldsWithThumb(String category,
                                            String title,
                                            String author,
                                            List<Field> fields,
                                            String thumbnailUrl) {
        final String now = ts();

        // Console echo for visibility
        StringBuilder console = new StringBuilder();
        console.append("[").append(now).append("] ").append(title == null || title.isBlank() ? category : title).append(": ");
        if (fields != null && !fields.isEmpty()) {
            boolean first = true;
            for (Field f : fields) {
                if (!first) console.append(" | ");
                console.append(f.name).append(" ").append(f.value == null || f.value.isBlank() ? "N/A" : mdEscape(f.value));
                first = false;
            }
        }
        plugin.getLogger().info(console.toString());

        if (!ready) return; // no webhook URL -> console only

        if (embedsEnabledFlag) {
            DiscordWebhook.sendEmbedWithFields(
                    plugin,
                    webhookUrl,
                    /*title*/ (title == null || title.isBlank()) ? category : title,
                    /*description*/ "",
                    /*color*/ colorFor(category),
                    /*timestampIso*/ OffsetDateTime.now(ZoneOffset.UTC).toString(),
                    /*author*/ (author == null || author.isBlank()) ? embedAuthorName : author,
                    /*footer*/ embedFooterText,     // << dynamic footer with version
                    /*thumbnailUrl*/ thumbnailUrl,
                    /*fields*/ toFieldsArray(fields)
            );
        } else {
            // Plain text fallback: multiline, readable
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

    /** Convenience wrapper that uses the default embed author and no thumbnail. */
    public static void eventFields(String category, String title, List<Field> fields) {
        eventFieldsWithThumb(category, title, embedAuthorName, fields, null);
    }

    private static String[][] toFieldsArray(List<Field> fields) {
        if (fields == null || fields.isEmpty()) return new String[0][0];
        String[][] arr = new String[fields.size()][3];
        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            String v = (f.value == null || f.value.isBlank()) ? "N/A" : f.value;
            arr[i][0] = f.name;
            arr[i][1] = v;
            arr[i][2] = Boolean.toString(f.inline);
        }
        return arr;
    }

    /** Build the hard-coded player avatar URL from UUID (mc-heads). */
    public static String playerAvatarUrl(UUID uuid) {
        if (uuid == null) return null;
        String noDash = uuid.toString().replace("-", "");
        return PLAYER_THUMB_TEMPLATE.replace("{uuid}", noDash);
    }

    /** Send the "Plugin Updates" embed with fields (used by the update checker). */
    public static void sendUpdateEmbed(String title,
                                       String description,
                                       int color,
                                       String timestampIso,
                                       String author,
                                       String footer,
                                       String currentVersion,
                                       String newVersion) {
        // Console visibility
        String now = ts();
        plugin.getLogger().info("[" + now + "] " + title + ": " + mdEscape(description));

        if (!ready) return; // no webhook URL -> console only

        DiscordWebhook.sendEmbedWithFields(
                plugin,
                webhookUrl,
                title,
                description,
                color,
                timestampIso,
                author,
                // If a custom footer is not provided, use dynamic versioned footer
                (footer == null || footer.isBlank()) ? embedFooterText : footer,
                null, // no thumbnail for update notices
                new String[][]{
                        new String[]{"Current Version", currentVersion, "false"},
                        new String[]{"New Version", newVersion, "false"}
                }
        );
    }
}
