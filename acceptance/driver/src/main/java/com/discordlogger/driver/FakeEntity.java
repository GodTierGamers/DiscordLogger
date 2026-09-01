package com.discordlogger.driver;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.UUID;

/**
 * The thing that exploded, without spawning one.
 *
 * <p>Spawning a real creeper and detonating it would be more faithful and far less
 * reliable: the entity has to load a chunk, survive a tick, and actually explode, and
 * several of the types worth covering cannot be spawned to order at all. What the
 * plugin reads from an explosion is the entity's type and location, so that is what
 * this answers.
 */
final class FakeEntity {

    private FakeEntity() {}

    /** An Entity reporting the given type, or null if this version has no such type. */
    static Entity of(final EntityType type, final World world) {
        if (type == null) return null;
        final Location where = new Location(world, 0, 70, 0);
        final UUID id = UUID.nameUUIDFromBytes(("fake-" + type.name()).getBytes());

        final InvocationHandler handler = new InvocationHandler() {
            @Override public Object invoke(Object proxy, Method m, Object[] args) {
                switch (m.getName()) {
                    case "getType":        return type;
                    case "getLocation":    return where;
                    case "getWorld":       return world;
                    case "getUniqueId":    return id;
                    case "getName":        return type.name();
                    case "getCustomName":  return null;
                    case "isDead":         return Boolean.FALSE;
                    case "hasMetadata":    return Boolean.FALSE;
                    case "getMetadata":    return Collections.emptyList();
                    case "toString":       return "FakeEntity(" + type.name() + ")";
                    case "hashCode":       return id.hashCode();
                    case "equals":         return proxy == (args == null ? null : args[0]);
                    default:
                        final Class<?> r = m.getReturnType();
                        if (!r.isPrimitive()) return null;
                        if (r == boolean.class) return Boolean.FALSE;
                        if (r == int.class)     return 0;
                        if (r == long.class)    return 0L;
                        if (r == double.class)  return 0.0d;
                        if (r == float.class)   return 0.0f;
                        return null;
                }
            }
        };
        return Fake.build(Entity.class, handler);
    }

    /**
     * An arrow, optionally fired by someone.
     *
     * <p>DiscordLogger tells "Shot by X" from a bare "Shot" by asking the projectile
     * who its shooter was, so the three wordings need three different projectiles
     * rather than three different damage causes.
     *
     * @param shooter the Player or Entity that fired it, or null for a projectile
     *                nobody owns
     */
    static Entity projectile(final World world, final Object shooter) {
        final Location where = new Location(world, 0, 70, 0);
        final UUID id = UUID.nameUUIDFromBytes("fake-arrow".getBytes());

        final InvocationHandler handler = new InvocationHandler() {
            @Override public Object invoke(Object proxy, Method m, Object[] args) {
                switch (m.getName()) {
                    case "getShooter":     return shooter;
                    case "getType":        return EntityType.valueOf("ARROW");
                    case "getLocation":    return where;
                    case "getWorld":       return world;
                    case "getUniqueId":    return id;
                    case "getName":        return "Arrow";
                    case "getCustomName":  return null;
                    case "isDead":         return Boolean.FALSE;
                    case "hasMetadata":    return Boolean.FALSE;
                    case "getMetadata":    return Collections.emptyList();
                    case "toString":       return "FakeArrow";
                    case "hashCode":       return id.hashCode();
                    case "equals":         return proxy == (args == null ? null : args[0]);
                    default:
                        final Class<?> r = m.getReturnType();
                        if (!r.isPrimitive()) return null;
                        if (r == boolean.class) return Boolean.FALSE;
                        if (r == int.class)     return 0;
                        if (r == long.class)    return 0L;
                        if (r == double.class)  return 0.0d;
                        if (r == float.class)   return 0.0f;
                        return null;
                }
            }
        };
        // Generated as an Arrow rather than a Projectile: the plugin checks
        // "instanceof Projectile", and Arrow is one on every version.
        return Fake.build(org.bukkit.entity.Arrow.class, handler);
    }
}
