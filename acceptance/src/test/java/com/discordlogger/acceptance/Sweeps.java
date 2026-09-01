package com.discordlogger.acceptance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/** Shared plumbing for the per-category sweeps. */
final class Sweeps {

    /** Screenshots kept per version, sampled from whatever that version exercised. */
    private static final int SCREENSHOTS_PER_VERSION = 5;

    private static final Random SAMPLE = new Random();
    private static int logHighWater = 0;

    private Sweeps() {}

    static String version() {
        return System.getProperty("dl.acceptance.version", "26.2");
    }

    static Path cache() {
        return Path.of(System.getProperty("dl.acceptance.cache",
                System.getProperty("user.home") + "/.cache/dl-acceptance"));
    }

    /** Where results and screenshots are written for metrics-data to collect. */
    static Path resultsDir() throws IOException {
        final Path dir = Path.of(System.getProperty("dl.acceptance.results", "target/acceptance"))
                .resolve(version());
        Files.createDirectories(dir);
        return dir;
    }

    static Path shippedJar() throws IOException {
        final Path target = Path.of("..", "target");
        if (!Files.isDirectory(target)) return null;
        try (var files = Files.list(target)) {
            return files.filter(p -> p.getFileName().toString().startsWith("discordlogger-"))
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .filter(p -> !p.getFileName().toString().startsWith("original-"))
                    .findFirst().orElse(null);
        }
    }

    static Path driverJar() {
        final Path p = Path.of("driver", "target", "DiscordLoggerDriver.jar");
        return Files.isRegularFile(p) ? p : null;
    }

    /** Points the plugin at the fake and keeps the driver's own commands out of the way. */
    static void prepare(MinecraftServer server, FakeDiscord discord) throws Exception {
        server.command("discordlogger webhook " + discord.webhookUrl());
        Thread.sleep(3000);
        // The plugin logs console commands, correctly, so the driver's own would arrive
        // alongside every event under test. Ignoring them uses a filter the plugin
        // already has; that filter is exercised where it is the subject, not here.
        server.editConfig("filters.ignored_commands", "[\"dldriver\", \"discordlogger\"]");
        reload(server);
        discord.reset();
    }

    /**
     * Waits until nothing has arrived for a moment, then clears what did.
     *
     * <p>Without this the cases race each other. The plugin queues sends and drains
     * them on its own thread, so a post from the previous case can arrive after
     * reset() and be read as the result of the current one. That is how the routing
     * check reported "went to the default webhook" in CI and "no post at all" locally
     * on the same commit: it was picking up whatever happened to be in flight.
     *
     * <p>Two runs disagreeing about which case failed is the signature of a race, and
     * it is worth naming rather than retrying until it passes.
     */
    static void quiesce(FakeDiscord discord, long quietMillis) throws InterruptedException {
        long lastSeen = System.currentTimeMillis();
        while (System.currentTimeMillis() - lastSeen < quietMillis) {
            final int before = discord.all().size();
            Thread.sleep(200);
            if (discord.all().size() != before) lastSeen = System.currentTimeMillis();
        }
        discord.reset();
    }

    static void reload(MinecraftServer server) throws Exception {
        server.command("discordlogger reload");
        Thread.sleep(1200);
    }

    /** The next embed naming {@code marker}, or null when none arrives in time. */
    /**
     * Whether the server is still acknowledging console commands.
     *
     * <p>Checked because a server that has stopped listening produces exactly the same
     * result as a plugin that has stopped sending, and the suite spent a run blaming
     * the second for the first.
     */
    static boolean stillResponding(MinecraftServer server) throws Exception {
        final int before = server.log().size();
        server.command("discordlogger status");
        for (int i = 0; i < 40 && server.log().size() == before; i++) Thread.sleep(250);
        return server.log().size() > before;
    }

    static String captureMatching(FakeDiscord discord, String marker, int seconds)
            throws InterruptedException {
        try {
            return discord.awaitPostMatching(r -> r.bodyContains(marker),
                    seconds, TimeUnit.SECONDS).body;
        } catch (AssertionError nothingArrived) {
            return null;
        }
    }

    /** The next embed, or null when nothing arrives in time. */
    static String captureOne(FakeDiscord discord, int seconds) throws InterruptedException {
        try {
            return discord.awaitPostMatching(r -> r.bodyContains("embeds")
                    || r.bodyContains("content"), seconds, TimeUnit.SECONDS).body;
        } catch (AssertionError nothingArrived) {
            return null;
        }
    }

    /** Server-side errors logged since the previous case, so one failure is not blamed twice. */
    static List<String> errorsSince(MinecraftServer server) {
        final List<String> log = server.log();
        final List<String> errors = new ArrayList<>();
        for (int i = logHighWater; i < log.size(); i++) {
            final String line = log.get(i);
            // The plugin's own warnings are not faults; a stack trace is.
            if (line.contains("Exception") || line.contains("Error:")
                    || line.contains("NoClassDefFound") || line.contains("NoSuchMethod")) {
                errors.add(line.trim());
            }
        }
        logHighWater = log.size();
        return errors;
    }

    /**
     * Writes the results, and samples a few embeds as pictures.
     *
     * <p>Rows are per event rather than per key: a key with thirty passing causes and one
     * that drifted should not report as a single verdict, because the interesting part is
     * which cause moved. The summary rolls them up for reading.
     */
    /**
     * The server's own last words, for a case that failed.
     *
     * <p>A sweep result saying "sent nothing" is useless on its own: it cannot
     * distinguish a plugin that declined to log from a driver that never fired. That
     * is exactly where 1.8.8 stalled -- fourteen cases reported nothing sent, and the
     * CI log carried no server output to say why.
     */
    static String serverContext(MinecraftServer server) {
        return "\n  --- last server output ---\n" + server.tail(25);
    }

    static void report(String category, List<Grader.Result> results) throws Exception {
        if (results.isEmpty()) return;
        final Path dir = resultsDir();

        final StringBuilder csv = new StringBuilder();
        if (!Files.exists(dir.resolve("results.csv"))) {
            csv.append("run_at,version,category,key,verdict,detail\n");
        }
        final Map<Verdict, Integer> tally = new LinkedHashMap<>();
        for (Grader.Result r : results) {
            tally.merge(r.verdict, 1, Integer::sum);
            csv.append(Instant.now()).append(',')
               .append(version()).append(',')
               .append(category).append(',')
               .append(r.key).append(',')
               .append(r.verdict).append(',')
               .append('"').append(r.detail.replace("\"", "'")).append('"')
               .append('\n');
        }
        Files.writeString(dir.resolve("results.csv"), csv.toString(), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);

        // Pictures of a handful, chosen at random so successive runs show different ones
        // rather than the same five forever.
        final List<Grader.Result> withPayload = new ArrayList<>();
        for (Grader.Result r : results) if (r.payload != null) withPayload.add(r);
        java.util.Collections.shuffle(withPayload, SAMPLE);
        int taken = 0;
        for (Grader.Result r : withPayload) {
            if (taken >= SCREENSHOTS_PER_VERSION) break;
            final Path png = dir.resolve("sample-" + r.key.replace('.', '_') + ".png");
            if (Files.exists(png)) continue;
            try {
                EmbedImage.render(r.payload, png,
                        r.key + " = as shipped   [" + version() + "]   " + r.verdict);
                taken++;
            } catch (Exception notRenderable) {
                // A payload that will not draw is not a reason to fail a sweep.
            }
        }

        System.out.printf("%n  %s on %s: %s%n", category, version(), tally);
        System.out.printf("  results -> %s%n", dir.resolve("results.csv"));
    }
}
