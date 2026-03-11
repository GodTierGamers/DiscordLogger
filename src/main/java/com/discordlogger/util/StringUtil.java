package com.discordlogger.util;

import java.util.Locale;

/**
 * Shared string helpers used across listeners and events.
 */
public final class StringUtil {
    private StringUtil() {}

    /**
     * Converts an UPPER_SNAKE_CASE enum name to Title Case.
     * Example: {@code "ENDER_PEARL"} → {@code "Ender Pearl"}.
     */
    public static String toTitle(String s) {
        String t = s.toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] parts = t.split("\\s+");
        StringBuilder out = new StringBuilder(t.length());
        for (int i = 0; i < parts.length; i++) {
            String w = parts[i];
            if (!w.isEmpty()) {
                out.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) out.append(w.substring(1));
                if (i + 1 < parts.length) out.append(' ');
            }
        }
        return out.toString();
    }
}