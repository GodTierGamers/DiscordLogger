package com.discordlogger.acceptance;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Obtains a server JAR for a Minecraft version, and caches it.
 *
 * <p>Cached because a nightly run boots several versions and re-downloading a few
 * hundred megabytes each time is the difference between a job that finishes and one
 * that irritates. The cache directory is meant to be restored by actions/cache.
 *
 * <p>The checksum is verified rather than trusted. This downloads an executable that
 * is then run with the plugin inside it; "it came over HTTPS" is not the same as
 * "it is what the project published", and a corrupted download would otherwise fail
 * later as something that looks like a plugin bug.
 */
final class ServerJars {

    private static final String FILL = "https://fill.papermc.io/v3/projects/paper/versions/";

    private ServerJars() {}

    /**
     * A server JAR for {@code mcVersion}, however it has to be obtained.
     *
     * <p>Prefers one already in the cache. That is how 1.8.0 is served: Paper's oldest
     * build is 1.8.8, so the floor has to come from Spigot's BuildTools, which compiles
     * it from source because Spigot cannot redistribute the JAR. CI does that once and
     * caches the result; here it just shows up as a file that is already present.
     */
    static Path forVersion(String mcVersion, Path cacheDir) throws Exception {
        Files.createDirectories(cacheDir);
        for (String name : new String[]{
                "spigot-" + mcVersion + ".jar", "craftbukkit-" + mcVersion + ".jar"}) {
            final Path local = cacheDir.resolve(name);
            if (Files.isRegularFile(local) && Files.size(local) > 1_000_000) return local;
        }
        return paper(mcVersion, cacheDir);
    }

    /**
     * A Paper JAR for {@code mcVersion}, downloaded on first use.
     *
     * <p>Paper publishes 1.8.8 upward. 1.8.0 is not available from anyone and has to be
     * built with Spigot's BuildTools, which is handled separately.
     */
    static Path paper(String mcVersion, Path cacheDir) throws Exception {
        Files.createDirectories(cacheDir);

        final String listing = get(FILL + mcVersion + "/builds");
        // Deliberately not a JSON library: this module has one dependency, JUnit, and
        // the two fields needed are unambiguous in the response.
        final Matcher url = Pattern.compile(
                "\"url\"\\s*:\\s*\"(https://[^\"]+/(paper-[^\"/]+\\.jar))\"").matcher(listing);
        if (!url.find()) {
            throw new IOException("no Paper build found for " + mcVersion
                    + ". Response began: " + listing.substring(0, Math.min(200, listing.length())));
        }
        final String jarUrl = url.group(1);
        // The cache filename is derived from a hash of the URL, not from the response.
        //
        // Validating the name out of the response was the first attempt and was still
        // wrong: it left a remote value flowing into a path, which is the thing to
        // avoid rather than something to sanitise, and this downloads a JAR that is
        // then executed. A hex digest cannot escape a directory whatever the server
        // says, and a changed build produces a different name on its own.
        final String name = "paper-" + sha256Hex(jarUrl.getBytes(StandardCharsets.UTF_8))
                .substring(0, 24) + ".jar";

        final Matcher sha = Pattern.compile("\"sha256\"\\s*:\\s*\"([0-9a-f]{64})\"").matcher(listing);
        final String expected = sha.find() ? sha.group(1) : null;

        final Path target = cacheDir.resolve(name);
        if (Files.isRegularFile(target) && matches(target, expected)) return target;

        final Path tmp = Files.createTempFile(cacheDir, "download-", ".part");
        try (InputStream in = new URL(jarUrl).openStream()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        if (!matches(tmp, expected)) {
            Files.deleteIfExists(tmp);
            throw new IOException("checksum mismatch for " + name + "; refusing to run it");
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private static boolean matches(Path file, String expectedSha256) throws Exception {
        if (expectedSha256 == null) return true;   // nothing published to compare against
        return sha256Hex(Files.readAllBytes(file)).equals(expectedSha256);
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        final byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        final StringBuilder hex = new StringBuilder(64);
        for (byte b : digest) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private static String get(String url) throws IOException {
        final HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", "DiscordLogger-acceptance");
        c.setConnectTimeout(30_000);
        c.setReadTimeout(30_000);
        try (InputStream in = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }
}
