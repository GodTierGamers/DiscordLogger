package com.discordlogger.acceptance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns a captured payload into a {@link Verdict} and a reason.
 *
 * <p>Deliberately made of rules rather than judgement. Everything it can decide is
 * mechanical -- a placeholder that never resolved is wrong under any wording -- and
 * everything it cannot decide it hands back as {@link Verdict#POTENTIAL_ERROR} with the
 * difference spelled out, rather than guessing.
 */
final class Grader {

    /** What the suite expected to happen. */
    static final class Expectation {
        final String key;             // the config key under test
        final boolean shouldSend;     // whether anything should have been posted
        final String approvedText;    // the approved wording, or null when not applicable
        final List<String> mustContain = new ArrayList<>();

        Expectation(String key, boolean shouldSend, String approvedText) {
            this.key = key;
            this.shouldSend = shouldSend;
            this.approvedText = approvedText;
        }

        Expectation requiring(String... snippets) {
            for (String s : snippets) mustContain.add(s);
            return this;
        }
    }

    static final class Result {
        final String key;
        final Verdict verdict;
        final String detail;
        final String payload;

        Result(String key, Verdict verdict, String detail, String payload) {
            this.key = key;
            this.verdict = verdict;
            this.detail = detail;
            this.payload = payload;
        }

        @Override public String toString() {
            return String.format("%-14s %-16s %s", verdict, key, detail);
        }
    }

    // A placeholder the plugin never filled in: "{player}", "{mine_wood}".
    private static final Pattern UNRESOLVED = Pattern.compile("\\{[a-z0-9_.]+}");
    // A MiniMessage tag reaching Discord, which shows it literally.
    private static final Pattern STRAY_TAG = Pattern.compile("</?[a-z][a-z0-9_#:-]*>");
    // A raw constant leaking into prose: "SONIC_BOOM" where words were meant.
    private static final Pattern RAW_ENUM = Pattern.compile("\\b[A-Z][A-Z0-9]*_[A-Z0-9_]+\\b");

    private Grader() {}

    /**
     * @param captured the payload the plugin sent, or null when it sent nothing
     */
    static Result grade(Expectation expected, String captured, List<String> serverErrors) {
        // Nothing needs judging about a stack trace.
        if (!serverErrors.isEmpty()) {
            return new Result(expected.key, Verdict.WRONG,
                    "the server logged an error: " + serverErrors.get(0), captured);
        }

        if (!expected.shouldSend) {
            return captured == null
                    ? new Result(expected.key, Verdict.PASS, "correctly sent nothing", null)
                    : new Result(expected.key, Verdict.WRONG,
                            "sent something while switched off", captured);
        }
        if (captured == null) {
            return new Result(expected.key, Verdict.WRONG, "sent nothing", null);
        }

        for (String required : expected.mustContain) {
            if (!captured.contains(required)) {
                return new Result(expected.key, Verdict.WRONG,
                        "missing expected content: " + required, captured);
            }
        }

        // Mechanical faults, wrong under any wording, so they outrank a text match.
        final String faults = mechanicalFaults(captured);
        if (faults != null) {
            return new Result(expected.key, Verdict.POTENTIAL_ERROR, faults, captured);
        }

        if (expected.approvedText == null) {
            return new Result(expected.key, Verdict.PASS, "no wording to compare", captured);
        }
        if (captured.contains(expected.approvedText)) {
            return new Result(expected.key, Verdict.PASS, "matches approved wording", captured);
        }
        final String normalised = normalise(expected.approvedText);
        if (normalise(captured).contains(normalised)) {
            return new Result(expected.key, Verdict.PROBABLY_FINE,
                    "matches once whitespace and case are ignored", captured);
        }
        return new Result(expected.key, Verdict.POTENTIAL_ERROR,
                "wording differs from approved. expected to contain: "
                        + expected.approvedText, captured);
    }

    /** A description of the first mechanical fault found, or null when clean. */
    private static String mechanicalFaults(String payload) {
        // Only the human-visible parts. A URL legitimately contains things these rules
        // would object to, and flagging an avatar link would train people to ignore the
        // whole category.
        final String visible = payload.replaceAll("\"(?:url|icon_url)\"\\s*:\\s*\"[^\"]*\"", "");

        if (UNRESOLVED.matcher(visible).find()) {
            return "an unresolved placeholder reached Discord: "
                    + UNRESOLVED.matcher(visible).results().findFirst()
                        .map(m -> m.group()).orElse("?");
        }
        if (STRAY_TAG.matcher(visible).find()) {
            return "a formatting tag reached Discord, which shows it literally: "
                    + STRAY_TAG.matcher(visible).results().findFirst()
                        .map(m -> m.group()).orElse("?");
        }
        if (visible.contains("\"null\"") || visible.contains(": null,\"name\"")) {
            return "a literal null reached Discord";
        }
        if (visible.contains("::")) return "a doubled colon reached Discord";
        if (RAW_ENUM.matcher(visible).find()) {
            return "a raw constant name reached Discord where words were expected: "
                    + RAW_ENUM.matcher(visible).results().findFirst()
                        .map(m -> m.group()).orElse("?");
        }
        return null;
    }

    private static String normalise(String s) {
        return s.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }
}
