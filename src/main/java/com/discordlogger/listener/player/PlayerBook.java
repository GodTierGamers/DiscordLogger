package com.discordlogger.listener.player;

import com.discordlogger.filter.Filters;
import com.discordlogger.lang.Lang;
import com.discordlogger.log.Log;
import com.discordlogger.util.Names;
import com.discordlogger.util.Strings;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * A book being signed — the point at which it becomes copyable and shareable.
 *
 * <p>Only signing, never editing. An unsigned book is a private draft that fires
 * this event on every page change, which would be both noisy and a running
 * transcript of something nobody has published yet.
 */
public final class PlayerBook implements Listener {
    private final JavaPlugin plugin;
    public PlayerBook(JavaPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEditBook(PlayerEditBookEvent e) {
        if (!plugin.getConfig().getBoolean("log.player.book.enabled", true)) return;
        if (!e.isSigning()) return;

        final Player p = e.getPlayer();
        if (Filters.blocksPlayer(p) || Filters.blocksWorld(p.getWorld().getName())) return;

        final BookMeta meta = e.getNewBookMeta();
        final String title = (meta != null && !Strings.isBlank(meta.getTitle()))
                ? meta.getTitle() : Lang.text("discord.book.untitled");
        final int pages = (meta != null && meta.getPages() != null) ? meta.getPages().size() : 0;

        final List<Log.Field> fields = new ArrayList<>();
        fields.add(new Log.Field(Lang.text("discord.book.player-field"), Names.display(p, plugin)));
        fields.add(new Log.Field(Lang.text("discord.book.title-field"), Log.mdEscape(title), true));
        fields.add(new Log.Field(Lang.text("discord.book.pages-field"), String.valueOf(pages), true));

        Log.eventFieldsWithThumb(
                "player_book",
                Lang.text("discord.book.title"),
                null,
                fields,
                Log.playerAvatarUrl(p.getUniqueId())
        );
    }
}
