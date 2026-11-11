package com.discordlogger.webhook;

import com.discordlogger.log.Log;
import com.discordlogger.webhook.WebhookQueue.Payload;
import com.discordlogger.webhook.WebhookQueue.Payload.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What stops player-written text from breaking, or weaponising, a log message.
 *
 * <p>Signs and books are the events that made this necessary — their content is typed
 * by whoever is being logged — but nothing here is specific to them, because the
 * enforcement is at the payload layer and therefore covers every event at once.
 */
class PayloadSafetyTest {

    private static final Pattern FIELD_VALUE =
            Pattern.compile("\"value\":\"(.*?)\",\"inline\"");

    private static String oneValue(String json) {
        final Matcher m = FIELD_VALUE.matcher(json);
        assertTrue(m.find(), json);
        return m.group(1);
    }

    // ---- Limits.clamp -------------------------------------------------------

    @Test
    @DisplayName("text inside the limit is returned untouched, and the same instance")
    void shortTextIsUnchanged() {
        assertEquals("hello", Limits.clamp("hello", 1024));
        assertEquals("", Limits.clamp("", 10));
        assertNull(Limits.clamp(null, 10));
    }

    @Test
    @DisplayName("over-long text is cut to the limit and says so with an ellipsis")
    void longTextIsClamped() {
        final String s = repeat('a', 5000);
        final String out = Limits.clamp(s, Limits.FIELD_VALUE);

        assertEquals(Limits.FIELD_VALUE, out.length());
        assertTrue(out.endsWith("…"), out.substring(out.length() - 5));
    }

    @Test
    @DisplayName("a cut never splits a surrogate pair into half a character")
    void surrogatePairsSurvive() {
        // Each emoji is two chars, so a limit landing mid-pair is reachable.
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) sb.append("💣"); // bomb
        final String out = Limits.clamp(sb.toString(), 21);

        assertTrue(out.length() <= 21);
        assertFalse(Character.isHighSurrogate(out.charAt(out.length() - 2)),
                "a lone high surrogate was left at the end");
    }

    @Test
    @DisplayName("a cut never leaves a dangling Markdown escape")
    void danglingBackslashIsDropped() {
        // "aaa\" -- the backslash would otherwise escape the ellipsis itself.
        final String out = Limits.clamp("aaa\\bbb", 5);
        assertFalse(out.contains("\\"), out);
        assertTrue(out.endsWith("…"), out);
    }

    // ---- The embed builder --------------------------------------------------

    @Test
    @DisplayName("a book title or sign long enough to be rejected is clamped instead")
    void fieldValueIsClamped() {
        final String json = DiscordWebhook.buildEmbedObject(
                "Sign Placed", null, 0, null, null, null, null,
                new String[][] {{"Text", repeat('x', 4000), "false"}});

        assertEquals(Limits.FIELD_VALUE, oneValue(json).length());
    }

    @Test
    @DisplayName("four full-length sign lines are clamped, not rejected by Discord")
    void aFullLengthSignIsClampedNotRejected() {
        // 384 characters is the longest a line can arrive as; four of them, plus the
        // newlines, is 1539 -- over the limit, and a real possibility from a plugin.
        final StringBuilder sign = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i > 0) sign.append('\n');
            sign.append(repeat('s', 384));
        }
        final String json = DiscordWebhook.buildEmbedObject(
                "Sign Placed", null, 0, null, null, null, null,
                new String[][] {{"Text", sign.toString(), "false"}});

        // The value is JSON-escaped by now, so measure the characters Discord counts.
        final String value = oneValue(json).replace("\\n", "\n");
        assertEquals(Limits.FIELD_VALUE, value.length());
    }

    @Test
    @DisplayName("the twenty-sixth field is dropped rather than losing the message")
    void fieldCountIsCapped() {
        final String[][] fields = new String[40][3];
        for (int i = 0; i < fields.length; i++) {
            fields[i] = new String[] {"n" + i, "v" + i, "false"};
        }
        final String json = DiscordWebhook.buildEmbedObject(
                "Many", null, 0, null, null, null, null, fields);

        final Matcher m = FIELD_VALUE.matcher(json);
        int count = 0;
        while (m.find()) count++;
        assertEquals(Limits.FIELDS_PER_EMBED, count);
    }

    @Test
    @DisplayName("title, author and footer are clamped too")
    void theOtherStringsAreClamped() {
        final String json = DiscordWebhook.buildEmbedObject(
                repeat('t', 900), repeat('d', 9000), 0, null,
                repeat('a', 900), repeat('f', 9000), null, null);

        assertTrue(json.contains("\"title\":\"" + repeat('t', Limits.TITLE - 1) + "…\""), "title");
        assertTrue(json.contains("\"name\":\"" + repeat('a', Limits.AUTHOR_NAME - 1) + "…\""), "author");
        assertTrue(json.contains("\"text\":\"" + repeat('f', Limits.FOOTER_TEXT - 1) + "…\""), "footer");
        assertTrue(json.contains("\"description\":\"" + repeat('d', Limits.DESCRIPTION - 1) + "…\""),
                "description");
    }

    // ---- Mentions and Markdown ---------------------------------------------

    @Test
    @DisplayName("every message says that nothing in it may ping anyone")
    void mentionsAreSuppressed() {
        final String embeds = WebhookQueue.envelope(
                List.of(new Payload(Kind.EMBED, "{\"a\":1}")));
        final String content = WebhookQueue.envelope(
                List.of(new Payload(Kind.CONTENT, "a player typed @everyone")));

        // Embeds never notify, but plain-text mode posts the same text as content,
        // where @everyone would ping the whole Discord server.
        assertTrue(embeds.contains(WebhookQueue.NO_MENTIONS), embeds);
        assertTrue(content.contains(WebhookQueue.NO_MENTIONS), content);
    }

    @Test
    @DisplayName("a sign cannot put a clickable link, or a spoiler, in the channel")
    void maskedLinksAndSpoilersAreEscaped() {
        final String out = Log.mdEscape("[Click here](https://example.invalid) ||hidden||");

        assertFalse(out.contains("[Click here]("), out);
        assertFalse(out.contains("||"), out);
        assertTrue(out.contains("\\["), out);
        assertTrue(out.contains("\\|"), out);
    }

    @Test
    @DisplayName("the escapes that only affect layout are left alone")
    void cosmeticMarkdownIsNotEscaped() {
        // Escaping these would put backslashes through ordinary chat for no safety.
        assertEquals("> # - hi", Log.mdEscape("> # - hi"));
    }

    @Test
    @DisplayName("redaction and validation agree about case, so no token escapes")
    void redactionIsCaseInsensitiveLikeValidation() {
        // The pair has to stay symmetric. isValidWebhookUrl is case-insensitive, so a
        // URL with a capitalised host is accepted and stored -- and if redaction did
        // not recognise the same string, /discordlogger webhook would echo the command
        // into the channel with the token intact.
        for (String host : new String[] {"discord.com", "Discord.com", "DISCORD.COM",
                                         "DiscordApp.com", "PTB.discord.com", "Canary.Discord.com"}) {
            final String url = "https://" + host + "/api/webhooks/123456789/SuperSecretToken";
            assertTrue(Log.isValidWebhookUrl(url), host + " should validate");
            assertFalse(Log.redactWebhooks("/discordlogger webhook " + url).contains("SuperSecretToken"),
                    host + " validated but its token was not redacted");
        }
    }

    private static String repeat(char c, int n) {
        final StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }
}
