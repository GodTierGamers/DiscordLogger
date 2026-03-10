package com.discordlogger.listener.player;

import com.discordlogger.log.Log;
import com.discordlogger.util.Names;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerChat implements Listener {
    private final JavaPlugin plugin;

    public PlayerChat(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        if (!plugin.getConfig().getBoolean("log.player.chat", true)) return;

        String who  = Names.display(e.getPlayer(), plugin);
        String text = Log.mdEscape(PlainTextComponentSerializer.plainText().serialize(e.message()));
        String msg  = "**" + who + "**: " + text;
        Log.eventWithThumb("Player Chat", msg, Log.playerAvatarUrl(e.getPlayer().getUniqueId()));
    }
}