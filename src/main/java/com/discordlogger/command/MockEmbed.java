package com.discordlogger.command;

import com.discordlogger.log.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses a hand-written embed for {@code /discordlogger test}.
 *
 * <p>Exists so the listing galleries can be filled without waiting for real events.
 * Screenshots of a logging plugin have to show deaths, bans and joins, and the only
 * previous way to produce one was to make those things actually happen on a live
 * server — which meant either a staged server or, worse, screenshots of real players.
 * Inventing players avoids putting anyone's name and skin in promotional material.
 *
 * <h2>Syntax</h2>
 *
 * <pre>
 * /discordlogger test player_death player="Notch" title="Player Death"
 *     desc="Notch fell from a high place" field="Cause:Fall" field="Coords:123, 64, -90:inline"
 * </pre>
 *
 * <p>Values are quoted because almost every one worth typing contains spaces — a death
 * message, a coordinate list, a ban reason. Quotes are what anyone would reach for
 * without being told, which matters for something used occasionally and from memory.
 * An unquoted value is still accepted when it has no spaces, so {@code player=Notch}
 * works.
 *
 * <p>Split from the command so the parsing is testable without a server, which is
 * most of the behaviour: the command itself only sends what this returns.
 */
public final class MockEmbed {

    /** mc-heads serves by name as well as UUID, so an invented player still gets a head. */
    private static final String AVATAR_BY_NAME = "https://mc-heads.net/avatar/%s/256";

    private final String category;
    private final String title;
    private final String description;
    private final String author;
    private final String thumbnail;
    private final List<Log.Field> fields;
    private final boolean detailed;

    private MockEmbed(String category, String title, String description, String author,
                      String thumbnail, List<Log.Field> fields, boolean detailed) {
        this.category = category;
        this.title = title;
        this.description = description;
        this.author = author;
        this.thumbnail = thumbnail;
        this.fields = fields;
        this.detailed = detailed;
    }

    public String category()      { return category; }
    public String title()         { return title; }
    public String description()   { return description; }
    public String author()        { return author; }
    public String thumbnail()     { return thumbnail; }
    public List<Log.Field> fields() { return fields; }

    /**
     * True when the caller supplied nothing but a category.
     *
     * <p>Tracked as it is parsed rather than inferred from the finished object. The
     * first attempt asked whether {@code author} was null, which it never is — the
     * sender's name is the fallback — so a bare {@code /discordlogger test} would have
     * rendered a titled embed instead of the one-line webhook check it has always been.
     */
    public boolean isPlain() {
        return !detailed;
    }

    /**
     * Parses the whole argument tail.
     *
     * <p><b>{@code player=} sets the avatar, not the author.</b> A real event puts
     * {@code embeds.author} in the author slot — "Server Logs" by default — and the
     * player's name in the description or a field. Writing the name into the author
     * would produce embeds that look nothing like the ones the plugin actually sends,
     * which for screenshots is the whole failure. {@code author=} overrides it for the
     * rare case that matters.
     *
     * @param raw everything after {@code test}, already joined with spaces
     */
    public static MockEmbed parse(String raw) {
        final String text = raw == null ? "" : raw.trim();

        String title = "";
        String description = "";
        String player = null;
        String avatar = null;
        String author = null;
        final List<Log.Field> fields = new ArrayList<>();

        // Everything before the first key= is the category. Taking it positionally
        // keeps the common case -- "/discordlogger test player_join" -- exactly as it
        // was, so the quick webhook check never grew a syntax.
        final int firstKey = indexOfKey(text);
        String category = (firstKey < 0 ? text : text.substring(0, firstKey))
                .trim().toLowerCase(Locale.ROOT);
        if (category.isEmpty()) category = "server";

        int i = firstKey < 0 ? text.length() : firstKey;
        while (i < text.length()) {
            final int eq = text.indexOf('=', i);
            if (eq < 0) break;
            final String key = text.substring(i, eq).trim().toLowerCase(Locale.ROOT);

            final String[] read = readValue(text, eq + 1);
            final String value = read[0];
            i = Integer.parseInt(read[1]);
            if (value.isEmpty()) continue;

            switch (key) {
                case "player" -> player = value;
                case "avatar" -> avatar = value;
                case "author" -> author = value;
                case "title"  -> title = value;
                case "desc", "description" -> description = value;
                case "field"  -> {
                    final Log.Field f = field(value);
                    if (f != null) fields.add(f);
                }
                default -> { /* unknown key: ignored, not an error */ }
            }
        }

        // An invented player gets a head from their name, so a handful of fake accounts
        // produce visibly different avatars with nothing to look up or host.
        String thumb = avatar;
        if (thumb == null && player != null) {
            thumb = String.format(AVATAR_BY_NAME, player.trim().replace(" ", "%20"));
        }

        final boolean detailed = player != null || avatar != null || author != null
                || !title.isEmpty() || !description.isEmpty() || !fields.isEmpty();

        // author stays null unless asked for: Log then uses embeds.author from the
        // config, exactly as every real event does.
        return new MockEmbed(category, title, description, author, thumb, fields, detailed);
    }

