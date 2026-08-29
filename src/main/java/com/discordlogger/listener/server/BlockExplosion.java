package com.discordlogger.listener.server;

import com.discordlogger.filter.Filters;
import com.discordlogger.log.Log;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Explosions with no entity behind them — a bed in the Nether, a respawn anchor.
 *
 * <h2>Why this is not just another method on {@link Explosion}</h2>
 *
 * <p>{@code BlockExplodeEvent} arrived in <b>1.8.3</b>, and the plugin supports 1.8.0.
 * A class cannot name a type the server does not have: the JVM fails to load it, and
 * registering it would throw {@link NoClassDefFoundError} out of {@code onEnable} and
 * take the whole plugin down. Keeping this handler on {@code Explosion} would
 * therefore have cost entity explosions too — creepers and TNT, which 1.8.0 handles
 * perfectly well — so the two are split and only this half is gated.
 *
 * <p>{@code com.discordlogger.util.Compat} builds it by name when the event exists.
 * Everything it reports goes through {@link Explosion}'s helpers, so the two produce
 * identical embeds and there is one place to change the wording.
 */
public final class BlockExplosion implements Listener {

    private final JavaPlugin plugin;

    public BlockExplosion(JavaPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (!Explosion.enabled(plugin)) return;
        if (Explosion.filteredWorld(e.getBlock() == null ? null : e.getBlock().getLocation())) return;

        final Block b = e.getBlock();
        final Material mat = (b == null) ? null : b.getType();
        final String srcMat = (mat == null) ? "Unknown Block" : Explosion.toTitle(mat.name());
        final String source  = "Block: " + srcMat;
        final String thumb   = Explosion.getBlockThumb(mat);

        final Location loc = (b == null) ? null : b.getLocation();
        final World w = (b == null) ? null : b.getWorld();
        final String world = (w == null) ? "unknown" : w.getName();

        final int affected = (e.blockList() == null) ? 0 : e.blockList().size();
        // A block explosion has a Material rather than an EntityType, so "BED"
        // and "RESPAWN_ANCHOR" filter the same way "CREEPER" does.
        if (Filters.blocksExplosion(mat == null ? null : mat.name(), affected)) return;

        final String yield  = Explosion.fmtYield(e.getYield());

        List<Log.Field> fields = new ArrayList<>();
        fields.add(new Log.Field("Source:", source));
        fields.add(new Log.Field("World:", world, true));
        fields.add(new Log.Field("Location:", Explosion.fmtLoc(loc)));
        fields.add(new Log.Field("Blocks Affected:", String.valueOf(affected), true));
        fields.add(new Log.Field("Yield:", yield, true));
        fields.add(new Log.Field("Players Nearby:", Explosion.playersNearbyString(plugin, w, loc)));

        Log.eventFieldsWithThumb(
                "server_explosion",
                "Explosion",
                null,
                fields,
                thumb                // dynamic icon or null if unknown
        );
    }
}
