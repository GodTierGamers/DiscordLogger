package com.discordlogger.custom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Template rendering. Tested harder than most things here because this is the one
 * place an ADMIN writes the output format, so it has to survive whatever they type.
 */
class CustomTemplateTest {

    private static final List<String> CMD = CustomLogs.words("sethome base camp");

    @Test
    @DisplayName("the documented placeholders all resolve")
    void placeholdersResolve() {
        assertEquals("Steve ran /sethome base camp in world",
                CustomTemplate.render("{player} ran /{command} {args} in {world}",
                        "Steve", CMD, "world"));
    }

    @Test
    @DisplayName("positional args pick out one part of the line")
    void positionalArgs() {
        assertEquals("base / camp",
                CustomTemplate.render("{arg1} / {arg2}", "Steve", CMD, "world"));
    }

    @Test
    @DisplayName("an out-of-range arg renders empty, not as literal text")
    void missingArgIsBlankNotLiteral() {
        // A command run with fewer arguments than usual should read as a gap. Leaving
        // "{arg5}" in the message would look like the plugin failed to render.
        String out = CustomTemplate.render("home:{arg5}", "Steve", CMD, "world");
        assertFalse(out.contains("{arg5}"), out);
        assertEquals("home:", out);
    }

    @Test
    @DisplayName("a webhook pasted into a command is redacted")
    void webhookRedacted() {
        // Without this, logging a command containing a webhook URL would publish that
        // URL to the very channel it posts to.
        List<String> cmd = CustomLogs.words(
                "setwebhook https://discord.com/api/webhooks/123456/SECRETTOKENVALUE");
        String out = CustomTemplate.render("{args}", "Steve", cmd, "world");
        assertFalse(out.contains("SECRETTOKENVALUE"), out);
    }

    @Test
    @DisplayName("markdown in a player-typed argument cannot escape the message")
    void markdownEscaped() {
        List<String> cmd = CustomLogs.words("say **bold** _x_");
        String out = CustomTemplate.render("{args}", "Steve", cmd, "world");
        assertTrue(out.contains("\\"), "player text must be escaped, got: " + out);
    }

    @Test
    @DisplayName("an empty or null template is not an error")
    void emptyTemplate() {
        assertEquals("", CustomTemplate.render(null, "Steve", CMD, "world"));
        assertEquals("", CustomTemplate.render("", "Steve", CMD, "world"));
    }

    @Test
    @DisplayName("a rule name becomes a readable default title")
    void titleised() {
        assertEquals("Set Home", CustomTemplate.titleise("set_home"));
        assertEquals("Rank Change", CustomTemplate.titleise("rank-change"));
        assertEquals("Sethome", CustomTemplate.titleise("sethome"));
    }
}
