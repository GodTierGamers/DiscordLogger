package com.discordlogger.listener;

import com.discordlogger.log.Log;
import com.discordlogger.util.Names;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class Unban implements Listener {
    private final JavaPlugin plugin;
    public Unban(JavaPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        if (!plugin.getConfig().getBoolean("log.moderation.unban", true)) return; // reuse same toggle group
        handle(e.getPlayer(), e.getMessage()); // includes leading "/"
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent e) {
        if (!plugin.getConfig().getBoolean("log.moderation.unban", true)) return;
        final String raw = "/" + e.getCommand(); // ServerCommandEvent lacks leading "/"
        handle(null, raw);
    }

    private void handle(Player actorPlayer, String rawWithSlash) {
        final String raw = rawWithSlash.startsWith("/") ? rawWithSlash.substring(1) : rawWithSlash;
        if (raw.isBlank()) return;

        // Parse: <cmd> <player>
        final String[] parts = raw.split("\\s+", 3);
        final String cmd = parts[0].toLowerCase(Locale.ROOT);
        final boolean isPardon = cmd.equals("pardon") || cmd.equals("unban");
        if (!isPardon) return;

        final String targetName = parts.length > 1 ? parts[1] : "(unknown)";

        // Moderator display (respects nicknames) or CONSOLE
        final String moderatorName = (actorPlayer != null)
                ? Names.display(actorPlayer, plugin)
                : "CONSOLE";

        // Target head thumbnail (if UUID resolvable)
        String thumb = null;
        UUID targetUuid = resolveUuid(targetName);
        if (targetUuid != null) thumb = Log.playerAvatarUrl(targetUuid);

        // Fields per your scaffold
        List<Log.Field> fields = new ArrayList<>();
        fields.add(new Log.Field("Player Unbanned", targetName));
        fields.add(new Log.Field("Unbanned by:", moderatorName));

        // Use category "ban" to pick up the red color; title "Player Unbanned" per your JSON.
        // Author: null -> uses embeds.author (default "Server Logs")
        Log.eventFieldsWithThumb(
                "Unban",             // color key (red)
                "Player Unbanned", // title
                null,              // author -> default (embeds.author)
                fields,
                thumb
        );
    }

    private static UUID resolveUuid(String name) {
        if (name == null || name.isBlank()) return null;
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) return p.getUniqueId();
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        return off != null ? off.getUniqueId() : null;
    }
}
