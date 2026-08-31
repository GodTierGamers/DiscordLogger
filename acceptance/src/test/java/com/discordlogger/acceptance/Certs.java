package com.discordlogger.acceptance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A throwaway CA and a {@code discord.com} certificate, made with keytool.
 *
 * <p>Generated rather than committed: a checked-in private key is a checked-in private
 * key, however loudly the filename says "test", and one signed for {@code discord.com}
 * is a particularly bad thing to have lying around a public repository. These live in
 * a temporary directory and die with the test run.
 *
 * <p>keytool rather than a crypto library because every JDK already has it, and adding
 * BouncyCastle to sign one certificate would be a dependency for the sake of avoiding
 * a subprocess.
 */
final class Certs {

    static final String PASSWORD = "acceptance";

    private Certs() {}

    /** Writes server.p12 (the discord.com identity) and truststore.p12 (the CA). */
    static void generate(Path dir) throws Exception {
        if (Files.exists(dir.resolve("truststore.p12"))) return;   // already made this run

        final Path ca = dir.resolve("ca.p12");
        final Path caPem = dir.resolve("ca.pem");
        final Path server = dir.resolve("server.p12");
        final Path csr = dir.resolve("discord.csr");
        final Path signed = dir.resolve("discord.pem");

        keytool(dir, "-genkeypair", "-alias", "testca", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "3650", "-dname", "CN=DiscordLogger Acceptance CA,O=test",
                "-ext", "bc:c", "-keystore", ca.toString(), "-storetype", "PKCS12");
        keytool(dir, "-exportcert", "-alias", "testca", "-keystore", ca.toString(),
                "-rfc", "-file", caPem.toString());

        keytool(dir, "-genkeypair", "-alias", "discord", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "3650", "-dname", "CN=discord.com,O=test",
                "-keystore", server.toString(), "-storetype", "PKCS12");
        keytool(dir, "-certreq", "-alias", "discord", "-keystore", server.toString(),
                "-file", csr.toString());
        keytool(dir, "-gencert", "-alias", "testca", "-keystore", ca.toString(),
                "-ext", "san=dns:discord.com,dns:ptb.discord.com,dns:canary.discord.com",
                "-infile", csr.toString(), "-outfile", signed.toString(), "-validity", "3650");

        // The leaf must be presented with its issuer, or the client cannot build a chain.
        keytool(dir, "-importcert", "-alias", "testca", "-keystore", server.toString(),
                "-file", caPem.toString(), "-noprompt");
        keytool(dir, "-importcert", "-alias", "discord", "-keystore", server.toString(),
                "-file", signed.toString(), "-noprompt");

        keytool(dir, "-importcert", "-alias", "testca",
                "-keystore", dir.resolve("truststore.p12").toString(),
                "-storetype", "PKCS12", "-file", caPem.toString(), "-noprompt");
    }

    private static void keytool(Path dir, String... args) throws IOException, InterruptedException {
        final List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
        for (String a : args) cmd.add(a);
        cmd.add("-storepass"); cmd.add(PASSWORD);
        cmd.add("-keypass");   cmd.add(PASSWORD);

        final Process p = new ProcessBuilder(cmd)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
        final String output = new String(p.getInputStream().readAllBytes());
        if (!p.waitFor(60, TimeUnit.SECONDS) || p.exitValue() != 0) {
            throw new IOException("keytool " + String.join(" ", args) + " failed:\n" + output);
        }
    }
}
