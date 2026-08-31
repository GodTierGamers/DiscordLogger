package com.discordlogger.acceptance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A real event, fired on a real server, asserted as it reaches Discord.
 *
 * <p>This is the shape every key in the sweep will take: put the server in a known
 * state, make something happen, and read what arrived. Nothing calls into
 * DiscordLogger -- the driver puts an event on Bukkit's bus and the plugin reacts to
 * it exactly as it would for a real player.
 */
class DrivenEventTest {

    private static Path shippedJar() throws Exception {
        try (var files = Files.list(Path.of("..", "target"))) {
            return files.filter(p -> p.getFileName().toString().startsWith("discordlogger-"))
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .filter(p -> !p.getFileName().toString().startsWith("original-"))
                    .findFirst().orElse(null);
        }
    }

    private static Path driverJar() {
        final Path p = Path.of("driver", "target", "DiscordLoggerDriver.jar");
        return Files.isRegularFile(p) ? p : null;
    }

    @Test
    @DisplayName("a join fires, and the plugin posts the join embed")
    void aJoinIsLogged(@TempDir Path tmp) throws Exception {
        final Path jar = shippedJar();
        assumeTrue(jar != null, "no plugin JAR in ../target -- run mvn package first");
        final Path driver = driverJar();
        assumeTrue(driver != null,
                "driver not built -- run mvn -f acceptance/driver/pom.xml package");

        final String mc = System.getProperty("dl.acceptance.version", "26.2");
        final int java = Jdks.javaFor(mc);
        assumeTrue(Jdks.javaBinary(java) != null, "no JDK " + java + " for " + mc);

        final Path cache = Path.of(System.getProperty("dl.acceptance.cache",
                System.getProperty("user.home") + "/.cache/dl-acceptance"));
        final Path serverJar = ServerJars.paper(mc, cache);

        try (FakeDiscord discord = FakeDiscord.start(tmp)) {
            try (MinecraftServer server = MinecraftServer.boot(
                    tmp.resolve("server"), serverJar, jar, driver, java, discord.jvmArgs())) {

                server.awaitStartup(4, TimeUnit.MINUTES);
                assertTrue(server.awaitLine("Acceptance driver ready", 30, TimeUnit.SECONDS),
                        "the driver did not load:\n" + server.tail(40));

                // Set the webhook the way an admin does, then let the plugin reload.
                server.command("discordlogger webhook " + discord.webhookUrl());
                Thread.sleep(3000);

                // The driver's own console commands are logged by the plugin, exactly as
                // it should log any console command. Adding it to the ignore list keeps
                // the capture to what is under test, using a filter the plugin already
                // has rather than switching a whole category off. The filter itself is
                // tested separately, where it is the subject rather than the scaffolding.
                server.editConfig("filters.ignored_commands", "[\"dldriver\"]");
                server.command("discordlogger reload");
                Thread.sleep(2000);
                discord.reset();

                server.command("dldriver join");
                assertTrue(server.awaitLine("DRIVER-OK join", 30, TimeUnit.SECONDS),
                        "the driver could not fire the join:\n" + server.tail(30));

                final FakeDiscord.Recorded post = discord.awaitPostMatching(
                        r -> r.bodyContains("Player1"), 45, TimeUnit.SECONDS);
                assertTrue(post.bodyContains("Player1"),
                        "the embed does not name the fake player: " + post.body);
                assertTrue(post.bodyContains("Join") || post.bodyContains("joined"),
                        "expected a join embed, got: " + post.body);
            }
        }
    }
}
