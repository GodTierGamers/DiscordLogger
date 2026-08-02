package com.discordlogger.listener.player;

import com.discordlogger.filter.Filters;
import com.discordlogger.lang.Lang;
import com.discordlogger.log.Log;
import com.discordlogger.util.Names;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class PlayerDeath implements Listener {
    private final Plugin plugin;

    public PlayerDeath(Plugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        if (!plugin.getConfig().getBoolean("log.player.death.enabled", true)) return;
        if (Filters.blocksPlayer(e.getEntity()) || Filters.blocksWorld(e.getEntity().getWorld().getName())) return;

        final Player victim = e.getEntity();
        final String vName = Names.display(victim, (JavaPlugin) plugin);

        final List<Log.Field> fields = new ArrayList<>();
        fields.add(new Log.Field(Lang.text("discord.death.cause-field"), describeCause(victim)));

        // Only present when asked for. Off by default: a death message with
        // coordinates tells everyone who can read the channel where the body — and
        // the inventory it dropped — is.
        if (plugin.getConfig().getBoolean("log.player.death.show_coords", false)) {
            fields.add(new Log.Field(Lang.text("discord.death.coords-field"), coordsOf(victim)));
        }

        Log.eventFieldsWithThumb(
                "Player Death",
                "Player Death",
                Lang.text("discord.death.description", "player", vName),
                null,                       // null -> use embeds.author from config
                fields,
                Log.playerAvatarUrl(victim.getUniqueId())
        );
    }

    /**
     * How the player died, as a standalone phrase for a field value.
     *
     * <p>Each branch used to send its own complete message, so the wording was
     * mid-sentence ("was slain by X"). As a field value it stands on its own, so it
     * reads "Slain by X".
     */
    private String describeCause(Player victim) {
        final Player killer = victim.getKiller();
        if (killer != null) {
            String kName = Names.display(killer, (JavaPlugin) plugin);
            String weapon = weaponName(killer.getInventory().getItemInMainHand());
            return Lang.text("discord.death.slain-by-player",
                    "killer", kName,
                    "weapon", weapon.isEmpty() ? "" : " [" + weapon + "]");
        }

        EntityDamageEvent last = victim.getLastDamageCause();
        if (last instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();

            if (damager instanceof Projectile proj) {
                Object shooter = proj.getShooter();
                if (shooter instanceof Player pShooter) {
                    return Lang.text("discord.death.shot-by",
                            "killer", Names.display(pShooter, (JavaPlugin) plugin));
                }
                if (shooter instanceof Entity eShooter) {
                    return Lang.text("discord.death.shot-by", "killer", mobName(eShooter));
                }
                return Lang.text("discord.death.shot");
            }
            return Lang.text("discord.death.slain-by-mob", "killer", mobName(damager));
        }

        final String text = last == null ? null : causeText(last.getCause());
        // Only reached when there is no damage cause at all, or Paper has added one
        // we do not know yet. causeTextIsExhaustive() in the tests fails on the latter.
        return text == null ? Lang.text("discord.death.unknown") : text;
    }

    /** Block coordinates and world, e.g. "128, 71, -344 in world". */
    private String coordsOf(Player victim) {
        final Location at = victim.getLocation();
        final String world = at.getWorld() == null ? "unknown" : at.getWorld().getName();
        return Lang.text("discord.death.coords-value",
                "x", at.getBlockX(), "y", at.getBlockY(), "z", at.getBlockZ(),
                "world", Log.mdEscape(world));
    }

    private String weaponName(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return "";
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return Log.mdEscape(item.getItemMeta().getDisplayName());
        }
        String mat = item.getType().name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(mat.charAt(0)) + mat.substring(1);
    }

    private String mobName(Entity e) {
        if (e == null) return "an unknown entity";
        String type = e.getType().name().toLowerCase().replace('_', ' ');
        return "a " + type;
    }

    /**
     * Phrasing for each damage cause, or null if this one is not handled.
     *
     * <p>Returning null rather than a generic string is what makes the gap
     * detectable: a test walks every {@code DamageCause} the API declares and fails
     * on any that returns null, so a cause added by a future Paper release is caught
     * in CI instead of surfacing as "Cause of Death: Died" on someone's server.
     */
    /**
     * Phrasing for a damage cause, or null if this one has no entry.
     *
     * <p>Reads {@code discord.death.causes.<cause>} from lang.yml, keyed by the enum
     * name lowercased with underscores as hyphens. Returning null rather than a
     * generic string is what keeps the gap detectable: a test walks every
     * {@code DamageCause} the API declares and fails on any without an entry, so a
     * cause added by a future Paper release is caught in CI instead of surfacing as
     * "Cause of Death: Died" on someone's server.
     */
    static String causeText(EntityDamageEvent.DamageCause cause) {
        if (cause == null) return null;
        final String key = "discord.death.causes." + langKey(cause);
        return Lang.has(key) ? Lang.text(key) : null;
    }

    /** {@code FIRE_TICK} to {@code fire-tick}, matching the keys in lang.yml. */
    static String langKey(EntityDamageEvent.DamageCause cause) {
        return cause.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

}
