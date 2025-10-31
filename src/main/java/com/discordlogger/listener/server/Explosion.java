package com.discordlogger.listener.server;

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
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class Explosion implements Listener {
    // ---------- YOUR CDN ----------
    private static final String ICON_BASE =
            "https://files.godtiergamers.xyz/discordlogger/icons/explosions/";

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

    private boolean enabled() {
        return plugin.getConfig().getBoolean("log.server.explosion", false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (!enabled()) return;

        final EntityType type = (e.getEntity() == null) ? null : e.getEntity().getType();
        final String source = (type == null) ? "Unknown" : toTitle(type.name());
        final String thumb  = getEntityThumb(type);

        final Location loc = e.getLocation();
        final World w = (loc == null) ? null : loc.getWorld();
        final String world = (w == null) ? "unknown" : w.getName();

        final int affected = (e.blockList() == null) ? 0 : e.blockList().size();
        final String yield  = fmtYield(e.getYield());

        List<Log.Field> fields = new ArrayList<>();
        fields.add(new Log.Field("Source:", source));
        fields.add(new Log.Field("World:", world, true));
        fields.add(new Log.Field("Location:", fmtLoc(loc)));
        fields.add(new Log.Field("Blocks Affected:", String.valueOf(affected), true));
        fields.add(new Log.Field("Yield:", yield, true));
        fields.add(new Log.Field("Players Nearby:", playersNearbyString(w, loc)));

        Log.eventFieldsWithThumb(
                "server_explosion",  // resolves to embeds.colors.server.explosion
                "Explosion",
                null,                // author -> default embeds.author
                fields,
                thumb                // dynamic icon or null if unknown
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (!enabled()) return;

        final Block b = e.getBlock();
        final Material mat = (b == null) ? null : b.getType();
        final String srcMat = (mat == null) ? "Unknown Block" : toTitle(mat.name());
        final String source  = "Block: " + srcMat;
        final String thumb   = getBlockThumb(mat);

        final Location loc = (b == null) ? null : b.getLocation();
        final World w = (b == null) ? null : b.getWorld();
        final String world = (w == null) ? "unknown" : w.getName();

        final int affected = (e.blockList() == null) ? 0 : e.blockList().size();
        final String yield  = fmtYield(e.getYield());

        List<Log.Field> fields = new ArrayList<>();
        fields.add(new Log.Field("Source:", source));
        fields.add(new Log.Field("World:", world, true));
        fields.add(new Log.Field("Location:", fmtLoc(loc)));
        fields.add(new Log.Field("Blocks Affected:", String.valueOf(affected), true));
        fields.add(new Log.Field("Yield:", yield, true));
        fields.add(new Log.Field("Players Nearby:", playersNearbyString(w, loc)));

        Log.eventFieldsWithThumb(
                "server_explosion",
                "Explosion",
                null,
                fields,
                thumb                // dynamic icon or null if unknown
        );
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

    private static String getBlockThumb(Material mat) {
        if (mat == null) return null;
        final String m = mat.name();
        if (m.equals("RESPAWN_ANCHOR")) return ICON_RESPAWN_ANCHOR;
        if (m.equals("TNT"))            return ICON_TNT;
        if (m.endsWith("_BED") || m.equals("BED")) return ICON_BED; // legacy + colored beds
        return null; // unknown -> no thumbnail
    }

    // ---------- helpers ----------

    private String playersNearbyString(World world, Location center) {
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

    private static String fmtYield(float yield) {
        return String.format(Locale.ROOT, "%.2f", yield);
    }

    private static String fmtLoc(Location loc) {
        if (loc == null) return "Unknown";
        World w = loc.getWorld();
        String wn = (w == null) ? "unknown" : w.getName();
        return wn + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
    }

    private static String toTitle(String s) {
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
