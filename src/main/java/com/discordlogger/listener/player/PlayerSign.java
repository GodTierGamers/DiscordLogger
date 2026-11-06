package com.discordlogger.listener.player;

import com.discordlogger.filter.Filters;
import com.discordlogger.lang.Lang;
import com.discordlogger.log.Log;
import com.discordlogger.util.Names;
import com.discordlogger.util.Places;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * The text written on a sign, at the moment it is written.
 *
 * <p>Signs are a standing harassment vector: the text is visible to everyone who
 * walks past and nothing records who placed it. Catching it here means the channel
 * has the author and the location even if the sign is broken minutes later.
 */
public final class PlayerSign implements Listener {
    private final JavaPlugin plugin;
    public PlayerSign(JavaPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent e) {
        if (!plugin.getConfig().getBoolean("log.player.sign.enabled", true)) return;

        final Player p = e.getPlayer();
        final Block b = e.getBlock();
        if (Filters.blocksPlayer(p) || Filters.blocksWorld(b.getWorld().getName())) return;

        final StringBuilder text = new StringBuilder();
        for (final String line : e.getLines()) {
            if (line == null || line.isEmpty()) continue;
            if (text.length() > 0) text.append('\n');
            text.append(line);
        }
        // A sign cleared of all text says nothing worth a message.
        if (text.length() == 0) return;

        final List<Log.Field> fields = new ArrayList<>();
        fields.add(new Log.Field(Lang.text("discord.sign.player-field"), Names.display(p, plugin)));
        fields.add(new Log.Field(Lang.text("discord.sign.where-field"), Places.of(b), true));
        // Escaped: sign text is player-controlled and goes into an embed verbatim.
        fields.add(new Log.Field(Lang.text("discord.sign.text-field"), Log.mdEscape(text.toString())));

        Log.eventFieldsWithThumb(
                "player_sign",
                Lang.text("discord.sign.title"),
                null,
                fields,
                Log.playerAvatarUrl(p.getUniqueId())
        );
    }

}
