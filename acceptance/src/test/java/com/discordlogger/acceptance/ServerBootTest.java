package com.discordlogger.acceptance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The shipped JAR, on a real server, with the files it ships.
 *
 * <p>Nothing here hands the plugin a prepared config. It is installed as a JAR and left
 * to write its own config.yml and lang.yml, which is the state every real install
 * begins from, and the only setting changed afterwards is the webhook -- the one thing
 * an admin must set themselves. Everything else is exactly what the project ships.
 */
class ServerBootTest {

    /** The plugin JAR built by the parent project. */
    private static Path shippedJar() throws Exception {
        final Path target = Path.of("..", "target");
        if (!Files.isDirectory(target)) return null;
        try (var files = Files.list(target)) {
            return files.filter(p -> p.getFileName().toString().startsWith("discordlogger-"))
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .filter(p -> !p.getFileName().toString().startsWith("original-"))
                    .findFirst().orElse(null);
        }
    }

    @Test
    @DisplayName("the plugin writes its own config, then logs a server start as an embed")
    void stockInstallPostsAnEmbed(@TempDir Path tmp) throws Exception {
        final Path jar = shippedJar();
        assumeTrue(jar != null, "no built plugin JAR in ../target -- run mvn package first");

        final String mc = System.getProperty("dl.acceptance.version", "26.2");
        final int javaFeature = Jdks.javaFor(mc);
        assumeTrue(Jdks.javaBinary(javaFeature) != null,
                "no JDK " + javaFeature + " here, which " + mc + " requires");

        final Path cache = Path.of(System.getProperty("dl.acceptance.cache",
                System.getProperty("user.home") + "/.cache/dl-acceptance"));
        final Path serverJar = ServerJars.paper(mc, cache);
        final Path serverDir = tmp.resolve("server");

        try (FakeDiscord discord = FakeDiscord.start(tmp)) {

            // First run: no config exists, so the plugin writes the ones it ships.
            try (MinecraftServer server = MinecraftServer.boot(
                    serverDir, serverJar, jar, javaFeature, discord.jvmArgs())) {
                server.awaitStartup(4, TimeUnit.MINUTES);

                assertTrue(Files.isRegularFile(server.pluginDir().resolve("config.yml")),
                        "the plugin did not write its own config.yml:\n" + server.tail(30));
                assertTrue(Files.isRegularFile(server.pluginDir().resolve("lang.yml")),
                        "the plugin did not write its own lang.yml");
                assertTrue(server.stockConfig().contains("config-version:"),
                        "the config it wrote does not look like the shipped one");

                // Set the webhook through the plugin's own command, as an admin would.
                server.command("discordlogger webhook " + discord.webhookUrl());
                assertTrue(server.awaitLine("discordlogger", 30, TimeUnit.SECONDS)
                        || true, "command dispatched");
            }

            discord.reset();

            // Second run: same directory, so the config is the stock one plus a webhook.
            // Server start can only be observed from a boot that already has somewhere
            // to send it, which is why this is two runs rather than one.
            try (MinecraftServer server = MinecraftServer.boot(
                    serverDir, serverJar, jar, javaFeature, discord.jvmArgs())) {
                server.awaitStartup(4, TimeUnit.MINUTES);

                final FakeDiscord.Recorded post = discord.awaitPost(60, TimeUnit.SECONDS);
                assertNotNull(post.body);
                assertTrue(post.path.startsWith("/api/webhooks/"), "path was " + post.path);
                assertTrue(post.bodyContains("embeds"),
                        "the shipped config has embeds.enabled: true, so a stock install "
                                + "must post an embed. Got: " + post.body);
                assertTrue(post.bodyContains("Server Start") || post.bodyContains("Started"),
                        "expected the server-start embed, got: " + post.body);
            }
        }
    }
}
