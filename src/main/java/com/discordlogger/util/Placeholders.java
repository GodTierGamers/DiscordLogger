package com.discordlogger.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

/**
 * Expands PlaceholderAPI placeholders in outgoing messages, when it is installed.
 *
 * <h2>Reflection, not a dependency</h2>
 *
 * <p>Reached by reflection for the same reason {@link ClientPlatform} reaches Floodgate
 * that way: PlaceholderAPI is present on roughly a third of servers, and the other
 * two-thirds should not carry a compile-time coupling to a plugin they do not run. One
 * method is resolved once and cached; a server without it pays a single failed lookup
 * at startup and nothing after.
 *
 * <h2>What it is allowed to touch</h2>
 *
 * <p>Only {@code lang.yml} values, and only after this plugin's own {@code {token}}
 * substitution has already run. That ordering is deliberate: a player who types
 * {@code %player_name%} into chat must never have it expanded. Their message reaches
 * the template as a value, and values are not re-scanned — so the only placeholders
 * that resolve are the ones the server owner wrote into {@code lang.yml} themselves.
 */
public final class Placeholders {

    private static final String API_CLASS = "me.clip.placeholderapi.PlaceholderAPI";

    /** Resolved once. Null when PlaceholderAPI is absent, which is the common case. */
    private static volatile Method setPlaceholders;
    private static volatile boolean resolved;

    /** Mirrors {@code format.placeholders}; false unless the config turns it on. */
    private static volatile boolean enabled;

    private Placeholders() {}

    /** Re-read the toggle. Called from applyRuntimeConfig, so /reload picks it up. */
    public static void reload(JavaPlugin plugin) {
        enabled = plugin.getConfig().getBoolean("format.placeholders", true) && available();
        if (enabled) {
            plugin.getLogger().info("PlaceholderAPI found — placeholders in lang.yml will be expanded.");
        }
    }

    /** True when PlaceholderAPI is installed and its API can be reached. */
    public static boolean available() {
        if (resolved) return setPlaceholders != null;
        synchronized (Placeholders.class) {
            if (resolved) return setPlaceholders != null;
            resolved = true;
            try {
                if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return false;
                setPlaceholders = Class.forName(API_CLASS)
                        .getMethod("setPlaceholders", OfflinePlayer.class, String.class);
            } catch (Throwable absentOrChanged) {
                setPlaceholders = null;
            }
            return setPlaceholders != null;
        }
    }

    /**
     * Expands placeholders against a player, or returns the text unchanged.
     *
     * <p>Never throws. An expansion is somebody else's code running inside this
     * plugin's send path, so a misbehaving one must cost the message its placeholders
     * and nothing more — a logging plugin that stops logging because a third-party
     * expansion threw is worse than one that prints {@code %some_placeholder%}.
     */
    public static String apply(OfflinePlayer player, String text) {
        if (!enabled || text == null || text.isEmpty() || player == null) return text;
        if (text.indexOf('%') < 0) return text;      // nothing to expand, skip the call
        final Method m = setPlaceholders;
        if (m == null) return text;
        try {
            final Object out = m.invoke(null, player, text);
            return (out instanceof String) ? (String) out : text;
        } catch (Throwable misbehavingExpansion) {
            return text;
        }
    }
}
