package com.discordlogger.listener.moderation;

import com.discordlogger.log.Log;
import com.discordlogger.util.Names;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Kick implements Listener {
    private final JavaPlugin plugin;

    /**
     * Stores kick intent keyed by target player UUID.
     * Written when the kick command is parsed; consumed by PlayerKickEvent.
     * Entries that don't result in an actual kick are removed after 2 ticks
     * via a scheduled cleanup, so stale entries never accumulate.
     */
    private final ConcurrentHashMap<UUID, KickData> pendingKicks = new ConcurrentHashMap<>();

    public Kick(JavaPlugin plugin) { this.plugin = plugin; }

    private record KickData(String actorName, String reason) {}

    // -------------------------------------------------------------------------
    // Command interception — records intent
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        if (!plugin.getConfig().getBoolean("log.moderation.kick", true)) return;
        handleCommand(e.getPlayer(), e.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent e) {
        if (!plugin.getConfig().getBoolean("log.moderation.kick", true)) return;
        handleCommand(null, "/" + e.getCommand());
    }

    private void handleCommand(Player actorPlayer, String rawWithSlash) {
        final String raw = rawWithSlash.startsWith("/") ? rawWithSlash.substring(1) : rawWithSlash;
        if (raw.isBlank()) return;

        final String[] parts = raw.split("\\s+", 3);
        if (!parts[0].toLowerCase(Locale.ROOT).equals("kick")) return;

        // Permission gate (console always allowed)
        if (actorPlayer != null && !hasAny(actorPlayer,
                "minecraft.command.kick", "bukkit.command.kick", "essentials.kick")) {
            return;
        }

        final String targetName = parts.length > 1 ? parts[1] : null;
        if (targetName == null) return;

        final Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) return;

        final String actorName = (actorPlayer != null)
                ? Names.display(actorPlayer, plugin)
                : "CONSOLE";
        final String reason = parts.length > 2 ? parts[2] : null;

        // Store intent; PlayerKickEvent will consume it
        pendingKicks.put(target.getUniqueId(), new KickData(actorName, reason));

        // Cleanup: if the kick command failed (e.g. player went offline before it ran),
        // remove the stale entry after 2 ticks so it doesn't linger indefinitely.
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                pendingKicks.remove(target.getUniqueId()), 2L);
    }

    // -------------------------------------------------------------------------
    // PlayerKickEvent — confirms the kick actually happened
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent e) {
        if (!plugin.getConfig().getBoolean("log.moderation.kick", true)) return;

        final UUID uuid = e.getPlayer().getUniqueId();
        final KickData data = pendingKicks.remove(uuid);

        // No pending kick record means this disconnect wasn't from a /kick command
        // (e.g. timeout, ban, plugin-triggered kick) — don't log it here.
        if (data == null) return;

        final String targetName = e.getPlayer().getName();
        final String thumb = Log.playerAvatarUrl(uuid);

        List<Log.Field> fields = new ArrayList<>();
        fields.add(new Log.Field("Player Kicked:", targetName));
        fields.add(new Log.Field("Kick Reason:",
                (data.reason() == null || data.reason().isBlank()) ? "N/A" : data.reason()));
        fields.add(new Log.Field("Kicked by:", data.actorName()));

        Log.eventFieldsWithThumb("kick", "Player Kicked", null, fields, thumb);
    }

    // -------------------------------------------------------------------------

    private static boolean hasAny(Player p, String... nodes) {
        if (p.isOp()) return true;
        for (String n : nodes) if (p.hasPermission(n)) return true;
        return false;
    }
}