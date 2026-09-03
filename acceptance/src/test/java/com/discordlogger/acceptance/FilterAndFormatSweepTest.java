package com.discordlogger.acceptance;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Everything that shapes a message rather than switching one on: {@code filters.*},
 * {@code format.*}, {@code embeds.*}, {@code webhook.url} and {@code log.custom}.
 *
 * <h2>Every filter is tested twice</h2>
 *
 * <p>A filter is proved by silence, and silence is the least trustworthy result this
 * suite can get. A driver that never fired, a console that stopped listening and a
 * filter working perfectly all look identical from the Discord side. Six times now,
 * this suite has read one as another.
 *
 * <p>So no filter case asserts silence on its own. Each one first runs the event with
 * the filter neutral and requires a message to arrive -- the control. Only once that
 * has proved the event still reaches Discord does it apply the filter and require the
 * silence. If the control fails, the case says the scene could not be set and grades
 * nothing, because a filter cannot be judged on an event that never happened.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class FilterAndFormatSweepTest {

    private static final String HEX = "AB12CD";
    private static final String PROBE_COMMAND = "say acceptance probe";

    /**
     * How a join is recognised, in both of the shapes the plugin can send.
     *
     * <p>Not "Join": that word is in the embed's title and nowhere else, so it
     * disappears the moment embeds.enabled is switched off -- which three of the cases
     * below do deliberately. Matching on it would have reported plain text as nothing
     * sent at all, and graded embeds.enabled=false as a fault for doing exactly what it
     * was asked. The message line is in the embed's description and is the whole of the
     * plain-text form, so it identifies a join either way.
     */
    private static final String JOINED = "joined the server";

    /** ignored_commands with only the harness's own commands in it: the neutral state. */
    private static final String NEUTRAL_COMMANDS = "[\"dldriver\", \"discordlogger\"]";

    private static FakeDiscord discord;
    private static MinecraftServer server;
    private static Path work;
    private static final List<Grader.Result> RESULTS = new ArrayList<>();
    private static boolean playerEventsSupported = true;
    private static boolean hasAdvancements = true;

    @BeforeAll
    void bootOnce() throws Exception {
        final Path jar = Sweeps.shippedJar();
        assumeTrue(jar != null, "no plugin JAR in ../target");
        final Path driver = Sweeps.driverJar();
        assumeTrue(driver != null, "driver not built");

        final String mc = Sweeps.version();
        assumeTrue(Jdks.javaBinary(Jdks.javaFor(mc)) != null,
                "no JDK for " + mc + " on this machine");

        work = Files.createTempDirectory("dl-sweep-filters");
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
                    + Sweeps.version() + Sweeps.serverContext(server));
        }

        server.command("dldriver join");
        playerEventsSupported = !server.awaitLine("DRIVER-UNSUPPORTED", 20, TimeUnit.SECONDS);
        hasAdvancements = !"1.8".equals(Sweeps.version()) && !"1.8.8".equals(Sweeps.version());
        Sweeps.quiesce(discord, 1500);
    }

    @AfterAll
    void reportAndStop() throws Exception {
        Sweeps.report("filters.* + format.* + embeds.*", RESULTS);
        if (server != null) server.close();
        if (discord != null) discord.close();
    }

    // =============================================================================
    // filters.*
    // =============================================================================

    @Test @DisplayName("filters.ignored_commands")
    void ignoredCommands() throws Exception {
        filterSuppresses("filters.ignored_commands",
                NEUTRAL_COMMANDS, "[\"say\", \"dldriver\", \"discordlogger\"]",
                PROBE_COMMAND, "Server Command", false);
    }

    @Test @DisplayName("filters.only_log_commands")
    void onlyLogCommands() throws Exception {
        // An allow-list holding something else: "say" is then the thing not allowed.
        filterSuppresses("filters.only_log_commands", "[]", "[\"list\"]",
                PROBE_COMMAND, "Server Command", false);
    }

    @Test @DisplayName("filters.ignored_players")
    void ignoredPlayers() throws Exception {
        filterSuppresses("filters.ignored_players", "[]", "[\"Player1\"]",
                "dldriver join", JOINED, true);
    }

    @Test @DisplayName("filters.ignored_worlds")
    void ignoredWorlds() throws Exception {
        filterSuppresses("filters.ignored_worlds", "[]", "[\"world\"]",
                "dldriver join", JOINED, true);
    }

    @Test @DisplayName("filters.exempt_permission")
    void exemptPermission() throws Exception {
        server.command("dldriver fake permission dl.acceptance.exempt");
        Thread.sleep(800);
        try {
            filterSuppresses("filters.exempt_permission", "\"\"", "\"dl.acceptance.exempt\"",
                    "dldriver join", JOINED, true);
        } finally {
            server.command("dldriver fake reset");
            Thread.sleep(800);
        }
    }

    @Test @DisplayName("filters.respect_vanish")
    void respectVanish() throws Exception {
        server.command("dldriver fake vanished true");
        Thread.sleep(800);
        try {
            // Inverted against the others: the filter is on by default, so the control
            // is the one that switches it off.
            filterSuppresses("filters.respect_vanish", "false", "true",
                    "dldriver join", JOINED, true);
        } finally {
            server.command("dldriver fake reset");
            Thread.sleep(800);
        }
    }

    @Test @DisplayName("filters.ignored_chat_containing")
    void ignoredChatContaining() throws Exception {
        filterSuppresses("filters.ignored_chat_containing", "[]", "[\"forbidden\"]",
                "dldriver chat a forbidden phrase", "Chat", true);
    }

    @Test @DisplayName("filters.minimum_chat_length")
    void minimumChatLength() throws Exception {
        filterSuppresses("filters.minimum_chat_length", "0", "50",
                "dldriver chat hi", "Chat", true);
    }

    @Test @DisplayName("filters.ignored_advancements")
    void ignoredAdvancements() throws Exception {
        // A trailing star matches a whole tab, and every key on every version starts
        // with this prefix -- including the synthesised ones on the achievement era.
        //
        // Driven against one tab rather than the whole tree: unfiltered, this fires
        // every advancement the server declares, which is well over a hundred on a
        // modern version and has to happen twice per case.
        filterSuppresses("filters.ignored_advancements", "[]", "[\"minecraft:*\"]",
                "dldriver advancement adventure", "Unlocked", true);
    }

    @Test @DisplayName("filters.log_recipe_advancements")
    void logRecipeAdvancements() throws Exception {
        Assumptions.assumeTrue(hasAdvancements,
                "this server has achievements rather than advancements, so it has no "
                        + "recipe unlocks to filter");
        // Inverted, like respect_vanish: recipes are excluded by default, so switching
        // the setting ON is the control and OFF is the suppression.
        //
        // Only recipe advancements are fired, and the marker is the ordinary
        // advancement one. Matching on "recipes/" instead would never find anything:
        // the embed carries the advancement's title, not its key, so the case would
        // report the setting as broken on every version whatever it did.
        filterSuppresses("filters.log_recipe_advancements", "true", "false",
                "dldriver advancement recipes/", "Unlocked", true);
    }

    @Test @DisplayName("filters.ignored_teleport_causes")
    void ignoredTeleportCauses() throws Exception {
        // The driver's teleport carries UNKNOWN, which is what the three-argument
        // event constructor produces and what the shipped defaults leave enabled.
        filterSuppresses("filters.ignored_teleport_causes", "[]", "[\"UNKNOWN\"]",
                "dldriver teleport", "Teleport", true);
    }

    @Test @DisplayName("filters.minimum_teleport_distance")
    void minimumTeleportDistance() throws Exception {
        // The driver moves the fake roughly 390 blocks, so 10000 excludes it and 0
        // excludes nothing.
        filterSuppresses("filters.minimum_teleport_distance", "0", "10000",
                "dldriver teleport", "Teleport", true);
    }

    @Test @DisplayName("filters.ignored_death_causes")
    void ignoredDeathCauses() throws Exception {
        filterSuppresses("filters.ignored_death_causes", "[]", "[\"VOID\"]",
                "dldriver death VOID", "Death", true);
    }

    @Test @DisplayName("filters.ignored_explosion_sources")
    void ignoredExplosionSources() throws Exception {
        filterSuppresses("filters.ignored_explosion_sources", "[]", "[\"CREEPER\"]",
                "dldriver explosion CREEPER", "Explosion", true);
    }

    @Test @DisplayName("filters.minimum_explosion_blocks")
    void minimumExplosionBlocks() throws Exception {
        // The driver's explosions destroy nothing, so any threshold above zero excludes
        // them -- which is exactly the case the setting exists for.
        filterSuppresses("filters.minimum_explosion_blocks", "0", "1",
                "dldriver explosion CREEPER", "Explosion", true);
    }

    // =============================================================================
    // embeds.* and format.*
    // =============================================================================

    @Test @DisplayName("embeds.enabled")
    void embedsEnabled() throws Exception {
        requireFake();
        apply("embeds.enabled", "true");
        String captured = fireAndCapture("dldriver join", JOINED, 30);
        RESULTS.add(Grader.grade(new Grader.Expectation("embeds.enabled", true, null)
                .requiring("embeds"), captured, Sweeps.errorsSince(server),
                Sweeps.serverContext(server)));

        apply("embeds.enabled", "false");
        captured = fireAndCapture("dldriver join", JOINED, 30);
        if (captured == null) {
            RESULTS.add(new Grader.Result("embeds.enabled", Verdict.WRONG,
                    "sent nothing with embeds off, when plain text was expected"
                            + Sweeps.serverContext(server), null));
        } else {
            final boolean plain = captured.contains("content") && !captured.contains("\"embeds\"");
            RESULTS.add(new Grader.Result("embeds.enabled",
                    plain ? Verdict.PASS : Verdict.WRONG,
                    plain ? "switched to plain text"
                          : "still sent an embed with embeds.enabled false", captured));
        }
        apply("embeds.enabled", "true");
    }

    @Test @DisplayName("embeds.author")
    void embedsAuthor() throws Exception {
        requireFake();
        apply("embeds.enabled", "true", "embeds.author", "\"Acceptance Author\"");
        final String captured = fireAndCapture("dldriver join", JOINED, 30);
        RESULTS.add(Grader.grade(new Grader.Expectation("embeds.author", true, null)
                .requiring("Acceptance Author"), captured, Sweeps.errorsSince(server),
                Sweeps.serverContext(server)));
        apply("embeds.author", "\"Server Logs\"");
    }

    @Test @DisplayName("format.time")
    void formatTime() throws Exception {
        requireFake();
        // Only read for plain text, which the comment above the key says outright.
        // Testing it with embeds on would assert nothing and pass forever.
        //
        // The pattern carries a quoted literal rather than brackets. The value is fed
        // to DateTimeFormatter.ofPattern, where [ and ] delimit an optional section and
        // are not printed -- so "[yyyy]" formats as "2026", and asserting on "[2026]"
        // failed against a setting that was working. Worth knowing beyond this test:
        // the brackets in the shipped default never reach Discord either.
        apply("embeds.enabled", "false", "format.time", "\"'DLTIME'yyyy\"");
        final String captured = fireAndCapture("dldriver join", JOINED, 30);
        RESULTS.add(Grader.grade(new Grader.Expectation("format.time", true, null)
                .requiring("DLTIME" + Year.now().getValue()),
                captured, Sweeps.errorsSince(server), Sweeps.serverContext(server)));
        apply("format.time", "\"[HH:mm:ss, dd:MM:yyyy]\"", "embeds.enabled", "true");
    }

    @Test @DisplayName("format.name")
    void formatName() throws Exception {
        requireFake();
        apply("embeds.enabled", "false", "format.name", "\"ACCEPTANCE\"");
        final String captured = fireAndCapture("dldriver join", JOINED, 30);
        RESULTS.add(Grader.grade(new Grader.Expectation("format.name", true, null)
                .requiring("ACCEPTANCE"), captured, Sweeps.errorsSince(server),
                Sweeps.serverContext(server)));
        apply("format.name", "\"\"", "embeds.enabled", "true");
    }

    @Test @DisplayName("format.nicknames")
    void formatNicknames() throws Exception {
        requireFake();
        server.command("dldriver fake nickname Nickname");
        Thread.sleep(800);
        try {
            apply("format.nicknames", "true");
            String captured = fireAndCapture("dldriver join", JOINED, 30);
            RESULTS.add(Grader.grade(new Grader.Expectation("format.nicknames", true, null)
                    .requiring("Nickname (Player1)"), captured, Sweeps.errorsSince(server),
                    Sweeps.serverContext(server)));

            apply("format.nicknames", "false");
            captured = fireAndCapture("dldriver join", JOINED, 30);
            if (captured == null) {
                RESULTS.add(new Grader.Result("format.nicknames", Verdict.WRONG,
                        "sent nothing" + Sweeps.serverContext(server), null));
            } else {
                final boolean realNameOnly = !captured.contains("Nickname");
                RESULTS.add(new Grader.Result("format.nicknames",
                        realNameOnly ? Verdict.PASS : Verdict.WRONG,
                        realNameOnly ? "used the real name when switched off"
                                     : "still showed the nickname when switched off",
                        captured));
            }
        } finally {
            server.command("dldriver fake reset");
            Thread.sleep(800);
            apply("format.nicknames", "true");
        }
    }

    /**
     * The promise this setting makes about chat, which is the half that can be broken.
     *
     * <p>PlaceholderAPI is not installed here, so the expansion itself cannot be
     * exercised -- and the setting says as much: it is ignored without the plugin. What
     * can be tested is the guarantee in the comment, that a player typing a placeholder
     * into chat has it logged exactly as typed. That is the security-relevant half, and
     * the only half that can go wrong without PlaceholderAPI present.
     */
    @Test @DisplayName("format.placeholders")
    void formatPlaceholders() throws Exception {
        requireFake();
        apply("format.placeholders", "true");
        final String captured = fireAndCapture("dldriver chat %player_name% typed this",
                "Chat", 30);
        if (captured == null) {
            RESULTS.add(new Grader.Result("format.placeholders", Verdict.WRONG,
                    "sent nothing" + Sweeps.serverContext(server), null));
        } else {
            // Compared with Markdown escapes removed. The plugin sends
            // "%player\\_name%": mdEscape backslashes the underscore so a player cannot
            // inject italics through chat, and Discord renders it back as
            // "%player_name%". Checking the raw payload reported that as a possible
            // leak on every version for two releases -- the plugin was doing exactly
            // what it promises, and the test could not see it.
            final boolean literal = unescapeMarkdown(captured).contains("%player_name%");
            RESULTS.add(new Grader.Result("format.placeholders",
                    literal ? Verdict.PASS : Verdict.POTENTIAL_ERROR,
                    literal ? "a placeholder typed in chat was logged as typed, not expanded"
                            : "a placeholder typed in chat did not survive as written; "
                                    + "expansion of player-supplied text would be a leak",
                    captured));
        }
    }

    // =============================================================================
    // The remaining singles
    // =============================================================================

    @Test @DisplayName("webhook.url")
    void webhookUrl() throws Exception {
        requireFake();
        server.editConfig("webhook.url", "\"" + discord.alternateWebhookUrl() + "\"");
        Sweeps.reload(server);
        Sweeps.quiesce(discord, 2000);
        server.command("dldriver join");
        try {
            final FakeDiscord.Recorded post = discord.awaitPostMatching(
                    r -> r.bodyContains(JOINED), 30, TimeUnit.SECONDS);
            final boolean routed = post.path.contains(FakeDiscord.ALTERNATE_ID);
            RESULTS.add(new Grader.Result("webhook.url",
                    routed ? Verdict.PASS : Verdict.WRONG,
                    routed ? "every event followed the main webhook when it changed"
                           : "kept using the old webhook: " + post.path, post.body));
        } catch (AssertionError nothingArrived) {
            RESULTS.add(new Grader.Result("webhook.url", Verdict.WRONG,
                    "sent nothing after the main webhook changed"
                            + Sweeps.serverContext(server), null));
        }
        server.editConfig("webhook.url", "\"" + discord.webhookUrl() + "\"");
        Sweeps.reload(server);
    }

    @Test @DisplayName("log.custom")
    void customRule() throws Exception {
        addCustomRule();
        try {
            Sweeps.reload(server);
            Sweeps.quiesce(discord, 2000);
            server.command("list");
            final String captured = Sweeps.captureMatching(discord, "Acceptance Custom", 30);
            RESULTS.add(Grader.grade(
                    new Grader.Expectation("log.custom", true, null)
                            .requiring("Acceptance Custom",
                                    "\"color\":" + Integer.parseInt(HEX, 16)),
                    captured, Sweeps.errorsSince(server), Sweeps.serverContext(server)));
        } finally {
            removeCustomRule();
            Sweeps.reload(server);
        }
    }

    @Test @DisplayName("log.player.death.show_coords")
    void deathShowsCoords() throws Exception {
        requireFake();
        apply("log.player.death.enabled", "true", "log.player.death.show_coords", "true");
        String captured = fireAndCapture("dldriver death VOID", "Death", 30);
        // The fake dies at a fixed spot, so the coordinate is known rather than guessed.
        RESULTS.add(Grader.grade(
                new Grader.Expectation("log.player.death.show_coords", true, null)
                        .requiring("-252"),
                captured, Sweeps.errorsSince(server), Sweeps.serverContext(server)));

        apply("log.player.death.show_coords", "false");
        captured = fireAndCapture("dldriver death VOID", "Death", 30);
        if (captured == null) {
            RESULTS.add(new Grader.Result("log.player.death.show_coords", Verdict.WRONG,
                    "sent nothing with coordinates off, when a death was still expected"
                            + Sweeps.serverContext(server), null));
        } else {
            final boolean hidden = !captured.contains("-252");
            RESULTS.add(new Grader.Result("log.player.death.show_coords",
                    hidden ? Verdict.PASS : Verdict.WRONG,
                    hidden ? "left the location out when switched off"
                           : "still published where the body is when switched off",
                    captured));
        }
    }

    /**
     * What this setting can be held to without Floodgate installed.
     *
     * <p>The flag it adds needs Geyser and Floodgate to tell a Bedrock player from a
     * Java one, and neither is here. What is testable is the other half, and the half
     * that would actually hurt: with the setting on and no Floodgate, an ordinary Java
     * player must be logged normally and must not be labelled as anything.
     */
    @Test @DisplayName("log.player.join.show_platform")
    void joinShowsPlatform() throws Exception {
        requireFake();
        apply("log.player.join.enabled", "true", "log.player.join.show_platform", "true");
        final String captured = fireAndCapture("dldriver join", JOINED, 30);
        if (captured == null) {
            RESULTS.add(new Grader.Result("log.player.join.show_platform", Verdict.WRONG,
                    "a join went unlogged with show_platform on"
                            + Sweeps.serverContext(server), null));
        } else {
            final boolean mislabelled = captured.contains("Bedrock");
            RESULTS.add(new Grader.Result("log.player.join.show_platform",
                    mislabelled ? Verdict.WRONG : Verdict.PASS,
                    mislabelled
                            ? "labelled a Java player as Bedrock with no Floodgate installed"
                            : "logged a Java join cleanly; the Bedrock flag itself needs "
                                    + "Floodgate and is not driveable here",
                    captured));
        }
        apply("log.player.join.show_platform", "true");
    }

    // =============================================================================

    /** Drops the backslashes mdEscape adds, so a comparison sees what Discord shows. */
    private static String unescapeMarkdown(String payload) {
        return payload == null ? null : payload.replaceAll("\\\\([_*~`>|\\[\\]()])", "$1");
    }

    private void requireFake() {
        Assumptions.assumeTrue(playerEventsSupported,
                "this server build cannot host a fake player, so player events cannot be "
                        + "driven on " + Sweeps.version());
    }

    /**
     * Proves the event still arrives, then proves the filter stops it.
     *
     * @param key      the filter under test
     * @param neutral  the value at which the filter does nothing
     * @param active   the value at which it should suppress the event
     * @param drive    the console command producing the event
     * @param marker   text identifying that event's message
     * @param needsFake whether driving it requires a fake player
     */
    private void filterSuppresses(String key, String neutral, String active,
                                  String drive, String marker, boolean needsFake)
            throws Exception {
        if (needsFake) requireFake();

        // Control. Without this, the silence below proves nothing: an event that never
        // fired is silent for reasons that have nothing to do with the filter.
        apply(key, neutral);
        final String control = fireAndCapture(drive, marker, 30);
        if (control == null) {
            RESULTS.add(new Grader.Result(key, Verdict.POTENTIAL_ERROR,
                    "the control did not post with this filter neutral, so the filter "
                            + "itself cannot be judged: '" + drive + "' produced nothing"
                            + Sweeps.serverContext(server), null));
            return;
        }

        apply(key, active);
        final String filtered = fireAndCapture(drive, marker, 8);
        RESULTS.add(filtered == null
                ? new Grader.Result(key, Verdict.PASS,
                        "suppressed the event it was set to suppress", control)
                : new Grader.Result(key, Verdict.WRONG,
                        "logged the event anyway", filtered));

        apply(key, neutral);
    }

    /** Edits one or more settings as key/value pairs, reloads, and clears the decks. */
    private void apply(String... keyThenValue) throws Exception {
        for (int i = 0; i + 1 < keyThenValue.length; i += 2) {
            server.editConfig(keyThenValue[i], keyThenValue[i + 1]);
        }
        Sweeps.reload(server);
        Sweeps.quiesce(discord, 2000);
    }

    private String fireAndCapture(String drive, String marker, int seconds) throws Exception {
        server.command(drive);
        return Sweeps.captureMatching(discord, marker, seconds);
    }

    // -----------------------------------------------------------------------------
    // log.custom ships as an empty map, so the rule has to be written in as YAML
    // -----------------------------------------------------------------------------

    private static final List<String> CUSTOM_RULE = List.of(
            "    acceptance:",
            "      enabled: true",
            "      match: \"list\"",
            "      title: \"Acceptance Custom\"",
            "      message: \"{player} ran {command}\"",
            "      color: \"#" + HEX + "\"",
            "      webhook: \"\"");

    private void addCustomRule() throws IOException {
        final Path cfg = server.pluginDir().resolve("config.yml");
        final List<String> lines = new ArrayList<>(Files.readAllLines(cfg));
        final int at = lines.indexOf("  custom:");
        if (at < 0) throw new IllegalStateException("no 'custom:' key in the shipped config");
        lines.addAll(at + 1, CUSTOM_RULE);
        Files.write(cfg, lines);
    }

    private void removeCustomRule() throws IOException {
        final Path cfg = server.pluginDir().resolve("config.yml");
        final List<String> lines = new ArrayList<>(Files.readAllLines(cfg));
        final int at = lines.indexOf("  custom:");
        if (at >= 0 && at + 1 < lines.size() && lines.get(at + 1).equals(CUSTOM_RULE.get(0))) {
            lines.subList(at + 1, at + 1 + CUSTOM_RULE.size()).clear();
            Files.write(cfg, lines);
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
