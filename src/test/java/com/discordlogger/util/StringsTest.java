package com.discordlogger.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * These replace Java 11 methods, so they are tested against the real ones: the port is
 * supposed to change the bytecode target and nothing else, and a helper that merely
 * looks right would move behaviour silently.
 */
class StringsTest {

    /** Whitespace that {@code trim()} does NOT remove but {@code strip()} does. */
    private static final String NBSP = " ";   // EM SPACE, > U+0020

    @Test
    @DisplayName("isBlank matches String.isBlank, including exotic whitespace")
    void isBlankMatchesJdk() {
        for (String s : new String[]{"", " ", "\t\n", "a", " a ", NBSP, NBSP + " "}) {
            assertEquals(s.isBlank(), Strings.isBlank(s), "input: [" + s + "]");
        }
        assertTrue(Strings.isBlank(null), "null has no content to be non-blank");
    }

    @Test
    @DisplayName("strip matches String.strip, including where trim would not")
    void stripMatchesJdk() {
        for (String s : new String[]{"", "  a  ", "\ta\n", NBSP + "a" + NBSP, "a"}) {
            assertEquals(s.strip(), Strings.strip(s), "input: [" + s + "]");
        }
    }

    @Test
    @DisplayName("strip differs from trim exactly where it should")
    void stripIsNotTrim() {
        final String padded = NBSP + "value" + NBSP;
        assertEquals("value", Strings.strip(padded));
        assertFalse(padded.trim().equals("value"),
                "if trim handled this, the helper would be unnecessary");
    }

    @Test
    @DisplayName("repeat matches String.repeat")
    void repeatMatchesJdk() {
        assertEquals("=".repeat(5), Strings.repeat("=", 5));
        assertEquals("", Strings.repeat("=", 0));
        assertEquals("", Strings.repeat("=", -1));
    }
}
