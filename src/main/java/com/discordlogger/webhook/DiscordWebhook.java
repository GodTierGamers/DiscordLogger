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
    private DiscordWebhook(){}

    // Hardcoded footer icon for all embeds
    private static final String FOOTER_ICON_URL =
            "https://files.godtiergamers.xyz/DiscordLogger-Logo-removebg.jpg";

    /** Plain content message (legacy). */
    public static void sendAsync(JavaPlugin plugin, String url, String content) {
        if (url == null || url.isBlank()) return;

        Runnable task = () -> postJson(url, "{\"content\":\"" + escape(content) + "\"}");
        try {
            if (plugin.isEnabled()) Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            else task.run(); // synchronous during shutdown
        } catch (org.bukkit.plugin.IllegalPluginAccessException ex) {
            task.run();
        }
    }

    /** Single-embed message. */
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

        // Trim trailing comma if present
        if (sb.charAt(sb.length() - 1) == ',') sb.setLength(sb.length() - 1);
        sb.append("}]}");

        String json = sb.toString();

        Runnable task = () -> postJson(url, json);
        try {
            if (plugin.isEnabled()) Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            else task.run();
        } catch (org.bukkit.plugin.IllegalPluginAccessException ex) {
            task.run();
        }
    }

    private static void postJson(String url, String json) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 204 && res.statusCode() != 200) {
                System.out.println("[DiscordLogger] Webhook HTTP " + res.statusCode());
            }
        } catch (Exception e) {
            System.out.println("[DiscordLogger] Webhook error: " + e.getMessage());
        }
    }

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
                    if (c < 0x20) sb.append(' ');
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    public static void sendEmbedWithFields(
            org.bukkit.plugin.java.JavaPlugin plugin,
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
        if (plugin == null || !plugin.isEnabled() || url == null || url.isBlank()) return;

        StringBuilder sb = new StringBuilder(512);
        sb.append("{\"content\":null,\"embeds\":[{");

        if (title != null)       sb.append("\"title\":").append(qf(title)).append(',');
        if (description != null) sb.append("\"description\":").append(qf(description)).append(',');
        sb.append("\"color\":").append(color).append(',');

        if (author != null) {
            sb.append("\"author\":{")
                    .append("\"name\":").append(qf(author))
                    .append("},");
        }
        if (footer != null) {
            sb.append("\"footer\":{")
                    .append("\"text\":").append(qf(footer)).append(',')
                    .append("\"icon_url\":").append(qf(FOOTER_ICON_URL))
                    .append("},");
        }
        if (thumbnailUrl != null) {
            sb.append("\"thumbnail\":{")
                    .append("\"url\":").append(qf(thumbnailUrl))
                    .append("},");
        }
        if (timestampIso != null) {
            sb.append("\"timestamp\":").append(qf(timestampIso)).append(',');
        }

        // fields
        if (fields != null && fields.length > 0) {
            sb.append("\"fields\":[");
            boolean first = true;
            for (String[] f : fields) {
                if (f == null || f.length < 3) continue;
                if (!first) sb.append(',');
                first = false;
                boolean inline = "true".equalsIgnoreCase(f[2]);
                sb.append('{')
                        .append("\"name\":").append(qf(f[0])).append(',')
                        .append("\"value\":").append(qf(f[1])).append(',')
                        .append("\"inline\":").append(inline)
                        .append('}');
            }
            sb.append("],");
        }

        // trim trailing comma if present
        if (sb.charAt(sb.length() - 1) == ',') sb.setLength(sb.length() - 1);

        sb.append("}],\"attachments\":[]}");

        postJsonAsyncFields(plugin, url, sb.toString());
    }

    // --- private helpers (named uniquely to avoid collisions) ---

    /** Minimal JSON string escaper + quotes. */
    private static String qf(String s) {
        if (s == null) return "null";
        String esc = s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return "\"" + esc + "\"";
    }

    private static void postJsonAsyncFields(org.bukkit.plugin.java.JavaPlugin plugin, String url, String json) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                        .build();
                client.send(request, java.net.http.HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {}
        });
    }
}
