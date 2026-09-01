package com.discordlogger.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The contract {@code WebhookQueue} paces itself on.
 *
 * <p>No network here: these pin the parts that would fail silently. A rate-limit
 * header returning null because of a case mismatch does not look like a bug — it
 * looks like Discord not sending one, and the queue simply stops pacing.
 */
class HttpTest {

    private static Http.Result result(int status, Map<String, String> headers) {
        return new Http.Result(status, "", headers);
    }

    @Test
    @DisplayName("header lookup is case-insensitive, like HttpResponse.headers()")
    void headersAreCaseInsensitive() {
        // HttpURLConnection reports whatever case the server sent. Discord sends
        // lowercase today; nothing guarantees it keeps doing so.
        final Map<String, String> h = new HashMap<>();
        h.put("x-ratelimit-remaining", "4");
        final Http.Result r = result(200, h);

        assertEquals("4", r.header("x-ratelimit-remaining"));
        assertEquals("4", r.header("X-RateLimit-Remaining"));
        assertEquals("4", r.header("X-RATELIMIT-REMAINING"));
    }

    @Test
    @DisplayName("a missing header is null, not an exception")
    void missingHeaderIsNull() {
        assertNull(result(200, new HashMap<>()).header("retry-after"));
        assertNull(result(200, null).header("retry-after"));
        assertNull(result(200, new HashMap<>()).header(null));
    }

    @Test
    @DisplayName("an unreachable host is status 0, never a throw")
    void unreachableIsStatusZero() {
        // The queue treats 0 as retryable. A thrown exception would kill the worker
        // thread and stop that destination permanently.
        final Http.Result r = Http.get("http://127.0.0.1:1/nothing", null, 250);
        assertEquals(0, r.status());
        assertNull(r.header("anything"));
        assertTrue(r.body().isEmpty());
    }

    @Test
    @DisplayName("a malformed URL is status 0 too")
    void malformedUrlIsStatusZero() {
        assertEquals(0, Http.get("not a url", null, 250).status());
        assertEquals(0, Http.postJson("also not a url", "{}", 250).status());
    }
}
