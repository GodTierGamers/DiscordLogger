package com.discordlogger.explosion;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * How long {@code explosions.csv} keeps a row, and how far back a lookup looks.
 *
 * <p>One row per explosion is small, and a server with a creeper or TNT farm produces
 * a great many of them, so the file needs a ceiling that is not "until the disk fills".
 * The value is a duration rather than a count because what an admin wants to say is
 * "keep a month", not "keep eighty thousand".
 *
 * <h2>Years and months are calendar units, and are treated as such</h2>
 *
 * <p>A week is always seven days, but a month is 28 to 31 and a year is 365 or 366. So
 * {@code 6mo} does not mean "180 days"; it means the same day of the month, six months
 * back, which is what somebody comparing against a calendar expects. That costs nothing
 * in the hot path: the cutoff is worked out once per prune, and every row is then a
 * single {@code long} comparison against it.
 *
 * <h2>Why the unit is looked up whole rather than by its last letter</h2>
 *
 * <p>{@code m} is minutes and {@code mo} is months, which is a trap if the suffix is
 * matched a character at a time — {@code 6mo} reads as six minutes, quietly, and a
 * retention of six months becomes six minutes. Splitting the value into digits and
 * letters and looking the letters up as a whole makes that impossible: {@code "m"} and
 * {@code "mo"} are different keys, and an unrecognised unit is refused rather than
 * guessed at.
 */
public final class Expiry {

    private Expiry() {}

    /** What the shipped config says, in case the key is missing from an older file. */
    public static final String DEFAULT = "30d";

    /** Returned for "keep everything": no row is ever old enough to drop. */
    public static final long NEVER = 0L;

    /** Fixed-length units, in milliseconds. */
    private static final long SECOND = 1_000L;
    private static final long MINUTE = 60L * SECOND;
    private static final long HOUR   = 60L * MINUTE;
    private static final long DAY    = 24L * HOUR;
    private static final long WEEK   = 7L * DAY;

    /** Calendar units, which cannot be a fixed number of milliseconds. */
    private static final long CALENDAR_MONTH = -1L;
    private static final long CALENDAR_YEAR  = -2L;

    /**
     * Every spelling accepted, mapped to its length or its calendar marker.
     *
     * <p>Spelled-out forms are here because a config file is typed by a person: whoever
     * writes {@code expiry_time: "6 months"} has said exactly what they mean and should
     * not be told it is invalid.
     */
    private static final Map<String, Long> UNITS = new LinkedHashMap<String, Long>();
    static {
        for (String k : new String[]{"y", "yr", "yrs", "year", "years"}) UNITS.put(k, CALENDAR_YEAR);
        for (String k : new String[]{"mo", "mon", "mos", "mths", "month", "months"}) UNITS.put(k, CALENDAR_MONTH);
        for (String k : new String[]{"w", "wk", "wks", "week", "weeks"}) UNITS.put(k, WEEK);
        for (String k : new String[]{"d", "day", "days"}) UNITS.put(k, DAY);
        for (String k : new String[]{"h", "hr", "hrs", "hour", "hours"}) UNITS.put(k, HOUR);
        for (String k : new String[]{"m", "min", "mins", "minute", "minutes"}) UNITS.put(k, MINUTE);
        for (String k : new String[]{"s", "sec", "secs", "second", "seconds"}) UNITS.put(k, SECOND);
    }

    /** A parsed duration: an amount and the unit it was written in. */
    static final class Window {
        final long amount;
        final long unit;      // millis per unit, or a CALENDAR_* marker
        Window(long amount, long unit) { this.amount = amount; this.unit = unit; }
        boolean calendar() { return unit == CALENDAR_MONTH || unit == CALENDAR_YEAR; }
    }

    /**
     * Splits a written duration into its amount and unit, or null if it is neither.
     *
     * <p>Blank, {@code 0}, {@code never} and anything unparseable return null, and the
     * callers read that as "keep everything". The asymmetry is deliberate: keeping too
     * much is recoverable and deleting too much is not, so a value nobody can read must
     * never be the reason a log is emptied.
     */
    static Window parse(String value) {
        if (value == null) return null;
        // Internal spaces removed so "6 months" and "6months" are the same thing.
        final String v = value.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (v.isEmpty() || v.equals("0") || v.equals("never") || v.equals("forever")) return null;

        int split = 0;
        while (split < v.length() && v.charAt(split) >= '0' && v.charAt(split) <= '9') split++;
        if (split == 0) return null;                       // no number at all

        final long amount;
        try {
            amount = Long.parseLong(v.substring(0, split));
        } catch (NumberFormatException tooBig) {
            return null;
        }
        if (amount <= 0) return null;

        final String unit = v.substring(split);
        // A bare number means days: the unit an admin means when they type "30" into a
        // field called expiry_time.
        final Long per = unit.isEmpty() ? Long.valueOf(DAY) : UNITS.get(unit);
        if (per == null) return null;                      // an unknown unit is not a guess

        if (per.longValue() > 0L && amount > Long.MAX_VALUE / per.longValue()) {
            // "99999999999999d" is a typo, and a wrapped multiply would put the cutoff
            // in the future, which expires every row ever written.
            return null;
        }
        return new Window(amount, per.longValue());
    }

    /**
     * The window as milliseconds, or {@link #NEVER}.
     *
     * <p>Calendar units are reported against the current time, since "a month" has no
     * length on its own. Use {@link #cutoff} wherever the answer matters.
     */
    public static long parseMillis(String value) {
        final Window w = parse(value);
        if (w == null) return NEVER;
        if (!w.calendar()) return w.amount * w.unit;
        final long now = System.currentTimeMillis();
        return now - cutoff(value, now);
    }

    /**
     * The timestamp below which a row has expired, or {@link #NEVER} to keep all.
     *
     * @param now epoch millis
     */
    public static long cutoff(String value, long now) {
        final Window w = parse(value);
        if (w == null) return NEVER;
        if (!w.calendar()) return now - w.amount * w.unit;

        // The system zone, the same one Log's timestamps use, so "a month ago" means
        // the same thing in a lookup as it does in a plain-text log line.
        final ZoneId zone = ZoneId.systemDefault();
        final LocalDateTime from = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone);
        final LocalDateTime back = (w.unit == CALENDAR_YEAR)
                ? from.minusYears(w.amount)
                : from.minusMonths(w.amount);
        return back.atZone(zone).toInstant().toEpochMilli();
    }

    /** Whether a row's timestamp is old enough to drop, given a cutoff. */
    public static boolean expired(long at, long cutoff) {
        return cutoff != NEVER && at < cutoff;
    }

    /** "30 days", "6 months", "kept forever" — for a readout, not for parsing back. */
    public static String describe(String value) {
        final Window w = parse(value);
        if (w == null) return "kept forever";
        final String noun;
        if (w.unit == CALENDAR_YEAR)       noun = "year";
        else if (w.unit == CALENDAR_MONTH) noun = "month";
        else if (w.unit == WEEK)           noun = "week";
        else if (w.unit == DAY)            noun = "day";
        else if (w.unit == HOUR)           noun = "hour";
        else if (w.unit == MINUTE)         noun = "minute";
        else                               noun = "second";
        return w.amount + " " + noun + (w.amount == 1 ? "" : "s");
    }
}
