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
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Set;

public final class Ban implements Listener {
    private final Plugin plugin;
    private static final Set<String> BAN_CMDS = Set.of("ban", "tempban");

    public Ban(Plugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        if (!plugin.getConfig().getBoolean("log.moderation.ban", true)) return;
        handle(e.getPlayer().getName(), e.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent e) {
        if (!plugin.getConfig().getBoolean("log.moderation.ban", true)) return;
        String raw = "/" + e.getCommand(); // ServerCommandEvent has no leading slash
        String actor = (e.getSender() != null) ? e.getSender().getName() : "Server";
        handle(actor, raw);
    }

    private void handle(String actorName, String rawWithSlash) {
        String raw = rawWithSlash.startsWith("/") ? rawWithSlash.substring(1) : rawWithSlash;
        if (raw.isBlank()) return;

        String[] parts = raw.split("\\s+", 3); // <cmd> <target> [reason...]
        String base = parts[0].toLowerCase(Locale.ROOT);
        if (!BAN_CMDS.contains(base)) return;

        String target = (parts.length > 1) ? parts[1] : "(unknown)";
        String reason = (parts.length > 2) ? parts[2] : null;

        // Pretty actor name (with nickname if available)
        String actorPretty;
        Player actorPlayer = Bukkit.getPlayerExact(actorName);
        if (actorPlayer != null) actorPretty = Names.display(actorPlayer, (JavaPlugin) plugin);
        else actorPretty = Log.mdEscape(actorName);

        // Try to show the TARGET’s head as thumbnail
        String thumb = null;
        Player tp = Bukkit.getPlayerExact(target);
        if (tp != null) thumb = Log.playerAvatarUrl(tp.getUniqueId());
        else {
            OfflinePlayer off = Bukkit.getOfflinePlayer(target);
            if (off.getUniqueId() != null) thumb = Log.playerAvatarUrl(off.getUniqueId());
        }

        StringBuilder msg = new StringBuilder();
        msg.append(actorPretty).append(" banned ").append(Log.mdEscape(target));
        if (reason != null && !reason.isBlank()) msg.append(": ").append(Log.mdEscape(reason));

        // Category title "Ban" → maps to embeds.colors.ban
        Log.eventWithThumb("Ban", msg.toString(), thumb);
    }
}
