package com.discordlogger.listener.player;

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

        final Player victim = e.getEntity();
        final String vName = Names.display(victim, (JavaPlugin) plugin);

        final List<Log.Field> fields = new ArrayList<>();
        fields.add(new Log.Field("Cause of Death", describeCause(victim)));

        // Only present when asked for. Off by default: a death message with
        // coordinates tells everyone who can read the channel where the body — and
        // the inventory it dropped — is.
        if (plugin.getConfig().getBoolean("log.player.death.show_coords", false)) {
            fields.add(new Log.Field("Coords", coordsOf(victim)));
        }

        Log.eventFieldsWithThumb(
                "Player Death",
                "Player Death",
                vName + " died",
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
            return "Slain by " + kName + (weapon.isEmpty() ? "" : " [" + weapon + "]");
        }

        EntityDamageEvent last = victim.getLastDamageCause();
        if (last instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();

            if (damager instanceof Projectile proj) {
                Object shooter = proj.getShooter();
                if (shooter instanceof Player pShooter) {
                    return "Shot by " + Names.display(pShooter, (JavaPlugin) plugin);
                }
                if (shooter instanceof Entity eShooter) {
                    return "Shot by " + mobName(eShooter);
                }
                return "Shot";
            }
            return "Slain by " + mobName(damager);
        }

        final String text = last == null ? null : causeText(last.getCause());
        // Only reached when there is no damage cause at all, or Paper has added one
        // we do not know yet. causeTextIsExhaustive() in the tests fails on the latter.
        return text == null ? "Died" : text;
    }

    /** Block coordinates and world, e.g. "128, 71, -344 in world". */
    private String coordsOf(Player victim) {
        final Location at = victim.getLocation();
        final String world = at.getWorld() == null ? "unknown" : at.getWorld().getName();
        return at.getBlockX() + ", " + at.getBlockY() + ", " + at.getBlockZ()
                + " in " + Log.mdEscape(world);
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
    static String causeText(EntityDamageEvent.DamageCause cause) {
        if (cause == null) return null;
        switch (cause) {
            case FALL: return "Fell from a high place";
            case LAVA: return "Tried to swim in lava";
            case FIRE:
            case FIRE_TICK: return "Burned to death";
            case DROWNING: return "Drowned";
            case SUFFOCATION: return "Suffocated in a wall";
            case VOID: return "Fell into the void";
            case CONTACT: return "Was pricked to death";
            case BLOCK_EXPLOSION:
            case ENTITY_EXPLOSION: return "Blew up";
            case MAGIC: return "Was killed by magic";
            case POISON: return "Was poisoned";
            case WITHER: return "Withered away";
            case STARVATION: return "Starved to death";
            case FREEZE: return "Froze to death";
            case LIGHTNING: return "Was struck by lightning";
            case HOT_FLOOR: return "Discovered the floor was lava";
            case CRAMMING: return "Was squished too much";
            case DRAGON_BREATH: return "Was roasted by dragon breath";
            case THORNS: return "Was killed by thorns";

            // Reachable via /kill, and the twelve others that previously fell through
            // to a bare "Died".
            case KILL: return "Killed by command";
            case SUICIDE: return "Killed by command";
            case WORLD_BORDER: return "Left the world border";
            case SONIC_BOOM: return "Hit by a warden's sonic boom";
            case CAMPFIRE: return "Burned on a campfire";
            case FALLING_BLOCK: return "Squashed by a falling block";
            case FLY_INTO_WALL: return "Flew into a wall";
            case DRYOUT: return "Dried out";
            case MELTING: return "Melted";
            // These normally resolve through the damager branch above; they only reach
            // here when the attacker is already gone by the time the death is read.
            case ENTITY_ATTACK: return "Slain";
            case ENTITY_SWEEP_ATTACK: return "Slain";
            case PROJECTILE: return "Shot";
            // Inflicted by a plugin, so there is nothing truthful to say beyond this.
            case CUSTOM: return "Died";

            default: return null;
        }
    }
}
