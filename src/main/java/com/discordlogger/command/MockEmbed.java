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
 * /discordlogger test player_death | player=Notch | title=Player Death
 *     | desc=Notch fell from a high place | field=Cause:Fall | field=Coords:123, 64, -90:inline
 * </pre>
 *
 * <p>Parts are split on {@code |} rather than spaces, because every value worth
 * typing here contains them — a death message, a coordinate list, a ban reason. A
 * space-delimited syntax would need quoting, and quoting inside Minecraft chat is
 * where this would stop being usable.
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
     * @param raw   everything after {@code test}, already joined with spaces
     * @param fallbackAuthor who ran the command, used when no {@code player=} was given
     */
    public static MockEmbed parse(String raw, String fallbackAuthor) {
        final String[] parts = (raw == null ? "" : raw).split("\\|", -1);

        String category = parts.length > 0 ? parts[0].trim().toLowerCase(Locale.ROOT) : "";
        if (category.isEmpty()) category = "server";

        String title = "";
        String description = "";
        String player = null;
        String avatar = null;
        final List<Log.Field> fields = new ArrayList<>();

        for (int i = 1; i < parts.length; i++) {
            final String part = parts[i].trim();
            final int eq = part.indexOf('=');
            if (eq <= 0) continue;
            final String key = part.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            final String value = part.substring(eq + 1).trim();
            if (value.isEmpty()) continue;

            switch (key) {
                case "player" -> player = value;
                case "avatar" -> avatar = value;
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
            thumb = String.format(AVATAR_BY_NAME, player.replace(" ", "%20"));
        }

        final boolean detailed = player != null || avatar != null
                || !title.isEmpty() || !description.isEmpty() || !fields.isEmpty();

        return new MockEmbed(category, title, description,
                player != null ? player : fallbackAuthor, thumb, fields, detailed);
    }

    /**
     * {@code Name:Value} or {@code Name:Value:inline}.
     *
     * <p>Only the first colon separates name from value, so a value may contain
     * colons of its own — timestamps and coordinates both do, and losing them to the
     * delimiter would rule out most of what is worth screenshotting.
     */
    static Log.Field field(String spec) {
        final int colon = spec.indexOf(':');
        if (colon <= 0) return null;
        final String name = spec.substring(0, colon).trim();
        String value = spec.substring(colon + 1).trim();

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
