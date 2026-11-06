package com.discordlogger.webhook;

/**
 * Discord's documented maximums, and the one place that enforces them.
 *
 * <p>Every string in an embed has a cap, and Discord's answer to a string over its
 * cap is a 400 for the whole message. The queue cannot rescue that: a rejected batch
 * is re-sent one at a time, so an over-long field costs only its own message — but it
 * costs that message every time, silently, and the event it carried is gone.
 *
 * <p>Nearly everything the plugin sends is comfortably short, which is exactly why
 * this is easy to forget. The exceptions are player-controlled: sign text, a book
 * title, a nickname, a command line. The Bukkit API puts no ceiling on any of them —
 * vanilla clients do, and a plugin calling {@code setLine} with an arbitrary string
 * does not — so the ceiling has to live here, on the way out, rather than in each
 * listener that happens to think of it.
 *
 * <p>Clamping happens <em>before</em> JSON escaping, because Discord counts the
 * characters a reader sees, not the bytes on the wire.
 */
public final class Limits {
    private Limits() {}

    /** Embed title. */
    public static final int TITLE = 256;
    /** Embed description. */
    public static final int DESCRIPTION = 4096;
    /** Embed author name. */
    public static final int AUTHOR_NAME = 256;
    /** Embed footer text. */
    public static final int FOOTER_TEXT = 2048;
    /** Field name. */
    public static final int FIELD_NAME = 256;
    /** Field value. */
    public static final int FIELD_VALUE = 1024;
    /** Fields in one embed. */
    public static final int FIELDS_PER_EMBED = 25;
    /** Plain-text message content. */
    public static final int CONTENT = 2000;

    private static final char ELLIPSIS = '…';

    /**
     * {@code s}, shortened to at most {@code max} characters, ending in an ellipsis
     * when anything was dropped.
     *
     * <p>Two details that a plain {@code substring} gets wrong. A cut between the two
     * halves of a surrogate pair leaves a lone surrogate, which is not a character and
     * which Discord rejects outright; the cut moves back one to keep the pair whole.
     * And a cut immediately after an odd number of backslashes leaves a dangling
     * escape, which would consume the ellipsis in Discord's Markdown and change what
     * the last visible character is; the trailing backslash goes too.
     */
    public static String clamp(String s, int max) {
        if (max <= 0) return "";
        if (s == null || s.length() <= max) return s;

        int cut = max - 1; // the ellipsis costs one
        if (cut > 0 && Character.isHighSurrogate(s.charAt(cut - 1))) cut--;

        int run = 0;
        for (int i = cut - 1; i >= 0 && s.charAt(i) == '\\'; i--) run++;
        if (run % 2 == 1) cut--;

        return s.substring(0, cut) + ELLIPSIS;
    }
}
