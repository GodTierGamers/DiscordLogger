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

public final class Deop implements Listener {
    private final JavaPlugin plugin;
    public Deop(JavaPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        if (!plugin.getConfig().getBoolean("log.moderation.deop", true)) return;
        handle(e.getPlayer(), e.getMessage()); // includes leading "/"
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent e) {
        if (!plugin.getConfig().getBoolean("log.moderation.deop", true)) return;
        final String raw = "/" + e.getCommand(); // ServerCommandEvent lacks leading "/"
        handle(null, raw);
    }

    private void handle(Player actorPlayer, String rawWithSlash) {
        final String raw = rawWithSlash.startsWith("/") ? rawWithSlash.substring(1) : rawWithSlash;
        if (raw.isBlank()) return;

        // Parse: deop <player>
        final String[] parts = raw.split("\\s+", 3);
        final String cmd = parts[0].toLowerCase(Locale.ROOT);
        if (!cmd.equals("deop")) return;

        // Permission gate (console always allowed)
        if (actorPlayer != null && !hasAny(actorPlayer,
                "minecraft.command.deop", "bukkit.command.deop", "essentials.deop")) {
            return;
        }

        final String targetName = parts.length > 1 ? parts[1] : "(unknown)";
        final OfflinePlayer off = Bukkit.getOfflinePlayer(targetName);
        final boolean wasOp = off.isOp();

        // Verify success next tick (op -> not op)
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (wasOp && !off.isOp()) {
                final String moderatorName = (actorPlayer != null)
                        ? Names.display(actorPlayer, plugin)
                        : "CONSOLE";

                String thumb = null;
                UUID uuid = off.getUniqueId();
                if (uuid != null) thumb = Log.playerAvatarUrl(uuid);

                List<Log.Field> fields = new ArrayList<>();
                fields.add(new Log.Field("Player Name:", targetName));
                fields.add(new Log.Field("Deopped by:", moderatorName));

                Log.eventFieldsWithThumb(
                        "deop",               // color key from embeds.colors.deop
                        "Player Deopped",     // title to mirror your style
                        null,                 // author -> default (embeds.author)
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
}
