package com.discordlogger.listener.player;

import com.discordlogger.log.Log;
import com.discordlogger.util.Names;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerChat implements Listener {
    private final JavaPlugin plugin;

    public PlayerChat(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (!plugin.getConfig().getBoolean("log.player.chat", true)) return;

        String who = Names.display(e.getPlayer(), plugin);
        String text = Log.mdEscape(e.getMessage());
        String msg = "**" + who + "**: " + text;
        Log.eventWithThumb("Player Chat", msg, Log.playerAvatarUrl(e.getPlayer().getUniqueId()));
    }
}