    /** Where the first {@code key=} starts, or -1 when the line is only a category. */
    private static int indexOfKey(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != '=') continue;
            // Walk back over the key to whitespace: that is where the pair begins.
            int start = i;
            while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) start--;
            if (start < i) return start;
        }
        return -1;
    }

    /**
     * Reads one value, quoted or not, and where it ended.
     *
     * <p>Returns {@code [value, nextIndex]} as strings so the caller can advance — a
     * two-field record for a private helper used once would be more ceremony than the
     * problem deserves.
     *
     * <p>An unterminated quote takes the rest of the line rather than failing. Someone
     * mid-way through typing a long embed should see what they have so far, not an
     * error telling them something they already know.
     */
    private static String[] readValue(String text, int from) {
        int i = from;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
        if (i >= text.length()) return new String[]{"", String.valueOf(text.length())};

        final char c = text.charAt(i);
        if (c == '"' || c == '\u201C') {
            final int close = indexOfClosingQuote(text, i + 1);
            if (close < 0) return new String[]{text.substring(i + 1).trim(),
                                               String.valueOf(text.length())};
            return new String[]{text.substring(i + 1, close), String.valueOf(close + 1)};
        }
        int end = i;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) end++;
        return new String[]{text.substring(i, end), String.valueOf(end)};
    }

    /** Closing quote, accepting the curly kind a phone keyboard or a doc paste produces. */
    private static int indexOfClosingQuote(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == '"' || c == '\u201D') return i;
        }
        return -1;
    }

    /**
     * {@code Name:Value}, {@code Name::Value} when the label itself ends in a colon,
     * or either with a {@code :inline} suffix.
     *
     * <p>Only the first colon separates name from value, so a value may contain
     * colons of its own — timestamps and coordinates both do, and losing them to the
     * delimiter would rule out most of what is worth screenshotting.
     */
    static Log.Field field(String spec) {
        final int colon = spec.indexOf(':');
        if (colon <= 0) return null;

        // A doubled colon means the LABEL ends in one. Most of this plugin's real field
        // names do -- "Player Name:", "Banned by:", "Blocks Affected:" -- so without
        // this there is no way to reproduce them, and "Player Name::Steve" silently
        // produced the value ":Steve" instead.
        final boolean labelKeepsColon = colon + 1 < spec.length() && spec.charAt(colon + 1) == ':';
        final String name = spec.substring(0, labelKeepsColon ? colon + 1 : colon).trim();
        String value = spec.substring(colon + (labelKeepsColon ? 2 : 1)).trim();

        boolean inline = false;
        final int last = value.lastIndexOf(':');
        if (last >= 0) {
            final String tail = value.substring(last + 1).trim().toLowerCase(Locale.ROOT);
            if (tail.equals("inline")) {
                inline = true;
                value = value.substring(0, last).trim();
            }
        }
        if (name.isEmpty() || value.isEmpty()) return null;
        return new Log.Field(name, value, inline);
    }
}
