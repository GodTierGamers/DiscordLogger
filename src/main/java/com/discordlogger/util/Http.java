package com.discordlogger.util;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The plugin's HTTP client, on {@link HttpURLConnection}.
 *
 * <p>{@code java.net.http.HttpClient} is Java 11 and the plugin targets Java 8, so
 * this replaces it. Everything that touches Discord goes through here, which is
 * deliberate: the rate-limit handling in {@code WebhookQueue} depends on headers
 * being read exactly as before, and one implementation is one place to get that
 * right rather than three.
 *
 * <h2>Four differences from HttpClient that had to be handled</h2>
 *
 * <ol>
 *   <li><b>Error bodies come from a different stream.</b> {@code getInputStream()}
 *       throws on any 4xx or 5xx; the body is on {@code getErrorStream()}. Reading
 *       only the former would have turned every Discord error into an exception and
 *       lost the status with it — and the status is the entire point for a 404 or a
 *       429.</li>
 *   <li><b>Redirects.</b> {@code HttpClient} does not follow them by default;
 *       {@code HttpURLConnection} does. Following is switched off, so behaviour
 *       matches what the plugin has always done.</li>
 *   <li><b>Two timeouts, not one.</b> Connect and read are separate, and only
 *       setting one leaves the other unbounded — a half-open socket would hang a
 *       webhook worker indefinitely.</li>
 *   <li><b>Connections must be released.</b> {@code disconnect()} in a finally,
 *       or a busy server leaks sockets.</li>
 * </ol>
 */
public final class Http {

    private Http() {}

    /**
     * One response: the status, the body, and the headers the queue paces itself on.
     *
     * <p>Headers are <b>copied</b> rather than read back through the connection. The
     * connection is disconnected in a finally before the caller ever sees this, and
     * reading headers from a disconnected connection is not a contract worth relying
     * on -- the failure would be a silently absent rate-limit header, which does not
     * look like a bug until a server is being throttled.
     */
    public static final class Result {
        private final int status;
        private final String body;
        private final Map<String, String> headers;

        Result(int status, String body, Map<String, String> headers) {
            this.status = status;
            this.body = body;
            this.headers = headers;
        }

        public int status() { return status; }
        public String body() { return body; }

        /** Case-insensitive, like {@code HttpResponse.headers().firstValue}. */
        public String header(String name) {
            if (headers == null || name == null) return null;
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }

    /** A POST of JSON. Never throws: an unreachable host comes back as status 0. */
    public static Result postJson(String url, String json, int timeoutMs) {
        HttpURLConnection conn = null;
        try {
            conn = open(url, "POST", timeoutMs, null);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            final byte[] payload = json.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(payload.length);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(payload);
            }
            return read(conn);
        } catch (Exception unreachable) {
            // Status 0 = never reached the far end (DNS, timeout, TLS). Retryable.
            return new Result(0, "", null);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** A GET, with optional request headers. Never throws; status 0 means unreachable. */
    public static Result get(String url, Map<String, String> headers, int timeoutMs) {
        HttpURLConnection conn = null;
        try {
            conn = open(url, "GET", timeoutMs, headers);
            return read(conn);
        } catch (Exception unreachable) {
            return new Result(0, "", null);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static HttpURLConnection open(String url, String method, int timeoutMs,
                                          Map<String, String> headers) throws Exception {
        final HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        // HttpClient's default policy is NEVER; match it rather than inherit
        // HttpURLConnection's, which follows.
        conn.setInstanceFollowRedirects(false);
        if (headers != null) {
            for (Map.Entry<String, String> h : headers.entrySet()) {
                conn.setRequestProperty(h.getKey(), h.getValue());
            }
        }
        return conn;
    }

    /**
     * Status plus body, taking the body from whichever stream actually has it.
     *
     * <p>The status is captured before any body read, so a stream that fails still
     * reports the status the caller needs. A 404 with an unreadable body is still a
     * 404, and treating it as unreachable would send the queue into a retry loop
     * against a webhook that is definitively gone.
     */
    private static Result read(HttpURLConnection conn) throws Exception {
        final int status = conn.getResponseCode();
        String body = "";
        try (InputStream in = (status >= 400) ? conn.getErrorStream() : conn.getInputStream()) {
            if (in != null) {
                final ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
                final byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                body = new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (Exception bodyUnreadable) {
            body = "";
        }
        // Snapshot every header before the caller can outlive the connection.
        final Map<String, String> headers = new HashMap<>();
        for (Map.Entry<String, List<String>> e : conn.getHeaderFields().entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isEmpty()) continue;
            headers.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().get(0));
        }
        return new Result(status, body, headers);
    }
}
