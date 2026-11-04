package com.discordlogger.webhook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which destinations survive a reload.
 *
 * <p>Destinations are created on first use and used to live until the plugin disabled,
 * so changing {@code webhook.url} left the old one with a worker thread parked in
 * {@code poll(250ms)} and its URL held in memory for the rest of the run — and listed
 * in {@code /discordlogger status} as though it still existed.
 *
 * <p>Only the decision is covered here. The draining itself belongs to a worker thread
 * that posts over HTTP, so exercising it in a unit test would mean either a real request
 * or a fake server; what can go wrong in the decision is a URL being retired when it
 * should not be, and that is testable without either.
 */
class DestinationRetirementTest {

    private static final String A = "https://discord.com/api/webhooks/111/aaa";
    private static final String B = "https://discord.com/api/webhooks/222/bbb";

    @Test
    @DisplayName("a destination the config still routes to is kept")
    void keepsWhatIsRouted() {
        final Set<String> live = new HashSet<>();
        live.add(A);
        live.add(B);

        assertTrue(WebhookQueue.shouldKeep(A, live));
        assertTrue(WebhookQueue.shouldKeep(B, live));
    }

    @Test
    @DisplayName("a destination nothing routes to any more is retired")
    void retiresWhatIsGone() {
        assertFalse(WebhookQueue.shouldKeep(A, Collections.singleton(B)),
                "the whole point: the old URL goes when the config stops naming it");
        assertFalse(WebhookQueue.shouldKeep(A, Collections.<String>emptySet()),
                "no webhook configured at all means no destination should survive");
    }

    @Test
    @DisplayName("an unknown live set keeps everything rather than silencing the plugin")
    void failsOpenOnNull() {
        // Null means "could not work it out". Keeping an idle thread costs a megabyte;
        // retiring a live webhook costs every log line that was going to it, silently.
        assertTrue(WebhookQueue.shouldKeep(A, null));
        assertTrue(WebhookQueue.shouldKeep(null, null));
    }

    @Test
    @DisplayName("URLs are matched exactly, because the token is part of the identity")
    void matchesExactly() {
        // Two webhooks in the same channel differ only by token, and the id is what
        // Discord rate-limits on. A loose match would merge two destinations into one.
        assertFalse(WebhookQueue.shouldKeep(A, Collections.singleton(A + "x")));
        assertFalse(WebhookQueue.shouldKeep(A.toUpperCase(), Collections.singleton(A)),
                "a token is case-sensitive, so a case-insensitive match would be wrong");
    }
}
