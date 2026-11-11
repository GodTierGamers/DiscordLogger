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
 *
 * <p>Only lava and water, each with its own toggle, and the event ships off. The
 * reason is the water half: an MLG bucket is an ordinary survival move, so on a busy
 * server water placements are most of what this would post and the lava placements
 * that matter are lost among them. Anything else a bucket can hold — milk, a fish,
 * powder snow — places nothing worth a record.
 */
public final class PlayerBucket implements Listener {
    private static final String ICON_BASE =
            "https://discordlogger.godtiergamers.xyz/assets/icons/buckets/";

    private final JavaPlugin plugin;
    public PlayerBucket(JavaPlugin plugin) { this.plugin = plugin; }

    /**
     * What a bucket can place that is worth a message, and how each one is shown.
     *
     * <p>The config paths are written out in full rather than built from the name.
     * {@code validate-config-generator.py} greps the Java for each key the wizard
     * offers, so a path assembled at runtime reads as a setting nothing honours.
     */
    enum Liquid {
        LAVA("log.player.bucket.lava", "lava", "discord.bucket.lava"),
        WATER("log.player.bucket.water", "water", "discord.bucket.water");

        final String enabledKey;
        final String iconName;
        final String langKey;
        Liquid(String enabledKey, String iconName, String langKey) {
            this.enabledKey = enabledKey;
            this.iconName = iconName;
            this.langKey = langKey;
        }
        // A still: the bucket item sprite does not animate, only the liquid block.
        String icon() { return ICON_BASE + iconName + ".png"; }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (!plugin.getConfig().getBoolean("log.player.bucket.enabled", false)) return;

        final Liquid liquid = liquidOf(e.getBucket());
        if (liquid == null) return;
        if (!plugin.getConfig().getBoolean(liquid.enabledKey, true)) return;

        final Player p = e.getPlayer();
        final Block b = e.getBlockClicked();
        if (Filters.blocksPlayer(p) || Filters.blocksWorld(b.getWorld().getName())) return;

        final List<Log.Field> fields = new ArrayList<>();
        fields.add(new Log.Field(Lang.text("discord.bucket.player-field"), Names.display(p, plugin)));
        fields.add(new Log.Field(Lang.text("discord.bucket.placed-field"), Lang.text(liquid.langKey), true));
        fields.add(new Log.Field(Lang.text("discord.bucket.where-field"), Places.of(b), true));

        // The liquid, not the player's head: lava or water is the thing a reader is
        // scanning for, and the name is already a field.
        Log.eventFieldsWithThumb(
                "player_bucket",
                Lang.text("discord.bucket.title"),
                null,
                fields,
                liquid.icon()
        );
    }

    /**
     * Which liquid a bucket holds, or null for one that places nothing.
     *
     * <p>Matched on the material's name rather than the enum constant: the names
     * moved in 1.13, and a name lookup keeps this compiling and behaving the same on
     * both sides of that split.
     */
    static Liquid liquidOf(Material bucket) {
        if (bucket == null) return null;
        final String n = bucket.name().toUpperCase(Locale.ROOT);
        if (n.contains("LAVA")) return Liquid.LAVA;
        if (n.contains("WATER")) return Liquid.WATER;
        return null;
    }
}
