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

    /** Passing embeds pictured per version, sampled at random from what it exercised. */
    private static final int SCREENSHOTS_PER_VERSION = 5;

    /** A ceiling on pictures of failures, so one broken run cannot flood the store. */
    private static final int MAX_PROBLEM_SCREENSHOTS = 40;

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

        for (Grader.Result r : results) {
            if (r.payload != null) keepPayload(dir, r);
        }
        renderSample(dir);

        System.out.printf("%n  %s on %s: %s%n", category, version(), tally);
        System.out.printf("  results -> %s%n", dir.resolve("results.csv"));
    }

    /** Every payload this version produced, kept so the sample can be drawn from all of it. */
    private static void keepPayload(Path dir, Grader.Result r) throws IOException {
        final Path store = dir.resolve("payloads");
        Files.createDirectories(store);
        Files.writeString(store.resolve(r.key.replace('.', '_') + "__" + r.verdict + ".json"),
                r.payload, StandardCharsets.UTF_8);
    }

    /**
     * Draws the version's screenshots: a sample of what passed, and all of what did not.
     *
     * <p>The two halves are chosen differently on purpose. A passing case is
     * illustrative -- five at random show what the plugin looks like when it is working,
     * and the sixth would say nothing the fifth did not. Anything that did not pass is
     * evidence, and evidence is not something to sample: a picture of the embed that
     * came out wrong is the fastest way to see what went wrong with it.
     *
     * <p>Re-chosen from scratch each time rather than topped up. Six sweep classes
     * report separately, and a budget spent first-come-first-served would hand all five
     * passes to whichever ran first, so every version would illustrate one category and
     * nothing else -- which is not a sample of anything. Drawing from the payloads on
     * disk means the last class to report leaves a genuine spread across the whole run.
     *
     * <p>A result whose payload is null has no picture, and cannot have one: those are
     * the cases where nothing arrived at all, and there is nothing to draw. The CSV row
     * is the record for those.
     */
    private static void renderSample(Path dir) throws IOException {
        final Path store = dir.resolve("payloads");
        if (!Files.isDirectory(store)) return;

        final List<Path> passed = new ArrayList<>();
        final List<Path> notPassed = new ArrayList<>();
        try (var files = Files.list(store)) {
            for (Path f : files.toList()) {
                if (!f.getFileName().toString().endsWith(".json")) continue;
                (verdictOf(f).equals(Verdict.PASS.name()) ? passed : notPassed).add(f);
            }
        }
        java.util.Collections.shuffle(passed, SAMPLE);

        final List<Path> chosen = new ArrayList<>(
                passed.subList(0, Math.min(SCREENSHOTS_PER_VERSION, passed.size())));
        // Worst case is every setting failing at once, which would put several hundred
        // images into a branch that keeps them forever. A ceiling that high is never
        // reached by a run worth looking at, and stops a broken run filling the store.
        if (notPassed.size() > MAX_PROBLEM_SCREENSHOTS) {
            System.out.printf("  %d results did not pass; drawing the first %d%n",
                    notPassed.size(), MAX_PROBLEM_SCREENSHOTS);
            chosen.addAll(notPassed.subList(0, MAX_PROBLEM_SCREENSHOTS));
        } else {
            chosen.addAll(notPassed);
        }

        // Anything drawn for a previous selection is no longer part of the sample.
        try (var stale = Files.list(dir)) {
            for (Path p : stale.toList()) {
                if (p.getFileName().toString().startsWith("sample-")) Files.deleteIfExists(p);
            }
        }

        for (Path payload : chosen) {
            final String key = keyOf(payload);
            final String verdict = verdictOf(payload);
            try {
                EmbedImage.render(Files.readString(payload, StandardCharsets.UTF_8),
                        // The verdict is in the filename as well as the caption, so the
                        // ones worth opening are obvious in a directory listing.
                        dir.resolve("sample-" + verdict + "-" + key + ".png"),
                        key.replace('_', '.') + "   [" + version() + "]   " + verdict);
            } catch (Exception notRenderable) {
                // A payload that will not draw is not a reason to fail a sweep.
            }
        }
    }

    private static String keyOf(Path payload) {
        final String name = payload.getFileName().toString().replace(".json", "");
        return name.contains("__") ? name.substring(0, name.indexOf("__")) : name;
    }

    private static String verdictOf(Path payload) {
        final String name = payload.getFileName().toString().replace(".json", "");
        return name.contains("__") ? name.substring(name.indexOf("__") + 2) : "";
    }
}
