package com.discordlogger.listener.moderation;

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

public final class Unban implements Listener {
    private final JavaPlugin plugin;
    public Unban(JavaPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        if (!plugin.getConfig().getBoolean("log.moderation.unban", true)) return;
        handle(e.getPlayer(), e.getMessage()); // includes leading "/"
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent e) {
        if (!plugin.getConfig().getBoolean("log.moderation.unban", true)) return;
        final String raw = "/" + e.getCommand();
        handle(null, raw);
    }

    private void handle(Player actorPlayer, String rawWithSlash) {
        final String raw = rawWithSlash.startsWith("/") ? rawWithSlash.substring(1) : rawWithSlash;
        if (raw.isBlank()) return;

        // <cmd> <player>
        final String[] parts = raw.split("\\s+", 3);
        final String cmd = parts[0].toLowerCase(Locale.ROOT);
        final boolean isPardon = cmd.equals("pardon") || cmd.equals("unban");
        if (!isPardon) return;

        // Permission gate (console always allowed)
        if (actorPlayer != null && !hasAny(actorPlayer,
                "minecraft.command.pardon", "minecraft.command.unban",
                "bukkit.command.unban", "essentials.unban")) {
            return;
        }

        final String targetName = parts.length > 1 ? parts[1] : "(unknown)";

        final BanList nameBans = Bukkit.getBanList(BanList.Type.NAME);
        final boolean wasBanned = nameBans.isBanned(targetName);

        // Only log if it actually changed from banned -> not banned on next tick
        Bukkit.getScheduler().runTask(plugin, () -> {
            final boolean nowBanned = nameBans.isBanned(targetName);
            if (wasBanned && !nowBanned) {
                final String moderatorName = (actorPlayer != null)
                        ? Names.display(actorPlayer, plugin)
                        : "CONSOLE";

                String thumb = null;
                UUID targetUuid = resolveUuid(targetName);
                if (targetUuid != null) thumb = Log.playerAvatarUrl(targetUuid);

                List<Log.Field> fields = new ArrayList<>();
                fields.add(new Log.Field("Player Unbanned", targetName));
                fields.add(new Log.Field("Unbanned by:", moderatorName));

                Log.eventFieldsWithThumb(
                        "unban",            // color key for unban
                        "Player Unbanned",
                        null,               // default author
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
