package com.discordlogger.custom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Rule matching — the whole decision, testable without a server. */
class CustomLogsMatchTest {

    private static CustomLogs.Rule rule(String name, String match) {
        return new CustomLogs.Rule(name, CustomLogs.words(match), name, "{player}");
    }

    private static final List<CustomLogs.Rule> RULES = List.of(
            rule("rank_change", "lp user"),
            rule("sethome", "sethome"));

    @Test
    @DisplayName("a single-word rule matches its command")
    void singleWord() {
        assertNotNull(CustomLogs.match(RULES, "/sethome base"));
        assertEquals("sethome", CustomLogs.match(RULES, "/sethome").name());
    }

    @Test
    @DisplayName("a multi-word rule matches only its subcommand")
    void multiWord() {
        assertEquals("rank_change",
                CustomLogs.match(RULES, "/lp user Steve parent add admin").name());
        // The reason multi-word matching exists: a rule that could only say "lp" would
        // fire on every LuckPerms command, including ones the admin did not ask about.
        assertNull(CustomLogs.match(RULES, "/lp group admin permission set foo"));
    }

    @Test
    @DisplayName("matching is on words, so a longer command name cannot satisfy a rule")
    void notAPrefixMatch() {
        // "/lpuserpanel" starts with "lp user" as a STRING but is a different command.
        assertNull(CustomLogs.match(RULES, "/lpuserpanel"));
        assertNull(CustomLogs.match(RULES, "/sethomeall"));
    }

    @Test
    @DisplayName("a plugin qualifier cannot bypass a rule")
    void qualifierStripped() {
        // Same reasoning as filters.ignored_commands: without this, typing the long
        // form is a trivial bypass.
        assertEquals("sethome", CustomLogs.match(RULES, "/essentials:sethome base").name());
        assertEquals("rank_change", CustomLogs.match(RULES, "/luckperms:lp user Steve info").name());
    }

    @Test
    @DisplayName("case and spacing do not matter")
    void caseAndSpacing() {
        assertEquals("sethome", CustomLogs.match(RULES, "  /SetHome   Base  ").name());
        assertEquals("rank_change", CustomLogs.match(RULES, "/LP   User  Steve").name());
    }

    @Test
    @DisplayName("a command shorter than the rule cannot match it")
    void tooFewWords() {
        assertNull(CustomLogs.match(RULES, "/lp"));
    }

    @Test
    @DisplayName("nothing matches when no rules are defined")
    void noRules() {
        assertNull(CustomLogs.match(List.of(), "/sethome"));
        assertNull(CustomLogs.match(RULES, ""));
        assertNull(CustomLogs.match(RULES, null));
    }
}
