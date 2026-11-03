package com.discordlogger.listener.server;

import com.discordlogger.explosion.Blast;
import com.discordlogger.explosion.ExplosionLog;
import com.discordlogger.explosion.ExplosionRecord;
import com.discordlogger.filter.Filters;
import com.discordlogger.log.Log;
import com.discordlogger.util.Names;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class Explosion implements Listener {
    // ---------- YOUR CDN ----------
    private static final String ICON_BASE =
            "https://discordlogger.godtiergamers.xyz/assets/icons/explosions/";

    // File names you mirrored (transparent PNGs)
    private static final String ICON_TNT             = ICON_BASE + "tnt.png";
    private static final String ICON_TNT_MINECART    = ICON_BASE + "tnt_minecart.png";
    private static final String ICON_CREEPER         = ICON_BASE + "creeper.png";
    private static final String ICON_END_CRYSTAL     = ICON_BASE + "end_crystal.png";
    private static final String ICON_RESPAWN_ANCHOR  = ICON_BASE + "respawn_anchor.png";
    private static final String ICON_BED             = ICON_BASE + "bed.png";
    private static final String ICON_WITHER_SKULL    = ICON_BASE + "wither_skull.png";
    private static final String ICON_FIREBALL        = ICON_BASE + "fireball.png";
    private static final String ICON_DRAGON_FIREBALL = ICON_BASE + "dragon_fireball.png";

    // Hard-coded nearby player settings (tweak later via config if you want)
    private static final int NEARBY_RADIUS_BLOCKS = 20;
    private static final int NEARBY_LIST_LIMIT    = 8;

    private final JavaPlugin plugin;
    public Explosion(JavaPlugin plugin) { this.plugin = plugin; }

    boolean enabled() {
        return enabled(plugin);
    }

    /** Package-private so the gated block-explosion listener shares one answer. */
    static boolean enabled(JavaPlugin plugin) {
        return plugin.getConfig().getBoolean("log.server.explosion.enabled", true);
    }

    /**
     * Explosions have no player, but they do have a world, so the world filter still
     * applies — otherwise "don't log anything in creative_plot" would silently leak
     * every TNT blast from it.
     */
    static boolean filteredWorld(org.bukkit.Location loc) {
        final org.bukkit.World w = (loc == null) ? null : loc.getWorld();
        return w != null && Filters.blocksWorld(w.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (!enabled()) return;
        if (filteredWorld(e.getLocation())) return;

        final EntityType type = (e.getEntity() == null) ? null : e.getEntity().getType();
        final String source = (type == null) ? "Unknown" : toTitle(type.name());
        final String thumb  = getEntityThumb(type);

        final Location loc = e.getLocation();
        final World w = (loc == null) ? null : loc.getWorld();
        final String world = (w == null) ? "unknown" : w.getName();

        final int affected = (e.blockList() == null) ? 0 : e.blockList().size();
        // Filter on the entity that exploded: CREEPER, PRIMED_TNT, END_CRYSTAL…
        if (Filters.blocksExplosion(type == null ? null : type.name(), affected)) return;

        final String yield  = fmtYield(e.getYield());
        final String nearby = playersNearbyString(w, loc);

        record(plugin, loc, world, type == null ? null : type.name(), source,
                e.blockList(), affected, e.getYield(), nearby);

        List<Log.Field> fields = new ArrayList<>();
        fields.add(// Source and World pair up on one row. World used to be the only inline
                // field between two full-width ones, so Discord gave it a row to itself
                // at a third of the width -- a narrow column holding one short word.
                new Log.Field("Source:", source, true));
        fields.add(new Log.Field("World:", world));
        fields.add(new Log.Field("Location:", fmtLoc(loc)));
        fields.add(new Log.Field("Blocks Affected:", String.valueOf(affected), true));
        fields.add(new Log.Field("Yield:", yield, true));
        fields.add(new Log.Field("Players Nearby:", nearby));
        addLookupField(plugin, fields, loc, world);

        Log.eventFieldsWithThumb(
                "server_explosion",  // colour comes from log.server.explosion.color
                "Explosion",
                null,                // author -> default embeds.author
                fields,
                thumb                // dynamic icon or null if unknown
        );
    }


    // ---------- the explosion log ----------

    /** Whether explosions are being written to {@code explosions.csv} at all. */
    static boolean historyEnabled(JavaPlugin plugin) {
        return plugin.getConfig().getBoolean("log.server.explosion.history.enabled", true);
    }

    /**
     * Writes one row to {@code explosions.csv} and returns the radius it stored.
     *
     * <p>Shared with the gated block-explosion listener so the two record the same
     * shape, for the same reason they share the embed helpers: two places building the
     * same row is two places for it to drift.
     */
    static int record(JavaPlugin plugin, Location loc, String world, String sourceName,
                      String sourceLabel, List<Block> blocks, int affected,
                      float yield, String nearby) {
        if (loc == null) return 0;
        if (!historyEnabled(plugin)) return 0;
        final int radius = Blast.radiusFor(measuredRadius(loc, blocks), sourceName);
        ExplosionLog.append(new ExplosionRecord(
                System.currentTimeMillis(), world,
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                radius, sourceLabel, affected, yield, nearby));
        return radius;
    }

    /**
     * How far the blast actually reached: the centre to the furthest block it broke.
     *
     * <p>Measured rather than assumed, so a charged creeper, a blast stopped dead by
     * obsidian and one that went off in open air each record what they really did.
     * Zero when nothing broke, which is what {@link Blast#radiusFor} has a floor for.
     */
    static double measuredRadius(Location centre, List<Block> blocks) {
        if (centre == null || blocks == null || blocks.isEmpty()) return 0.0;
        final int cx = centre.getBlockX();
        final int cy = centre.getBlockY();
        final int cz = centre.getBlockZ();
        long worst = 0L;
        for (Block b : blocks) {
            if (b == null) continue;
            final long dx = b.getX() - cx;
            final long dy = b.getY() - cy;
            final long dz = b.getZ() - cz;
            final long d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > worst) worst = d2;
        }
        return Math.sqrt((double) worst);
    }

    /**
     * Adds the field that tells a reader how to ask about this spot in game.
     *
     * <p>Controlled by {@code log.server.explosion.history.show_tip}, and it names the
     * coordinates the embed is already showing one field above, so it leaks nothing
     * the message did not already carry.
     */
    static void addLookupField(JavaPlugin plugin, List<Log.Field> fields,
                               Location loc, String world) {
        if (loc == null) return;
        // Both gates, in this order: a tip pointing at a log nobody is writing is
        // worse than no tip, so turning the history off takes the field with it.
        if (!historyEnabled(plugin)) return;
        if (!plugin.getConfig().getBoolean("log.server.explosion.history.show_tip", true)) return;
        // The full command name, not an alias. /dlogger and /dlog both work, but this
        // field exists to be copied into a chat box by somebody who has never run the
        // command before, and a tip that only works if you happen to have the aliases
        // is worse than no tip.
        fields.add(new Log.Field("Look It Up:",
                "`/discordlogger lookup " + loc.getBlockX() + " " + loc.getBlockY() + " "
                        + loc.getBlockZ() + " world:" + world + "`"));
    }

    // ---------- icon selection (version-safe; name matching) ----------

    private static String getEntityThumb(EntityType type) {
        if (type == null) return null;
        final String name = type.name(); // keeps compatibility across versions
        if (name.contains("CREEPER"))            return ICON_CREEPER;
        if (name.contains("TNT_MINECART"))       return ICON_TNT_MINECART;
        if (name.contains("PRIMED_TNT") || name.equals("TNT")) return ICON_TNT;
        if (name.contains("ENDER_CRYSTAL") || name.contains("END_CRYSTAL")) return ICON_END_CRYSTAL;
        if (name.contains("WITHER_SKULL"))       return ICON_WITHER_SKULL;
        if (name.contains("DRAGON_FIREBALL"))    return ICON_DRAGON_FIREBALL;
        if (name.contains("FIREBALL"))           return ICON_FIREBALL; // covers FIREBALL & SMALL_FIREBALL
        return null; // unknown -> no thumbnail
    }

    static String getBlockThumb(Material mat) {
        if (mat == null) return null;
        final String m = mat.name();
        if (m.equals("RESPAWN_ANCHOR")) return ICON_RESPAWN_ANCHOR;
        if (m.equals("TNT"))            return ICON_TNT;
        if (m.endsWith("_BED") || m.equals("BED")) return ICON_BED; // legacy + colored beds
        return null; // unknown -> no thumbnail
    }

    // ---------- helpers ----------

    String playersNearbyString(World world, Location center) {
        return playersNearbyString(plugin, world, center);
    }

    /** Package-private so the gated block-explosion listener shares one answer. */
    static String playersNearbyString(JavaPlugin plugin, World world, Location center) {
        if (world == null || center == null) return "None";

        final double r2 = (double) NEARBY_RADIUS_BLOCKS * (double) NEARBY_RADIUS_BLOCKS;

        // Collect players in the same world within radius
        List<Player> near = new ArrayList<>();
        for (Player p : world.getPlayers()) {
            Location pl = p.getLocation();
            if (pl == null || pl.getWorld() == null) continue;
            if (!world.equals(pl.getWorld())) continue;
            if (pl.distanceSquared(center) <= r2) {
                near.add(p);
            }
        }

        if (near.isEmpty()) return "None";

        // Sort by distance ascending
        near.sort(Comparator.comparingDouble(p -> {
            Location pl = p.getLocation();
            return (pl == null) ? Double.MAX_VALUE : pl.distanceSquared(center);
        }));

        // Build "Name (12.3m)" strings up to hard-coded limit
        List<String> parts = new ArrayList<>(Math.min(NEARBY_LIST_LIMIT, near.size()));
        int count = 0;
        for (Player p : near) {
            if (count >= NEARBY_LIST_LIMIT) break;
            double d = 0.0;
            try {
                d = p.getLocation().distance(center);
            } catch (IllegalArgumentException ignored) {}
            String label = Names.display(p, plugin) + " (" + String.format(Locale.ROOT, "%.1f", d) + "m)";
            parts.add(label);
            count++;
        }

        if (near.size() > NEARBY_LIST_LIMIT) {
            parts.add("…and " + (near.size() - NEARBY_LIST_LIMIT) + " more");
        }

        return String.join(", ", parts);
    }

    static String fmtYield(float yield) {
        return String.format(Locale.ROOT, "%.2f", yield);
    }

    static String fmtLoc(Location loc) {
        if (loc == null) return "Unknown";
        World w = loc.getWorld();
        String wn = (w == null) ? "unknown" : w.getName();
        return wn + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
    }

    static String toTitle(String s) {
        String t = s.toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] parts = t.split("\\s+");
        StringBuilder out = new StringBuilder(t.length());
        for (int i = 0; i < parts.length; i++) {
            String w = parts[i];
            if (!w.isEmpty()) {
                out.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) out.append(w.substring(1));
                if (i + 1 < parts.length) out.append(' ');
            }
        }
        return out.toString();
    }
}