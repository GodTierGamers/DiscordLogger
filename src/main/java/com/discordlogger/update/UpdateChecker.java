package com.discordlogger.update;

import com.discordlogger.log.Log;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

public final class UpdateChecker {
    private static final String LATEST_URL = "https://github.com/GodTierGamers/DiscordLogger/releases/latest";
    private static final int UPDATE_COLOR = 458_496; // per your spec

    private UpdateChecker() {}

    /** Fire-and-forget onEnable hook. */
    public static void checkAsync(JavaPlugin plugin) {
        final String current = normalizeVersion(plugin.getDescription().getVersion());

        // Run off the main thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .build();

                HttpRequest req = HttpRequest.newBuilder(URI.create(LATEST_URL))
                        .header("User-Agent", "DiscordLogger/" + current)
                        .GET()
                        .build();

                HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
                String latestTag = lastPathSegment(resp.uri().getPath()); // e.g. v2.1.1
                String latest = normalizeVersion(latestTag);

                if (isNewer(latest, current)) {
                    // Console banner
                    banner(plugin, "NEW UPDATE AVAILABLE",
                            "Current: " + current,
                            "Latest : " + latest,
                            "Download: " + LATEST_URL);

                    // Send to Discord
                    sendWebhookNotice(plugin, current, latest);
                }
            } catch (Exception e) {
                // Quietly ignore network hiccups; no spam
                plugin.getLogger().fine("Update check failed: " + e.getMessage());
            }
        });
    }

    private static void sendWebhookNotice(JavaPlugin plugin, String current, String latest) {
        // If embeds are enabled, send a rich embed with fields; else plain line
        if (Log.embedsEnabled()) {
            String nowIso = OffsetDateTime.now(ZoneOffset.UTC).toString();
            Log.sendUpdateEmbed(
                    "Plugin Updates",
                    "A new update is available for DiscordLogger, you can download it [here](" + LATEST_URL + ")",
                    UPDATE_COLOR,
                    nowIso,
                    Log.embedAuthor(),
                    "DiscordLogger",
                    current,
                    latest
            );
        } else {
            Log.plain("**Plugin Updates**: A new update for DiscordLogger is available, [download here](" + LATEST_URL + ")");
        }
    }

    private static void banner(JavaPlugin plugin, String title, String... lines) {
        String bar = "==============================";
        plugin.getLogger().warning(bar + " " + title + " " + bar);
        for (String l : lines) plugin.getLogger().warning(l);
        plugin.getLogger().warning("================================================================");
    }

    private static String lastPathSegment(String path) {
        int idx = path.lastIndexOf('/');
        return (idx >= 0 && idx + 1 < path.length()) ? path.substring(idx + 1) : path;
    }

    private static String normalizeVersion(String v) {
        if (v == null) return "";
        v = v.trim();
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        v = v.replace("-SNAPSHOT", "");
        return v;
    }

    /** Very simple semver-ish compare: 1.2.3 vs 1.2.10; returns true if a>b numerically. */
    private static boolean isNewer(String a, String b) {
        String[] as = a.split("\\D+"); // split on non-digits
        String[] bs = b.split("\\D+");
        int n = Math.max(as.length, bs.length);
        for (int i = 0; i < n; i++) {
            int ai = (i < as.length && !as[i].isEmpty()) ? Integer.parseInt(as[i]) : 0;
            int bi = (i < bs.length && !bs[i].isEmpty()) ? Integer.parseInt(bs[i]) : 0;
            if (ai != bi) return ai > bi;
        }
        return false;
    }
}
