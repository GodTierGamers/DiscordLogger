package com.discordlogger.custom;

import com.discordlogger.filter.Filters;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Admin-defined log events, built from commands rather than from a plugin's API.
 *
 * <h2>Why this instead of an integration per plugin</h2>
 *
 * <p>Hooking Essentials, Towny, mcMMO and the rest means a compile-time dependency
 * and a version coupling for each, forever, and a release every time someone wants
 * a plugin nobody has heard of. This covers all of them at once and costs nothing to
 * maintain, because it knows about commands and commands are universal.
 *
 * <p>What it cannot do is the honest limit: it sees a command being <em>run</em>, not
 * an action <em>succeeding</em>. The moderation listeners exist as they do — sniff the
 * command, then confirm on the next tick that the ban list actually changed — precisely
 * because "someone typed /ban" and "someone was banned" are different facts. A custom
 * rule reports the first. For anything where that distinction matters, a real
 * integration is the answer and this is not.
 *
 * <h2>Live state, immutable snapshot</h2>
 *
 * <p>Same shape as {@link Filters}: rules are parsed once on load into an immutable
 * list and swapped in one write, so a reload can never be observed half-applied by a
 * command running on another thread.
 */
public final class CustomLogs {

    /** One admin-defined rule. */
    public static final class Rule {
        private final String name;
        private final List<String> match;
        private final String title;
        private final String message;

        public Rule(String name, List<String> match, String title, String message) {
            this.name = name;
            this.match = match;
            this.title = title;
            this.message = message;
        }

        public String name()         { return name; }
        public List<String> match()  { return match; }
        public String title()        { return title; }
        public String message()      { return message; }

        /**
         * The category this logs under, which is also how {@code Log} finds its colour
         * and webhook — {@code log.custom.<name>} is walked by exactly the same code
         * that handles {@code log.player.join}, so routing and colours needed no new
         * machinery at all.
         */
        public String category() {
            return "custom " + name;
        }
    }

    private static volatile List<Rule> rules = Collections.emptyList();

    private CustomLogs() {}

    public static List<Rule> rules() {
        return rules;
    }

    /** Re-read the rules. Called from applyRuntimeConfig, so /reload picks them up. */
    public static void reload(JavaPlugin plugin) {
        final List<Rule> parsed = new ArrayList<>();
        final ConfigurationSection custom =
                plugin.getConfig().getConfigurationSection("log.custom");

        if (custom != null) {
            for (String name : custom.getKeys(false)) {
                final ConfigurationSection sec = custom.getConfigurationSection(name);
                if (sec == null || !sec.getBoolean("enabled", true)) continue;

                final List<String> match = words(sec.getString("match", name));
                if (match.isEmpty()) {
                    plugin.getLogger().warning("log.custom." + name
                            + " has no 'match', so it can never fire. Set it to the command "
                            + "to watch, e.g. \"sethome\" or \"lp user\".");
                    continue;
                }

                // The command deny-list wins, and saying so is the whole point of the
                // warning. filters.ignored_commands ships with /login and /msg in it
                // because command logging posts the line verbatim -- a custom rule that
                // silently overrode that would turn a security default into a footgun
                // for the one person who would never think to check.
                if (Filters.blocksCommand(match.get(0))) {
                    plugin.getLogger().warning("log.custom." + name + " watches \""
                            + match.get(0) + "\", which filters.ignored_commands blocks. "
                            + "The filter wins and this rule will never fire. Remove it from "
                            + "ignored_commands if you really want that command logged.");
                    continue;
                }

                parsed.add(new Rule(
                        name,
                        match,
                        sec.getString("title", name),
                        sec.getString("message", "{player} ran /{command} {args}")));
            }
        }

        rules = Collections.unmodifiableList(new ArrayList<>(parsed));
        if (!parsed.isEmpty()) {
            plugin.getLogger().info("Custom logs active: " + parsed.size() + " rule(s).");
        }
    }

    /**
     * The first rule matching this command line, or null.
     *
     * <p>Matching is on <em>words</em>, not a prefix string, so {@code "lp user"} cannot
     * be satisfied by {@code /lpuserpanel}. Multi-word matching is what makes the
     * feature usable at all: half the interesting commands are subcommands, and a rule
     * that could only say {@code lp} would fire on every LuckPerms command there is.
     *
     * <p>Split out and static so the whole decision is testable without a server.
     */
    public static Rule match(List<Rule> from, String rawWithSlash) {
        final List<String> typed = words(strip(rawWithSlash));
        if (typed.isEmpty()) return null;
        for (Rule r : from) {
            if (typed.size() < r.match().size()) continue;
            boolean hit = true;
            for (int i = 0; i < r.match().size(); i++) {
                if (!typed.get(i).equals(r.match().get(i))) { hit = false; break; }
            }
            if (hit) return r;
        }
        return null;
    }

    /**
     * Lower-cased words, with the leading slash and any plugin qualifier removed.
     *
     * <p>{@code /essentials:sethome Base} and {@code /SetHome Base} both become
     * {@code [sethome, base]} — the qualifier strip matters for the same reason it does
     * in {@code filters.ignored_commands}: without it, a rule is trivially bypassed by
     * typing the long form.
     */
    public static List<String> words(String s) {
        if (s == null) return Collections.emptyList();
        final List<String> out = new ArrayList<>();
        for (String w : s.trim().split("\\s+")) {
            if (!w.isEmpty()) out.add(w.toLowerCase(Locale.ROOT));
        }
        if (!out.isEmpty()) {
            final String first = out.get(0);
            final int colon = first.indexOf(':');
            if (colon >= 0 && colon < first.length() - 1) {
                out.set(0, first.substring(colon + 1));
            }
        }
        return out;
    }

    private static String strip(String raw) {
        if (raw == null) return "";
        final String s = raw.trim();
        return s.startsWith("/") ? s.substring(1) : s;
    }
}
