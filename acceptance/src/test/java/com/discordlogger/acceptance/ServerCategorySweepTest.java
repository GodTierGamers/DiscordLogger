package com.discordlogger.acceptance;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Every setting under {@code log.server.*} and {@code log.moderation.*}.
 *
 * <p>These share a class, and so a single boot, because they are driven the same way:
 * through real console commands rather than synthesised events. The moderation
 * listeners watch commands and then confirm the action landed -- Ban re-reads the ban
 * list, Op re-reads the op flag -- so there is nothing to fake. Running {@code ban
 * Player1} on the console is the real thing.
 *
 * <h2>Why each case asks the server what happened</h2>
 *
 * <p>A moderation case that sees no embed has two possible causes, and they call for
 * opposite conclusions: the plugin declined to log a ban that happened, which is a
 * fault, or the ban never happened, which is the harness failing to set the scene. The
 * two are indistinguishable from the Discord side, and this suite has already spent
 * five separate runs blaming the plugin for the second.
 *
 * <p>So every case reads the server's own state files afterwards -- banned-players.json,
 * ops.json, whitelist.json, server.properties -- and only grades the plugin when the
 * action provably took effect. Where it did not, the case says so and asks for a
 * person, rather than manufacturing a verdict in either direction.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class ServerCategorySweepTest {

    /** The fake player's name, matching the driver's. Moderation acts on a name. */
    private static final String TARGET = "Player1";

    /** Whether an action landed, read from the files the server keeps it in. */
    private interface StateProbe {
        boolean tookEffect(Path serverDir);
    }

    /**
     * @param key      the config key prefix, e.g. {@code log.moderation.ban}
     * @param prime    a command putting the server in the state the drive needs, or null
     * @param drive    the command that should produce the embed
     * @param marker   text identifying that embed among everything else in flight
     * @param needsFake whether driving this requires a fake player to exist
     * @param primed   how to confirm the prime landed, or null when there is nothing to prime
     * @param probe    how to confirm the action landed, or null when it always does
     */
    private record Category(String key, String prime, String drive, String marker,
                            boolean needsFake, StateProbe primed, StateProbe probe) {
        String name() { return key.substring(key.lastIndexOf('.') + 1); }
    }

    private static final List<Category> CATEGORIES = List.of(
            // A command the plugin will log: "say" is not in ignored_commands, and the
            // driver's own commands are, so this is the only console traffic that counts.
            new Category("log.server.command", null, "say acceptance probe",
                    "Server Command", false, null, null),
            new Category("log.server.explosion", null, "dldriver explosion",
                    "Explosion", true, null, null),

            // Each pair is a real state change in both directions, and both directions
            // are checked. Confirming only the drive would let a prime that silently
            // failed turn into a verdict against the plugin: if "op Player1" never ran,
            // "deop Player1" changes nothing, and the missing embed is correct.
            new Category("log.moderation.ban", "pardon " + TARGET, "ban " + TARGET,
                    "Player Banned", false,
                    dir -> !mentions(dir, "banned-players.json", TARGET),
                    dir -> mentions(dir, "banned-players.json", TARGET)),
            new Category("log.moderation.unban", "ban " + TARGET, "pardon " + TARGET,
                    "Player Unbanned", false,
                    dir -> mentions(dir, "banned-players.json", TARGET),
                    dir -> !mentions(dir, "banned-players.json", TARGET)),
            new Category("log.moderation.op", "deop " + TARGET, "op " + TARGET,
                    "Player Opped", false,
                    dir -> !mentions(dir, "ops.json", TARGET),
                    dir -> mentions(dir, "ops.json", TARGET)),
            new Category("log.moderation.deop", "op " + TARGET, "deop " + TARGET,
                    "Player Deopped", false,
                    dir -> mentions(dir, "ops.json", TARGET),
                    dir -> !mentions(dir, "ops.json", TARGET)),
            new Category("log.moderation.whitelist_toggle", "whitelist off", "whitelist on",
                    "Whitelist Toggled", false,
                    dir -> mentions(dir, "server.properties", "white-list=false"),
                    dir -> mentions(dir, "server.properties", "white-list=true")),
            new Category("log.moderation.whitelist_edit",
                    "whitelist remove " + TARGET, "whitelist add " + TARGET,
                    "Player Whitelisted", false,
                    dir -> !mentions(dir, "whitelist.json", TARGET),
                    dir -> mentions(dir, "whitelist.json", TARGET)));

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

        work = Files.createTempDirectory("dl-sweep-server");
        discord = FakeDiscord.start(work);
        server = MinecraftServer.boot(work.resolve("server"),
                ServerJars.forVersion(mc, Sweeps.cache()), jar, driver,
                Jdks.javaFor(mc), discord.jvmArgs());
        server.awaitStartup(4, TimeUnit.MINUTES);

        if (!server.awaitLine("Acceptance driver ready", 30, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    "the acceptance driver did not load, so nothing can be fired on "
                            + Sweeps.version() + Sweeps.serverContext(server));
        }
        Sweeps.prepare(server, discord);

        if (!Sweeps.stillResponding(server)) {
            throw new IllegalStateException("the server stopped accepting console commands on "
                    + Sweeps.version() + ", so nothing can be driven"
                    + Sweeps.serverContext(server));
        }

        // Only the explosion case needs one; the moderation commands act on a name and
        // work perfectly well against a player who has never connected.
        server.command("dldriver join");
        playerEventsSupported = !server.awaitLine("DRIVER-UNSUPPORTED", 20, TimeUnit.SECONDS);
        Sweeps.quiesce(discord, 1500);
    }

    @AfterAll
    void reportAndStop() throws Exception {
        Sweeps.report("log.server.* + log.moderation.*", RESULTS);
        if (server != null) server.close();
        if (discord != null) discord.close();
    }

    static Stream<Category> categories() { return CATEGORIES.stream(); }

    // -----------------------------------------------------------------------------
    // The four things every category setting has to do
    // -----------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}.enabled = true posts an embed")
    @MethodSource("categories")
    @DisplayName("enabled")
    void enabledPosts(Category c) throws Exception {
        skipIfUndriveable(c);
        final String key = c.key() + ".enabled";
        server.editConfig(key, "true");
        Sweeps.reload(server);
        final Attempt a = stageAndDrive(c, 30);
        if (!a.staged()) { recordSceneNotSet(key, c); return; }
        if (recordIfNothingHappened(key, c)) return;

        final String captured = a.captured();
        RESULTS.add(Grader.grade(
                new Grader.Expectation(key, true, null).requiring("embeds"),
                captured, Sweeps.errorsSince(server), Sweeps.serverContext(server)));
    }

    @ParameterizedTest(name = "{0}.enabled = false posts nothing")
    @MethodSource("categories")
    @DisplayName("disabled")
    void disabledIsSilent(Category c) throws Exception {
        skipIfUndriveable(c);
        final String key = c.key() + ".enabled";
        server.editConfig(key, "false");
        Sweeps.reload(server);
        final Attempt a = stageAndDrive(c, 8);
        if (!a.staged()) { recordSceneNotSet(key, c); server.editConfig(key, "true"); return; }

        // Checked here too, and this is the case that most needs it. Silence when the
        // action never happened is not the plugin obeying the switch; it is the suite
        // proving nothing at all and calling it a pass.
        if (recordIfNothingHappened(key, c)) {
            server.editConfig(key, "true");
            return;
        }

        RESULTS.add(Grader.grade(new Grader.Expectation(key, false, null),
                a.captured(), Sweeps.errorsSince(server)));
        server.editConfig(key, "true");   // leave it as shipped for the next case
    }

    @ParameterizedTest(name = "{0}.color reaches the embed")
    @MethodSource("categories")
    @DisplayName("colour")
    void colourIsApplied(Category c) throws Exception {
        skipIfUndriveable(c);
        final String key = c.key() + ".color";
        server.editConfig(c.key() + ".enabled", "true");
        final String hex = "AB12CD";
        server.editConfig(key, "\"#" + hex + "\"");
        Sweeps.reload(server);
        final Attempt a = stageAndDrive(c, 30);
        if (!a.staged()) { recordSceneNotSet(key, c); return; }
        if (recordIfNothingHappened(key, c)) return;

        final String captured = a.captured();
        RESULTS.add(Grader.grade(
                new Grader.Expectation(key, true, null)
                        .requiring("\"color\":" + Integer.parseInt(hex, 16)),
                captured, Sweeps.errorsSince(server), Sweeps.serverContext(server)));
    }

    @ParameterizedTest(name = "{0}.webhook routes elsewhere")
    @MethodSource("categories")
    @DisplayName("routing")
    void webhookRoutes(Category c) throws Exception {
        skipIfUndriveable(c);
        final String key = c.key() + ".webhook";
        server.editConfig(c.key() + ".enabled", "true");
        server.editConfig(key, "\"" + discord.alternateWebhookUrl() + "\"");
        Sweeps.reload(server);

        if (!prime(c)) {
            recordSceneNotSet(key, c);
            server.editConfig(key, "\"\"");
            return;
        }
        server.command(c.drive());
        FakeDiscord.Recorded post = null;
        try {
            post = discord.awaitPostMatching(r -> r.bodyContains(c.marker()),
                    30, TimeUnit.SECONDS);
        } catch (AssertionError nothingArrived) {
            // falls through to the state check below
        }
        if (recordIfNothingHappened(key, c)) {
            server.editConfig(key, "\"\"");
            return;
        }
        if (post == null) {
            RESULTS.add(new Grader.Result(key, Verdict.WRONG,
                    "sent nothing" + Sweeps.serverContext(server), null));
            server.editConfig(key, "\"\"");
            return;
        }

        final boolean routed = post.path.contains(FakeDiscord.ALTERNATE_ID);
        RESULTS.add(new Grader.Result(key,
                routed ? Verdict.PASS : Verdict.WRONG,
                routed ? "routed to its own webhook"
                       : "went to the default webhook instead: " + post.path,
                post.body));

        server.editConfig(key, "\"\"");
    }

    // -----------------------------------------------------------------------------
    // Kick, which cannot be driven the way the rest can
    // -----------------------------------------------------------------------------

    /**
     * Kick is the one moderation event this harness cannot stage.
     *
     * <p>The listener deliberately logs a kick only once it has seen both halves: the
     * command naming a player who is online, and the PlayerKickEvent that follows for
     * that same player. That is what stops it reporting timeouts and plugin
     * disconnections as staff action, and it is correct.
     *
     * <p>It also means a kick needs a genuinely connected client. The fake player is
     * synthesised inside the server and never appears in getOnlinePlayers, so
     * {@code kick Player1} finds nobody and records no intent, and a PlayerKickEvent
     * fired on its own is correctly ignored for having no intent behind it.
     *
     * <p>So this asserts what it honestly can -- that both halves run without faulting
     * -- and reports the three kick settings as needing a person rather than inventing
     * a verdict. Marking them PASS would be a lie, and WRONG would blame the plugin for
     * a limitation of the test.
     */
    @Test
    @DisplayName("kick: exercised as far as a fake player allows")
    void kickIsNotFullyDriveable() throws Exception {
        Sweeps.quiesce(discord, 1500);
        server.editConfig("log.moderation.kick.enabled", "true");
        Sweeps.reload(server);

        server.command("kick " + TARGET + " acceptance probe");
        Thread.sleep(2000);

        final List<String> errors = Sweeps.errorsSince(server);
        final String reason = "a real kick needs a connected client, which this harness "
                + "cannot provide; both halves of the listener ran without faulting";

        for (String leaf : List.of("enabled", "color", "webhook")) {
            final String key = "log.moderation.kick." + leaf;
            RESULTS.add(errors.isEmpty()
                    ? new Grader.Result(key, Verdict.POTENTIAL_ERROR, reason, null)
                    : new Grader.Result(key, Verdict.WRONG,
                            "the server logged an error: " + errors.get(0), null));
        }
    }

    // -----------------------------------------------------------------------------

    private void skipIfUndriveable(Category c) {
        Assumptions.assumeTrue(!c.needsFake() || playerEventsSupported,
                "this server build cannot host a fake player, so " + c.key()
                        + " cannot be driven on " + Sweeps.version());
    }

    /**
     * One run of a case.
     *
     * @param staged   whether the server was actually put in the state the case needs
     * @param captured what arrived at Discord, or null when nothing did
     */
    private record Attempt(boolean staged, String captured) {}

    /**
     * Puts the server in the state the drive command needs, and clears what that emitted.
     *
     * @return false when the setup command did not take, so nothing can be driven
     */
    private boolean prime(Category c) throws Exception {
        if (c.prime() != null) {
            server.command(c.prime());
            Thread.sleep(1500);
            if (c.primed() != null && !awaitState(c.primed())) return false;
        }
        // After the prime, not before: priming a ban emits its own embed, and reading
        // that as the result of the case under test is precisely the race this avoids.
        Sweeps.quiesce(discord, 2000);
        return true;
    }

    private Attempt stageAndDrive(Category c, int seconds) throws Exception {
        if (!prime(c)) return new Attempt(false, null);
        server.command(c.drive());
        final String captured = Sweeps.captureMatching(discord, c.marker(), seconds);

        // Asked after every moderation case, so a case that logged nothing carries the
        // server's own view of the name with it: who it thinks is an operator, under
        // which UUID, and what getOfflinePlayer answers. The state file says the action
        // landed; this says whether the API the plugin reads agrees, and those two have
        // already disagreed once on servers older than 1.13.
        if (c.key().startsWith("log.moderation")) {
            server.command("dldriver probe " + TARGET);
            Thread.sleep(700);
        }
        return new Attempt(true, captured);
    }

    /** Records the case as unjudgeable because the setup command never took effect. */
    private void recordSceneNotSet(String key, Category c) {
        RESULTS.add(new Grader.Result(key, Verdict.POTENTIAL_ERROR,
                "'" + c.prime() + "' did not put the server in the state this case needs, "
                        + "so the plugin was never given the chance to log anything"
                        + Sweeps.serverContext(server), null));
    }

    private boolean awaitState(StateProbe probe) throws Exception {
        for (int i = 0; i < 20; i++) {
            if (probe.tookEffect(server.dir())) return true;
            Thread.sleep(400);
        }
        return false;
    }

    /**
     * Records a verdict when the action under test never actually happened.
     *
     * @return true when the case was recorded and the caller should stop
     */
    private boolean recordIfNothingHappened(String key, Category c) throws Exception {
        if (c.probe() == null) return false;
        if (awaitState(c.probe())) return false;
        RESULTS.add(new Grader.Result(key, Verdict.POTENTIAL_ERROR,
                "'" + c.drive() + "' did not change the server's own state, so nothing "
                        + "can be concluded about the plugin here"
                        + Sweeps.serverContext(server), null));
        return true;
    }

    private static boolean mentions(Path serverDir, String file, String needle) {
        try {
            final Path p = serverDir.resolve(file);
            if (!Files.isRegularFile(p)) return false;
            return Files.readString(p, StandardCharsets.UTF_8).contains(needle);
        } catch (IOException unreadable) {
            return false;
        }
    }

    @AfterAll
    void noneWereWrong() {
        final List<String> wrong = new ArrayList<>();
        for (Grader.Result r : RESULTS) if (r.verdict.fails()) wrong.add(r.toString());
        assertFalse(!wrong.isEmpty(), "settings behaved incorrectly:\n  "
                + String.join("\n  ", wrong));
    }
}
