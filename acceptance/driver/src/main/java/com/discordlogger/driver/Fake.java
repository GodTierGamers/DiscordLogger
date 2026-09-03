package com.discordlogger.driver;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.UUID;

/**
 * A player who is not there.
 *
 * <h2>Why a proxy</h2>
 *
 * <p>Every event worth testing carries a {@link Player}, and a headless server has
 * none. A real one would need a client to connect, which is not something a nightly
 * job can arrange. So this answers the handful of questions the plugin's listeners
 * actually ask -- name, uuid, world, location -- and returns a harmless default for
 * everything else rather than exploding on the first unexpected call.
 *
 * <p>The uuid is fixed and belongs to nobody. That matters twice: the avatar URL the
 * plugin builds resolves to a default Steve rather than a real person's skin, and
 * every run produces byte-identical payloads, so a diff between versions means a
 * behavioural difference rather than a different random id.
 */
public final class Fake {

    /**
     * Deliberately not a real account. mc-heads answers unknown ids with Steve.
     *
     * <p>Not all zeros, which the obvious choice would be. Floodgate issues Bedrock
     * players UUIDs whose most significant bits are zero, and DiscordLogger reads that
     * shape as Bedrock when Floodgate is absent -- correctly, since it is Floodgate's
     * own format. A fake numbered 0000...0001 therefore arrived at Discord labelled
     * "Platform: Bedrock" on every single join, which is right for that UUID and wrong
     * for what the fake is meant to represent. The suite would have gone on asserting
     * that a Java player is not mislabelled while driving it with a Bedrock id.
     */
    public static final UUID UUID_ = UUID.fromString("00acce55-0000-4000-8000-000000000001");
    public static final String NAME = "Player1";

    /**
     * Answers the fake gives that a test needs to change.
     *
     * <p>Three settings cannot be reached with a player who always answers the same
     * way. filters.exempt_permission needs a player who holds a permission,
     * filters.respect_vanish needs one a vanish plugin is hiding, and format.nicknames
     * needs a display name that differs from the real one. A fixed fake answers no,
     * no and "same as the name", which makes all three look like they are working when
     * nothing has been tested.
     *
     * <p>Volatile because the console thread sets them and the server thread reads
     * them, and the handler reads them per call rather than capturing them, so a change
     * applies to the fake that already exists.
     */
    public static volatile String nickname = null;
    public static volatile String permission = null;
    public static volatile boolean vanished = false;

    /** Set once by the driver, for building the metadata a vanish plugin would attach. */
    public static volatile org.bukkit.plugin.Plugin owner = null;

    /** Puts every answer back to a plain, unremarkable player. */
    public static void reset() {
        nickname = null;
        permission = null;
        vanished = false;
        killer = null;
        bedrock = false;
    }

    private Fake() {}

    /**
     * Whether the fake should look like a Bedrock player to DiscordLogger.
     *
     * <p>Floodgate issues Bedrock players UUIDs whose most significant bits are zero,
     * and the plugin falls back to that shape when Floodgate is not installed. So a
     * UUID of that shape exercises the real detection without needing Floodgate on the
     * test server -- which is why the Bedrock path had no coverage at all until now.
     */
    public static volatile boolean bedrock = false;

    /** Floodgate's shape: all of the most significant bits zero. */
    private static final UUID BEDROCK_UUID = new UUID(0L, 0x9A1D0000_0000_0001L);

    /** The player named as the killer, when a case wants a death with one. */
    public static volatile String killer = null;

    /** A Player that answers what DiscordLogger asks, and null to everything else. */
    public static Player player(final World world) {
        return player(world, NAME, UUID_);
    }

    /**
     * A Player that answers what DiscordLogger asks, and null to everything else.
     *
     * <p>Takes a name so a second one can be built to act as a killer, which the
     * slain-by-player wording needs and no single fake can provide for itself.
     */
    public static Player player(final World world, final String who, final UUID id) {
        final Location where = new Location(world, -252, 66, 252);

        // The victim's own damage, remembered.
        //
        // A real death carries its cause on the victim, and DiscordLogger reads it back
        // off them -- getLastDamageCause is the whole of how it tells a fall from a
        // drowning. A fake that accepted the setter and dropped the value answered null
        // to every read, so every one of the thirty causes came out as the "Died"
        // fallback and the thirty lang lines under discord.death.causes were never once
        // exercised. The suite fired all thirty and tested one.
        final Object[] lastDamage = new Object[1];
        final Object inventory = inventory();

        final InvocationHandler handler = new InvocationHandler() {
            @Override public Object invoke(Object proxy, Method m, Object[] args) {
                final String name = m.getName();
                switch (name) {
                    case "getName":
                    case "getCustomName":
                        return who;
                    case "getDisplayName":
                        return (nickname != null && who.equals(NAME)) ? nickname : who;
                    case "getUniqueId":
                        return (bedrock && who.equals(NAME)) ? BEDROCK_UUID : id;
                    case "getWorld":          return world;
                    case "getLocation":       return where;
                    case "setLastDamageCause":
                        lastDamage[0] = (args == null || args.length == 0) ? null : args[0];
                        return null;
                    case "getLastDamageCause":
                        return lastDamage[0];
                    case "getInventory":      return inventory;
                    case "getKiller":
                        // Only the victim has a killer; the killer does not have one of
                        // their own, which would recurse forever.
                        return (killer != null && who.equals(NAME))
                                ? player(world, killer,
                                        UUID.nameUUIDFromBytes(("k-" + killer).getBytes()))
                                : null;
                    case "isOp":              return Boolean.FALSE;
                    case "hasPermission":
                    case "isPermissionSet":
                        return Boolean.valueOf(holds(args));
                    case "hasMetadata":
                        return Boolean.valueOf(vanished && isVanishKey(args));
                    case "getMetadata":
                        return (vanished && isVanishKey(args) && owner != null)
                                ? Collections.singletonList(
                                        new org.bukkit.metadata.FixedMetadataValue(
                                                owner, Boolean.TRUE))
                                : Collections.emptyList();
                    case "isOnline":          return Boolean.TRUE;
                    // PlayerCommandPreprocessEvent's constructor builds a recipient set
                    // from player.getServer(), so a fake answering null there takes the
                    // event down before it is ever fired -- which the sweep could only
                    // see as "the plugin logged nothing".
                    case "getServer":         return org.bukkit.Bukkit.getServer();
                    case "getGameMode":       return org.bukkit.GameMode.SURVIVAL;
                    case "getType":           return org.bukkit.entity.EntityType.PLAYER;
                    case "toString":          return "FakePlayer(" + who + ")";
                    case "hashCode":          return id.hashCode();
                    case "equals":            return proxy == (args == null ? null : args[0]);
                    default:
                        return defaultFor(m.getReturnType());
                }
            }
        };
        return build(Player.class, handler);
    }

