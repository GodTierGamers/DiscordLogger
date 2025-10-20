package com.discordlogger.listener.player;

import com.discordlogger.log.Log;
import com.discordlogger.util.Names;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PlayerGamemode implements Listener {
    private final JavaPlugin plugin;
    public PlayerGamemode(JavaPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGamemodeChange(PlayerGameModeChangeEvent e) {
        if (!plugin.getConfig().getBoolean("log.player.gamemode", false)) return;

        final Player p = e.getPlayer();
        final GameMode from = p.getGameMode();      // old mode (event fires before apply)
        final GameMode to   = e.getNewGameMode();   // new mode

        if (from == to) return; // no-op / redundant

        final String playerName = Names.display(p, plugin);
        final UUID uuid = p.getUniqueId();
        final String thumb = Log.playerAvatarUrl(uuid);

        List<Log.Field> fields = new ArrayList<>();
        fields.add(new Log.Field("Player:", playerName));
        fields.add(new Log.Field("From:", pretty(from), true));
        fields.add(new Log.Field("To:", pretty(to), true));

        // category key -> "player_gamemode" (embeds.colors.player.gamemode)
        Log.eventFieldsWithThumb(
                "player_gamemode",
                "Gamemode Changed",
                null,   // author -> default embeds.author
                fields,
                thumb
        );
    }

    private static String pretty(GameMode gm) {
        if (gm == null) return "Unknown";
        String s = gm.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
