package com.discordlogger.listener.player;

import com.discordlogger.filter.Filters;
import com.discordlogger.lang.Lang;
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
        if (!plugin.getConfig().getBoolean("log.player.chat.enabled", true)) return;
        if (Filters.blocksPlayer(e.getPlayer()) || Filters.blocksWorld(e.getPlayer().getWorld().getName())) return;

        String who  = Names.display(e.getPlayer(), plugin);
        final String plain = PlainTextComponentSerializer.plainText().serialize(e.message());
        if (Filters.blocksChat(plain)) return;

        String text = Log.mdEscape(plain);
        String msg  = Lang.textFor(e.getPlayer(), "discord.player-chat", "player", who, "message", text);
        Log.eventWithThumb("Player Chat", msg, Log.playerAvatarUrl(e.getPlayer().getUniqueId()));
    }
}