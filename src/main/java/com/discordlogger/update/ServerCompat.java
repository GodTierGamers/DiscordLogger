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
     * <p>Walks the payload once, tracking string and brace state, and hands back the
     * {@code game_versions} of the object whose {@code version_number} matches. Both
     * fields are read from the same object, so their order inside it does not matter.
     *
     * <h2>Why that last sentence is the whole fix</h2>
     *
     * <p>This used to arm on {@code version_number} and take the next
     * {@code game_versions} that followed it, on the stated assumption that Modrinth
     * emits them in that order. It does not: {@code game_versions} comes first. So the
     * armed scan ran past the rest of the matching object and returned the list
     * belonging to the <em>next</em> release in the document.
     *
     * <p>The effect was silent and one-directional. Releases list newest first, so
     * 2.4.0 was reported as supporting whatever 2.3.1 supported -- 1.19 and up. Servers
     * on 1.19+ saw a correct answer by coincidence; every server between 1.8 and 1.18
     * was told the update could not run on it, which was exactly the audience 2.4.0
     * added support for.
     *
     * <p>The tests did not catch it because the fixture was written with the fields in
     * the order the parser expected rather than the order Modrinth sends. It proved the
     * parser agreed with itself.
     *
     * <p>Still linear, and still no JSON library: the scan is one pass, and quoted
     * text is skipped so a brace or bracket inside a changelog cannot move the
     * boundaries.
     */
    static Set<String> parse(String json, String wantedVersion) {
        final Set<String> out = new LinkedHashSet<>();
        if (json == null || wantedVersion == null) return out;

        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        int objectStart = -1;

        for (int i = 0; i < json.length(); i++) {
            final char c = json.charAt(i);

            if (escaped) { escaped = false; continue; }
            if (inString && c == '\\') { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;

            if (c == '{') {
                if (depth == 0) objectStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objectStart >= 0) {
                    final String object = json.substring(objectStart, i + 1);
                    if (wantedVersion.equals(stringField(object, "\"version_number\""))) {
                        arrayField(object, "\"game_versions\"", out);
                        return out;
                    }
                    objectStart = -1;
                }
            }
        }
        return out;
    }

    /** The string value of {@code key} in one object, or null. */
    private static String stringField(String object, String key) {
        final int at = object.indexOf(key);
        if (at < 0) return null;
        final int colon = object.indexOf(':', at + key.length());
        if (colon < 0) return null;
        final int open = object.indexOf('"', colon + 1);
        if (open < 0) return null;
        final int close = object.indexOf('"', open + 1);
        if (close < 0) return null;
        return object.substring(open + 1, close);
    }

    /** Every string in the array at {@code key}, added to {@code out}. */
    private static void arrayField(String object, String key, Set<String> out) {
        final int at = object.indexOf(key);
        if (at < 0) return;
        final int open = object.indexOf('[', at + key.length());
        final int close = open < 0 ? -1 : object.indexOf(']', open);
        if (open < 0 || close < 0) return;
        addQuoted(object, open, close, out);
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
