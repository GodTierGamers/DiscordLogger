package com.discordlogger.listener;

import com.discordlogger.log.Log;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

public final class PlayerChat implements Listener {
    private final Plugin plugin;

    public PlayerChat(Plugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        if (!plugin.getConfig().getBoolean("log.player.chat", true)) return;
        final String name = Log.mdEscape(e.getPlayer().getName());
        final String msg  = Log.mdEscape(e.getMessage());
        final String thumb = Log.playerAvatarUrl(e.getPlayer().getUniqueId());
        Log.eventWithThumb("Player Chat", name + " — " + msg, thumb);
    }
}
