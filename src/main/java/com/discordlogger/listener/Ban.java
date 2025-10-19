package com.discordlogger.listener;

import com.discordlogger.log.Log;
import com.discordlogger.util.Names;
import org.bukkit.BanList;
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

public final class Ban implements Listener {
    private final JavaPlugin plugin;
    public Ban(JavaPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        if (!plugin.getConfig().getBoolean("log.moderation.ban", true)) return;
        handle(e.getPlayer(), e.getMessage()); // includes leading "/"
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent e) {
        if (!plugin.getConfig().getBoolean("log.moderation.ban", true)) return;
        final String raw = "/" + e.getCommand();
        handle(null, raw);
    }

    private void handle(Player actorPlayer, String rawWithSlash) {
        final String raw = rawWithSlash.startsWith("/") ? rawWithSlash.substring(1) : rawWithSlash;
        if (raw.isBlank()) return;

        // <cmd> <player> [duration/reason...]
        final String[] parts = raw.split("\\s+", 3);
        final String cmd = parts[0].toLowerCase(Locale.ROOT);
        final boolean isBan  = cmd.equals("ban");
        final boolean isTemp = cmd.equals("tempban");
        if (!isBan && !isTemp) return;

        // Permission gate (console always allowed)
        if (actorPlayer != null && !hasAny(actorPlayer,
                "minecraft.command.ban", "bukkit.command.ban", "essentials.ban",
                "essentials.tempban")) {
            return;
        }

        final String targetName = parts.length > 1 ? parts[1] : "(unknown)";
        final String remainder  = parts.length > 2 ? parts[2] : null;

        String banDuration = "lifetime";
        String banReason   = null;
        if (isTemp && remainder != null) {
            int sp = remainder.indexOf(' ');
            if (sp > 0) {
                banDuration = remainder.substring(0, sp).trim();
                banReason   = remainder.substring(sp + 1).trim();
            } else {
                banDuration = remainder.trim();
            }
        } else {
            banReason = remainder;
        }

        final BanList nameBans = Bukkit.getBanList(BanList.Type.NAME);
        final boolean wasBanned = nameBans.isBanned(targetName);

        // Snapshot mutable values for lambda
        final String reasonSnap   = banReason;
        final String durationSnap = banDuration;

        // Verify ban took effect on the next tick
        Bukkit.getScheduler().runTask(plugin, () -> {
            final boolean nowBanned = nameBans.isBanned(targetName);
            if (!wasBanned && nowBanned) {
                final String moderatorName = (actorPlayer != null)
                        ? Names.display(actorPlayer, plugin)
                        : "CONSOLE";

                String thumb = null;
                UUID targetUuid = resolveUuid(targetName);
                if (targetUuid != null) thumb = Log.playerAvatarUrl(targetUuid);

                List<Log.Field> fields = new ArrayList<>();
                fields.add(new Log.Field("Player Name:", targetName));
                fields.add(new Log.Field("Ban Reason (if provided):",
                        (reasonSnap == null || reasonSnap.isBlank()) ? "N/A" : reasonSnap));
                fields.add(new Log.Field("Banned by:", moderatorName));
                fields.add(new Log.Field("Ban Duration:",
                        (durationSnap == null || durationSnap.isBlank()) ? "N/A" : durationSnap));

                Log.eventFieldsWithThumb(
                        "ban",
                        "Player Banned",
                        "Server Logs",
                        fields,
                        thumb
                );
            }
        });
    }

    private static boolean hasAny(Player p, String... nodes) {
        if (p.isOp()) return true;
        for (String n : nodes) if (p.hasPermission(n)) return true;
        return false;
    }

    @SuppressWarnings("deprecation")
    private static UUID resolveUuid(String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) return p.getUniqueId();
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        return off != null ? off.getUniqueId() : null;
    }
}