    /**
     * An inventory holding a weapon, because the killer's is read without a null check.
     *
     * <p>DiscordLogger names the weapon a killer was holding, reaching it through
     * getInventory().getItemInHand(). A fake answering null to getInventory takes the
     * plugin down with a NullPointerException before it can write the message, which
     * would look exactly like a bug in the plugin.
     */
    private static Object inventory() {
        final org.bukkit.inventory.ItemStack weapon =
                new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_SWORD);
        return build(org.bukkit.inventory.PlayerInventory.class, new InvocationHandler() {
            @Override public Object invoke(Object proxy, Method m, Object[] args) {
                switch (m.getName()) {
                    case "getItemInHand":
                    case "getItemInMainHand":
                        return weapon;
                    case "toString": return "FakeInventory";
                    default:         return defaultFor(m.getReturnType());
                }
            }
        });
    }

    /** Whether the node being asked about is the one this fake was told to hold. */
    private static boolean holds(Object[] args) {
        if (permission == null || args == null || args.length == 0 || args[0] == null) {
            return false;
        }
        // Bukkit asks by String on some paths and by Permission on others.
        final String asked = (args[0] instanceof org.bukkit.permissions.Permission)
                ? ((org.bukkit.permissions.Permission) args[0]).getName()
                : args[0].toString();
        return permission.equalsIgnoreCase(asked);
    }

    private static boolean isVanishKey(Object[] args) {
        return args != null && args.length > 0 && "vanished".equals(args[0]);
    }

    /**
     * A class implementing {@code type}, with every method driven through {@code h}.
     *
     * <h2>Why not java.lang.reflect.Proxy</h2>
     *
     * <p>Proxy cannot implement {@link Player} on any 1.8 build. That era's Bukkit
     * declares {@code getHealth()} twice with different return types, and Proxy rejects
     * the interface outright: "methods with same signature but incompatible return
     * types". No handler code changes that, because the restriction is inside Proxy.
     *
     * <p>It is a restriction of the Java language rather than the JVM. Two methods
     * differing only in return type are illegal in source and perfectly legal in
     * bytecode, which is why a generated class can do what Proxy will not. The same
     * path is used on every version, so nothing here is special-cased for 1.8 and the
     * generator is exercised everywhere rather than only where it is essential.
     *
     * <p>Loaded through WRAPPER, which defines the class in a child of the plugin's own
     * loader. INJECTION would define it into that loader directly and needs reflective
     * access newer JVMs refuse; this has to work from Java 8 through 25.
     */
    @SuppressWarnings("unchecked")
    static <T> T build(Class<T> type, InvocationHandler h) {
        try {
            final Class<? extends T> generated = new ByteBuddy()
                    .subclass(type)
                    .method(ElementMatchers.any())
                    .intercept(InvocationHandlerAdapter.of(h))
                    .make()
                    .load(Fake.class.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                    .getLoaded();
            return (T) generated.getDeclaredConstructor().newInstance();
        } catch (Throwable cannotGenerate) {
            // Reported rather than thrown. A server this cannot build a fake for is a
            // limit of the harness, and the suite skips those cases with the reason
            // instead of recording settings as broken.
            return null;
        }
    }

    /** The first loaded world, which every server has by the time plugins are enabled. */
    public static World world() {
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    /**
     * Something harmless for a return type we have not thought about.
     *
     * <p>Returning null for an unexpected object is what keeps this small: the listeners
     * are the specification, and when one of them starts asking a new question the
     * failure is a clear NullPointerException in that listener rather than a proxy that
     * silently invented an answer.
     */
    private static Object defaultFor(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return Boolean.FALSE;
        if (type == int.class)     return 0;
        if (type == long.class)    return 0L;
        if (type == double.class)  return 0.0d;
        if (type == float.class)   return 0.0f;
        if (type == short.class)   return (short) 0;
        if (type == byte.class)    return (byte) 0;
        if (type == char.class)    return (char) 0;
        return null;
    }
}
