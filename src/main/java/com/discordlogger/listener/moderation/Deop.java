package com.discordlogger.listener.moderation;

import com.discordlogger.log.Log;
import com.discordlogger.util.Names;
import com.discordlogger.util.Roster;
import com.discordlogger.util.Strings;
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
        if (!plugin.getConfig().getBoolean("log.moderation.deop.enabled", true)) return;
        handle(e.getPlayer(), e.getMessage()); // includes leading "/"
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent e) {
        if (!plugin.getConfig().getBoolean("log.moderation.deop.enabled", true)) return;
        final String raw = "/" + e.getCommand(); // ServerCommandEvent lacks leading "/"
        handle(null, raw);
    }

    private void handle(Player actorPlayer, String rawWithSlash) {
        final String raw = rawWithSlash.startsWith("/") ? rawWithSlash.substring(1) : rawWithSlash;
        if (Strings.isBlank(raw)) return;

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
        // See Roster: read from the server's operator list by name, because an
        // OfflinePlayer built before the command cannot answer this on older versions.
        final boolean wasOp = Roster.isOp(targetName);

        // Verify success next tick (op -> not op)
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (wasOp && !Roster.isOp(targetName)) {
                final String moderatorName = (actorPlayer != null)
                        ? Names.display(actorPlayer, plugin)
                        : "CONSOLE";

                String thumb = null;
                final OfflinePlayer off = Bukkit.getOfflinePlayer(targetName);
                UUID uuid = off == null ? null : off.getUniqueId();
                if (uuid != null) thumb = Log.playerAvatarUrl(uuid);

                List<Log.Field> fields = new ArrayList<>();
                fields.add(new Log.Field("Player Name:", targetName));
                fields.add(new Log.Field("Deopped by:", moderatorName));

                Log.eventFieldsWithThumb(
                        "deop",               // colour from log.moderation.deop.color
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
