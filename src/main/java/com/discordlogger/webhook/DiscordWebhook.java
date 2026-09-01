package com.discordlogger.webhook;

import com.discordlogger.util.Http;
import com.discordlogger.util.Strings;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class DiscordWebhook {
    private DiscordWebhook() {}

    // Footer icon for all embeds (served from GitHub Pages alongside the plugin source)
    private static final String FOOTER_ICON_URL =
            "https://discordlogger.godtiergamers.xyz/assets/icons/DiscordLogger-Logo-removebg.png";

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Plain content message. */
    public static void sendAsync(JavaPlugin plugin, String url, String content) {
        if (url == null || Strings.isBlank(url)) return;
        dispatch(plugin, url, "{\"content\":\"" + escape(content) + "\"}");
    }

    /** Single-embed message (no fields). */
    public static void sendEmbed(
            JavaPlugin plugin, String url,
            String title, String description, int color,
            String isoTimestampUtc, String authorName, String footerText, String thumbnailUrl
    ) {
        if (url == null || Strings.isBlank(url)) return;

        StringBuilder sb = new StringBuilder(1024);
        sb.append("{\"embeds\":[{");

        if (title != null && !Strings.isBlank(title)) {
            sb.append("\"title\":\"").append(escape(title)).append("\",");
        }
        sb.append("\"description\":\"").append(escape(description == null ? "" : description)).append("\",");
        sb.append("\"color\":").append(color).append(",");

        if (authorName != null && !Strings.isBlank(authorName)) {
            sb.append("\"author\":{\"name\":\"").append(escape(authorName)).append("\"},");
        }
        if (footerText != null && !Strings.isBlank(footerText)) {
            sb.append("\"footer\":{")
                    .append("\"text\":\"").append(escape(footerText)).append("\",")
                    .append("\"icon_url\":\"").append(escape(FOOTER_ICON_URL)).append("\"")
                    .append("},");
        }
        if (thumbnailUrl != null && !Strings.isBlank(thumbnailUrl)) {
            sb.append("\"thumbnail\":{\"url\":\"").append(escape(thumbnailUrl)).append("\"},");
        }
        if (isoTimestampUtc != null && !Strings.isBlank(isoTimestampUtc)) {
            sb.append("\"timestamp\":\"").append(escape(isoTimestampUtc)).append("\",");
        }

        trimComma(sb);
        sb.append("}]}");

        dispatch(plugin, url, sb.toString());
    }

    /** Embed with structured fields. */
    public static void sendEmbedWithFields(
            JavaPlugin plugin,
            String url,
            String title,
            String description,
            int color,
            String timestampIso,
            String author,
            String footer,
            String thumbnailUrl,
            String[][] fields // each element: { name, value, inline("true"/"false") }
    ) {
        if (url == null || Strings.isBlank(url)) return;
        dispatch(plugin, url, buildEmbedJson(title, description, color, timestampIso,
                author, footer, thumbnailUrl, fields));
    }

    /**
     * Builds the embed payload. Split from the send so the exact JSON that reaches
     * Discord can be asserted in a test — it is the user-visible output format, and
     * a silent change to it is not something a compiler catches.
     */
    static String buildEmbedJson(
            String title,
            String description,
            int color,
            String timestampIso,
            String author,
            String footer,
            String thumbnailUrl,
            String[][] fields
    ) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\"content\":null,\"embeds\":[{");

        if (title != null)       sb.append("\"title\":\"").append(escape(title)).append("\",");
        if (description != null) sb.append("\"description\":\"").append(escape(description)).append("\",");
        sb.append("\"color\":").append(color).append(",");

        if (author != null) {
            sb.append("\"author\":{")
                    .append("\"name\":\"").append(escape(author)).append("\"")
                    .append("},");
        }
        if (footer != null) {
            sb.append("\"footer\":{")
                    .append("\"text\":\"").append(escape(footer)).append("\",")
                    .append("\"icon_url\":\"").append(escape(FOOTER_ICON_URL)).append("\"")
                    .append("},");
        }
        if (thumbnailUrl != null) {
            sb.append("\"thumbnail\":{")
                    .append("\"url\":\"").append(escape(thumbnailUrl)).append("\"")
                    .append("},");
        }
        if (timestampIso != null) {
            sb.append("\"timestamp\":\"").append(escape(timestampIso)).append("\",");
        }

        if (fields != null && fields.length > 0) {
            sb.append("\"fields\":[");
            boolean first = true;
            for (String[] f : fields) {
                if (f == null || f.length < 3) continue;
                if (!first) sb.append(',');
                first = false;
                boolean inline = "true".equalsIgnoreCase(f[2]);
                sb.append('{')
                        .append("\"name\":\"").append(escape(f[0])).append("\",")
                        .append("\"value\":\"").append(escape(f[1])).append("\",")
                        .append("\"inline\":").append(inline)
                        .append('}');
            }
            sb.append("],");
        }

        trimComma(sb);
        sb.append("}],\"attachments\":[]}");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Shared internals
    // -------------------------------------------------------------------------

    /**
     * Hands the payload to {@link WebhookQueue}, which serialises sends, paces them
     * against Discord's rate limits and retries transient failures. Returns
     * immediately — callers may be on the main thread and must never wait on HTTP.
     */
    private static void dispatch(JavaPlugin plugin, String url, String json) {
        WebhookQueue.enqueue(url, json);
    }

    /** Outcome of a single POST, with the rate-limit facts the queue needs to pace itself. */
    public static final class Response {
        private final int status;
        private final Long remaining;      // X-RateLimit-Remaining, null if absent
        private final Long resetAfterMs;   // X-RateLimit-Reset-After, null if absent
        private final long retryAfterMs;   // from Retry-After on a 429; 0 otherwise

        public Response(int status, Long remaining, Long resetAfterMs, long retryAfterMs) {
            this.status = status;
            this.remaining = remaining;
            this.resetAfterMs = resetAfterMs;
            this.retryAfterMs = retryAfterMs;
        }

        public int status()          { return status; }
        public Long remaining()      { return remaining; }
        public Long resetAfterMs()   { return resetAfterMs; }
        public long retryAfterMs()   { return retryAfterMs; }

        public boolean success()      { return status == 200 || status == 204; }
        public boolean rateLimited()  { return status == 429; }
        /** Transient: worth retrying the same payload. Network failures use status 0. */
        public boolean retryable()    { return status == 0 || status >= 500; }
    }

    /**
     * Performs one POST. Reports the outcome instead of logging it — the queue owns
     * the decision to retry, wait or give up, and only it knows the surrounding context.
     */
    static Response post(String url, String json) {
        final Http.Result res = Http.postJson(url, json, (int) TIMEOUT.toMillis());
        if (res.status() == 0) {
            // Never reached Discord (DNS, timeout, TLS). Retryable.
            return new Response(0, null, null, 0L);
        }

        final int status = res.status();
        final Long remaining = headerAsLong(res, "x-ratelimit-remaining", 1.0);
        final Long resetAfterMs = headerAsLong(res, "x-ratelimit-reset-after", 1000.0);

        long retryAfterMs = 0L;
        if (status == 429) {
            // Retry-After is seconds (possibly fractional); reset-after is the same
            // value under a different name. Fall back to a second if neither parses.
            final Long fromRetryAfter = headerAsLong(res, "retry-after", 1000.0);
            retryAfterMs = (fromRetryAfter != null) ? fromRetryAfter
                    : (resetAfterMs != null ? resetAfterMs : 1000L);
        }

        return new Response(status, remaining, resetAfterMs, retryAfterMs);
    }

    /**
     * Asks Discord whether a webhook still exists, without posting anything.
     *
     * <p>A GET on a webhook URL returns 200 with its JSON while it lives and 404 once
     * it has been deleted in Discord. That distinction is the whole point: a deleted
     * webhook is otherwise only discovered when the first real event 404s, which in
     * production meant 345 messages posted into a dead webhook across three and a half
     * hours before anyone noticed.
     *
     * <p>Returns the raw status so the caller can tell "gone" (404) from "could not
     * ask" (0, or a 5xx) — those need different wording, since only one of them is
     * the admin's problem.
     */
    public static int probe(String url) {
        return Http.get(url, null, (int) TIMEOUT.toMillis()).status();
    }

    /** Reads a numeric header and scales it (headers are in seconds; we work in millis). */
    private static Long headerAsLong(Http.Result res, String name, double scale) {
        final String v = res.header(name);
        if (v == null) return null;
        try {
            return (long) Math.ceil(Double.parseDouble(v.trim()) * scale);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    /** JSON string escaper. Handles all control characters correctly. */
    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        // Encode remaining control characters as Unicode escapes
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static void trimComma(StringBuilder sb) {
        if (sb.charAt(sb.length() - 1) == ',') sb.setLength(sb.length() - 1);
    }
}