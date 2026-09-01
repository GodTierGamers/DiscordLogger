package com.discordlogger.acceptance;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A real Minecraft server, running the shipped JAR, talking to {@link FakeDiscord}.
 *
 * <p>Everything here is deliberately the same as a human installing the plugin: the
 * JAR is copied into {@code plugins/}, the config is written as a file, and the server
 * is started as a subprocess. No classpath tricks, no reflection into the plugin, no
 * test-only entry points. What is verified is the artifact, not the source tree.
 */
final class MinecraftServer implements AutoCloseable {

    private final Process process;
    private final Path dir;
    private final Writer console;
    private final List<String> log = new CopyOnWriteArrayList<>();

    private MinecraftServer(Process process, Path dir) {
        this.process = process;
        this.dir = dir;
        this.console = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
    }

    /**
     * Lays out a server directory and boots it.
     *
     * @param pluginJar the shipped JAR, exactly as released
     */
    static MinecraftServer boot(Path dir, Path serverJar, Path pluginJar,
                                int javaFeature, List<String> extraJvmArgs)
            throws Exception {
        return boot(dir, serverJar, pluginJar, null, javaFeature, extraJvmArgs);
    }

    /**
     * As above, plus the acceptance driver.
     *
     * <p>The driver is what makes a headless server able to produce a join, a death or a
     * chat line. It is a separate plugin on purpose: the two never call each other, so a
     * fault in the driver cannot fake a passing result for DiscordLogger.
     */
    static MinecraftServer boot(Path dir, Path serverJar, Path pluginJar, Path driverJar,
                                int javaFeature, List<String> extraJvmArgs)
            throws Exception {
        Files.createDirectories(dir);

        // A server refuses to start without this, and saying so is the point of the file.
        Files.writeString(dir.resolve("eula.txt"), "eula=true\n");

        // Offline mode because there is no Mojang session here, and a fixed port of 0
        // is not possible, so a high one keeps parallel runs from colliding.
        Files.writeString(dir.resolve("server.properties"),
                "online-mode=false\n"
              + "server-port=0\n"
              + "max-players=5\n"
              + "spawn-protection=0\n"
              + "level-type=flat\n"
              + "generate-structures=false\n"
              + "allow-nether=false\n"
              + "view-distance=4\n"
              + "sync-chunk-writes=false\n");

        // Only the JAR is installed. config.yml and lang.yml are NOT written here: the
        // plugin ships them and writes them itself on first run, and that is the state
        // every real install starts from. Supplying a hand-made config would test a
        // file this project never produces, and would quietly exercise the Java
        // fallbacks for every key it omitted rather than the shipped defaults.
        //
        // Tests change settings afterwards, through editConfig, the way an admin does.
        Files.createDirectories(dir.resolve("plugins"));
        Files.copy(pluginJar, dir.resolve("plugins").resolve("DiscordLogger.jar"),
                StandardCopyOption.REPLACE_EXISTING);
        if (driverJar != null) {
            Files.copy(driverJar, dir.resolve("plugins").resolve("DiscordLoggerDriver.jar"),
                    StandardCopyOption.REPLACE_EXISTING);
        }

        // bStats would post real telemetry from a test run. Switching it off here is
        // both correct and necessary: the charts are used to make decisions, and a
        // nightly job pretending to be dozens of servers would corrupt them.
        final Path bstats = dir.resolve("plugins").resolve("bStats");
        Files.createDirectories(bstats);
        Files.writeString(bstats.resolve("config.yml"),
                "enabled: false\nserverUuid: 00000000-0000-0000-0000-000000000000\n"
              + "logFailedRequests: false\nlogSentData: false\nlogResponseStatusText: false\n");

        final Path java = Jdks.javaBinary(javaFeature);
        if (java == null) {
            throw new IllegalStateException("no JDK " + javaFeature + " on this machine");
        }

        final List<String> cmd = new ArrayList<>();
        cmd.add(java.toString());
        cmd.add("-Xmx1G");
        cmd.add("-Dcom.mojang.eula.agree=true");

        // Servers of the 1.8 to 1.12 era drive their console through jline, which takes
        // over stdin and, with no terminal attached, quietly stops delivering piped
        // commands after the first few. On 1.8.8 that looked like the plugin logging
        // nothing: the console accepted the webhook and one reload, then every later
        // command vanished, so every case expecting output failed while every case
        // expecting silence passed. A server that has stopped listening is
        // indistinguishable from a plugin that has stopped sending.
        //
        // Harmless on newer servers, which do not use jline this way.
        if (javaFeature <= 11) {
            cmd.add("-Djline.terminal=jline.UnsupportedTerminal");
        }
        cmd.addAll(extraJvmArgs);
        cmd.add("-jar");
        cmd.add(serverJar.toAbsolutePath().toString());
        cmd.add("nogui");

        final Process p = new ProcessBuilder(cmd)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
        final MinecraftServer server = new MinecraftServer(p, dir);
        server.startLogPump();
        return server;
    }

