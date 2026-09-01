package com.discordlogger.acceptance;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Every line of lang.yml that reaches Discord, edited one at a time.
 *
 * <h2>Why edit them rather than read them</h2>
 *
 * <p>lang.yml exists to be rewritten: it is the file an admin translates, rewords, or
 * fills with their server's voice. A suite that only checked the shipped wording would
 * pass on a plugin that had stopped reading the file at all, because the shipped
 * wording is also what the Java fallbacks say.
 *
 * <p>So each line is replaced with a value nothing else could produce, and the case
 * passes only when that value comes out the other end. That proves the line is read,
 * proves it is the line used for that event, and proves the placeholders in it still
 * resolve -- the sentinel keeps whatever placeholders the shipped line had, so a
 * {@code {player}} that stopped resolving arrives as literal text and the grader
 * catches it.
 *
 * <p>One line at a time, and a different sentinel per line. Editing several at once
 * would mean a message could satisfy the wrong case, and a shared sentinel would let a
 * post still in flight from the previous line be read as this one's.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LangSweepTest {

    /**
     * @param key    the lang.yml key, dotted
     * @param setup  console commands to run first, or empty
     * @param drive  the console command producing the message
     */
    private record Line(String key, List<String> setup, String drive) {
        Line(String key, String drive) { this(key, List.of(), drive); }
    }

    /** Placeholders are kept so the sentinel proves they still resolve. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[a-z_]+}");

    private static final List<Line> LINES = new ArrayList<>();
    static {
        LINES.add(new Line("discord.player-join", "dldriver join"));
        LINES.add(new Line("discord.player-quit", "dldriver quit"));
        LINES.add(new Line("discord.player-chat", "dldriver chat hello"));
        LINES.add(new Line("discord.player-command", "dldriver command /acceptance"));
        LINES.add(new Line("discord.server-command", "say acceptance probe"));

        // The embed's own parts. Any death produces the description and the cause
        // field; the coordinate pair needs the setting that adds them.
        LINES.add(new Line("discord.death.description", "dldriver death VOID"));
        LINES.add(new Line("discord.death.cause-field", "dldriver death VOID"));
        LINES.add(new Line("discord.death.coords-field",
                List.of("CONFIG log.player.death.show_coords=true"), "dldriver death VOID"));
        LINES.add(new Line("discord.death.coords-value",
                List.of("CONFIG log.player.death.show_coords=true"), "dldriver death VOID"));

        // The four ways a death can name who did it, each reached through a different
        // shape of damage rather than a different cause.
        LINES.add(new Line("discord.death.slain-by-player",
                List.of("dldriver fake killer Killer"), "dldriver death ENTITY_ATTACK"));
        LINES.add(new Line("discord.death.slain-by-mob",
                "dldriver death ENTITY_ATTACK by-mob"));
        LINES.add(new Line("discord.death.shot-by",
                "dldriver death PROJECTILE shot-by-player"));
        LINES.add(new Line("discord.death.shot", "dldriver death PROJECTILE shot"));
        // Reached only when there is no damage at all, which is the one way to ask for
        // the fallback deliberately instead of arriving at it by accident.
        LINES.add(new Line("discord.death.unknown", "dldriver death VOID none"));
    }

    /**
     * One line per damage cause, derived rather than listed.
     *
     * <p>The plugin builds the key as {@code discord.death.causes.} plus the enum name
     * lowercased with underscores turned into hyphens, so the mapping is computed the
     * same way here. Writing the pairs out by hand would make this a second place to
     * keep in step with Minecraft, and the two would drift.
     */
    private static List<Line> causeLines(String langYml) {
        final List<Line> out = new ArrayList<>();
        for (String key : keysUnder(langYml, "discord.death.causes.")) {
            final String cause = key.substring(key.lastIndexOf('.') + 1)
                    .toUpperCase(java.util.Locale.ROOT).replace('-', '_');
            out.add(new Line(key, "dldriver death " + cause));
        }
        return out;
    }

    private static FakeDiscord discord;
    private static MinecraftServer server;
    private static Path work;
    private static final List<Grader.Result> RESULTS = new ArrayList<>();
    private static boolean playerEventsSupported = true;
    private static String stockLang;
    private static Map<String, String> shipped = new LinkedHashMap<>();

    @BeforeAll
    void bootOnce() throws Exception {
        final Path jar = Sweeps.shippedJar();
        assumeTrue(jar != null, "no plugin JAR in ../target");
        final Path driver = Sweeps.driverJar();
        assumeTrue(driver != null, "driver not built");

        final String mc = Sweeps.version();
        assumeTrue(Jdks.javaBinary(Jdks.javaFor(mc)) != null,
                "no JDK for " + mc + " on this machine");

        work = Files.createTempDirectory("dl-sweep-lang");
        discord = FakeDiscord.start(work);
        server = MinecraftServer.boot(work.resolve("server"),
                ServerJars.forVersion(mc, Sweeps.cache()), jar, driver,
                Jdks.javaFor(mc), discord.jvmArgs());
        server.awaitStartup(4, TimeUnit.MINUTES);

        if (!server.awaitLine("Acceptance driver ready", 30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("the acceptance driver did not load on "
                    + Sweeps.version() + Sweeps.serverContext(server));
        }
        Sweeps.prepare(server, discord);
        if (!Sweeps.stillResponding(server)) {
            throw new IllegalStateException("the server stopped accepting console commands on "
                    + Sweeps.version() + Sweeps.serverContext(server));
        }

        stockLang = server.stockLang();
        shipped = flatten(stockLang);

        server.command("dldriver join");
        playerEventsSupported = !server.awaitLine("DRIVER-UNSUPPORTED", 20, TimeUnit.SECONDS);
        Sweeps.quiesce(discord, 1500);
    }

    @AfterAll
    void reportAndStop() throws Exception {
        Sweeps.report("lang.yml discord.*", RESULTS);
        if (server != null) server.close();
        if (discord != null) discord.close();
    }

    static Stream<Line> lines() throws Exception {
        final List<Line> all = new ArrayList<>(LINES);
        // Read from the shipped file rather than a list here, so a cause added to
        // lang.yml is swept without this class being edited.
        all.addAll(causeLines(Files.readString(
                Path.of("..", "src", "main", "resources", "lang.yml"))));
        return all.stream();
    }

    @ParameterizedTest(name = "{0} reaches Discord when it is rewritten")
    @MethodSource("lines")
    @DisplayName("lang line")
    void lineIsRead(Line line) throws Exception {
        Assumptions.assumeTrue(playerEventsSupported,
                "this server build cannot host a fake player, so lang lines carried by "
                        + "player events cannot be driven on " + Sweeps.version());

        final String original = shipped.get(line.key());
        Assumptions.assumeTrue(original != null,
                line.key() + " is not in this build's lang.yml");

        final String sentinel = "DLACC" + Math.abs(line.key().hashCode() % 100000);
        server.editLang(line.key(), "\"" + sentinel + placeholdersOf(original)
                + " " + sentinel + "\"");

        for (String s : line.setup()) {
            if (s.startsWith("CONFIG ")) {
                final String[] kv = s.substring("CONFIG ".length()).split("=", 2);
                server.editConfig(kv[0], kv[1]);
            } else {
                server.command(s);
                Thread.sleep(600);
            }
        }
        Sweeps.reload(server);
        Sweeps.quiesce(discord, 2000);
        server.command(line.drive());

        final String captured = Sweeps.captureMatching(discord, sentinel, 25);

        try {
            if (captured == null && drivenNothing()) {
                // A cause this version does not have is not a missing line. FREEZE
                // arrived in 1.17 and SONIC_BOOM in 1.19; asking 1.8 for either is a
                // question about Minecraft, not about the plugin.
                RESULTS.add(new Grader.Result(line.key(), Verdict.PROBABLY_FINE,
                        "this server has no such damage cause, so the line cannot be "
                                + "reached here", null));
                return;
            }
            RESULTS.add(Grader.grade(
                    new Grader.Expectation(line.key(), true, null).requiring(sentinel),
                    captured, Sweeps.errorsSince(server), Sweeps.serverContext(server)));
        } finally {
            // Put the line back before the next case, so one failure cannot cascade.
            server.editLang(line.key(), "\"" + original.replace("\"", "'") + "\"");
            for (String s : line.setup()) {
                if (s.startsWith("CONFIG ")) {
                    server.editConfig("log.player.death.show_coords", "false");
                } else if (s.contains("fake")) {
                    server.command("dldriver fake reset");
                }
            }
        }
    }

    /** Whether the driver reported firing nothing, i.e. this version has no such cause. */
    private boolean drivenNothing() {
        final List<String> log = server.log();
        for (int i = log.size() - 1; i >= 0 && i > log.size() - 60; i--) {
            final String l = log.get(i);
            final int at = l.indexOf("DRIVER-COUNT death ");
            if (at >= 0) return l.substring(at + "DRIVER-COUNT death ".length()).trim().equals("0");
        }
        return false;
    }

    /** The placeholders the shipped line used, so the sentinel still proves they resolve. */
    private static String placeholdersOf(String original) {
        final StringBuilder sb = new StringBuilder();
        final Matcher m = PLACEHOLDER.matcher(original);
        while (m.find()) sb.append(' ').append(m.group());
        return sb.toString();
    }

    // -----------------------------------------------------------------------------
    // Reading the shipped file. Deliberately not SnakeYAML: the point is to see the
    // file as text, the way an admin editing it does.
    // -----------------------------------------------------------------------------

    static Map<String, String> flatten(String yaml) {
        final Map<String, String> out = new LinkedHashMap<>();
        final String[] path = new String[8];
        for (String raw : yaml.split("\n", -1)) {
            final String trimmed = raw.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            final int indent = raw.length() - raw.stripLeading().length();
            final int depth = indent / 2;
            if (depth >= path.length) continue;
            final int colon = trimmed.indexOf(':');
            if (colon < 0) continue;
            final String name = trimmed.substring(0, colon).trim();
            String value = trimmed.substring(colon + 1).trim();
            path[depth] = name;
            if (value.isEmpty()) continue;               // a section, not a line
            if (value.length() > 1
                    && ((value.startsWith("\"") && value.endsWith("\""))
                     || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            final StringBuilder key = new StringBuilder();
            for (int d = 0; d <= depth; d++) {
                if (d > 0) key.append('.');
                key.append(path[d]);
            }
            out.put(key.toString(), value);
        }
        return out;
    }

    static List<String> keysUnder(String yaml, String prefix) {
        final List<String> out = new ArrayList<>();
        for (String k : flatten(yaml).keySet()) if (k.startsWith(prefix)) out.add(k);
        return out;
    }

    @AfterAll
    void noneWereWrong() {
        final List<String> wrong = new ArrayList<>();
        for (Grader.Result r : RESULTS) if (r.verdict.fails()) wrong.add(r.toString());
        assertFalse(!wrong.isEmpty(), "lang lines did not reach Discord:\n  "
                + String.join("\n  ", wrong));
    }
}
