package com.discordlogger.acceptance;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stands in for Discord, so a real server's posts can be asserted on.
 *
 * <h2>Why a proxy and not a fake URL</h2>
 *
 * <p>The plugin refuses any webhook that is not on {@code discord.com}, and rightly so.
 * Rather than weaken that check for testing, or edit the JAR under test, this steers
 * the traffic underneath it: the server JVM is started with {@code https.proxyHost}
 * pointing here and a truststore containing a throwaway CA. The plugin posts to a
 * genuine {@code https://discord.com/api/webhooks/...} URL, believes it is talking to
 * Discord, and this answers.
 *
 * <p>That is what makes the test honest. The artifact under test is the shipped JAR,
 * byte for byte, doing exactly what it does in production.
 *
 * <h2>Traffic that is not Discord</h2>
 *
 * <p>The proxy setting is JVM-wide, so the server's own calls come through here too --
 * update checks, Mojang, bStats. Those are tunnelled to the real destination
 * unmodified rather than blocked, because a server that cannot reach the internet
 * behaves differently from one that can, and the difference would show up as
 * mysterious test failures rather than as anything to do with the plugin.
 */
public final class FakeDiscord implements AutoCloseable {

    /** One captured request. */
    public static final class Recorded {
        public final String method;
        public final String path;
        public final Map<String, String> headers;
        public final String body;

        Recorded(String method, String path, Map<String, String> headers, String body) {
            this.method = method;
            this.path = path;
            this.headers = headers;
            this.body = body;
        }

        public String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }

        /** True when the JSON body contains this exact snippet. */
        public boolean bodyContains(String snippet) {
            return body != null && body.contains(snippet);
        }

        @Override public String toString() {
            return method + " " + path + "  " + body;
        }
    }

    private final ServerSocket listener;
    private final SSLSocketFactory tlsAsDiscord;
    private final Path trustStore;
    private final BlockingQueue<Recorded> received = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(true);

    private FakeDiscord(ServerSocket listener, SSLSocketFactory tls, Path trustStore) {
        this.listener = listener;
        this.tlsAsDiscord = tls;
        this.trustStore = trustStore;
    }

    /** Generates a CA, a discord.com certificate and a truststore, then starts listening. */
    public static FakeDiscord start(Path workDir) throws Exception {
        final Path certs = workDir.resolve("certs");
        Files.createDirectories(certs);
        Certs.generate(certs);

        final KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(certs.resolve("server.p12"))) {
            ks.load(in, Certs.PASSWORD.toCharArray());
        }
        final KeyManagerFactory kmf =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, Certs.PASSWORD.toCharArray());
        final SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);

        final FakeDiscord fake = new FakeDiscord(
                new ServerSocket(0), ctx.getSocketFactory(), certs.resolve("truststore.p12"));
        final Thread accept = new Thread(fake::acceptLoop, "fake-discord");
        accept.setDaemon(true);
        accept.start();
        return fake;
    }

    public int proxyPort() { return listener.getLocalPort(); }
    public Path trustStorePath() { return trustStore; }
    public String trustStorePassword() { return Certs.PASSWORD; }

    /** A webhook URL the plugin will accept, pointing at a webhook that does not exist. */
    public String webhookUrl() {
        return "https://discord.com/api/webhooks/1234567890123456789/acceptance-test-token";
    }

    /**
     * A socket factory trusting this fake, for callers inside the test JVM.
     *
     * <p>Needed because {@code javax.net.ssl.trustStore} is read once, when the default
     * SSLContext first initialises. Any earlier HTTPS call in the same JVM -- a server
     * JAR download, say -- fixes it before a later test can set it, and the failure is
     * an opaque PKIX error rather than anything pointing at the ordering.
     */
    public SSLSocketFactory clientSocketFactory() throws Exception {
        final KeyStore trust = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(trustStore)) {
            trust.load(in, Certs.PASSWORD.toCharArray());
        }
        final TrustManagerFactory tmf =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trust);
        final SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tmf.getTrustManagers(), null);
        return ctx.getSocketFactory();
    }

    /** A second webhook id, for proving a per-event route actually went elsewhere. */
    public static final String ALTERNATE_ID = "9876543210987654321";

    /** A different, equally valid Discord URL. Routing shows up as a different path. */
    public String alternateWebhookUrl() {
        return "https://discord.com/api/webhooks/" + ALTERNATE_ID + "/acceptance-alt-token";
    }

    /** The JVM flags a server must be started with for its traffic to arrive here. */
    public List<String> jvmArgs() {
        final List<String> args = new ArrayList<>();
        args.add("-Dhttps.proxyHost=127.0.0.1");
        args.add("-Dhttps.proxyPort=" + proxyPort());
        args.add("-Djavax.net.ssl.trustStore=" + trustStore.toAbsolutePath());
        args.add("-Djavax.net.ssl.trustStorePassword=" + Certs.PASSWORD);
        args.add("-Djavax.net.ssl.trustStoreType=PKCS12");
        return args;
    }

    /** Waits for the next post, or fails the calling test if none arrives. */
    public Recorded awaitPost(long timeout, TimeUnit unit) throws InterruptedException {
        final Recorded r = received.poll(timeout, unit);
        if (r == null) {
            throw new AssertionError("no request reached the fake Discord within "
                    + timeout + " " + unit.name().toLowerCase(Locale.ROOT)
                    + ". The plugin either sent nothing, or its traffic did not come "
                    + "through the proxy.");
        }
        return r;
    }

    /**
     * Waits for a post whose body matches, ignoring anything else that arrives.
     *
     * <p>Needed because a server is never doing only the thing under test. Driving an
     * event from the console makes the plugin log the console command too, so the first
     * post to arrive is frequently not the one being asserted. Matching on content
     * rather than taking the first is the difference between a test that means something
     * and one that passes on whatever happened to be quickest.
     */
    public Recorded awaitPostMatching(java.util.function.Predicate<Recorded> wanted,
                                      long timeout, TimeUnit unit) throws InterruptedException {
        final long deadline = System.nanoTime() + unit.toNanos(timeout);
        final List<Recorded> seen = new ArrayList<>();
        while (System.nanoTime() < deadline) {
            final Recorded r = received.poll(500, TimeUnit.MILLISECONDS);
            if (r == null) continue;
            seen.add(r);
            if (wanted.test(r)) return r;
        }
        throw new AssertionError("no matching post within " + timeout + " " + unit
                + ". Saw " + seen.size() + " other post(s):\n  "
                + seen.stream().map(Object::toString)
                      .collect(java.util.stream.Collectors.joining("\n  ")));
    }

    /** Everything captured so far, oldest first. */
    public List<Recorded> all() { return new ArrayList<>(received); }

    /** Asserts nothing arrives in the given window. Used for "this toggle is off". */
    public void assertSilent(long timeout, TimeUnit unit) throws InterruptedException {
        final Recorded r = received.poll(timeout, unit);
        if (r != null) {
            throw new AssertionError("expected nothing to be sent, but got: " + r);
        }
    }

    public void reset() { received.clear(); }

    @Override public void close() {
        running.set(false);
        try { listener.close(); } catch (IOException ignored) { }
    }

    // ------------------------------------------------------------------ internals

    private void acceptLoop() {
        while (running.get()) {
            try {
                final Socket client = listener.accept();
                final Thread t = new Thread(() -> handle(client), "fake-discord-conn");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (running.get()) {
                    // A closed listener during shutdown is expected; anything else is not.
                    System.err.println("[FakeDiscord] accept failed: " + e);
                }
                return;
            }
        }
    }

    private void handle(Socket raw) {
        try (Socket client = raw) {
            // Not closed deliberately: closing the reader closes the socket, and the
            // socket must survive to carry the tunnelled TLS session. The try-with
            // -resources on the socket itself is what releases both.
            @SuppressWarnings("resource")
            final BufferedReader head = new BufferedReader(
                    new InputStreamReader(client.getInputStream(), StandardCharsets.ISO_8859_1));
            final String requestLine = head.readLine();
            if (requestLine == null) return;
            while (true) {
                final String l = head.readLine();
                if (l == null || l.isEmpty()) break;
            }

            if (!requestLine.startsWith("CONNECT ")) return;
            final String hostPort = requestLine.split(" ")[1];
            final String host = hostPort.contains(":")
                    ? hostPort.substring(0, hostPort.indexOf(':')) : hostPort;

            client.getOutputStream().write(
                    "HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            client.getOutputStream().flush();

            if (host.endsWith("discord.com")) {
                interceptAsDiscord(client);
            } else {
                tunnel(client, host, portOf(hostPort));
            }
        } catch (Exception e) {
            // A dropped connection is normal when a server shuts down mid-request.
        }
    }

    private static int portOf(String hostPort) {
        final int i = hostPort.indexOf(':');
        return i < 0 ? 443 : Integer.parseInt(hostPort.substring(i + 1));
    }

    private void interceptAsDiscord(Socket client) throws Exception {
        try (SSLSocket tls = (SSLSocket) tlsAsDiscord.createSocket(
                client, null, client.getPort(), false)) {
            tls.setUseClientMode(false);
            @SuppressWarnings("resource")   // closed with the SSLSocket above
            final BufferedReader in = new BufferedReader(
                    new InputStreamReader(tls.getInputStream(), StandardCharsets.UTF_8));

            final String requestLine = in.readLine();
            if (requestLine == null) return;
            final String[] parts = requestLine.split(" ");

            final Map<String, String> headers = new LinkedHashMap<>();
            int length = 0;
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                final int colon = line.indexOf(':');
                if (colon > 0) {
                    final String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                    final String value = line.substring(colon + 1).trim();
                    headers.put(name, value);
                    if (name.equals("content-length")) {
                        try {
                            length = Integer.parseInt(value);
                        } catch (NumberFormatException notANumber) {
                            // A malformed header is the client's problem, not a reason to
                            // take down the listener the whole suite depends on.
                            length = 0;
                        }
                    }
                }
            }

            String body = "";
            if (length > 0) {
                final char[] buf = new char[length];
                int read = 0;
                while (read < length) {
                    final int n = in.read(buf, read, length - read);
                    if (n < 0) break;
                    read += n;
                }
                body = new String(buf, 0, Math.max(read, 0));
            }

            received.add(new Recorded(parts.length > 0 ? parts[0] : "?",
                    parts.length > 1 ? parts[1] : "?", headers, body));

            // Discord answers 204 to a successful webhook execute. The rate-limit headers
            // are sent because WebhookQueue paces itself on them, and a queue that never
            // sees them behaves differently from one that does.
            final OutputStream out = tls.getOutputStream();
            out.write(("HTTP/1.1 204 No Content\r\n"
                    + "x-ratelimit-remaining: 5\r\n"
                    + "x-ratelimit-reset-after: 1\r\n"
                    + "Content-Length: 0\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    /**
     * Anything that is not Discord is passed through untouched.
     *
     * <p>Flagged as request forgery, and the shape is real: a host named by the client
     * is dialled. It is also the entire point -- this is a proxy, and the client is a
     * Minecraft server this suite launched itself, on a loopback port, inside a test.
     * Refusing would break the server's own update checks, which is what the tunnel
     * exists to preserve.
     *
     * <p>Bounded rather than trusted: only 443, and only a plausible hostname, so a
     * malformed CONNECT cannot turn the listener into a port scanner.
     */
    private void tunnel(Socket client, String host, int port) throws IOException {
        if (port != 443 || !host.matches("[A-Za-z0-9._-]{1,253}")) {
            return;
        }
        try (Socket upstream = new Socket(host, port)) {
            final Thread up = pipe(client.getInputStream(), upstream.getOutputStream());
            pipe(upstream.getInputStream(), client.getOutputStream()).join();
            up.interrupt();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static Thread pipe(InputStream from, OutputStream to) {
        final Thread t = new Thread(() -> {
            final byte[] buf = new byte[8192];
            try {
                int n;
                while ((n = from.read(buf)) != -1) { to.write(buf, 0, n); to.flush(); }
            } catch (IOException closed) {
                // Either end going away ends the tunnel; nothing to report.
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }
}
