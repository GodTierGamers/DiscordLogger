package com.discordlogger.explosion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What {@code expiry_time} means, since getting it wrong deletes somebody's log. */
class ExpiryTest {

    private static final long DAY = 86_400_000L;

    @Test
    @DisplayName("every fixed unit parses, and a bare number means days")
    void unitsParse() {
        assertEquals(30 * DAY, Expiry.parseMillis("30d"));
        assertEquals(4 * 7 * DAY, Expiry.parseMillis("4w"));
        assertEquals(12 * 3_600_000L, Expiry.parseMillis("12h"));
        assertEquals(90 * 60_000L, Expiry.parseMillis("90m"));
        assertEquals(45_000L, Expiry.parseMillis("45s"));
        // What an admin means by "30" in a field called expiry_time.
        assertEquals(30 * DAY, Expiry.parseMillis("30"));
        assertEquals(7 * DAY, Expiry.parseMillis("  7D  "));
    }

    @Test
    @DisplayName("minutes and months do not collide")
    void mIsMinutesAndMoIsMonths() {
        // The trap this guards: matching the unit one letter at a time reads 6mo as
        // six minutes, so a six-month retention silently becomes a six-minute one and
        // the log empties itself. The unit is looked up whole for exactly this reason.
        assertEquals(6 * 60_000L, Expiry.parseMillis("6m"));

        final long now = 1_762_128_000_000L;
        final long sixMonths = now - Expiry.cutoff("6mo", now);
        assertTrue(sixMonths > 150 * DAY, "6mo must be months, not minutes: " + sixMonths);
    }

    @Test
    @DisplayName("spelled-out and abbreviated spellings mean the same thing")
    void spellingsAgree() {
        // A config file is typed by a person, and "6 months" says exactly what it means.
        final long now = 1_762_128_000_000L;
        assertEquals(Expiry.cutoff("6mo", now), Expiry.cutoff("6 months", now));
        assertEquals(Expiry.cutoff("1y", now), Expiry.cutoff("1 year", now));
        assertEquals(Expiry.parseMillis("3w"), Expiry.parseMillis("3 weeks"));
        assertEquals(Expiry.parseMillis("2h"), Expiry.parseMillis("2 hours"));
        assertEquals(Expiry.parseMillis("5m"), Expiry.parseMillis("5 minutes"));
    }

    @Test
    @DisplayName("months and years follow the calendar, not a 30-day average")
    void calendarUnitsAreCalendarUnits() {
        // 2027-03-31T12:00Z. One month back is the 28th of February, not the 1st of
        // March, which is what subtracting 30 days would give.
        final long march31 = java.time.LocalDateTime.of(2027, 3, 31, 12, 0)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        final long oneMonthBack = Expiry.cutoff("1mo", march31);
        final java.time.LocalDateTime back = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(oneMonthBack), java.time.ZoneId.systemDefault());
        assertEquals(2, back.getMonthValue());
        assertEquals(28, back.getDayOfMonth(), "February has no 31st, so java.time clamps");

        // A leap year is 366 days, so a fixed 365 would be a day out.
        final long mar1 = java.time.LocalDateTime.of(2029, 3, 1, 0, 0)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        final java.time.LocalDateTime yearBack = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(Expiry.cutoff("1y", mar1)),
                java.time.ZoneId.systemDefault());
        assertEquals(2028, yearBack.getYear());
        assertEquals(3, yearBack.getMonthValue());
        assertEquals(1, yearBack.getDayOfMonth());
    }

    @Test
    @DisplayName("anything unreadable keeps the log rather than emptying it")
    void unreadableMeansForever() {
        // The asymmetry is deliberate: keeping too much is recoverable, deleting too
        // much is not, so every doubtful value falls to NEVER.
        assertEquals(Expiry.NEVER, Expiry.parseMillis("0"));
        assertEquals(Expiry.NEVER, Expiry.parseMillis(""));
        assertEquals(Expiry.NEVER, Expiry.parseMillis(null));
        assertEquals(Expiry.NEVER, Expiry.parseMillis("never"));
        assertEquals(Expiry.NEVER, Expiry.parseMillis("soon"));
        assertEquals(Expiry.NEVER, Expiry.parseMillis("30q"), "an unknown unit is not a guess");
        assertEquals(Expiry.NEVER, Expiry.parseMillis("30 fortnights"));
        assertEquals(Expiry.NEVER, Expiry.parseMillis("-5d"));
        assertEquals(Expiry.NEVER, Expiry.parseMillis("d"));
    }

    @Test
    @DisplayName("an absurd number does not wrap into a cutoff that expires everything")
    void noOverflow() {
        // A multiply that wrapped would produce a negative window, and a cutoff in the
        // future expires every row in the file.
        assertEquals(Expiry.NEVER, Expiry.parseMillis("99999999999999999d"));
        assertEquals(Expiry.NEVER, Expiry.parseMillis(String.valueOf(Long.MAX_VALUE)));
    }

    @Test
    @DisplayName("the cutoff and the expiry test agree about the boundary")
    void cutoffAndExpiry() {
        final long now = 1_000_000_000_000L;
        final long cutoff = Expiry.cutoff("1d", now);
        assertEquals(now - DAY, cutoff);

        assertTrue(Expiry.expired(cutoff - 1, cutoff));
        assertFalse(Expiry.expired(cutoff, cutoff), "exactly at the cutoff is still kept");
        assertFalse(Expiry.expired(now, cutoff));
        // NEVER must never expire anything, including a row stamped at zero.
        assertFalse(Expiry.expired(0L, Expiry.NEVER));
    }

    @Test
    @DisplayName("the Java default equals what config.yml ships")
    void defaultMatchesTheShippedConfig() throws Exception {
        // validate-config-generator.py checks getBoolean fallbacks against the shipped
        // config, but expiry_time is a string and reached through Expiry.DEFAULT rather
        // than a literal, so no regex can see it. The mismatch it would catch is the
        // same one: a config missing the key behaving against its documented default.
        final java.nio.file.Path config = java.nio.file.Paths.get(
                "src/main/resources/config.yml");
        String shipped = null;
        for (String line : java.nio.file.Files.readAllLines(config)) {
            final java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("^\\s*expiry_time:\\s*\"([^\"]+)\"").matcher(line);
            if (m.find()) { shipped = m.group(1); break; }
        }
        assertEquals(Expiry.DEFAULT, shipped,
                "config.yml ships expiry_time: " + shipped + " but Expiry.DEFAULT is "
                + Expiry.DEFAULT);
    }

    @Test
    @DisplayName("describe() reads as a person would say it")
    void describes() {
        assertEquals("30 days", Expiry.describe("30d"));
        assertEquals("1 day", Expiry.describe("1d"));
        assertEquals("6 hours", Expiry.describe("6h"));
        assertEquals("6 months", Expiry.describe("6mo"));
        assertEquals("1 year", Expiry.describe("1y"));
        assertEquals("2 weeks", Expiry.describe("2w"));
        assertEquals("kept forever", Expiry.describe("0"));
    }
}
