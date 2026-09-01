package com.discordlogger.util;

/**
 * String helpers that exist only because the plugin targets Java 8.
 *
 * <h2>Faithful, not approximate</h2>
 *
 * <p>{@code isBlank} and {@code strip} arrived in Java 11 and the obvious Java 8
 * substitutes are <em>not</em> equivalent: {@code trim()} removes characters up to
 * {@code U+0020} and nothing else, so it leaves a non-breaking space, an ideographic
 * space, and most of the Unicode whitespace block untouched. {@code strip()} uses
 * {@link Character#isWhitespace}.
 *
 * <p>Substituting {@code trim()} would therefore have quietly changed behaviour for
 * exactly the inputs that are hardest to notice — a config value someone pasted from a
 * document, a player name with an unusual space. These reimplement the Java 11
 * semantics instead, so the port changes the bytecode target and nothing else.
 */
public final class Strings {

    private Strings() {}

    /** Java 11's {@code String.isBlank()}: empty, or only whitespace. */
    public static boolean isBlank(String s) {
        if (s == null || s.isEmpty()) return true;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) return false;
        }
        return true;
    }

    /** Java 11's {@code String.strip()}: both ends, {@link Character#isWhitespace}. */
    public static String strip(String s) {
        if (s == null || s.isEmpty()) return s;
        int start = 0;
        int end = s.length();
        while (start < end && Character.isWhitespace(s.charAt(start))) start++;
        while (end > start && Character.isWhitespace(s.charAt(end - 1))) end--;
        return (start == 0 && end == s.length()) ? s : s.substring(start, end);
    }

    /** Java 11's {@code String.repeat(int)}. */
    public static String repeat(String s, int count) {
        if (s == null || count <= 0) return "";
        final StringBuilder sb = new StringBuilder(s.length() * count);
        for (int i = 0; i < count; i++) sb.append(s);
        return sb.toString();
    }
}
