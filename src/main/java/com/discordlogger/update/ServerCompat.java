package com.discordlogger.update;

import com.discordlogger.util.Http;
import org.bukkit.Bukkit;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Whether a release can actually run on this server.
 *
 * <h2>Why the update checker needed this</h2>
 *
 * <p>{@link UpdateChecker} ranks releases by version string and knows nothing about
 * Minecraft versions. That has been harmless because the supported floor has only ever
 * moved <em>down</em> — but the first release that drops a Minecraft version will tell
 * every server still on it, in game, to install a JAR that cannot load. bStats now puts
 * 8 of 29 servers below the newest Minecraft, so that is a real population rather than
 * a hypothetical one.
 *
 * <h2>Why Modrinth rather than GitHub</h2>
 *
 * <p>A GitHub release does not declare which Minecraft versions it supports; nothing in
 * the tag, the body or the asset names carries it. Modrinth does, per version, because
 * {@code publish-listings.py} sends {@code <dl.game.versions>} with every upload and
 * validates it against Modrinth's own list first. It is therefore the same data the
 * listing already advertises, not a second source that could disagree with it.
 *
 * <h2>Fails open, always</h2>
 *
 * <p>Every failure — Modrinth down, version absent, response unparseable — reports
 * "compatible" and lets the existing notice through. An update checker that goes quiet
 * because a third-party API is unreachable is worse than one that occasionally
 * recommends a build the admin then declines to install: the first hides releases with
 * no way to tell, the second is visible and harmless.
 */
public final class ServerCompat {

    private static final String VERSIONS_API =
            "https://api.modrinth.com/v2/project/discordlogger/version";
    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    /** {@code "version_number":"2.3.1"} … {@code "game_versions":["1.19", …]} */
    private ServerCompat() {}

    /**
     * True unless we positively know this release does not list this server's version.
     *
     * <p>The asymmetry is the point: only a definite "your version is absent from that
     * release's list" suppresses the notice.
     */
    public static boolean canRun(String releaseVersion) {
        final Set<String> supported = supportedBy(releaseVersion);
        if (supported.isEmpty()) return true;          // unknown -> do not suppress
        final String mine = serverVersion();
        if (mine == null || mine.isEmpty()) return true;
        return supported.contains(mine);
    }

    /**
     * The Minecraft versions a release lists, or empty when that cannot be established.
     *
     * <p>Parsed by regex rather than with a JSON library for the same reason
     * {@link UpdateChecker} does it: this plugin ships no JSON parser, and adding one
     * to read two fields would put a dependency in every JAR to serve a check that
     * must already tolerate being wrong.
     */
    static Set<String> supportedBy(String releaseVersion) {
        final Set<String> out = new LinkedHashSet<>();
        if (releaseVersion == null || releaseVersion.isEmpty()) return out;
        final String wanted = releaseVersion.startsWith("v")
                ? releaseVersion.substring(1) : releaseVersion;
        try {
            final Map<String, String> headers = new LinkedHashMap<>();
            headers.put("User-Agent", "DiscordLogger");
            final Http.Result resp = Http.get(VERSIONS_API, headers, (int) TIMEOUT.toMillis());
            if (resp.status() != 200) return out;
            return parse(resp.body(), wanted);
        } catch (Exception unreachable) {
            return out;
        }
    }

    /**
     * Pulls one release's game versions out of the listing payload.
     *
     * <p>Walks version_number and game_versions in document order and keeps the list
     * that follows the matching number. Modrinth emits them per object in that order,
     * so this needs no structural parsing — and if that ever stops being true the
     * result is an empty set, which fails open like every other unknown.
     */
    static Set<String> parse(String json, String wantedVersion) {
        final Set<String> out = new LinkedHashSet<>();
        if (json == null) return out;

        // Scanned with indexOf rather than matched with a regex.
        //
        // Both forms accept the same JSON, but a regex walked across a body this size
        // costs more than linear: the engine retries at every position, and the body
        // comes from a third party, so its size and shape are not this plugin's
        // decision. Scanning forward from one marker to the next is linear no matter
        // what arrives, and reads as what it is -- a walk through a document whose
        // order is known.
        final String NUMBER = "\"version_number\"";
        final String VERSIONS = "\"game_versions\"";

        boolean armed = false;
        int at = 0;
        while (at < json.length()) {
            final int number = json.indexOf(NUMBER, at);
            final int versions = json.indexOf(VERSIONS, at);
            if (number < 0 && versions < 0) return out;

            final boolean numberFirst = versions < 0 || (number >= 0 && number < versions);
            if (numberFirst) {
                final int open = json.indexOf('"', json.indexOf(':', number) + 1);
                final int close = open < 0 ? -1 : json.indexOf('"', open + 1);
                if (open < 0 || close < 0) return out;
                armed = json.substring(open + 1, close).equals(wantedVersion);
                at = close + 1;
                continue;
            }

            final int open = json.indexOf('[', versions);
            final int close = open < 0 ? -1 : json.indexOf(']', open);
            if (open < 0 || close < 0) return out;
            if (armed) {
                addQuoted(json, open, close, out);
                return out;
            }
            at = close + 1;
        }
        return out;
    }

    /** Every quoted string between two offsets, in order. */
    private static void addQuoted(String json, int from, int to, Set<String> out) {
        int at = from;
        while (true) {
            final int open = json.indexOf('"', at);
            if (open < 0 || open >= to) return;
            final int close = json.indexOf('"', open + 1);
            if (close < 0 || close > to) return;
            out.add(json.substring(open + 1, close));
            at = close + 1;
        }
    }

    /** This server's Minecraft version, e.g. {@code "1.21.1"}. */
    static String serverVersion() {
        try {
            final String raw = Bukkit.getBukkitVersion();   // "1.21.1-R0.1-SNAPSHOT"
            if (raw == null) return null;
            final int dash = raw.indexOf('-');
            return (dash > 0 ? raw.substring(0, dash) : raw).trim().toLowerCase(Locale.ROOT);
        } catch (Throwable noServer) {
            return null;
        }
    }
}
