package com.discordlogger.listener;

import com.discordlogger.log.Log;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;

public final class PlayerCommand implements Listener {
    private final Plugin plugin;

    public PlayerCommand(Plugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        if (!plugin.getConfig().getBoolean("log.player.command", true)) return;
        final String name = Log.mdEscape(e.getPlayer().getName());
        final String cmd  = Log.mdEscape(e.getMessage()); // includes leading slash
        final String thumb = Log.playerAvatarUrl(e.getPlayer().getUniqueId());
        Log.eventWithThumb("Player Command", name + " ran: " + cmd, thumb);
    }
}
