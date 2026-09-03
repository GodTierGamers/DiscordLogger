package com.discordlogger.driver;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
        // Needed to build the metadata a vanish plugin attaches, which is the only way
        // to exercise filters.respect_vanish without installing a real vanish plugin.
        Fake.owner = this;
        getLogger().info("Acceptance driver ready.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("usage: /dldriver <join|quit|chat|command|teleport|gamemode|death|explosion|advancement|fake> [args]");
            return true;
        }

        // Reports what the server thinks about a name, so a moderation case that logged
        // nothing can say whether the plugin was wrong or the server never agreed the
        // action happened. Without it the answer is guesswork across a 90 minute run.
        if (args[0].equalsIgnoreCase("probe")) {
            final String who = args.length > 1 ? args[1] : Fake.NAME;
            final StringBuilder ops = new StringBuilder();
            for (org.bukkit.OfflinePlayer o : Bukkit.getOperators()) {
                ops.append('[').append(o.getName()).append('/')
                   .append(o.getUniqueId()).append(']');
            }
            final StringBuilder white = new StringBuilder();
            for (org.bukkit.OfflinePlayer o : Bukkit.getWhitelistedPlayers()) {
                white.append('[').append(o.getName()).append(']');
            }
            @SuppressWarnings("deprecation")
            final org.bukkit.OfflinePlayer direct = Bukkit.getOfflinePlayer(who);
            final String line = "DRIVER-PROBE " + who
                    + " operators=" + (ops.length() == 0 ? "none" : ops)
                    + " whitelisted=" + (white.length() == 0 ? "none" : white)
                    + " getOfflinePlayer.isOp=" + (direct != null && direct.isOp())
                    + " getOfflinePlayer.uuid=" + (direct == null ? "?" : direct.getUniqueId());
            sender.sendMessage(line);
            getLogger().info(line);
            return true;
        }

        // Handled before anything else, because it changes what the fake player answers
        // and must work whether or not one can be built on this server.
        if (args[0].equalsIgnoreCase("fake")) {
            configureFake(sender, Arrays.copyOfRange(args, 1, args.length));
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
                    fireDeaths(sender, player, world, rest);
                    break;
                case "explosion":
                    fireExplosions(sender, player, world, rest);
                    break;
                case "advancement":
                    fireAdvancements(sender, player, rest);
                    break;
                case "command":
                    // A command run by a player, which is a different event and a
                    // different lang line from one run on the console.
                    fire("org.bukkit.event.player.PlayerCommandPreprocessEvent",
                            new Object[]{player, rest.isEmpty() ? "/acceptance" : rest});
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
            // Unwrapped, because a reflective call reports every failure as
            // InvocationTargetException and that name says nothing about what actually
            // went wrong. The sweep quotes this line verbatim in its verdict.
            Throwable cause = t;
            while (cause instanceof java.lang.reflect.InvocationTargetException
                    && cause.getCause() != null) {
                cause = cause.getCause();
            }
            final String detail = (cause == t) ? String.valueOf(t)
                    : t.getClass().getSimpleName() + " caused by " + cause;
            sender.sendMessage("DRIVER-ERROR " + what + ": " + detail);
            getLogger().warning("DRIVER-ERROR " + what + ": " + detail);
        }
        return true;
    }

    /**
     * Changes what the fake player answers, for the filters that ask it something.
     *
     * <p>{@code fake nickname Nick}, {@code fake permission some.node},
     * {@code fake vanished true}, {@code fake reset}. Kept here rather than in the
     * suite because the fake lives inside the server, and a setting that only the
     * server can see has to be set from the server.
     */
    private void configureFake(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("reset")) {
            Fake.reset();
            sender.sendMessage("DRIVER-OK fake reset");
            getLogger().info("DRIVER-OK fake reset");
            return;
        }
        final String what = args[0].toLowerCase(Locale.ROOT);
        final String value = args.length > 1
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "";
        switch (what) {
            case "nickname":
                Fake.nickname = value.isEmpty() ? null : value;
                break;
            case "permission":
                Fake.permission = value.isEmpty() ? null : value;
                break;
            case "vanished":
                Fake.vanished = Boolean.parseBoolean(value);
                break;
            case "killer":
                Fake.killer = value.isEmpty() ? null : value;
                break;
            case "bedrock":
                Fake.bedrock = Boolean.parseBoolean(value);
                break;
            default:
                sender.sendMessage("DRIVER-ERROR unknown fake setting " + what);
                return;
        }
        sender.sendMessage("DRIVER-OK fake " + what + " " + value);
        getLogger().info("DRIVER-OK fake " + what + " " + value);
    }

    /**
     * One death per damage cause this server declares.
     *
     * <p>Enumerated from the enum rather than listed, so a version with fewer causes is
     * swept for exactly what it has and a cause added by a future release is covered
     * without editing this. Naming them here would make the driver a second place to
     * keep in step with Minecraft.
     */
    private void fireDeaths(CommandSender sender, Player player, World world, String args)
            throws Exception {
        // "death VOID" is a cause; "death ENTITY_ATTACK by-mob" adds who did it.
        final String[] parts = args.trim().isEmpty() ? new String[0] : args.trim().split("\\s+");
        final String only = parts.length > 0 ? parts[0] : "";
        final String by = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "";

        int fired = 0;
        for (org.bukkit.event.entity.EntityDamageEvent.DamageCause cause
                : org.bukkit.event.entity.EntityDamageEvent.DamageCause.values()) {
            if (!only.isEmpty() && !only.equalsIgnoreCase(cause.name())) continue;
            // The listener reads the cause off the victim's last damage, which is how a
            // real death carries it, so it is set the same way here.
            player.setLastDamageCause(damageFor(player, world, cause, by));
            fireBestEffort("org.bukkit.event.entity.PlayerDeathEvent",
                    player, Fake.NAME + " died");
            fired++;
            pause();
        }
        sender.sendMessage("DRIVER-COUNT death " + fired);
        getLogger().info("DRIVER-COUNT death " + fired);
    }

    /**
     * The damage a death carries, in whichever of its shapes the case asked for.
     *
     * <p>DiscordLogger describes a death from what damaged the player, and the four
     * wordings it can choose between are reached through four different damage events
     * rather than four different causes. A plain EntityDamageEvent can only ever
     * produce the cause text.
     *
     * <p>"none" leaves the victim with no damage at all, which is the only way to reach
     * the "Died" fallback deliberately rather than by accident.
     */
    private org.bukkit.event.entity.EntityDamageEvent damageFor(
            Player player, World world,
            org.bukkit.event.entity.EntityDamageEvent.DamageCause cause, String by) {
        switch (by) {
            case "none":
                return null;
            case "by-mob":
                return new org.bukkit.event.entity.EntityDamageByEntityEvent(
                        FakeEntity.of(org.bukkit.entity.EntityType.valueOf("ZOMBIE"), world),
                        player, cause, 100.0);
            case "shot-by-player":
                return new org.bukkit.event.entity.EntityDamageByEntityEvent(
                        FakeEntity.projectile(world, Fake.player(world, "Archer",
                                java.util.UUID.nameUUIDFromBytes("archer".getBytes()))),
                        player, cause, 100.0);
            case "shot-by-mob":
                return new org.bukkit.event.entity.EntityDamageByEntityEvent(
                        FakeEntity.projectile(world, FakeEntity.of(
                                org.bukkit.entity.EntityType.valueOf("SKELETON"), world)),
                        player, cause, 100.0);
            case "shot":
                return new org.bukkit.event.entity.EntityDamageByEntityEvent(
                        FakeEntity.projectile(world, null), player, cause, 100.0);
            default:
                return new org.bukkit.event.entity.EntityDamageEvent(player, cause, 100.0);
        }
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
            fireAdapting("org.bukkit.event.entity.EntityExplodeEvent",
                    source, player.getLocation(),
                    new java.util.ArrayList<org.bukkit.block.Block>(), 0.0f);
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
    /**
     * At most this many per command, whatever the filter matches.
     *
     * <p>"recipes/" matches roughly a thousand advancements on a modern server. Firing
     * them all took the server past its heap and killed it mid-sweep, which surfaced as
     * seven unrelated cases failing with a closed stream -- the console had gone, so
     * everything after it looked like the plugin had stopped responding.
     */
    private static final int MAX_ADVANCEMENTS = 12;

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
                if (fired >= MAX_ADVANCEMENTS) break;
                fire("org.bukkit.event.player.PlayerAdvancementDoneEvent",
                        new Object[]{player, advancement});
                fired++;
                pause();
            }
        } catch (NoSuchMethodException tooOld) {
            // Pre-1.12: achievements, a different event and a fixed enum.
            final Class<?> achievement = Class.forName("org.bukkit.Achievement");
            final Object[] all = (Object[]) achievement.getMethod("values").invoke(null);
            for (Object a : all) {
                if (!only.isEmpty()
                        && !String.valueOf(a).toLowerCase(Locale.ROOT)
                                .contains(only.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                fire("org.bukkit.event.player.PlayerAchievementAwardedEvent",
                        new Object[]{player, a});
                fired++;
                pause();
            }
            // A filter naming an advancement tab means nothing to an achievement enum,
            // so a caller asking for "adventure" would get silence on these versions --
            // which reads as the category being broken rather than named differently.
            // One is fired regardless, so the caller always sees the behaviour it asked
            // about even when its filter belongs to a newer Minecraft.
            if (fired == 0 && all.length > 0) {
                fire("org.bukkit.event.player.PlayerAchievementAwardedEvent",
                        new Object[]{player, all[0]});
                fired++;
            }
        }
        sender.sendMessage("DRIVER-COUNT advancement " + fired);
        getLogger().info("DRIVER-COUNT advancement " + fired);
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
    /**
     * Builds an event by filling whatever constructor this version offers.
     *
     * <p>PlayerDeathEvent has changed shape repeatedly -- a damage source was added,
     * parameters reordered -- so naming one argument list works on some versions and
     * fails on others with NoSuchMethodException, which is what happened here on 26.2.
     * Rather than keep a list of shapes in step with Minecraft, this fills each
     * parameter by type: the player where a Player is wanted, the message where a
     * String is, an empty list, a zero, and null for anything else.
     *
     * <p>Nulls are acceptable because the plugin reads the victim and the cause, not
     * the drops or the damage source. If a version ever needs one of those, it fails
     * naming the constructor it tried rather than silently sending a wrong event.
     */
    /**
     * Fires an event by matching what is offered to whatever constructor this version has.
     *
     * <p>Bukkit's event constructors change. EntityExplodeEvent gained an
     * ExplosionResult in a recent version, so the four-argument call that worked
     * everywhere else failed on 26.2 with "no constructor matching" -- and the sweep
     * reported three explosion settings as broken when the plugin had never been asked
     * anything.
     *
     * <p>Arguments are matched by type rather than by position, so a parameter added in
     * the middle of a signature does not shift everything after it. A parameter nothing
     * offered gets a harmless default; for an enum that is its first constant rather
     * than null, because these constructors reject null far more often than they reject
     * an unexpected value.
     */
    private void fireAdapting(String eventClass, Object... offered) throws Exception {
        final Class<?> type = Class.forName(eventClass);
        Constructor<?> best = null;
        final List<String> tried = new ArrayList<>();
        for (Constructor<?> c : type.getConstructors()) {
            tried.add(Arrays.toString(c.getParameterTypes()));
            if (best == null || c.getParameterCount() < best.getParameterCount()) best = c;
        }
        if (best == null) {
            throw new NoSuchMethodException(eventClass + " has no public constructor");
        }

        final Class<?>[] params = best.getParameterTypes();
        final Object[] args = new Object[params.length];
        final boolean[] used = new boolean[offered.length];
        for (int i = 0; i < params.length; i++) {
            args[i] = pick(params[i], offered, used);
        }
        try {
            Bukkit.getPluginManager().callEvent((Event) best.newInstance(args));
        } catch (Throwable t) {
            throw new IllegalStateException(eventClass + " would not construct with "
                    + Arrays.toString(params) + "; this version offers " + tried, t);
        }
    }

    /** The first unused argument this parameter will accept, or a harmless default. */
    private static Object pick(Class<?> want, Object[] offered, boolean[] used) {
        for (int i = 0; i < offered.length; i++) {
            if (used[i] || offered[i] == null) continue;
            final Class<?> have = offered[i].getClass();
            if (want.isAssignableFrom(have)
                    || (want == float.class  && offered[i] instanceof Float)
                    || (want == double.class && offered[i] instanceof Double)
                    || (want == int.class    && offered[i] instanceof Integer)
                    || (want == boolean.class && offered[i] instanceof Boolean)) {
                used[i] = true;
                return offered[i];
            }
        }
        if (want.isEnum()) {
            final Object[] constants = want.getEnumConstants();
            return (constants != null && constants.length > 0) ? constants[0] : null;
        }
        if (java.util.List.class.isAssignableFrom(want)) return new ArrayList<Object>();
        if (!want.isPrimitive())     return null;
        if (want == boolean.class)   return Boolean.FALSE;
        if (want == float.class)     return 0.0f;
        if (want == double.class)    return 0.0d;
        if (want == long.class)      return 0L;
        return 0;
    }

    private void fireBestEffort(String eventClass, Player player, String message)
            throws Exception {
        final Class<?> type = Class.forName(eventClass);
        final List<String> tried = new ArrayList<>();

        Constructor<?> best = null;
        Object[] bestArgs = null;
        for (Constructor<?> c : type.getConstructors()) {
            final Class<?>[] p = c.getParameterTypes();
            final Object[] args = new Object[p.length];
            for (int i = 0; i < p.length; i++) {
                if (p[i].isAssignableFrom(Player.class)) args[i] = player;
                else if (p[i] == String.class) args[i] = message;
                else if (java.util.List.class.isAssignableFrom(p[i])) args[i] = new ArrayList<Object>();
                else if (p[i] == int.class) args[i] = 0;
                else if (p[i] == double.class) args[i] = 0.0d;
                else if (p[i] == float.class) args[i] = 0.0f;
                else if (p[i] == boolean.class) args[i] = Boolean.FALSE;
                else if (p[i].isPrimitive()) args[i] = 0;
                else args[i] = null;
            }
            tried.add(Arrays.toString(p));
            // Fewest parameters wins: the shortest constructor is the one least likely
            // to want something only a real death could supply.
            if (best == null || p.length < best.getParameterCount()) {
                best = c;
                bestArgs = args;
            }
        }
        if (best == null) {
            throw new NoSuchMethodException(eventClass + " has no public constructor");
        }
        try {
            Bukkit.getPluginManager().callEvent((Event) best.newInstance(bestArgs));
        } catch (Throwable t) {
            throw new IllegalStateException(eventClass + " would not construct. Tried "
                    + best.getParameterCount() + " args; this version offers " + tried, t);
        }
    }

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
