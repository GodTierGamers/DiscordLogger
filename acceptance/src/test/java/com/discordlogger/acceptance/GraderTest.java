package com.discordlogger.acceptance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The grader, graded.
 *
 * <p>Every verdict this produces is a claim about the plugin, so a mistake here is
 * worse than no suite at all: it either hides a fault or invents one. These pin each
 * rule against the case it was written for, including the ones drawn from bugs this
 * project has actually shipped.
 */
class GraderTest {

    private static final List<String> NO_ERRORS = List.of();

    private static Grader.Expectation expect(String key, String approved) {
        return new Grader.Expectation(key, true, approved);
    }

    private static String embed(String description) {
        return "{\"embeds\":[{\"title\":\"Player Death\",\"description\":\"" + description
                + "\",\"footer\":{\"text\":\"DiscordLogger\"},"
                + "\"thumbnail\":{\"url\":\"https://mc-heads.net/avatar/000/256\"}}]}";
    }

    @Test
    @DisplayName("the approved wording passes")
    void exactMatchPasses() {
        final Grader.Result r = Grader.grade(
                expect("discord.death.causes.fall", "Fell from a high place"),
                embed("Fell from a high place"), NO_ERRORS);
        assertEquals(Verdict.PASS, r.verdict, r.detail);
    }

    @Test
    @DisplayName("an unresolved placeholder is a potential error, not a pass")
    void unresolvedPlaceholderIsFlagged() {
        // The case raised directly: an advancement rendering as {mine_wood}.
        final Grader.Result r = Grader.grade(
                expect("log.player.advancement.enabled", null),
                embed("Player1 earned {mine_wood}"), NO_ERRORS);
        assertEquals(Verdict.POTENTIAL_ERROR, r.verdict);
        assertTrue(r.detail.contains("{mine_wood}"), r.detail);
    }

    @Test
    @DisplayName("a MiniMessage tag reaching Discord is a potential error")
    void strayTagIsFlagged() {
        // discord.* is plain text; a tag there is posted literally to the channel.
        final Grader.Result r = Grader.grade(
                expect("discord.player-chat", null),
                embed("<green>Player1 said hello</green>"), NO_ERRORS);
        assertEquals(Verdict.POTENTIAL_ERROR, r.verdict);
        assertTrue(r.detail.contains("<green>"), r.detail);
    }

    @Test
    @DisplayName("a raw constant leaking into prose is a potential error")
    void rawEnumIsFlagged() {
        // What a missing lang entry looks like: the enum name instead of words.
        final Grader.Result r = Grader.grade(
                expect("discord.death.causes.sonic-boom", null),
                embed("Player1 died of SONIC_BOOM"), NO_ERRORS);
        assertEquals(Verdict.POTENTIAL_ERROR, r.verdict);
        assertTrue(r.detail.contains("SONIC_BOOM"), r.detail);
    }

    @Test
    @DisplayName("a URL is not mistaken for a fault")
    void urlsAreNotFlagged() {
        // Avatar and icon URLs contain things these rules object to. Flagging them
        // would make the whole category noise, and noise gets ignored.
        final Grader.Result r = Grader.grade(
                expect("discord.death.causes.fall", "Fell from a high place"),
                "{\"embeds\":[{\"description\":\"Fell from a high place\","
                        + "\"thumbnail\":{\"url\":\"https://x.test/a_B/SOME_ID/256\"}}]}",
                NO_ERRORS);
        assertEquals(Verdict.PASS, r.verdict, r.detail);
    }

    @Test
    @DisplayName("silence when switched off is a pass; noise is wrong")
    void disabledCategories() {
        final Grader.Expectation off = new Grader.Expectation("log.player.chat.enabled", false, null);
        assertEquals(Verdict.PASS, Grader.grade(off, null, NO_ERRORS).verdict);
        assertEquals(Verdict.WRONG, Grader.grade(off, embed("hello"), NO_ERRORS).verdict);
    }

    @Test
    @DisplayName("silence when it should have sent is wrong")
    void missingPostIsWrong() {
        final Grader.Result r = Grader.grade(expect("log.player.join.enabled", null), null, NO_ERRORS);
        assertEquals(Verdict.WRONG, r.verdict);
    }

    @Test
    @DisplayName("a server error outranks everything else")
    void serverErrorIsWrong() {
        // A stack trace needs no judgement, even if the payload looked perfect.
        final Grader.Result r = Grader.grade(
                expect("log.player.death.enabled", "Fell from a high place"),
                embed("Fell from a high place"),
                List.of("java.lang.NoClassDefFoundError: com/google/gson/JsonParseException"));
        assertEquals(Verdict.WRONG, r.verdict);
        assertTrue(r.detail.contains("NoClassDefFoundError"), r.detail);
    }

    @Test
    @DisplayName("whitespace-only difference is probably fine, not a failure")
    void whitespaceDifferenceIsSoft() {
        final Grader.Result r = Grader.grade(
                expect("discord.death.causes.fall", "Fell from a high place"),
                embed("Fell  from a high  place"), NO_ERRORS);
        assertEquals(Verdict.PROBABLY_FINE, r.verdict, r.detail);
    }

    @Test
    @DisplayName("drifted wording is a potential error, and says what was expected")
    void driftedWordingIsFlagged() {
        final Grader.Result r = Grader.grade(
                expect("discord.death.causes.fall", "Fell from a high place"),
                embed("Fell from a high palce"), NO_ERRORS);
        assertEquals(Verdict.POTENTIAL_ERROR, r.verdict);
        assertTrue(r.detail.contains("Fell from a high place"), r.detail);
    }

    @Test
    @DisplayName("verdicts roll up to the worst of their events")
    void rollUp() {
        assertEquals(Verdict.WRONG, Verdict.PASS.worseOf(Verdict.WRONG));
        assertEquals(Verdict.POTENTIAL_ERROR,
                Verdict.PROBABLY_FINE.worseOf(Verdict.POTENTIAL_ERROR));
        assertTrue(Verdict.WRONG.fails());
        assertTrue(!Verdict.POTENTIAL_ERROR.fails(), "a debatable string must not block a run");
    }
}
