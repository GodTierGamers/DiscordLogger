package com.discordlogger.webhook;

import com.discordlogger.webhook.WebhookQueue.Payload;
import com.discordlogger.webhook.WebhookQueue.Payload.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The batching rules, tested without a thread, a queue worker or a network.
 *
 * <p>These exist because of what the live metrics said: 3.47 million messages dropped
 * past the queue cap, every one of those polls alongside a rate-limit wait and most
 * with no failed send at all. Batching is the fix for that ceiling, and the two things
 * it must not break are ordering and the shape of the JSON Discord receives.
 */
class WebhookBatchTest {

    private static Payload embed(String body) { return new Payload(Kind.EMBED, body); }
    private static Payload content(String body) { return new Payload(Kind.CONTENT, body); }

    private static Queue<Payload> queueOf(Payload... items) {
        return new ArrayDeque<>(List.of(items));
    }

    @Test
    @DisplayName("a lone event is sent on its own, with nothing held back for company")
    void aLoneEventDoesNotWait() {
        final Queue<Payload> empty = new ArrayDeque<>();
        final List<Payload> batch = WebhookQueue.takeBatch(embed("{\"a\":1}"), empty);

        assertEquals(1, batch.size());
        // The point: batching consumes a backlog that already exists. It never delays
        // a message to see whether another turns up, which would tax every quiet
        // server to help the few busy ones.
        assertEquals("{\"content\":null,\"embeds\":[{\"a\":1}],\"attachments\":[]," + WebhookQueue.NO_MENTIONS + "}",
                WebhookQueue.envelope(batch));
    }

    @Test
    @DisplayName("ten embeds travel together and the eleventh waits its turn")
    void tenIsTheCeiling() {
        final Payload[] rest = new Payload[12];
        for (int i = 0; i < rest.length; i++) rest[i] = embed("{\"n\":" + i + "}");
        final Queue<Payload> waiting = queueOf(rest);

        final List<Payload> batch = WebhookQueue.takeBatch(embed("{\"n\":-1}"), waiting);

        assertEquals(WebhookQueue.MAX_EMBEDS_PER_MESSAGE, batch.size(),
                "Discord accepts ten embeds in a message and no more");
        assertEquals(3, waiting.size(), "the rest stay queued for the next send");
        assertEquals("{\"n\":9}", waiting.peek().body, "and they stay in order");
    }

    @Test
    @DisplayName("the batch stops short of Discord's combined embed limit")
    void sizeGuardStopsTheBatch() {
        final String big = "{\"description\":\"" + "x".repeat(2000) + "\"}";
        final Queue<Payload> waiting = queueOf(embed(big), embed(big), embed(big));

        final List<Payload> batch = WebhookQueue.takeBatch(embed(big), waiting);

        assertTrue(batch.size() < WebhookQueue.MAX_EMBEDS_PER_MESSAGE,
                "the count limit is not the only limit");
        assertTrue(WebhookQueue.envelope(batch).length() < 6000,
                "Discord allows 6000 characters across a message's embeds");
        assertFalse(waiting.isEmpty(), "what did not fit is still there");
    }

    @Test
    @DisplayName("a change of kind ends the batch rather than being stepped over")
    void orderingWinsOverFilling() {
        final Payload plain = content("a plain line");
        final Queue<Payload> waiting = queueOf(embed("{\"b\":2}"), plain, embed("{\"c\":3}"));

        final List<Payload> batch = WebhookQueue.takeBatch(embed("{\"a\":1}"), waiting);

        assertEquals(2, batch.size());
        // Reaching past the plain line for the embed behind it would deliver them out
        // of order, and ordering is the entire reason a destination has one worker.
        assertSame(plain, waiting.peek());
    }

    @Test
    @DisplayName("a raw payload is never merged and is sent exactly as given")
    void rawIsLeftAlone() {
        final Queue<Payload> waiting = queueOf(new Payload(Kind.RAW, "{\"x\":1}"));
        final Payload raw = new Payload(Kind.RAW, "{\"whole\":\"payload\"}");

        final List<Payload> batch = WebhookQueue.takeBatch(raw, waiting);

        assertEquals(1, batch.size());
        assertEquals(1, waiting.size());
        assertEquals("{\"whole\":\"payload\"}", WebhookQueue.envelope(batch));
    }

    @Test
    @DisplayName("plain lines join into one message, separated by an escaped newline")
    void contentJoinsAsLines() {
        final Queue<Payload> waiting = queueOf(content("second"), content("third"));

        final List<Payload> batch = WebhookQueue.takeBatch(content("first"), waiting);

        assertEquals(3, batch.size());
        // Two characters, not a real newline: the bodies are already escaped, so the
        // separator has to be escaped too or the JSON string ends mid-message.
        assertEquals("{\"content\":\"first\\nsecond\\nthird\"," + WebhookQueue.NO_MENTIONS + "}", WebhookQueue.envelope(batch));
    }

    @Test
    @DisplayName("joined content stops short of Discord's 2000-character message limit")
    void contentHasItsOwnCeiling() {
        final String line = "y".repeat(500);
        final Queue<Payload> waiting = queueOf(content(line), content(line), content(line), content(line));

        final List<Payload> batch = WebhookQueue.takeBatch(content(line), waiting);

        assertTrue(WebhookQueue.envelope(batch).length() < 2000, "content is capped at 2000");
        assertFalse(waiting.isEmpty());
    }

    @Test
    @DisplayName("one embed produces exactly the payload that shipped before batching")
    void aSingleEmbedIsUnchanged() {
        final String[][] fields = {{"Cause of Death", "Fell from a high place", "false"}};
        final String before = DiscordWebhook.buildEmbedJson(
                "Player Death", "Lachlan died", 0xFF0000,
                "2026-11-02T00:00:00Z", "Server Logs", "DiscordLogger", null, fields);

        final String after = WebhookQueue.envelope(Collections.singletonList(
                embed(DiscordWebhook.buildEmbedObject(
                        "Player Death", "Lachlan died", 0xFF0000,
                        "2026-11-02T00:00:00Z", "Server Logs", "DiscordLogger", null, fields))));

        // The quiet path is the one almost every server is on, so it had better be
        // byte-identical to what they were already receiving.
        assertEquals(before, after);
    }

    @Test
    @DisplayName("a description full of brackets cannot corrupt the envelope")
    void bracketsInTheTextAreHarmless() {
        // Why the queue stores embed objects instead of finished payloads: finding the
        // end of an embed in rendered JSON means parsing, and a world called a]b or a
        // death message with a brace in it would break any hand-rolled attempt at it.
        final String nasty = "{\"description\":\"died in world a]b }{ \\\"quoted\\\"\"}";
        final List<Payload> batch =
                WebhookQueue.takeBatch(embed(nasty), queueOf(embed("{\"n\":2}")));

        assertEquals(2, batch.size());
        assertEquals("{\"content\":null,\"embeds\":[" + nasty + ",{\"n\":2}],\"attachments\":[]," + WebhookQueue.NO_MENTIONS + "}",
                WebhookQueue.envelope(batch));
    }
}
