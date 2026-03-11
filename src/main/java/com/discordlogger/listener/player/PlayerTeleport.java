package com.discordlogger.listener.player;

import com.discordlogger.log.Log;
import com.discordlogger.util.Names;
import com.discordlogger.util.StringUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PlayerTeleport implements Listener {
    private final JavaPlugin plugin;

    public PlayerTeleport(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (!plugin.getConfig().getBoolean("log.player.teleport", false)) return;

        final Player p = e.getPlayer();
        final String playerName = Names.display(p, plugin);
        final String cause = prettyCause(e.getCause());

        final Location from = e.getFrom();
        final Location to = e.getTo();

        final boolean worldChange = worldsDifferent(from, to);
        final String fromStr = fmtLoc(from);
        final String toStr = fmtLoc(to);

        // Distance only when same world & both locs present
        String distStr = "N/A";
        if (!worldChange && from != null && to != null && from.getWorld() != null) {
            try {
                double d = from.distance(to);
                distStr = String.format(Locale.ROOT, "%.1f blocks", d);
            } catch (IllegalArgumentException ignored) {
                // different worlds or nulls
            }
        }

        // Player head thumbnail
        final UUID uuid = p.getUniqueId();
        final String thumb = Log.playerAvatarUrl(uuid);

        List<Log.Field> fields = new ArrayList<>();
        fields.add(new Log.Field("Player:", playerName));
        fields.add(new Log.Field("From:", fromStr));
        fields.add(new Log.Field("To:", toStr));
        fields.add(new Log.Field("Cause:", cause, true));
        fields.add(new Log.Field("World Change:", worldChange ? "Yes" : "No", true));
        fields.add(new Log.Field("Distance:", distStr, true));

        // Category key -> "player_teleport" (embeds.colors.player.teleport)
        Log.eventFieldsWithThumb(
                "player_teleport",
                "Player Teleport",
                null,   // author -> default embeds.author
                fields,
                thumb
        );
    }

    private static boolean worldsDifferent(Location a, Location b) {
        World wa = (a == null) ? null : a.getWorld();
        World wb = (b == null) ? null : b.getWorld();
        if (wa == null || wb == null) return true;
        return !wa.getName().equals(wb.getName());
    }

    private static String fmtLoc(Location loc) {
        if (loc == null) return "Unknown";
        World w = loc.getWorld();
        String wn = (w == null) ? "unknown" : w.getName();
        return wn + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
    }

    private static String prettyCause(PlayerTeleportEvent.TeleportCause c) {
        if (c == null) return "Unknown";
        switch (c) {
            case COMMAND:
                return "Command";
            case PLUGIN:
                return "Plugin";
            case ENDER_PEARL:
                return "Ender Pearl";
            case NETHER_PORTAL:
                return "Nether Portal";
            case END_PORTAL:
                return "End Portal";
            case END_GATEWAY:
                return "End Gateway";
            case SPECTATE:
                return "Spectate";
            case UNKNOWN:
                return "Unknown";
            default:
                return StringUtil.toTitle(c.name());
        }
    }

}