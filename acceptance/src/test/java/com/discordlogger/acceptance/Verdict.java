package com.discordlogger.acceptance;

/**
 * How wrong something is, rather than whether it is wrong.
 *
 * <p>A binary pass/fail is the wrong shape for output made of sentences. A string can
 * be present, correctly wired and still slightly off; another can differ from the
 * approved wording in a way that is obviously deliberate. Collapsing those into "fail"
 * produces a suite people learn to ignore, and collapsing them into "pass" produces one
 * that never tells anyone anything.
 *
 * <p>The two middle grades exist so a run can be honest about the difference between
 * "this is not what was approved" and "this is broken".
 */
public enum Verdict {

    /** Exactly the approved wording, with only the values that vary substituted. */
    PASS,

    /**
     * Not identical, but nothing suggests a fault: whitespace, or a field that
     * legitimately differs by server version. Reported, never failed on.
     */
    PROBABLY_FINE,

    /**
     * Present and plausible, but something looks wrong: an unresolved placeholder, a
     * stray tag, a raw enum name in prose, or wording that has drifted from what was
     * approved. Needs a person, so it is surfaced rather than failed.
     */
    POTENTIAL_ERROR,

    /**
     * Contradicts the specification: nothing sent when something was expected,
     * something sent when it was suppressed, a required field missing, or the server
     * threw. No judgement needed.
     */
    WRONG;

    /** Whether a run carrying this verdict should go red. */
    public boolean fails() {
        return this == WRONG;
    }

    /** The worse of two verdicts, for rolling event results up to a key. */
    public Verdict worseOf(Verdict other) {
        return other == null || ordinal() >= other.ordinal() ? this : other;
    }
}
