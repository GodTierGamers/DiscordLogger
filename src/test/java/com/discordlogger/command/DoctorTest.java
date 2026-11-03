package com.discordlogger.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

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

    @Test
    @DisplayName("every password-carrying command removed is named, one finding each")
    void removedRiskyCommandsAreNamed() {
        assertEquals(List.of(), Doctor.riskyCommandFindings(
                List.of("login", "register", "msg"), List.of()),
                "the shipped list is clean, so there is nothing to say");

        final List<String> one = Doctor.riskyCommandFindings(
                List.of("register", "msg"), List.of());
        assertEquals(1, one.size());
        assertTrue(one.get(0).contains("\"login\""), one.get(0));
        // Naming the command is the point: "your filter changed" tells an admin
        // nothing, and the useful half is which one and what it now publishes.
        assertTrue(one.get(0).contains("as typed"), one.get(0));

        assertEquals(3, Doctor.riskyCommandFindings(List.of(), List.of()).size());
        assertEquals(3, Doctor.riskyCommandFindings(null, null).size(),
                "an absent list is an empty one, not a reason to say nothing");
    }

    @Test
    @DisplayName("an allow-list silences the deny-list findings, because it wins outright")
    void onlyLogCommandsSilencesThem() {
        // only_log_commands wins: a command absent from it is not logged whatever
        // ignored_commands says, so warning that /login would be published would be
        // a warning about something that cannot happen.
        assertEquals(Collections.emptyList(),
                Doctor.riskyCommandFindings(List.of(), List.of("say", "me")));
    }

    @Test
    @DisplayName("the headline counts and pluralises, and is shared with the startup report")
    void headlineReads() {
        assertEquals("Found 1 thing worth checking", Doctor.headline(1));
        assertEquals("Found 3 things worth checking", Doctor.headline(3));
    }
}
