package com.discordlogger.webhook;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class DiscordWebhook {
    private DiscordWebhook() {}

    // Footer icon for all embeds (served from GitHub Pages alongside the plugin source)
    private static final String FOOTER_ICON_URL =
            "https://discordlogger.godtiergamers.xyz/assets/icons/DiscordLogger-Logo-removebg.png";

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Plain content message. */
    public static void sendAsync(JavaPlugin plugin, String url, String content) {
        if (url == null || url.isBlank()) return;
        dispatch(plugin, url, "{\"content\":\"" + escape(content) + "\"}");
    }

    /** Single-embed message (no fields). */
    public static void sendEmbed(
            JavaPlugin plugin, String url,
            String title, String description, int color,
            String isoTimestampUtc, String authorName, String footerText, String thumbnailUrl
    ) {
        if (url == null || url.isBlank()) return;

        StringBuilder sb = new StringBuilder(1024);
        sb.append("{\"embeds\":[{");

        if (title != null && !title.isBlank()) {
            sb.append("\"title\":\"").append(escape(title)).append("\",");
        }
        sb.append("\"description\":\"").append(escape(description == null ? "" : description)).append("\",");
        sb.append("\"color\":").append(color).append(",");

        if (authorName != null && !authorName.isBlank()) {
            sb.append("\"author\":{\"name\":\"").append(escape(authorName)).append("\"},");
        }
        if (footerText != null && !footerText.isBlank()) {
            sb.append("\"footer\":{")
                    .append("\"text\":\"").append(escape(footerText)).append("\",")
                    .append("\"icon_url\":\"").append(escape(FOOTER_ICON_URL)).append("\"")
                    .append("},");
        }
        if (thumbnailUrl != null && !thumbnailUrl.isBlank()) {
            sb.append("\"thumbnail\":{\"url\":\"").append(escape(thumbnailUrl)).append("\"},");
        }
        if (isoTimestampUtc != null && !isoTimestampUtc.isBlank()) {
            sb.append("\"timestamp\":\"").append(escape(isoTimestampUtc)).append("\",");
        }

        trimComma(sb);
        sb.append("}]}");

        dispatch(plugin, url, sb.toString());
    }

    /** Embed with structured fields. */
    public static void sendEmbedWithFields(
            JavaPlugin plugin,
            String url,
            String title,
            String description,
            int color,
            String timestampIso,
            String author,
            String footer,
            String thumbnailUrl,
            String[][] fields // each element: { name, value, inline("true"/"false") }
    ) {
        if (url == null || url.isBlank()) return;

        StringBuilder sb = new StringBuilder(512);
        sb.append("{\"content\":null,\"embeds\":[{");

        if (title != null)       sb.append("\"title\":\"").append(escape(title)).append("\",");
        if (description != null) sb.append("\"description\":\"").append(escape(description)).append("\",");
        sb.append("\"color\":").append(color).append(",");

        if (author != null) {
            sb.append("\"author\":{")
                    .append("\"name\":\"").append(escape(author)).append("\"")
                    .append("},");
        }
        if (footer != null) {
            sb.append("\"footer\":{")
                    .append("\"text\":\"").append(escape(footer)).append("\",")
                    .append("\"icon_url\":\"").append(escape(FOOTER_ICON_URL)).append("\"")
                    .append("},");
        }
        if (thumbnailUrl != null) {
            sb.append("\"thumbnail\":{")
                    .append("\"url\":\"").append(escape(thumbnailUrl)).append("\"")
                    .append("},");
        }
        if (timestampIso != null) {
            sb.append("\"timestamp\":\"").append(escape(timestampIso)).append("\",");
        }

        if (fields != null && fields.length > 0) {
            sb.append("\"fields\":[");
            boolean first = true;
            for (String[] f : fields) {
                if (f == null || f.length < 3) continue;
                if (!first) sb.append(',');
                first = false;
                boolean inline = "true".equalsIgnoreCase(f[2]);
                sb.append('{')
                        .append("\"name\":\"").append(escape(f[0])).append("\",")
                        .append("\"value\":\"").append(escape(f[1])).append("\",")
                        .append("\"inline\":").append(inline)
                        .append('}');
            }
            sb.append("],");
        }

        trimComma(sb);
        sb.append("}],\"attachments\":[]}");

        dispatch(plugin, url, sb.toString());
    }

    // -------------------------------------------------------------------------
    // Shared internals
    // -------------------------------------------------------------------------

    /**
     * Dispatches a JSON payload asynchronously.
     * Falls back to a synchronous call if the plugin is disabled (e.g. server stop)
     * so that shutdown embeds are not silently dropped.
     */
    private static void dispatch(JavaPlugin plugin, String url, String json) {
        Runnable task = () -> postJson(plugin, url, json);
        try {
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            } else {
                task.run();
            }
        } catch (org.bukkit.plugin.IllegalPluginAccessException ex) {
            task.run();
        }
    }

    private static void postJson(JavaPlugin plugin, String url, String json) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 204 && res.statusCode() != 200) {
                plugin.getLogger().warning("[DiscordWebhook] Unexpected HTTP " + res.statusCode());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[DiscordWebhook] Request failed: " + e.getMessage());
        }
    }

    /** JSON string escaper. Handles all control characters correctly. */
    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        // Encode remaining control characters as Unicode escapes
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static void trimComma(StringBuilder sb) {
        if (sb.charAt(sb.length() - 1) == ',') sb.setLength(sb.length() - 1);
    }
}