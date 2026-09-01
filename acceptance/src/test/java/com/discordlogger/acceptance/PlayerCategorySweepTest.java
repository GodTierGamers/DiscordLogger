package com.discordlogger.acceptance;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Every setting under {@code log.player.*}, driven on a real server.
 *
 * <p>The name ends in Test because Surefire only collects classes matching {@code *Test},
 * {@code Test*}, {@code *Tests} or {@code *TestCase}. Named PlayerCategorySweep it was
 * never collected, never skipped and never reported -- it simply did not run, on any
 * version, while the suite went green. A test that silently does nothing is worse than
 * one that fails.
 *
 * <h2>One server, many cases</h2>
 *
 * <p>Booting per case would cost forty seconds each and buy nothing: the plugin reloads
 * its config in place, which is what an admin does anyway. So the server comes up once
 * and each case edits a setting, reloads, fires the event it belongs to, and reads what
 * arrived.
 *
 * <p>Cases are independent despite the shared server. Every one sets the value it cares
 * about rather than assuming the previous case left things alone, because a suite whose
 * results depend on order is a suite that reports the wrong key when something breaks.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class PlayerCategorySweepTest {

    /** An event the driver can fire, and how to recognise what it produces. */
    private record Category(String name, String driverCommand, String marker) {}

    private static final List<Category> CATEGORIES = List.of(
            new Category("join",        "join",        "Join"),
            new Category("quit",        "quit",        "Quit"),
            new Category("chat",        "chat hello",  "Chat"),
            new Category("gamemode",    "gamemode",    "Gamemode"),
            new Category("teleport",    "teleport",    "Teleport"),
            new Category("death",       "death VOID",  "Death"),
            // "Unlocked" rather than "Advancement": servers before 1.12 have no
            // advancements, and the plugin correctly reports an achievement there under
            // the same setting. Matching the newer word made 1.8.8 report the category
            // as sending nothing when it had sent exactly what it should.
            new Category("advancement", "advancement adventure", "Unlocked"));

    private static FakeDiscord discord;
    private static MinecraftServer server;
    private static Path work;
    private static final List<Grader.Result> RESULTS = new ArrayList<>();
    /** False where this server build cannot host a fake player at all. */
    private static boolean playerEventsSupported = true;

    @BeforeAll
    void bootOnce() throws Exception {
        final Path jar = Sweeps.shippedJar();
        assumeTrue(jar != null, "no plugin JAR in ../target");
        final Path driver = Sweeps.driverJar();
        assumeTrue(driver != null, "driver not built");

        final String mc = Sweeps.version();
        assumeTrue(Jdks.javaBinary(Jdks.javaFor(mc)) != null,
                "no JDK for " + mc + " on this machine");

        work = Files.createTempDirectory("dl-sweep-player");
        discord = FakeDiscord.start(work);
        server = MinecraftServer.boot(work.resolve("server"),
                ServerJars.forVersion(mc, Sweeps.cache()), jar, driver,
                Jdks.javaFor(mc), discord.jvmArgs());
        server.awaitStartup(4, TimeUnit.MINUTES);

        // Without this the whole sweep reports "sent nothing" for every case, which
        // reads as a plugin that logs nothing rather than a driver that never loaded.
        // That is how 1.8.8 produced fourteen identical failures naming the wrong thing.
        if (!server.awaitLine("Acceptance driver ready", 30, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    "the acceptance driver did not load, so nothing can be fired on "
                            + Sweeps.version() + Sweeps.serverContext(server));
        }
        Sweeps.prepare(server, discord);

        // One check that the console is usable before running twenty-eight cases
        // through it. Without it, a console that stops listening is reported as
        // twenty-eight plugin faults.
        if (!Sweeps.stillResponding(server)) {
            throw new IllegalStateException("the server stopped accepting console commands on "
                    + Sweeps.version() + ", so nothing can be driven"
                    + Sweeps.serverContext(server));
        }

        // Asked once, up front. Every 1.8.x build carries a Bukkit declaring getHealth()
        // twice with different return types, which java.lang.reflect.Proxy refuses to
        // implement, so no fake player can exist there. Without this the sweep runs all
        // twenty-eight cases against a driver that cannot fire and reports fourteen
        // settings as broken, naming the plugin for a limitation of the harness.
        server.command("dldriver join");
        playerEventsSupported = !server.awaitLine("DRIVER-UNSUPPORTED", 20, TimeUnit.SECONDS);
        Sweeps.quiesce(discord, 1500);
    }

    @AfterAll
    void reportAndStop() throws Exception {
        Sweeps.report("log.player.*", RESULTS);
        if (server != null) server.close();
        if (discord != null) discord.close();
    }

    static Stream<Category> categories() { return CATEGORIES.stream(); }

    @ParameterizedTest(name = "log.player.{0}.enabled = true posts an embed")
    @MethodSource("categories")
    @DisplayName("enabled")
    void enabledPosts(Category c) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(playerEventsSupported,
                "this server build cannot host a fake player, so player events cannot be "
                        + "driven on " + Sweeps.version());
        final String key = "log.player." + c.name() + ".enabled";
        server.editConfig(key, "true");
        Sweeps.reload(server);
        Sweeps.quiesce(discord, 2000);
        server.command("dldriver " + c.driverCommand());

        final String captured = Sweeps.captureMatching(discord, c.marker(), 30);
        RESULTS.add(Grader.grade(
                new Grader.Expectation(key, true, null).requiring("embeds"),
                captured, Sweeps.errorsSince(server), Sweeps.serverContext(server)));
    }

    @ParameterizedTest(name = "log.player.{0}.enabled = false posts nothing")
    @MethodSource("categories")
    @DisplayName("disabled")
    void disabledIsSilent(Category c) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(playerEventsSupported,
                "this server build cannot host a fake player, so player events cannot be "
                        + "driven on " + Sweeps.version());
        final String key = "log.player." + c.name() + ".enabled";
        server.editConfig(key, "false");
        Sweeps.reload(server);
        Sweeps.quiesce(discord, 2000);
        server.command("dldriver " + c.driverCommand());

        // A shorter window than the enabled case on purpose: this waits to prove absence,
        // and every second of it is spent on every category.
        final String captured = Sweeps.captureMatching(discord, c.marker(), 8);
        RESULTS.add(Grader.grade(
                new Grader.Expectation(key, false, null), captured, Sweeps.errorsSince(server)));

        server.editConfig(key, "true");   // leave it as shipped for the next case
    }

    @ParameterizedTest(name = "log.player.{0}.color reaches the embed")
    @MethodSource("categories")
    @DisplayName("colour")
    void colourIsApplied(Category c) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(playerEventsSupported,
                "this server build cannot host a fake player, so player events cannot be "
                        + "driven on " + Sweeps.version());
        // A colour no shipped default uses, so seeing it proves the key was read rather
        // than coinciding with what the plugin would have sent anyway.
        final String key = "log.player." + c.name() + ".color";
        server.editConfig("log.player." + c.name() + ".enabled", "true");
        final String hex = "AB12CD";
        server.editConfig(key, "\"#" + hex + "\"");
        Sweeps.reload(server);
        Sweeps.quiesce(discord, 2000);
        server.command("dldriver " + c.driverCommand());

        final String captured = Sweeps.captureMatching(discord, c.marker(), 30);
        RESULTS.add(Grader.grade(
                new Grader.Expectation(key, true, null)
                        // Converted rather than written out. The first version of this
                        // hardcoded the decimal and got it wrong by 512, so six
                        // categories reported WRONG against a number that was never
                        // right. A test asserting a miscalculation is worse than none.
                        .requiring("\"color\":" + Integer.parseInt(hex, 16)),
                captured, Sweeps.errorsSince(server), Sweeps.serverContext(server)));
    }

    @ParameterizedTest(name = "log.player.{0}.webhook routes elsewhere")
    @MethodSource("categories")
    @DisplayName("routing")
    void webhookRoutes(Category c) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(playerEventsSupported,
                "this server build cannot host a fake player, so player events cannot be "
                        + "driven on " + Sweeps.version());
        final String key = "log.player." + c.name() + ".webhook";
        server.editConfig("log.player." + c.name() + ".enabled", "true");
        server.editConfig(key, "\"" + discord.alternateWebhookUrl() + "\"");
        Sweeps.reload(server);
        Sweeps.quiesce(discord, 2000);
        server.command("dldriver " + c.driverCommand());

        // Matched on this category, not on "any embed". Taking whatever arrived first
        // is what let a leftover post from the previous case be read as this one's.
        final FakeDiscord.Recorded post = discord.awaitPostMatching(
                r -> r.bodyContains(c.marker()), 30, TimeUnit.SECONDS);
        // Routing is visible in the path: a different webhook id is a different URL.
        final boolean routed = post.path.contains(FakeDiscord.ALTERNATE_ID);
        RESULTS.add(new Grader.Result(key,
                routed ? Verdict.PASS : Verdict.WRONG,
                routed ? "routed to its own webhook"
                       : "went to the default webhook instead: " + post.path,
                post.body));

        server.editConfig(key, "\"\"");
    }

    @AfterAll
    void noneWereWrong() {
        final List<String> wrong = new ArrayList<>();
        for (Grader.Result r : RESULTS) if (r.verdict.fails()) wrong.add(r.toString());
        assertFalse(!wrong.isEmpty(), "settings behaved incorrectly:\n  "
                + String.join("\n  ", wrong));
    }
}
