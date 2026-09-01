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
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Every line of lang.yml that reaches a person rather than Discord.
 *
 * <p>The {@code chat.*} half is what the plugin says back to whoever ran a command. It
 * never reaches a webhook, so the fake Discord cannot see it and the assertion is made
 * against the server's own console -- which is exactly where these lines land when an
 * admin is the one typing.
 *
 * <p>Same discipline as {@link LangSweepTest}: each line is rewritten to a value nothing
 * else could produce, and the case passes only when that value comes back. Checking the
 * shipped wording instead would pass on a plugin that had stopped reading lang.yml,
 * because the shipped wording is also what the Java fallbacks say.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChatLangSweepTest {

    /**
     * @param key    the lang.yml key
     * @param before console commands or CONFIG edits to apply first
     * @param drive  the command whose reply should carry the line
     */
    private record Line(String key, List<String> before, String drive) {
        Line(String key, String drive) { this(key, List.of(), drive); }
    }

    private static final String BAD_URL = "http://example.com/not-a-webhook";

    private static final List<Line> LINES = List.of(
            // The prefix goes in front of the lines that use one, so any of them proves it.
            new Line("chat.prefix", "discordlogger reload"),
            new Line("chat.reload-ok", "discordlogger reload"),

            new Line("chat.reload-no-webhook",
                    List.of("CONFIG webhook.url=\"\""), "discordlogger reload"),
            new Line("chat.reload-no-webhook-hint",
                    List.of("CONFIG webhook.url=\"\""), "discordlogger reload"),

            new Line("chat.webhook-usage", "discordlogger webhook"),
            new Line("chat.webhook-where", "discordlogger webhook"),
            new Line("chat.webhook-private", "discordlogger webhook"),

            new Line("chat.webhook-invalid", "discordlogger webhook " + BAD_URL),
            new Line("chat.webhook-expected", "discordlogger webhook " + BAD_URL),

            new Line("chat.regen-warning", "discordlogger regen"),
            new Line("chat.regen-confirm", "discordlogger regen"),

            new Line("chat.help-header", "discordlogger"),
            new Line("chat.help-entry", "discordlogger"),
            new Line("chat.unknown-subcommand", "discordlogger definitelynotasubcommand"));

    private static FakeDiscord discord;
    private static MinecraftServer server;
    private static Path work;
    private static final List<Grader.Result> RESULTS = new ArrayList<>();
    private static Map<String, String> shipped;

    @BeforeAll
    void bootOnce() throws Exception {
        final Path jar = Sweeps.shippedJar();
        assumeTrue(jar != null, "no plugin JAR in ../target");
        final String mc = Sweeps.version();
        assumeTrue(Jdks.javaBinary(Jdks.javaFor(mc)) != null,
                "no JDK for " + mc + " on this machine");

        work = Files.createTempDirectory("dl-sweep-chat");
        discord = FakeDiscord.start(work);
        server = MinecraftServer.boot(work.resolve("server"),
                ServerJars.forVersion(mc, Sweeps.cache()), jar, Sweeps.driverJar(),
                Jdks.javaFor(mc), discord.jvmArgs());
        server.awaitStartup(4, TimeUnit.MINUTES);
        Sweeps.prepare(server, discord);
        if (!Sweeps.stillResponding(server)) {
            throw new IllegalStateException("the server stopped accepting console commands on "
                    + Sweeps.version() + Sweeps.serverContext(server));
        }
        shipped = LangSweepTest.flatten(server.stockLang());
    }

    @AfterAll
    void reportAndStop() throws Exception {
        Sweeps.report("lang.yml chat.*", RESULTS);
        if (server != null) server.close();
        if (discord != null) discord.close();
    }

    static Stream<Line> lines() { return LINES.stream(); }

    @ParameterizedTest(name = "{0} reaches the console when it is rewritten")
    @MethodSource("lines")
    @DisplayName("chat line")
    void lineIsRead(Line line) throws Exception {
        final String original = shipped.get(line.key());
        Assumptions.assumeTrue(original != null,
                line.key() + " is not in this build's lang.yml");

        final String sentinel = "DLCHAT" + Math.abs(line.key().hashCode() % 100000);
        // Colour tags are dropped: the console renders MiniMessage as legacy section
        // codes, which would land in the middle of the sentinel and stop it matching.
        server.editLang(line.key(), "\"" + sentinel + "\"");
        for (String b : line.before()) applyBefore(b);

        // Loaded before the command under test runs, because the plugin reads lang.yml
        // on reload rather than on every message.
        server.command("discordlogger reload");
        Thread.sleep(1500);

        final int from = server.log().size();
        server.command(line.drive());
        final boolean seen = awaitConsole(sentinel, from, 15);

        RESULTS.add(new Grader.Result(line.key(),
                seen ? Verdict.PASS : Verdict.WRONG,
                seen ? "the rewritten line came back from '" + line.drive() + "'"
                     : "'" + line.drive() + "' did not produce this line"
                             + Sweeps.serverContext(server),
                null));

        restore(line, original);
    }

    private void applyBefore(String instruction) throws Exception {
        if (instruction.startsWith("CONFIG ")) {
            final String[] kv = instruction.substring("CONFIG ".length()).split("=", 2);
            server.editConfig(kv[0], kv[1]);
        } else {
            server.command(instruction);
            Thread.sleep(600);
        }
    }

    private void restore(Line line, String original) throws Exception {
        server.editLang(line.key(), "\"" + original.replace("\"", "'") + "\"");
        if (!line.before().isEmpty()) {
            // Only one setting is ever disturbed, and it is the webhook.
            server.editConfig("webhook.url", "\"" + discord.webhookUrl() + "\"");
        }
        server.command("discordlogger reload");
        Thread.sleep(800);
    }

    /** Whether the console said this, in the lines added since {@code from}. */
    private boolean awaitConsole(String needle, int from, int seconds) throws Exception {
        final long until = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < until) {
            final List<String> log = server.log();
            for (int i = from; i < log.size(); i++) {
                if (log.get(i).contains(needle)) return true;
            }
            Thread.sleep(250);
        }
        return false;
    }

    @AfterAll
    void noneWereWrong() {
        final List<String> wrong = new ArrayList<>();
        for (Grader.Result r : RESULTS) if (r.verdict.fails()) wrong.add(r.toString());
        assertFalse(!wrong.isEmpty(), "chat lines did not reach the console:\n  "
                + String.join("\n  ", wrong));
    }
}
