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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateChecker {
    // Lists releases (stable + pre-releases), newest first -- unlike /releases/latest,
    // which only ever returns the newest non-prerelease and can't tell a nightly
    // build how far behind it is.
    private static final String RELEASES_LIST_API_URL =
            "https://api.github.com/repos/GodTierGamers/DiscordLogger/releases?per_page=50";
    private static final String RELEASES_URL =
            "https://github.com/GodTierGamers/DiscordLogger/releases/latest";
    private static final Duration TIMEOUT      = Duration.ofSeconds(10);
    private static final int      UPDATE_COLOR = 458_496;

    // Nightly users are notified about EVERY new stable release, but about newer
    // nightlies only once they're meaningfully behind (more than 2 builds) --
    // "upgrade frequently" without nagging on every single nightly.
    private static final int NIGHTLY_LAG_THRESHOLD = 2;

    private static final Pattern TAG_NAME_RE = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]*)\"");

    private UpdateChecker() {}

    /** Fire-and-forget onEnable hook. */
    public static void checkAsync(JavaPlugin plugin) {
        if (BuildInfo.isDev()) {
            plugin.getLogger().fine("Update check skipped for a local/dev build.");
            return;
        }

        final SemVer current = SemVer.parse(BuildInfo.version());
        if (current == null) {
            plugin.getLogger().fine("Update check skipped: could not parse running version.");
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            // Deliberately NOT try-with-resources. HttpClient only became
            // AutoCloseable in Java 21, and this is compiled for 17 so the plugin
            // loads on servers older than 1.20.5. Nothing leaks: one request is
            // made and the client is unreachable immediately afterwards.
            final HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .build();
            try {

                HttpRequest req = HttpRequest.newBuilder(URI.create(RELEASES_LIST_API_URL))
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

                List<ReleaseInfo> releases = parseReleases(resp.body());

                if (BuildInfo.isStable()) {
                    checkStable(plugin, current, releases);
                } else if (BuildInfo.isNightly()) {
                    checkNightly(plugin, current, releases);
                }
            } catch (Exception e) {
                // Quietly ignore network hiccups on startup
                plugin.getLogger().fine("Update check failed: " + e.getMessage());
            }
        });
    }

    // -------------------------------------------------------------------------
    // Channel-specific logic
    // -------------------------------------------------------------------------

    /** Stable channel: notify on ANY newer stable release. Pre-releases are invisible. */
    private static void checkStable(JavaPlugin plugin, SemVer current, List<ReleaseInfo> releases) {
        for (ReleaseInfo r : releases) {
            if (r.prerelease()) continue;
            SemVer latest = SemVer.parse(r.tag());
            if (latest == null) return;
            if (latest.compareTo(current) > 0) {
                notify(plugin, current, latest, "A new stable release is available.");
            }
            return; // first non-prerelease encountered (list is newest-first) is latest stable
        }
    }

    /**
     * Nightly channel: notify on EVERY newer stable release, and about newer
     * nightlies only once more than {@link #NIGHTLY_LAG_THRESHOLD} of them exist.
     */
    private static void checkNightly(JavaPlugin plugin, SemVer current, List<ReleaseInfo> releases) {
        SemVer bestStable = null;
        int newerNightlyCount = 0;

        for (ReleaseInfo r : releases) {
            SemVer v = SemVer.parse(r.tag());
            if (v == null) continue;

            if (r.prerelease()) {
                if (v.compareTo(current) > 0) newerNightlyCount++;
            } else if (bestStable == null || v.compareTo(bestStable) > 0) {
                bestStable = v;
            }
        }

        if (bestStable != null && bestStable.compareTo(current) > 0) {
            notify(plugin, current, bestStable,
                    "A new stable release is available -- nightly users should move to it.");
            return;
        }

        if (newerNightlyCount > NIGHTLY_LAG_THRESHOLD) {
            notify(plugin, current, null,
                    newerNightlyCount + " newer nightly builds are available -- you are falling behind.");
        }
    }

    // -------------------------------------------------------------------------
    // Notification (console banner + Discord webhook notice)
    // -------------------------------------------------------------------------

    private static void notify(JavaPlugin plugin, SemVer current, SemVer latest, String headline) {
        String latestStr = (latest != null) ? latest.toString() : "see the releases page";
        banner(plugin,
                headline,
                "Current: " + current,
                "Latest : " + latestStr,
                "Download: " + RELEASES_URL);
        sendWebhookNotice(current.toString(), latestStr, headline);
    }

    private static void sendWebhookNotice(String current, String latest, String headline) {
        if (Log.embedsEnabled()) {
            Log.sendUpdateEmbed(
                    "Plugin Updates",
                    headline + " You can download it [here](" + RELEASES_URL + ")",
                    UPDATE_COLOR,
                    OffsetDateTime.now(ZoneOffset.UTC).toString(),
                    Log.embedAuthor(),
                    "DiscordLogger",
                    current,
                    latest
            );
        } else {
            Log.plain("**Plugin Updates**: " + headline + " [Download here](" + RELEASES_URL + ")");
        }
    }

    private static void banner(JavaPlugin plugin, String... lines) {
        String bar = "==============================";
        plugin.getLogger().warning(bar + " NEW UPDATE AVAILABLE " + bar);
        for (String l : lines) plugin.getLogger().warning(l);
        plugin.getLogger().warning("================================================================");
    }

    // -------------------------------------------------------------------------
    // GitHub releases-list JSON scraping (no JSON dependency, matches the
    // string-scanning approach this class has always used)
    // -------------------------------------------------------------------------

    private record ReleaseInfo(String tag, boolean prerelease) {}

    /**
     * Pairs each "tag_name" with the "prerelease" flag that appears before the NEXT
     * "tag_name" in the document. Safe without a full JSON parser because GitHub's
     * release objects are emitted sequentially and never nest another release inside
     * one -- so "everything between this tag_name and the next" is exactly one release.
     */
    private static List<ReleaseInfo> parseReleases(String json) {
        List<ReleaseInfo> out = new ArrayList<>();
        if (json == null) return out;

        List<String> tags = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();

        Matcher m = TAG_NAME_RE.matcher(json);
        while (m.find()) {
            tags.add(m.group(1));
            starts.add(m.start());
            ends.add(m.end());
        }

        for (int i = 0; i < tags.size(); i++) {
            int from = ends.get(i);
            int to = (i + 1 < tags.size()) ? starts.get(i + 1) : json.length();
            String segment = json.substring(from, to);
            boolean prerelease = segment.contains("\"prerelease\":true") || segment.contains("\"prerelease\": true");
            out.add(new ReleaseInfo(tags.get(i), prerelease));
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Semver-ish version, with -BETA.N precedence (stable ranks above any beta
    // of the same major.minor.patch; higher BETA numbers rank above lower ones)
    // -------------------------------------------------------------------------

    private record SemVer(int major, int minor, int patch, Integer beta) implements Comparable<SemVer> {
        static SemVer parse(String raw) {
            if (raw == null) return null;
            String v = raw.trim();
            if (v.isEmpty()) return null;
            if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
            v = v.replace("-SNAPSHOT", "");

            Integer betaNum = null;
            int betaIdx = v.toUpperCase(Locale.ROOT).indexOf("-BETA.");
            if (betaIdx >= 0) {
                String betaPart = v.substring(betaIdx + "-BETA.".length());
                try {
                    betaNum = Integer.parseInt(betaPart.replaceAll("\\D.*$", ""));
                } catch (NumberFormatException ignored) {
                    betaNum = 0;
                }
                v = v.substring(0, betaIdx);
            }

            String[] parts = v.split("\\.");
            if (parts.length == 0 || parts[0].isBlank()) return null;

            return new SemVer(part(parts, 0), part(parts, 1), part(parts, 2), betaNum);
        }

        private static int part(String[] parts, int i) {
            if (i >= parts.length) return 0;
            String digits = parts[i].replaceAll("\\D", "");
            if (digits.isEmpty()) return 0;
            try {
                return Integer.parseInt(digits);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        @Override
        public int compareTo(SemVer o) {
            if (major != o.major) return Integer.compare(major, o.major);
            if (minor != o.minor) return Integer.compare(minor, o.minor);
            if (patch != o.patch) return Integer.compare(patch, o.patch);
            if (beta == null && o.beta == null) return 0;
            if (beta == null) return 1;   // stable ranks above any beta of the same x.y.z
            if (o.beta == null) return -1;
            return Integer.compare(beta, o.beta);
        }

        @Override
        public String toString() {
            String base = major + "." + minor + "." + patch;
            return (beta != null) ? base + "-BETA." + beta : base;
        }
    }
}
