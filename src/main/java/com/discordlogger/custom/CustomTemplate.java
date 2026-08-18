package com.discordlogger.custom;

import com.discordlogger.log.Log;

import java.util.List;
import java.util.Locale;

/**
 * Fills an admin's {@code message} and {@code title} with what actually happened.
 *
 * <p>Split from the listener so the substitution can be tested exhaustively without a
 * server, which matters more here than usual: this is the one place in the plugin
 * where <em>the admin</em> writes the output format, so every placeholder they might
 * reasonably try has to behave, and the ones they get wrong must fail visibly rather
 * than producing a half-rendered line.
 */
public final class CustomTemplate {

    private CustomTemplate() {}

    /**
     * Renders one template.
     *
     * <p><b>Every substituted value is escaped and redacted.</b> The text comes from a
     * command line a player typed, so it reaches Discord as content: unescaped, a
     * player could close the embed's markdown and forge the rest of the message, and
     * an unredacted webhook URL pasted into a command would be published to the very
     * channel it posts to. {@code Log.mdEscape} and {@code Log.redactWebhooks} are the
     * same two the command listeners already apply, for the same two reasons.
     *
     * @param who   display name of whoever ran it, or "Console"
     * @param words the command as typed, already lower-cased and split
     */
    public static String render(String template, String who, List<String> words, String world) {
        if (template == null) return "";

        final String command = words.isEmpty() ? "" : words.get(0);
        final String args = words.size() > 1
                ? String.join(" ", words.subList(1, words.size()))
                : "";

        String out = template
                .replace("{player}", safe(who))
                .replace("{command}", safe(command))
                .replace("{args}", safe(args))
                .replace("{world}", safe(world));

        // {arg1}..{arg9}: positional access for rules that want one part of the line,
        // e.g. "{player} promoted {arg2}" for /lp user Steve promote. Out-of-range
        // resolves to empty rather than being left as literal "{arg3}" -- a command run
        // with fewer arguments than usual should read as a gap, not as a broken template.
        for (int i = 1; i <= 9; i++) {
            final String token = "{arg" + i + "}";
            if (!out.contains(token)) continue;
            out = out.replace(token, i < words.size() ? safe(words.get(i)) : "");
        }
        return out.trim();
    }

    /** The two protections every player-controlled string in this plugin gets. */
    private static String safe(String s) {
        return s == null ? "" : Log.mdEscape(Log.redactWebhooks(s));
    }

    /** Title-cases a rule name for a default embed title: {@code set_home} -> "Set Home". */
    public static String titleise(String name) {
        final StringBuilder sb = new StringBuilder();
        for (String part : name.replace('_', ' ').replace('-', ' ').split("\\s+")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)))
              .append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.length() == 0 ? name : sb.toString();
    }
}
