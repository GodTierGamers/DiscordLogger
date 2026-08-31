package com.discordlogger.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every request carries a User-Agent.
 *
 * <p>Discord sits behind Cloudflare, requires a User-Agent, and answers <b>403</b> to
 * the JVM default of {@code Java/1.8.0_502}. Nothing caught that: the unit tests do no
 * networking, and a live check from a modern JDK sends a different default that gets
 * through. It took a real Java 8 server to surface it, by which point every webhook
 * post was being rejected.
 *
 * <p>So this reads the bytes actually written to a socket rather than trusting the
 * code that writes them. It needs no internet: a loopback listener captures one
 * request, answers, and the headers are asserted.
 */
class HttpUserAgentTest {

    /** Captures the request headers of a single connection. */
    private static List<String> captureHeadersOf(RequestSender send) throws Exception {
        final List<String> headers = new ArrayList<>();
        final CountDownLatch done = new CountDownLatch(1);

        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(5000);
            final int port = server.getLocalPort();

            final Thread listener = new Thread(() -> {
                try (Socket s = server.accept();
                     BufferedReader in = new BufferedReader(
                             new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = in.readLine()) != null && !line.isEmpty()) headers.add(line);
                    final OutputStream out = s.getOutputStream();
                    out.write("HTTP/1.1 204 No Content\r\nContent-Length: 0\r\n\r\n"
                            .getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } catch (Exception ignored) {
                    // Assertions below fail on an empty capture, which is the real signal.
                } finally {
                    done.countDown();
                }
            });
            listener.setDaemon(true);
            listener.start();

            send.sendTo("http://127.0.0.1:" + port + "/api/webhooks/1/token");
            done.await(5, TimeUnit.SECONDS);
        }
        return headers;
    }

    private interface RequestSender { void sendTo(String url); }

    private static String userAgentIn(List<String> headers) {
        for (String h : headers) {
            if (h.toLowerCase().startsWith("user-agent:")) return h.substring("user-agent:".length()).trim();
        }
        return null;
    }

    @Test
    @DisplayName("a webhook POST identifies the plugin, not the JVM")
    void postSendsOurUserAgent() throws Exception {
        final List<String> headers = captureHeadersOf(
                url -> Http.postJson(url, "{\"content\":\"x\"}", 4000));

        assertFalse(headers.isEmpty(), "the listener captured no request at all");
        final String ua = userAgentIn(headers);
        assertTrue(ua != null && ua.startsWith("DiscordLogger"),
                "webhook POSTs must identify the plugin; Discord answers 403 to the JVM "
                        + "default. Sent: " + ua);
        assertFalse(ua.startsWith("Java/"),
                "sending the JVM default is exactly what Cloudflare rejects: " + ua);
    }

    @Test
    @DisplayName("GETs carry one too")
    void getSendsOurUserAgent() throws Exception {
        final List<String> headers = captureHeadersOf(url -> Http.get(url, null, 4000));

        assertFalse(headers.isEmpty(), "the listener captured no request at all");
        final String ua = userAgentIn(headers);
        assertTrue(ua != null && !ua.startsWith("Java/"), "sent: " + ua);
    }
}
