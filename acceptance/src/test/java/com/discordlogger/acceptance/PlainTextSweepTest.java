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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Every event again, with embeds switched off.
 *
 * <h2>Why bother with a mode nobody uses</h2>
 *
 * <p>Plain text is the other half of the plugin's output and it ships enabled-able: any
 * admin can set {@code embeds.enabled: false} and get it. It is also the half nobody
 * looks at, which is precisely why it is worth a sweep -- a placeholder that stopped
 * resolving or a MiniMessage tag arriving literally would sit there for releases
 * without anyone noticing.
 *
 * <p>This does not re-prove the toggles; the embed sweeps already do that, and repeating
 * them here would double a long run to learn nothing. What it checks is what the other
 * sweeps cannot: that the plain-text form of each event is actually built, is actually
 * plain text, and is clean -- no {@code {placeholder}} left unresolved, no
 * {@code <colour>} tag Discord would print literally, no raw enum name where a sentence
 * was meant, no literal "null".
 *
 * <p>Those checks are the grader's, so a fault found here reads the same as one found
 * anywhere else in the suite.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlainTextSweepTest {

    /**
     * @param what   the event, for the report
     * @param drive  the console command producing it
     * @param expect text the plain-text form must contain
     */
    private record Case(String what, String drive, String expect, boolean needsFake) {}

    private static final List<Case> CASES = List.of(
            new Case("player join",     "dldriver join",          "joined the server", true),
            new Case("player quit",     "dldriver quit",           "left the server",  true),
            new Case("player chat",     "dldriver chat hello",     "hello",            true),
            new Case("player command",  "dldriver command /acceptance", "acceptance",  true),
            new Case("player death",    "dldriver death VOID",     "Player1",          true),
            new Case("player teleport", "dldriver teleport",       "Player1",          true),
            new Case("player gamemode", "dldriver gamemode",       "Player1",          true),
            new Case("server command",  "say acceptance probe",    "acceptance probe", false),
            new Case("explosion",       "dldriver explosion CREEPER", "Player1",       true),
            new Case("moderation op",   "op Player1",              "Player1",          false));

    private static FakeDiscord discord;
    private static MinecraftServer server;
    private static Path work;
    private static final List<Grader.Result> RESULTS = new ArrayList<>();
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

        work = Files.createTempDirectory("dl-sweep-plain");
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

        // The whole point of this class. Set once and left alone: every case below runs
        // against a plugin configured the way an admin who prefers plain text runs it.
        server.editConfig("embeds.enabled", "false");
        // Deopped first so the moderation case has a state change to make.
        server.command("deop Player1");
        Sweeps.reload(server);

        server.command("dldriver join");
        playerEventsSupported = !server.awaitLine("DRIVER-UNSUPPORTED", 20, TimeUnit.SECONDS);
        Sweeps.quiesce(discord, 1500);
    }

    @AfterAll
    void reportAndStop() throws Exception {
        Sweeps.report("plain text (embeds off)", RESULTS);
        if (server != null) server.close();
        if (discord != null) discord.close();
    }

    static Stream<Case> cases() { return CASES.stream(); }

    @ParameterizedTest(name = "{0} reads correctly as plain text")
    @MethodSource("cases")
    @DisplayName("plain text")
    void readsCleanly(Case c) throws Exception {
        Assumptions.assumeTrue(!c.needsFake() || playerEventsSupported,
                "this server build cannot host a fake player, so " + c.what()
                        + " cannot be driven on " + Sweeps.version());

        Sweeps.quiesce(discord, 2000);
        server.command(c.drive());
        final String captured = Sweeps.captureMatching(discord, c.expect(), 30);

        final String key = "plain-text." + c.what().replace(' ', '-');
        if (captured == null) {
            RESULTS.add(new Grader.Result(key, Verdict.WRONG,
                    "nothing arrived with embeds off, though the same event posts with "
                            + "them on" + Sweeps.serverContext(server), null));
            return;
        }
        if (captured.contains("\"embeds\"")) {
            RESULTS.add(new Grader.Result(key, Verdict.WRONG,
                    "sent an embed even though embeds.enabled is false", captured));
            return;
        }

        // The grader's own rules from here: an unresolved placeholder or a stray tag is
        // wrong in plain text for exactly the reasons it is wrong in an embed.
        RESULTS.add(Grader.grade(
                new Grader.Expectation(key, true, null).requiring("content", c.expect()),
                captured, Sweeps.errorsSince(server), Sweeps.serverContext(server)));
    }

    @AfterAll
    void noneWereWrong() {
        final List<String> wrong = new ArrayList<>();
        for (Grader.Result r : RESULTS) if (r.verdict.fails()) wrong.add(r.toString());
        assertFalse(!wrong.isEmpty(), "plain text did not hold up:\n  "
                + String.join("\n  ", wrong));
    }
}
