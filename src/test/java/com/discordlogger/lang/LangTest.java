package com.discordlogger.lang;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeping lang.yml and the code that reads it in step.
 *
 * <p>Moving strings into a file trades one failure for another: nothing now fails to
 * compile when a key is renamed or removed. A missing entry surfaces as the raw key
 * appearing in chat, or a Discord message reading {@code discord.player-join} — both
 * only visible when that exact event happens on a real server.
 */
class LangTest {

    private static final Path LANG = Path.of("src/main/resources/lang.yml");
    private static final Path SRC = Path.of("src/main/java");

    /** Every key the Java source asks Lang for. */
    private static List<String> keysUsedInCode() throws Exception {
        final Pattern call = Pattern.compile("Lang\\.(?:text|chat|prefixed|has)\\(\\s*\"([^\"]+)\"");
        final List<String> keys = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SRC)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                final Matcher m = call.matcher(Files.readString(f));
                while (m.find()) keys.add(m.group(1));
            }
        }
        return keys;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> lang() throws Exception {
        return (Map<String, Object>) new Yaml().load(Files.readString(LANG));
    }

    private static boolean hasPath(Map<String, Object> root, String dotted) {
        Object node = root;
        for (String part : dotted.split("\\.")) {
            if (!(node instanceof Map<?, ?> map) || !map.containsKey(part)) return false;
            node = map.get(part);
        }
        return node instanceof String;
    }

    @Test
    @DisplayName("every key the code asks for exists in lang.yml")
    void noCodeAsksForAMissingKey() throws Exception {
        final Map<String, Object> lang = lang();
        final List<String> missing = keysUsedInCode().stream()
                // Death causes are built at runtime from the enum, covered separately.
                .filter(k -> !k.startsWith("discord.death.causes."))
                .filter(k -> !hasPath(lang, k))
                .distinct()
                .toList();

        assertEquals(List.of(), missing,
                "these would appear in chat or Discord as their own key name");
    }

    @ParameterizedTest
    @EnumSource(DamageCause.class)
    @DisplayName("every damage cause has wording in lang.yml")
    void everyCauseHasAnEntry(DamageCause cause) throws Exception {
        final String key = "discord.death.causes."
                + cause.name().toLowerCase(Locale.ROOT).replace('_', '-');
        assertTrue(hasPath(lang(), key),
                cause + " has no entry, so a player dying this way is reported as a bare "
                        + "\"Died\". Add " + key + " to lang.yml.");
    }

    @Test
    @DisplayName("chat messages are valid MiniMessage")
    void chatMessagesParse() throws Exception {
        // An unclosed tag does not throw — it renders as literal text — so this checks
        // the round trip drops the tags rather than leaving them visible to players.
        @SuppressWarnings("unchecked")
        final Map<String, Object> chat = (Map<String, Object>) lang().get("chat");
        for (Map.Entry<String, Object> e : chat.entrySet()) {
            final String raw = String.valueOf(e.getValue());
            final String rendered = PlainTextComponentSerializer.plainText()
                    .serialize(Lang.chat("chat." + e.getKey()));
            if (raw.contains("<") && !raw.contains("{")) {
                assertFalse(rendered.contains("<green>") || rendered.contains("<red>"),
                        "chat." + e.getKey() + " has a tag that did not parse: " + rendered);
            }
        }
    }

    @Test
    @DisplayName("Discord messages carry no MiniMessage tags")
    void discordMessagesArePlain() throws Exception {
        // Discord renders Markdown, not MiniMessage: a <green> here would be posted
        // to the channel literally.
        final List<String> tagged = new ArrayList<>();
        collectStrings((Map<?, ?>) lang().get("discord"), "discord", tagged);
        for (String entry : tagged) {
            assertFalse(entry.contains("<green>") || entry.contains("<red>")
                            || entry.contains("<yellow>") || entry.contains("<gray>"),
                    "a MiniMessage tag in " + entry + " would be posted to Discord as text");
        }
    }

    @Test
    @DisplayName("placeholders are substituted, and unknown ones are left visible")
    void placeholderSubstitution() {
        assertEquals("Steve joined the server",
                Lang.text("discord.player-join", "player", "Steve"));

        // A typo must not silently produce a sentence with a hole in it.
        assertTrue(Lang.text("discord.player-join", "wrong", "Steve").contains("{player}"));
    }

    @Test
    @DisplayName("an unknown key returns itself rather than nothing")
    void missingKeyIsVisible() {
        assertEquals("chat.no-such-message", Lang.text("chat.no-such-message"));
    }

    private static void collectStrings(Map<?, ?> node, String prefix, List<String> out) {
        for (Map.Entry<?, ?> e : node.entrySet()) {
            final Object v = e.getValue();
            if (v instanceof Map<?, ?> child) collectStrings(child, prefix + "." + e.getKey(), out);
            else if (v instanceof String s) out.add(prefix + "." + e.getKey() + " = " + s);
        }
    }
}
