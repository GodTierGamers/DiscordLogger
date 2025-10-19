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

public final class Kick implements Listener {
    private final JavaPlugin plugin;
    public Kick(JavaPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        if (!plugin.getConfig().getBoolean("log.moderation.kick", true)) return;
        handle(e.getPlayer(), e.getMessage()); // includes leading "/"
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent e) {
        if (!plugin.getConfig().getBoolean("log.moderation.kick", true)) return;
        final String raw = "/" + e.getCommand(); // ServerCommandEvent lacks leading "/"
        handle(null, raw);
    }

    private void handle(Player actorPlayer, String rawWithSlash) {
        final String raw = rawWithSlash.startsWith("/") ? rawWithSlash.substring(1) : rawWithSlash;
        if (raw.isBlank()) return;

        // Parse: kick <player> [reason...]
        final String[] parts = raw.split("\\s+", 3);
        final String cmd = parts[0].toLowerCase(Locale.ROOT);
        if (!cmd.equals("kick")) return;

        final String targetName = parts.length > 1 ? parts[1] : "(unknown)";
        final String reasonRaw  = parts.length > 2 ? parts[2] : null;

        // Moderator display (respects nicknames) or CONSOLE
        final String moderatorName = (actorPlayer != null)
                ? Names.display(actorPlayer, plugin)
                : "CONSOLE";

        // Target head thumbnail (if UUID resolvable)
        String thumb = null;
        UUID targetUuid = resolveUuid(targetName);
        if (targetUuid != null) thumb = Log.playerAvatarUrl(targetUuid);

        // Fields in desired order: Player Kicked, Kick Reason (if provided), Kicked by
        List<Log.Field> fields = new ArrayList<>();
        fields.add(new Log.Field("Player Kicked", targetName));
        fields.add(new Log.Field("Kick Reason (if provided):",
                (reasonRaw == null || reasonRaw.isBlank()) ? "N/A" : stripColors(reasonRaw)));
        fields.add(new Log.Field("Kicked by:", moderatorName));

        // Category "kick" -> uses embeds.colors.kick
        Log.eventFieldsWithThumb(
                "kick",
                "Player Kicked",
                null,      // author -> default embeds.author ("Server Logs")
                fields,
                thumb
        );
    }

    private static String stripColors(String s) {
        return s == null ? null : s.replaceAll("§.", "");
    }

    private static UUID resolveUuid(String name) {
        if (name == null || name.isBlank()) return null;
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) return p.getUniqueId();
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        return off != null ? off.getUniqueId() : null;
    }
}
