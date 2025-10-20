package com.discordlogger.listener.player;

import com.discordlogger.log.Log;
import com.discordlogger.util.Names;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerCommand implements Listener {
    private final JavaPlugin plugin;

    public PlayerCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        if (!plugin.getConfig().getBoolean("log.player.command", true)) return;

        String who = Names.display(e.getPlayer(), plugin);
        String cmd = Log.mdEscape(e.getMessage()); // includes leading '/'
        String msg = who + " ran: " + cmd;
        Log.eventWithThumb("Player Command", msg, Log.playerAvatarUrl(e.getPlayer().getUniqueId()));
    }
}
