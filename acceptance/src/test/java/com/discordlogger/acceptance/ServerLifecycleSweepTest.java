package com.discordlogger.acceptance;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@code log.server.start.*} and {@code log.server.stop.*}, which only a real boot
 * produces.
 *
 * <p>Every other setting can be swept against a running server, because the plugin
 * re-reads its config on reload and the event can be fired again afterwards. These two
 * cannot: the start message is sent once, while the plugin is enabling, and the stop
 * message once, while it is shutting down. Neither can be replayed, so each case needs
 * its own server run.
 *
 * <h2>Four boots, not twelve</h2>
 *
 * <p>The obvious shape is one boot per case, which is twelve. But a single run produces
 * both a start and a stop, and the config it shuts down with is the config the next run
 * starts with. So each boot is asked about start on the way up, then reconfigured for
 * what the next shutdown should prove:
 *
 * <ol>
 *   <li>generate the config, point it at the fake, set the colours, shut down
 *       -> stop.enabled and stop.color</li>
 *   <li>start -> start.enabled and start.color; set both webhooks, shut down
 *       -> stop.webhook</li>
 *   <li>start -> start.webhook; disable both, shut down -> stop.enabled = false</li>
 *   <li>start -> start.enabled = false</li>
 * </ol>
 *
 * <p>Six keys, eight assertions, four boots. Written as one method because the boots are
 * a sequence rather than independent cases: each one depends on the state the last left
 * behind, and splitting them into separate tests would only disguise that.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServerLifecycleSweepTest {

    /** A colour no shipped default uses, so seeing it proves the key was read. */
    private static final String HEX = "AB12CD";
    private static final int DECIMAL = Integer.parseInt(HEX, 16);

    private static final String STARTED = "Server Started";
    private static final String STOPPED = "Server Stopped";

    private static final List<Grader.Result> RESULTS = new ArrayList<>();

    private FakeDiscord discord;
    private Path work;
    private Path serverDir;
    private Path serverJar;
    private Path pluginJar;
    private Path driverJar;
    private int java;

    @Test
    @DisplayName("start and stop, across four real server runs")
    void lifecycle() throws Exception {
        pluginJar = Sweeps.shippedJar();
        assumeTrue(pluginJar != null, "no plugin JAR in ../target");
        driverJar = Sweeps.driverJar();
        assumeTrue(driverJar != null, "driver not built");

        final String mc = Sweeps.version();
        java = Jdks.javaFor(mc);
        assumeTrue(Jdks.javaBinary(java) != null, "no JDK for " + mc + " on this machine");

        work = Files.createTempDirectory("dl-sweep-lifecycle");
        serverDir = work.resolve("server");
        serverJar = ServerJars.forVersion(mc, Sweeps.cache());
        discord = FakeDiscord.start(work);

        firstRunSetsUpAndProvesStop();
        secondRunProvesStartThenRoutesStop();
        thirdRunProvesStartRoutingThenSilencesStop();
        fourthRunProvesStartIsSilent();
    }

    // -----------------------------------------------------------------------------

    /** Boot 1: nothing to assert on the way up, because the config did not exist yet. */
    private void firstRunSetsUpAndProvesStop() throws Exception {
        final MinecraftServer s = boot();

        s.command("discordlogger webhook " + discord.webhookUrl());
        Thread.sleep(3000);
        // The shutdown command is itself a console command, and the plugin logs those.
        // Without this the stop case has a "Server Command" embed arriving alongside it.
        s.editConfig("filters.ignored_commands", "[\"dldriver\", \"discordlogger\", \"stop\"]");
        s.editConfig("log.server.start.enabled", "true");
        s.editConfig("log.server.stop.enabled", "true");
        s.editConfig("log.server.start.color", "\"#" + HEX + "\"");
        s.editConfig("log.server.stop.color", "\"#" + HEX + "\"");
        Sweeps.reload(s);
        Sweeps.quiesce(discord, 2000);

        s.close();   // sends "stop" and waits for the process to end

        final String stop = capture(STOPPED, 30);
        RESULTS.add(Grader.grade(
                new Grader.Expectation("log.server.stop.enabled", true, null).requiring("embeds"),
                stop, List.of(), ""));
        RESULTS.add(Grader.grade(
                new Grader.Expectation("log.server.stop.color", true, null)
                        .requiring("\"color\":" + DECIMAL),
                stop, List.of(), ""));
    }

    /** Boot 2: the colours set above are still in the config, so the way up is testable. */
    private void secondRunProvesStartThenRoutesStop() throws Exception {
        discord.reset();
        final MinecraftServer s = boot();

        final String start = capture(STARTED, 30);
        RESULTS.add(Grader.grade(
                new Grader.Expectation("log.server.start.enabled", true, null).requiring("embeds"),
                start, List.of(), Sweeps.serverContext(s)));
        RESULTS.add(Grader.grade(
                new Grader.Expectation("log.server.start.color", true, null)
                        .requiring("\"color\":" + DECIMAL),
                start, List.of(), Sweeps.serverContext(s)));

        s.editConfig("log.server.start.webhook", "\"" + discord.alternateWebhookUrl() + "\"");
        s.editConfig("log.server.stop.webhook", "\"" + discord.alternateWebhookUrl() + "\"");
        Sweeps.reload(s);
        Sweeps.quiesce(discord, 2000);

        s.close();
        RESULTS.add(routing("log.server.stop.webhook", STOPPED));
    }

    /** Boot 3: proves start routing, then switches both off for the shutdown. */
    private void thirdRunProvesStartRoutingThenSilencesStop() throws Exception {
        discord.reset();
        final MinecraftServer s = boot();

        RESULTS.add(routing("log.server.start.webhook", STARTED));

        s.editConfig("log.server.start.webhook", "\"\"");
        s.editConfig("log.server.stop.webhook", "\"\"");
        s.editConfig("log.server.start.enabled", "false");
        s.editConfig("log.server.stop.enabled", "false");
        Sweeps.reload(s);
        Sweeps.quiesce(discord, 2000);

        s.close();
        RESULTS.add(Grader.grade(
                new Grader.Expectation("log.server.stop.enabled", false, null),
                capture(STOPPED, 10), List.of(), ""));
    }

    /** Boot 4: the config says start is off, so the way up should be silent. */
    private void fourthRunProvesStartIsSilent() throws Exception {
        discord.reset();
        final MinecraftServer s = boot();

        final String start = capture(STARTED, 12);
        RESULTS.add(Grader.grade(
                new Grader.Expectation("log.server.start.enabled", false, null),
                start, List.of(), ""));

        // Left as shipped, in case anything else ever runs against this directory.
        s.editConfig("log.server.start.enabled", "true");
        s.editConfig("log.server.stop.enabled", "true");
        s.close();
    }

    // -----------------------------------------------------------------------------

    private MinecraftServer boot() throws Exception {
        final MinecraftServer s = MinecraftServer.boot(serverDir, serverJar, pluginJar,
                driverJar, java, discord.jvmArgs());
        s.awaitStartup(4, TimeUnit.MINUTES);
        return s;
    }

    private String capture(String marker, int seconds) throws InterruptedException {
        return Sweeps.captureMatching(discord, marker, seconds);
    }

    private Grader.Result routing(String key, String marker) throws Exception {
        final FakeDiscord.Recorded post;
        try {
            post = discord.awaitPostMatching(r -> r.bodyContains(marker), 30, TimeUnit.SECONDS);
        } catch (AssertionError nothingArrived) {
            return new Grader.Result(key, Verdict.WRONG, "sent nothing", null);
        }
        final boolean routed = post.path.contains(FakeDiscord.ALTERNATE_ID);
        return new Grader.Result(key,
                routed ? Verdict.PASS : Verdict.WRONG,
                routed ? "routed to its own webhook"
                       : "went to the default webhook instead: " + post.path,
                post.body);
    }

    @AfterAll
    void reportAndStop() throws Exception {
        Sweeps.report("log.server.start/stop", RESULTS);
        if (discord != null) discord.close();

        final List<String> wrong = new ArrayList<>();
        for (Grader.Result r : RESULTS) if (r.verdict.fails()) wrong.add(r.toString());
        assertFalse(!wrong.isEmpty(), "settings behaved incorrectly:\n  "
                + String.join("\n  ", wrong));
    }
}
