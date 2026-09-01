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

    /** Deliberately not a real account. mc-heads answers unknown ids with Steve. */
    public static final UUID UUID_ = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final String NAME = "Player1";

    private Fake() {}

    /** A Player that answers what DiscordLogger asks, and null to everything else. */
    public static Player player(final World world) {
        final Location where = new Location(world, -252, 66, 252);

        final InvocationHandler handler = new InvocationHandler() {
            @Override public Object invoke(Object proxy, Method m, Object[] args) {
                final String name = m.getName();
                switch (name) {
                    case "getName":
                    case "getCustomName":
                        return NAME;
                    case "getDisplayName":
                        return NAME;
                    case "getUniqueId":       return UUID_;
                    case "getWorld":          return world;
                    case "getLocation":       return where;
                    case "getKiller":         return null;
                    case "isOp":              return Boolean.FALSE;
                    case "hasPermission":     return Boolean.FALSE;
                    case "isPermissionSet":   return Boolean.FALSE;
                    case "hasMetadata":       return Boolean.FALSE;
                    case "getMetadata":       return Collections.emptyList();
                    case "isOnline":          return Boolean.TRUE;
                    case "getGameMode":       return org.bukkit.GameMode.SURVIVAL;
                    case "getType":           return org.bukkit.entity.EntityType.PLAYER;
                    case "toString":          return "FakePlayer(" + NAME + ")";
                    case "hashCode":          return UUID_.hashCode();
                    case "equals":            return proxy == (args == null ? null : args[0]);
                    default:
                        return defaultFor(m.getReturnType());
                }
            }
        };
        return build(Player.class, handler);
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
    private static <T> T build(Class<T> type, InvocationHandler h) {
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
