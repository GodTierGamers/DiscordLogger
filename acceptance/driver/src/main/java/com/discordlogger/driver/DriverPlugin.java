package com.discordlogger.driver;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Locale;

/**
 * Fires the events DiscordLogger listens for, on request.
 *
 * <p>Driven from the server console by the acceptance suite: {@code dldriver join},
 * {@code dldriver chat hello}, and so on. Each one builds a real Bukkit event around
 * {@link Fake#player} and puts it through the ordinary event bus, so what is being
 * tested is DiscordLogger's listener registration and handling, not a method called
 * directly.
 *
 * <p>Nothing here reaches into DiscordLogger. The two plugins never touch: this one
 * makes something happen, and the suite reads what arrived at the fake Discord. That
 * separation is why a driver bug cannot fake a passing result.
 */
public final class DriverPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Acceptance driver ready.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("usage: /dldriver <join|quit|chat|teleport|gamemode> [args]");
            return true;
        }
        final World world = Fake.world();
        if (world == null) {
            sender.sendMessage("DRIVER-ERROR no world loaded");
            return true;
        }
        final Player player = Fake.player(world);
        if (player == null) {
            // The suite reads this and skips the player-driven cases for this version
            // rather than reporting them as plugin faults, which they are not.
            sender.sendMessage("DRIVER-UNSUPPORTED no fake player on this server build");
            getLogger().warning("DRIVER-UNSUPPORTED no fake player on this server build");
            return true;
        }
        final String what = args[0].toLowerCase(Locale.ROOT);
        final String rest = args.length > 1
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "";

        try {
            switch (what) {
                case "join":
                    fire("org.bukkit.event.player.PlayerJoinEvent",
                            new Object[]{player, Fake.NAME + " joined the server"});
                    break;
                case "quit":
                    fire("org.bukkit.event.player.PlayerQuitEvent",
                            new Object[]{player, Fake.NAME + " left the server"});
                    break;
                case "chat":
                    // Async by contract, and DiscordLogger listens to it as such. Firing
                    // it off the main thread is what a real chat packet does.
                    final String message = rest.isEmpty() ? "hello from the driver" : rest;
                    Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
                        @Override public void run() {
                            try {
                                fire("org.bukkit.event.player.AsyncPlayerChatEvent",
                                        new Object[]{Boolean.TRUE, player, message,
                                                new java.util.HashSet<Player>()});
                            } catch (Exception e) {
                                getLogger().warning("DRIVER-ERROR chat: " + e);
                            }
                        }
                    });
                    break;
                case "gamemode":
                    fire("org.bukkit.event.player.PlayerGameModeChangeEvent",
                            new Object[]{player, org.bukkit.GameMode.CREATIVE});
                    break;
                case "death":
                    // Every cause the server actually has, so the sweep covers what this
                    // version can produce rather than what a newer one could.
                    fireDeaths(sender, player, rest);
                    break;
                case "explosion":
                    fireExplosions(sender, player, world, rest);
                    break;
                case "advancement":
                    fireAdvancements(sender, player, rest);
                    break;
                case "teleport":
                    fire("org.bukkit.event.player.PlayerTeleportEvent",
                            new Object[]{player, player.getLocation(),
                                    new org.bukkit.Location(world, 100, 70, 100)});
                    break;
                default:
                    sender.sendMessage("DRIVER-ERROR unknown event " + what);
                    return true;
            }
            // The suite waits for this line before asserting, so it never races the
            // event it asked for.
            sender.sendMessage("DRIVER-OK " + what);
            getLogger().info("DRIVER-OK " + what);
        } catch (Throwable t) {
            sender.sendMessage("DRIVER-ERROR " + what + ": " + t);
            getLogger().warning("DRIVER-ERROR " + what + ": " + t);
        }
        return true;
    }

    /**
     * One death per damage cause this server declares.
     *
     * <p>Enumerated from the enum rather than listed, so a version with fewer causes is
     * swept for exactly what it has and a cause added by a future release is covered
     * without editing this. Naming them here would make the driver a second place to
     * keep in step with Minecraft.
     */
    private void fireDeaths(CommandSender sender, Player player, String only) throws Exception {
        int fired = 0;
        for (org.bukkit.event.entity.EntityDamageEvent.DamageCause cause
                : org.bukkit.event.entity.EntityDamageEvent.DamageCause.values()) {
            if (!only.isEmpty() && !only.equalsIgnoreCase(cause.name())) continue;
            // The listener reads the cause off the victim's last damage, which is how a
            // real death carries it, so it is set the same way here.
            final org.bukkit.event.entity.EntityDamageEvent damage =
                    new org.bukkit.event.entity.EntityDamageEvent(player, cause, 100.0);
            player.setLastDamageCause(damage);
            fire("org.bukkit.event.entity.PlayerDeathEvent",
                    new Object[]{player, new java.util.ArrayList<org.bukkit.inventory.ItemStack>(),
                            0, Fake.NAME + " died"});
            fired++;
            pause();
        }
        sender.sendMessage("DRIVER-COUNT death " + fired);
    }

    /**
     * One explosion per source that can cause one here.
     *
     * <p>Entity explosions are fired for every entity type the version knows; block
     * explosions only where BlockExplodeEvent exists, which is 1.8.3 and up.
     */
    private void fireExplosions(CommandSender sender, Player player, World world, String only)
            throws Exception {
        int fired = 0;
        for (org.bukkit.entity.EntityType type : org.bukkit.entity.EntityType.values()) {
            if (!EXPLOSIVE.contains(type.name())) continue;
            if (!only.isEmpty() && !only.equalsIgnoreCase(type.name())) continue;
            final org.bukkit.entity.Entity source = FakeEntity.of(type, world);
            if (source == null) continue;
            fire("org.bukkit.event.entity.EntityExplodeEvent",
                    new Object[]{source, player.getLocation(),
                            new java.util.ArrayList<org.bukkit.block.Block>(), 0.0f});
            fired++;
            pause();
        }
        sender.sendMessage("DRIVER-COUNT explosion " + fired);
    }

    /** Entity types that produce an explosion, by name so the list spans every version. */
    private static final java.util.Set<String> EXPLOSIVE = new java.util.HashSet<String>(
            Arrays.asList("CREEPER", "PRIMED_TNT", "TNT", "MINECART_TNT", "TNT_MINECART",
                    "FIREBALL", "SMALL_FIREBALL", "DRAGON_FIREBALL", "WITHER_SKULL",
                    "ENDER_CRYSTAL", "END_CRYSTAL", "ENDER_DRAGON", "WITHER", "GHAST"));

    /**
     * One advancement per advancement the server declares, or one per achievement on
     * versions old enough to have those instead.
     */
    private void fireAdvancements(CommandSender sender, Player player, String only)
            throws Exception {
        int fired = 0;
        try {
            final java.util.Iterator<?> it = (java.util.Iterator<?>)
                    Bukkit.class.getMethod("advancementIterator").invoke(null);
            while (it.hasNext()) {
                final Object advancement = it.next();
                final Object key = advancement.getClass().getMethod("getKey").invoke(advancement);
                if (!only.isEmpty() && !String.valueOf(key).contains(only)) continue;
                fire("org.bukkit.event.player.PlayerAdvancementDoneEvent",
                        new Object[]{player, advancement});
                fired++;
                pause();
            }
        } catch (NoSuchMethodException tooOld) {
            // Pre-1.12: achievements, a different event and a fixed enum.
            final Class<?> achievement = Class.forName("org.bukkit.Achievement");
            for (Object a : (Object[]) achievement.getMethod("values").invoke(null)) {
                if (!only.isEmpty() && !String.valueOf(a).equalsIgnoreCase(only)) continue;
                fire("org.bukkit.event.player.PlayerAchievementAwardedEvent",
                        new Object[]{player, a});
                fired++;
                pause();
            }
        }
        sender.sendMessage("DRIVER-COUNT advancement " + fired);
    }

    /**
     * A breath between events.
     *
     * <p>A sweep fires well over a hundred in a row. Without this they queue faster than
     * the plugin drains them and the run measures its own overflow handling rather than
     * what each event produced.
     */
    private static void pause() {
        try {
            Thread.sleep(120);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Builds an event by name and puts it on the bus.
     *
     * <p>Reflective because Bukkit's event constructors are not stable across the range
     * under test: parameters have been added and types changed between 1.8 and 26.x, so
     * a driver compiled against the 1.8 API cannot simply call {@code new} and expect it
     * to link on a modern server. Choosing the constructor at runtime means one JAR
     * works everywhere, and an event whose shape has moved fails with a clear message
     * naming it rather than a NoSuchMethodError from the class loader.
     */
    private void fire(String eventClass, Object[] args) throws Exception {
        final Class<?> type = Class.forName(eventClass);
        Constructor<?> best = null;
        for (Constructor<?> c : type.getConstructors()) {
            if (c.getParameterCount() != args.length) continue;
            final Class<?>[] p = c.getParameterTypes();
            boolean ok = true;
            for (int i = 0; i < p.length && ok; i++) {
                ok = args[i] == null || wrap(p[i]).isInstance(args[i]);
            }
            if (ok) { best = c; break; }
        }
        if (best == null) {
            throw new NoSuchMethodException(eventClass + " has no constructor matching "
                    + Arrays.toString(args) + "; its signature has changed on this version");
        }
        Bukkit.getPluginManager().callEvent((Event) best.newInstance(args));
    }

    private static Class<?> wrap(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == boolean.class) return Boolean.class;
        if (c == int.class)     return Integer.class;
        if (c == long.class)    return Long.class;
        if (c == double.class)  return Double.class;
        if (c == float.class)   return Float.class;
        return c;
    }
}
