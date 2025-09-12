package com.discordlogger.listener;

import com.discordlogger.log.Log;
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

public final class PlayerDeath implements Listener {
    private final Plugin plugin;

    public PlayerDeath(Plugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        if (!plugin.getConfig().getBoolean("log.player.death", true)) return;

        final Player victim = e.getEntity();
        final String vName = Log.mdEscape(victim.getName());

        // Prefer killer context
        final Player killer = victim.getKiller();
        if (killer != null) {
            String kName = Log.mdEscape(killer.getName());
            String weapon = weaponName(killer.getInventory().getItemInMainHand());
            String suffix = weapon.isEmpty() ? "" : " [" + weapon + "]";
            Log.event("Player Death", vName + " was slain by " + kName + suffix);
            return;
        }

        // Otherwise use last damage cause + attacker (mob/projectile/explosion/etc.)
        EntityDamageEvent last = victim.getLastDamageCause();
        if (last instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();

            // Projectiles (arrow, trident, etc.) – include shooter when possible
            if (damager instanceof Projectile proj) {
                Object shooter = proj.getShooter();
                if (shooter instanceof Player pShooter) {
                    String kName = Log.mdEscape(pShooter.getName());
                    Log.event("Player Death", vName + " was shot by " + kName);
                    return;
                } else if (shooter instanceof Entity eShooter) {
                    Log.event("Player Death", vName + " was shot by " + mobName(eShooter));
                    return;
                } else {
                    Log.event("Player Death", vName + " was shot");
                    return;
                }
            }

            // Generic mob/entity
            Log.event("Player Death", vName + " was slain by " + mobName(damager));
            return;
        }

        // Fallback to specific environmental causes
        String cause = causeText(last == null ? null : last.getCause());
        Log.event("Player Death", vName + " " + cause);
    }

    private String weaponName(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return "";
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return Log.mdEscape(item.getItemMeta().getDisplayName());
        }
        // Friendly material name, e.g. "Iron Sword"
        String mat = item.getType().name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(mat.charAt(0)) + mat.substring(1);
    }

    private String mobName(Entity e) {
        if (e == null) return "an unknown entity";
        String type = e.getType().name().toLowerCase().replace('_', ' ');
        return "a " + type; // e.g., "a zombie", "a creeper"
    }

    private String causeText(EntityDamageEvent.DamageCause cause) {
        if (cause == null) return "died";
        switch (cause) {
            case FALL: return "fell from a high place";
            case LAVA: return "tried to swim in lava";
            case FIRE:
            case FIRE_TICK: return "burned to death";
            case DROWNING: return "drowned";
            case SUFFOCATION: return "suffocated in a wall";
            case VOID: return "fell into the void";
            case CONTACT: return "was pricked to death";
            case BLOCK_EXPLOSION:
            case ENTITY_EXPLOSION: return "blew up";
            case MAGIC: return "was killed by magic";
            case POISON: return "was poisoned";
            case WITHER: return "withered away";
            case STARVATION: return "starved to death";
            case FREEZE: return "froze to death";
            case LIGHTNING: return "was struck by lightning";
            case HOT_FLOOR: return "discovered the floor was lava";
            case CRAMMING: return "was squished too much";
            case DRAGON_BREATH: return "was roasted by dragon breath";
            case THORNS: return "was killed by thorns";
            default: return "died";
        }
    }
}
