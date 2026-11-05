package com.discordlogger.listener.player;

import com.discordlogger.filter.Filters;
import com.discordlogger.lang.Lang;
import com.discordlogger.log.Log;
import com.discordlogger.util.Names;
import com.discordlogger.util.Places;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lava and water placed from a bucket — the oldest griefing technique there is.
 *
 * <p>Only emptying, never filling. Picking liquid up destroys nothing, and logging
 * it would double the volume of an event whose whole value is that it is rare.
 */
public final class PlayerBucket implements Listener {
    private final JavaPlugin plugin;
    public PlayerBucket(JavaPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (!plugin.getConfig().getBoolean("log.player.bucket.enabled", true)) return;

        final Player p = e.getPlayer();
        final Block b = e.getBlockClicked();
        if (Filters.blocksPlayer(p) || Filters.blocksWorld(b.getWorld().getName())) return;

        final List<Log.Field> fields = new ArrayList<>();
        fields.add(new Log.Field(Lang.text("discord.bucket.player-field"), Names.display(p, plugin)));
        fields.add(new Log.Field(Lang.text("discord.bucket.placed-field"), contents(e.getBucket()), true));
        fields.add(new Log.Field(Lang.text("discord.bucket.where-field"), Places.of(b), true));

        Log.eventFieldsWithThumb(
                "player_bucket",
                Lang.text("discord.bucket.title"),
                null,
                fields,
                Log.playerAvatarUrl(p.getUniqueId())
        );
    }

    /**
     * The bucket's contents as a word. Matched on the name rather than the enum
     * constant: the material names moved in 1.13, and a name lookup keeps this
     * compiling and behaving the same on both sides of that split.
     */
    static String contents(Material bucket) {
        if (bucket == null) return "Unknown";
        final String n = bucket.name().toUpperCase(Locale.ROOT);
        if (n.contains("LAVA")) return Lang.text("discord.bucket.lava");
        if (n.contains("WATER")) return Lang.text("discord.bucket.water");
        if (n.contains("POWDER_SNOW")) return Lang.text("discord.bucket.powder-snow");
        if (n.contains("MILK")) return Lang.text("discord.bucket.milk");
        // A fish bucket, or something a future version adds.
        final String s = n.replace("_BUCKET", "").replace('_', ' ').toLowerCase(Locale.ROOT);
        return s.isEmpty() ? "Unknown" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