    /** Blocks until the server reports it is up, or fails with the log attached. */
    void awaitStartup(long timeout, TimeUnit unit) throws InterruptedException {
        // Keyed on the server's own "Done (1.234s)!" line rather than a fixed sleep,
        // because boot time varies by an order of magnitude between versions and
        // machines, and a sleep long enough for the slowest is wasted on every run.
        if (!awaitLine("Done (", timeout, unit)) {
            throw new AssertionError("server did not finish starting within "
                    + timeout + " " + unit + ". Last lines:\n" + tail(40));
        }
    }

    /** Waits for a line containing {@code needle}. */
    boolean awaitLine(String needle, long timeout, TimeUnit unit) throws InterruptedException {
        final long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            for (String l : log) if (l.contains(needle)) return true;
            if (!process.isAlive()) return false;
            Thread.sleep(100);
        }
        return false;
    }

    /** The plugin's own data directory, once it has written its defaults. */
    Path pluginDir() { return dir.resolve("plugins").resolve("DiscordLogger"); }

    /** The stock config.yml the plugin wrote for itself. */
    String stockConfig() throws IOException {
        return Files.readString(pluginDir().resolve("config.yml"));
    }

    /** The stock lang.yml the plugin wrote for itself. */
    String stockLang() throws IOException {
        return Files.readString(pluginDir().resolve("lang.yml"));
    }

    /**
     * Changes one setting in the plugin's own config, as an admin editing the file.
     *
     * <p>Rewrites the value in place rather than regenerating the file, so everything
     * around it stays exactly as shipped -- comments, ordering, every other default.
     * A test that swapped the whole file would no longer be testing the shipped one.
     *
     * @param path  dotted key, e.g. {@code log.player.chat.enabled}
     */
    void editConfig(String path, String value) throws IOException {
        final Path file = pluginDir().resolve("config.yml");
        final List<String> lines = new ArrayList<>(Files.readAllLines(file));
        final String[] parts = path.split("\\.");
        int depth = 0;
        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            final String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            final int indent = line.length() - line.stripLeading().length();
            if (indent != depth * 2) continue;
            if (!trimmed.startsWith(parts[depth] + ":")) continue;

            if (depth == parts.length - 1) {
                final String comment = line.contains("#")
                        ? " " + line.substring(line.indexOf('#')) : "";
                lines.set(i, " ".repeat(indent) + parts[depth] + ": " + value + comment);

                // Several settings ship as block lists -- ignored_commands is six lines
                // of "- login" and friends. Replacing only the key line leaves those
                // orphaned and the file no longer parses, which the plugin reports by
                // silently logging nothing at all.
                int j = i + 1;
                while (j < lines.size()) {
                    final String next = lines.get(j);
                    final String nt = next.trim();
                    if (nt.isEmpty() || nt.startsWith("#")) break;
                    final int ni = next.length() - next.stripLeading().length();
                    if (ni > indent || nt.startsWith("- ")) { lines.remove(j); continue; }
                    break;
                }

                Files.write(file, lines);
                verifyParses(file, path);
                return;
            }
            depth++;
        }
        throw new IOException("no key '" + path + "' in the shipped config.yml");
    }

    /** Fails at the edit rather than letting a broken config surface as silence. */
    private static void verifyParses(Path file, String justChanged) throws IOException {
        try (java.io.InputStream in = Files.newInputStream(file)) {
            new org.yaml.snakeyaml.Yaml().load(in);
        } catch (Exception malformed) {
            throw new IOException("editing '" + justChanged + "' left config.yml unparseable: "
                    + malformed.getMessage(), malformed);
        }
    }

    /** Runs a console command, as an operator would. */
    void command(String line) throws IOException {
        console.write(line + "\n");
        console.flush();
    }

    List<String> log() { return Collections.unmodifiableList(new ArrayList<>(log)); }

    String tail(int lines) {
        final List<String> all = new ArrayList<>(log);
        final List<String> last = all.subList(Math.max(0, all.size() - lines), all.size());
        return String.join("\n", last);
    }

    private void startLogPump() {
        final Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) log.add(line);
            } catch (IOException closed) {
                // Process ended; nothing further to read.
            }
        }, "mc-log");
        t.setDaemon(true);
        t.start();
    }

    @Override public void close() throws Exception {
        if (process.isAlive()) {
            try {
                // "stop" rather than a kill, because the shutdown path is itself under
                // test: the stop message is queued, then the queue drains, then Adventure
                // closes. Killing the process would skip exactly that.
                command("stop");
                process.waitFor(60, TimeUnit.SECONDS);
            } catch (IOException ignored) {
                // Already gone.
            }
        }
        if (process.isAlive()) process.destroyForcibly();
    }
}
