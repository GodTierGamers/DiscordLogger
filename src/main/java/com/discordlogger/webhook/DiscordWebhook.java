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

    public static void sendAsync(JavaPlugin plugin, String url, String content) {
        if (url == null || url.isBlank()) return;

        Runnable task = () -> post(url, content);

        // If the plugin is enabled, use Bukkit's async scheduler.
        // If it's disabling (onDisable path), run inline to avoid IllegalPluginAccessException.
        try {
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            } else {
                task.run(); // synchronous send during shutdown
            }
        } catch (org.bukkit.plugin.IllegalPluginAccessException ex) {
            // Fallback if Paper flips the enabled state earlier than expected
            task.run();
        }
    }


    private static void post(String url, String content) {
        try {
            String json = "{\"content\":\"" + escape(content) + "\"}";
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
}
