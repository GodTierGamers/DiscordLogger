package com.discordlogger.update;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Reads the build channel baked into the JAR at package time (build-info.properties,
 * Maven-filtered from -Ddl.build.channel=stable|nightly). Deliberately NOT derived
 * from the version string -- a version string is just text an operator can rename;
 * the channel here can only change by re-packaging the JAR.
 */
public final class BuildInfo {
    private static volatile String channel = "dev";
    private static volatile String version = "";
    private static volatile String built = "unknown";

    private BuildInfo() {}

    public static void load(JavaPlugin plugin) {
        Properties p = new Properties();
        try (InputStream in = plugin.getResource("build-info.properties")) {
            if (in != null) {
                p.load(in);
                channel = p.getProperty("channel", "dev");
                version = p.getProperty("version", "");
                built = p.getProperty("built", "unknown");
            }
        } catch (IOException ignored) {
            // Missing/corrupt build-info.properties -> stay on "dev" defaults.
        }
    }

    public static boolean isNightly() { return "nightly".equals(channel); }
    public static boolean isStable()  { return "stable".equals(channel); }
    public static boolean isDev()     { return "dev".equals(channel); }

    public static String channel() { return channel; }
    public static String version() { return version; }
    public static String built()   { return built; }
}
