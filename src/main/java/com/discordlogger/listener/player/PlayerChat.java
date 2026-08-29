package com.discordlogger.listener.player;

import com.discordlogger.filter.Filters;
import com.discordlogger.lang.Lang;
import com.discordlogger.log.Log;
import com.discordlogger.util.Names;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Logs player chat.
 *
 * <h2>Why the deprecated event</h2>
 *
 * <p>This listened to Paper's {@code AsyncChatEvent}, which does not exist on
 * Spigot. {@link AsyncPlayerChatEvent} does, on every version from 1.7 to current,
 * and Paper still fires it — it carries {@code @Deprecated} but not
 * {@code forRemoval}, and Paper marks it {@code @Warning(value = false, reason =
 * "Don't nag on old event yet")}, which is an explicit decision not to push plugins
 * off it yet.
 *
 * <p>So one listener covers the whole supported range with no reflection and no
 * second class to keep in step. The message arrives as a {@code String} rather than
 * a Component, which also removes the need to flatten it before filtering.
 *
 * <p>The trade is that a chat plugin rewriting messages only through the modern
 * Paper event would not be reflected here. That is worth revisiting if it ever
 * shows up; it is not worth a reflective dual listener in advance.
 */
public final class PlayerChat implements Listener {
    private final JavaPlugin plugin;

    public PlayerChat(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (!plugin.getConfig().getBoolean("log.player.chat.enabled", true)) return;
        if (Filters.blocksPlayer(e.getPlayer()) || Filters.blocksWorld(e.getPlayer().getWorld().getName())) return;

        String who = Names.display(e.getPlayer(), plugin);
        final String plain = e.getMessage();
        if (Filters.blocksChat(plain)) return;

        String text = Log.mdEscape(plain);
        String msg  = Lang.textFor(e.getPlayer(), "discord.player-chat", "player", who, "message", text);
        Log.eventWithThumb("Player Chat", msg, Log.playerAvatarUrl(e.getPlayer().getUniqueId()));
    }
}
