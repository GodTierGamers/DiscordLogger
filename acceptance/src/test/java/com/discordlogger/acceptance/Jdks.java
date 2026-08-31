package com.discordlogger.acceptance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Finds a JDK old enough to run a given Minecraft version.
 *
 * <p>Minecraft is strict about this in a way that is easy to forget: 1.8 and 1.12
 * need Java 8, 1.16.5 will not start on anything past 16, and 26.x needs 21 or newer.
 * One JDK cannot serve the range, so each server is launched with its own.
 *
 * <p>On Actions, {@code setup-java} exports JAVA_HOME_8_X64 and friends, which is the
 * documented way to have several installed at once. Locally macOS has java_home. If
 * neither turns one up the test is skipped rather than failed: a missing JDK 8 on a
 * developer's laptop is not a defect in the plugin, and failing would train people to
 * ignore a red suite.
 */
final class Jdks {

    private Jdks() {}

    /** The Java feature version a given Minecraft version needs. */
    static int javaFor(String mcVersion) {
        if (mcVersion.startsWith("1.8") || mcVersion.startsWith("1.9")
                || mcVersion.startsWith("1.10") || mcVersion.startsWith("1.11")
                || mcVersion.startsWith("1.12")) return 8;
        if (mcVersion.startsWith("1.13") || mcVersion.startsWith("1.14")
                || mcVersion.startsWith("1.15") || mcVersion.startsWith("1.16")) return 16;
        if (mcVersion.startsWith("1.17") || mcVersion.startsWith("1.18")) return 17;
        if (mcVersion.startsWith("1.19") || mcVersion.startsWith("1.20")) return 21;
        return 25;
    }

    /** The java binary for that version, or null when this machine has no such JDK. */
    static Path javaBinary(int feature) {
        // 1. Actions: setup-java exports one of these per installed JDK.
        for (String var : new String[]{
                "JAVA_HOME_" + feature + "_X64", "JAVA_HOME_" + feature + "_ARM64",
                "JAVA_HOME_" + feature + "_x64"}) {
            final String home = System.getenv(var);
            if (home != null && !home.isEmpty()) {
                final Path bin = Path.of(home, "bin", "java");
                if (Files.isExecutable(bin)) return bin;
            }
        }

        // 2. macOS keeps a registry of installed JDKs.
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            try {
                final Process p = new ProcessBuilder("/usr/libexec/java_home", "-v",
                        String.valueOf(feature)).redirectErrorStream(true).start();
                final String out = new String(p.getInputStream().readAllBytes()).trim();
                if (p.waitFor() == 0 && !out.isEmpty()) {
                    final Path bin = Path.of(out, "bin", "java");
                    if (Files.isExecutable(bin)) return bin;
                }
            } catch (IOException | InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        // 3. The JDK running this suite, if it happens to be the right one.
        if (Runtime.version().feature() == feature) {
            return Path.of(System.getProperty("java.home"), "bin", "java");
        }
        return null;
    }
}
