package com.discordlogger.listener.player;

import com.discordlogger.filter.Filters;
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
        if (!plugin.getConfig().getBoolean("log.player.command.enabled", true)) return;
        if (Filters.blocksPlayer(e.getPlayer()) || Filters.blocksWorld(e.getPlayer().getWorld().getName())) return;

        // Checked before anything is rendered: /login carries a password, so it must
        // not reach the console echo either.
        if (Filters.blocksCommand(e.getMessage())) return;

        String who = Names.display(e.getPlayer(), plugin);
        String cmd = Log.mdEscape(Log.redactWebhooks(e.getMessage())); // includes leading '/'
        String msg = who + " ran: " + cmd;
        Log.eventWithThumb("Player Command", msg, Log.playerAvatarUrl(e.getPlayer().getUniqueId()));
    }
}
