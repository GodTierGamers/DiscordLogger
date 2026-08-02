package com.discordlogger.lang;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import com.discordlogger.config.ConfigMigrator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Every message the plugin shows, loaded from {@code lang.yml}.
 *
 * <p>Two audiences, two formats, deliberately kept apart:
 *
 * <ul>
 *   <li><b>In game</b> — {@link #chat} renders MiniMessage, so colour and formatting
 *       are editable without touching code.</li>
 *   <li><b>Discord</b> — {@link #text} returns plain text. Discord renders Markdown,
 *       not MiniMessage, so a {@code <green>} tag there would be posted literally.
 *       Keeping the two methods separate makes that hard to get wrong.</li>
 * </ul>
 *
 * <p>Versioned with {@code config.yml} rather than separately: all of this plugin's
 * config files carry one shared version and are upgraded together, so there is never a
 * combination of versions to reason about.
 *
 * <p>Console messages are deliberately absent. They are diagnostics, and a translated
 * error is one nobody can search for — support threads and search results depend on
 * the English text being stable.
 *
 * <p>A missing key returns the key itself rather than empty or null. A blank message
 * looks like the plugin failing silently; {@code chat.reload-ok} appearing in game is
 * obviously a lang problem and says exactly which entry to look at.
 */
public final class Lang {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private static volatile YamlConfiguration lang = new YamlConfiguration();

    /**
     * The shipped English, loaded from the jar at class-load rather than in
     * {@link #reload}.
     *
     * <p>It is a constant of the build, so it does not need a plugin instance to
     * read — and having it available unconditionally means messages still resolve if
     * reload has not run yet, or failed. Without this every message would fall back
     * to its own key until the first successful load.
     */
    private static final YamlConfiguration BUNDLED = loadBundled();

    private static YamlConfiguration loadBundled() {
        try (InputStream in = Lang.class.getResourceAsStream("/lang.yml")) {
            if (in == null) return new YamlConfiguration();
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new YamlConfiguration();
        }
    }

    private Lang() {}

    /**
     * Load {@code lang.yml}, writing the bundled copy on first run.
     *
     * <p>The bundled file is kept alongside the user's and used as a fallback, so a
     * key added in a later version still resolves for someone whose lang.yml predates
     * it — they get English for the new message rather than a raw key.
     */
    public static void reload(JavaPlugin plugin) {
        final File file = new File(plugin.getDataFolder(), "lang.yml");
        if (!file.exists()) {
            plugin.saveResource("lang.yml", false);
        }

        // Shares config.yml's version, so it upgrades on the same schedule and
        // through the same code. ConfigMigrator is file-agnostic: it reads the
        // default from the jar and the user's copy from disk, whichever they are.
        ConfigMigrator.migrateIfVersionChanged(plugin, "lang.yml", file);

        lang = YamlConfiguration.loadConfiguration(file);
    }

    /** A message for Discord: plain text, placeholders filled, no MiniMessage. */
    public static String text(String key, Object... placeholders) {
        return fill(raw(key), placeholders);
    }

    /** A message for in game: MiniMessage rendered to a Component. */
    public static Component chat(String key, Object... placeholders) {
        return MINI.deserialize(fill(raw(key), placeholders));
    }

    /** As {@link #chat}, with {@code chat.prefix} in front. */
    public static Component prefixed(String key, Object... placeholders) {
        return MINI.deserialize(fill(raw("chat.prefix") + raw(key), placeholders));
    }

    /** Whether a key exists at all, for callers that vary structure rather than wording. */
    public static boolean has(String key) {
        return lang.isString(key) || BUNDLED.isString(key);
    }

    private static String raw(String key) {
        final String value = lang.getString(key);
        if (value != null) return value;

        // Falls back to the shipped English so a lang.yml from an older version does
        // not leave new messages showing as raw keys.
        final String shipped = BUNDLED.getString(key);
        return shipped != null ? shipped : key;
    }

    /**
     * Replaces {@code {name}} placeholders.
     *
     * <p>Takes alternating name/value pairs. An unknown placeholder is left in the
     * message rather than blanked, so a typo is visible in the output instead of
     * producing a sentence with a hole in it.
     */
    private static String fill(String message, Object... placeholders) {
        if (message == null || placeholders.length == 0) return message;

        String out = message;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            final String token = "{" + placeholders[i] + "}";
            final String value = String.valueOf(placeholders[i + 1]);
            out = out.replace(token, value);
        }
        return out;
    }

    /** Every key under a section, for callers that enumerate (e.g. death causes). */
    public static Map<String, Object> section(String path) {
        final var sec = lang.getConfigurationSection(path);
        if (sec != null) return sec.getValues(false);
        final var fallback = BUNDLED.getConfigurationSection(path);
        return fallback != null ? fallback.getValues(false) : Map.of();
    }
}
