package com.discordlogger.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped config must pass its own linter.
 *
 * <p>Checked against the real file rather than a fixture: a doctor that flags the
 * defaults is worse than no doctor, because the first thing every admin does is run it
 * on an untouched config and learn to ignore whatever it says.
 */
class DoctorTest {

    private static String shipped() throws Exception {
        return Files.readString(Path.of("src/main/resources/config.yml"));
    }

    @Test
    @DisplayName("the shipped config ships the password-carrying commands filtered")
    void defaultsFilterRiskyCommands() throws Exception {
        // Doctor warns when these leave ignored_commands. The defaults must contain
        // them, or the warning fires on a config nobody has touched.
        final String s = shipped();
        for (String risky : new String[]{"- login", "- register", "- msg"}) {
            assertTrue(s.contains(risky), "config.yml must ship " + risky + " in ignored_commands");
        }
    }

    @Test
    @DisplayName("the shipped config leaves only_log_commands empty")
    void defaultAllowListIsEmpty() throws Exception {
        // Set alongside ignored_commands it silently makes the deny-list inert, which
        // is the contradiction Doctor exists to name. It must not be the default.
        assertTrue(shipped().contains("only_log_commands: []"),
                "an allow-list in the defaults would make the shipped deny-list dead");
    }

    @Test
    @DisplayName("the shipped config has events enabled")
    void defaultsEnableEvents() throws Exception {
        assertTrue(shipped().contains("enabled: true"),
                "a config with everything off would trip the doctor on a fresh install");
    }
}
