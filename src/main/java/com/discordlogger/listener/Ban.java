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

public final class Ban implements Listener {
    private final JavaPlugin plugin;
    public Ban(JavaPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        if (!plugin.getConfig().getBoolean("log.moderation.ban", true)) return;
        handle(e.getPlayer(), e.getMessage()); // includes leading "/"
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent e) {
        if (!plugin.getConfig().getBoolean("log.moderation.ban", true)) return;
        final String raw = "/" + e.getCommand(); // ServerCommandEvent lacks leading "/"
        handle(null, raw);
    }

    private void handle(Player actorPlayer, String rawWithSlash) {
        final String raw = rawWithSlash.startsWith("/") ? rawWithSlash.substring(1) : rawWithSlash;
        if (raw.isBlank()) return;

        // Parse: <cmd> <player> [<duration or reason> ...]
        final String[] parts = raw.split("\\s+", 3);
        final String cmd = parts[0].toLowerCase(Locale.ROOT);
        final boolean isBan    = cmd.equals("ban");
        final boolean isTemp   = cmd.equals("tempban");
        if (!isBan && !isTemp) return;

        final String targetName = parts.length > 1 ? parts[1] : "(unknown)";
        final String remainder  = parts.length > 2 ? parts[2] : null;

        // Duration / Reason rules (kept simple & robust):
        // - /ban <player> [reason...]          => duration = "lifetime", reason = remainder
        // - /tempban <player> <duration> [reason...] => duration = first token of remainder, reason = the rest
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

        // Resolve actor (nicknames respected) and console label
        final String moderatorName = (actorPlayer != null)
                ? Names.display(actorPlayer, plugin)
                : "CONSOLE";

        // Try to show TARGET’s head as thumbnail
        String thumb = null;
        UUID targetUuid = resolveUuid(targetName);
        if (targetUuid != null) thumb = Log.playerAvatarUrl(targetUuid);

        // Build FIELDS to mirror your scaffold (no JSON; we use a helper in Log)
        List<Log.Field> fields = new ArrayList<>();
        fields.add(new Log.Field("Player Name:", targetName));
        fields.add(new Log.Field("Ban Reason (if provided):", emptyToNA(banReason)));
        fields.add(new Log.Field("Banned by:", moderatorName));
        fields.add(new Log.Field("Ban Duration:", emptyToNA(banDuration))); // “lifetime” if not specified

        // Title and Author exactly as in your example
        final String title      = "Player Ban";
        final String authorName = "Server Logs";

        // Send via your standard pipeline (color from embeds.colors.ban)
        Log.eventFieldsWithThumb(
                "Ban",            // category -> color key "ban"
                title,
                authorName,
                fields,
                thumb
        );
    }

    private static String emptyToNA(String s) {
        return (s == null || s.isBlank()) ? "N/A" : stripColors(s);
    }

    private static String stripColors(String s) { return s.replaceAll("§.", ""); }

    private static UUID resolveUuid(String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) return p.getUniqueId();
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        return off != null ? off.getUniqueId() : null;
    }
}
