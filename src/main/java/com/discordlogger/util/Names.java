package com.discordlogger.util;

import com.discordlogger.log.Log;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class Names {
    private Names() {}

    private static final Pattern SEC_CODES = Pattern.compile("§[0-9A-FK-ORa-fk-or]");
    private static final Pattern AMP_CODES = Pattern.compile("&[0-9A-FK-ORa-fk-or]");

    private static final Map<UUID, String> NICK_CACHE = new ConcurrentHashMap<>();

    /** Returns either "Nick (Real)" if nicknames enabled & present, else "Real". */
    public static String display(Player player, JavaPlugin plugin) {
        if (player == null) return "";
        final String real = player.getName();
        final boolean useNick = plugin.getConfig().getBoolean("format.nicknames", true);
        if (!useNick) return real;

        // Prefer live displayName, else cached nick
        String nick = cleanDisplay(player.getDisplayName());
        if (nick == null || nick.isBlank() || nick.equalsIgnoreCase(real)) {
            nick = NICK_CACHE.get(player.getUniqueId());
        }

        if (nick == null || nick.isBlank() || nick.equalsIgnoreCase(real)) {
            return real; // no distinct nickname
        }
        return Log.mdEscape(nick) + " (" + Log.mdEscape(real) + ")";
    }

    /** Capture & cache nickname if it’s distinct from real name. */
    public static void capture(Player player) {
        if (player == null) return;
        final String real = player.getName();
        String nick = cleanDisplay(player.getDisplayName());
        if (nick != null && !nick.isBlank() && !nick.equalsIgnoreCase(real)) {
            NICK_CACHE.put(player.getUniqueId(), nick);
        }
    }

    /** Remove cache entry (optional; useful on quit if desired). */
    public static void remove(Player player) {
        if (player != null) NICK_CACHE.remove(player.getUniqueId());
    }

    private static String cleanDisplay(String s) {
        if (s == null) return null;
        String out = SEC_CODES.matcher(s).replaceAll("");
        out = AMP_CODES.matcher(out).replaceAll("");
        out = out.replace("§", "").trim();
        return out;
    }
}
