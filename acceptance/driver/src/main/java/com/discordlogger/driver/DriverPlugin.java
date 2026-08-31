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
