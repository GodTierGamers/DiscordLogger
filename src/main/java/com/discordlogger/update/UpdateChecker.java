package com.discordlogger.update;

import com.discordlogger.log.Log;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class UpdateChecker {
    private static final String API_URL =
            "https://api.github.com/repos/GodTierGamers/DiscordLogger/releases/latest";
    private static final String RELEASES_URL =
            "https://github.com/GodTierGamers/DiscordLogger/releases/latest";
    private static final Duration TIMEOUT     = Duration.ofSeconds(10);
    private static final int      UPDATE_COLOR = 458_496;

    private UpdateChecker() {}

    /** Fire-and-forget onEnable hook. */
    public static void checkAsync(JavaPlugin plugin) {
        final String current = normalizeVersion(plugin.getPluginMeta().getVersion());

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .build()) {

                // Use the GitHub JSON API instead of following the HTML redirect.
                // The redirect URL parsing was brittle (e.g. if GitHub changes the
                // URL structure). The API returns stable JSON with a "tag_name" field.
                HttpRequest req = HttpRequest.newBuilder(URI.create(API_URL))
                        .timeout(TIMEOUT)
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "DiscordLogger/" + current)
                        .GET()
                        .build();

                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() != 200) {
                    plugin.getLogger().fine("Update check returned HTTP " + resp.statusCode());
                    return;
                }

                String body = resp.body();

                // Skip pre-release / nightly builds entirely
                if (isPreRelease(body)) {
                    plugin.getLogger().fine("Update check: latest release is a pre-release, skipping.");
                    return;
                }

                String latestTag = extractTagName(body);
                if (latestTag == null || latestTag.isBlank()) {
                    plugin.getLogger().fine("Update check: could not parse tag_name from response.");
                    return;
                }

                String latest = normalizeVersion(latestTag);

                if (isNewer(latest, current)) {
                    banner(plugin,
                            "Current: " + current,
                            "Latest : " + latest,
                            "Download: " + RELEASES_URL);
                    sendWebhookNotice(current, latest);
                }
            } catch (Exception e) {
                // Quietly ignore network hiccups on startup
                plugin.getLogger().fine("Update check failed: " + e.getMessage());
            }
        });
    }

    // -------------------------------------------------------------------------

    private static void sendWebhookNotice(String current, String latest) {
        if (Log.embedsEnabled()) {
            Log.sendUpdateEmbed(
                    "Plugin Updates",
                    "A new update is available for DiscordLogger, you can download it [here](" + RELEASES_URL + ")",
                    UPDATE_COLOR,
                    OffsetDateTime.now(ZoneOffset.UTC).toString(),
                    Log.embedAuthor(),
                    "DiscordLogger",
                    current,
                    latest
            );
        } else {
            Log.plain("**Plugin Updates**: A new update for DiscordLogger is available, [download here](" + RELEASES_URL + ")");
        }
    }

    private static void banner(JavaPlugin plugin, String... lines) {
        String bar = "==============================";
        plugin.getLogger().warning(bar + " NEW UPDATE AVAILABLE " + bar);
        for (String l : lines) plugin.getLogger().warning(l);
        plugin.getLogger().warning("================================================================");
    }

    /**
     * Returns true if the GitHub API response has "prerelease": true.
     * Used to suppress update notices for nightly/dev releases.
     */
    private static boolean isPreRelease(String json) {
        if (json == null) return false;
        final String key = "\"prerelease\"";
        int ki = json.indexOf(key);
        if (ki < 0) return false;
        int colon = json.indexOf(':', ki + key.length());
        if (colon < 0) return false;
        // value will be "true" or "false" (no quotes, JSON boolean)
        String rest = json.substring(colon + 1).trim();
        return rest.startsWith("true");
    }

    /**
     * Extracts the value of "tag_name" from a GitHub API JSON response.
     * Avoids a full JSON parser dependency — the field is always a simple string.
     * Example: {"tag_name":"v2.1.6",...} -> "v2.1.6"
     */
    private static String extractTagName(String json) {
        if (json == null) return null;
        final String key = "\"tag_name\"";
        int ki = json.indexOf(key);
        if (ki < 0) return null;
        int colon = json.indexOf(':', ki + key.length());
        if (colon < 0) return null;
        int open = json.indexOf('"', colon + 1);
        if (open < 0) return null;
        int close = json.indexOf('"', open + 1);
        if (close < 0) return null;
        return json.substring(open + 1, close);
    }

    private static String normalizeVersion(String v) {
        if (v == null) return "";
        v = v.trim();
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        v = v.replace("-SNAPSHOT", "");
        return v;
    }

    /** Simple semver-ish compare: returns true if a > b numerically. */
    private static boolean isNewer(String a, String b) {
        String[] as = a.split("\\D+");
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