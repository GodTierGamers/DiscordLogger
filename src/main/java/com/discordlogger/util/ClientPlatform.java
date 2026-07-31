package com.discordlogger.util;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Works out whether a player connected from Bedrock rather than Java.
 *
 * <p>Bedrock players reach a Paper server through Geyser, and are only
 * distinguishable at all when <b>Floodgate</b> is handling their accounts —
 * with Geyser standalone they authenticate as ordinary Java accounts and nothing,
 * including Floodgate's own API, can tell them apart. So "not Bedrock" here means
 * "nothing indicates Bedrock", never "definitely Java".
 *
 * <p>Two detection routes, in order:
 *
 * <ol>
 *   <li><b>Floodgate's API</b>, called reflectively. It is the documented,
 *       supported answer ({@code FloodgateApi#isFloodgatePlayer}). Reflection
 *       rather than a compile-time dependency keeps a Maven repository and an
 *       artifact out of the build for what is an optional integration — the same
 *       reasoning as {@link Platform}, which probes for classes it cannot assume.</li>
 *   <li><b>The UUID shape</b>, if Floodgate is absent or its API changes shape.
 *       Floodgate issues Bedrock players UUIDs whose most significant bits are
 *       zero. This is <i>not documented</i>, so it is the fallback and not the
 *       primary: it works today and may quietly stop. A real Java UUID is version
 *       4 random, so the chance of one colliding with this pattern is about
 *       2^-64 — false positives are not a practical concern.</li>
 * </ol>
 *
 * <p>The reflective lookup is resolved once and cached; a per-join
 * {@code Class.forName} on a server with no Floodgate would be pure waste.
 */
public final class ClientPlatform {

    private static final String FLOODGATE_API = "org.geysermc.floodgate.api.FloodgateApi";

    /**
     * Whether Floodgate's API class exists at all. Fixed for the JVM's lifetime —
     * a plugin cannot appear after startup — so this is safe to cache either way.
     */
    private static final boolean FLOODGATE_PRESENT = classExists(FLOODGATE_API);

    /**
     * The resolved method, cached only once it is actually found.
     *
     * <p>A negative result is deliberately NOT cached. {@code FloodgateApi.getInstance()}
     * returns null until Floodgate has finished initialising, and this class may load
     * before that; caching that null would disable detection permanently for the rest
     * of the session, on exactly the servers the feature exists for.
     */
    private static volatile Object apiInstance;
    private static volatile Method isBedrockMethod;

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** @return true if the API is usable right now. Retries until it is. */
    private static boolean resolveApi() {
        if (isBedrockMethod != null) return true;
        if (!FLOODGATE_PRESENT) return false;
        try {
            Class<?> api = Class.forName(FLOODGATE_API);
            Object instance = api.getMethod("getInstance").invoke(null);
            if (instance == null) return false;   // not initialised yet; try again later
            Method method = api.getMethod("isFloodgatePlayer", UUID.class);
            apiInstance = instance;
            isBedrockMethod = method;
            return true;
        } catch (Throwable t) {
            // Floodgate present but unusable — resolving the interface can pull in a
            // Cumulus class this plugin's classloader cannot see (observed in testing,
            // which is why this catches Throwable, not Exception).
            return false;
        }
    }

    private ClientPlatform() {}

    /**
     * True when anything indicates this player connected from Bedrock.
     *
     * <p>The signals are OR'd, deliberately. An earlier version returned the API's
     * answer directly whenever the API was available, which made a {@code false}
     * from it override a UUID that was plainly Floodgate's. That is wrong: the API
     * is authoritative when it says <i>yes</i>, but a <i>no</i> only means "not in
     * my player registry", and behind a Velocity proxy the backend's registry does
     * not necessarily contain a player whose handshake the proxy handled. A
     * Floodgate-shaped UUID is positive evidence in its own right.
     */
    public static boolean isBedrock(UUID uuid) {
        if (uuid == null) return false;
        return Boolean.TRUE.equals(apiVerdict(uuid))
                || Boolean.TRUE.equals(floodgateIdVerdict(uuid))
                || looksLikeFloodgateUuid(uuid);
    }

    /**
     * Floodgate's own {@code isFloodgateId} — a shape check rather than a registry
     * lookup, so it answers for a player the local registry has never seen. Preferred
     * over our fallback because it is Floodgate's definition of its own UUID format,
     * not our reading of it.
     */
    public static Boolean floodgateIdVerdict(UUID uuid) {
        if (uuid == null || !resolveApi()) return null;
        try {
            Method m = apiInstance.getClass().getMethod("isFloodgateId", UUID.class);
            return Boolean.TRUE.equals(m.invoke(apiInstance, uuid));
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Floodgate's UUID shape: the most significant 64 bits are zero, giving
     * {@code 00000000-0000-0000-xxxx-xxxxxxxxxxxx}. Undocumented, hence the fallback.
     */
    static boolean looksLikeFloodgateUuid(UUID uuid) {
        return uuid != null && uuid.getMostSignificantBits() == 0L;
    }

    /** Whether Floodgate's API was found — for diagnostics, not for callers to branch on. */
    public static boolean floodgateApiAvailable() {
        return resolveApi();
    }

    /** Whether the Floodgate API class is on the classpath at all. */
    public static boolean floodgateClassVisible() {
        return FLOODGATE_PRESENT;
    }

    /** The API's own verdict, or null when the API isn't usable. */
    public static Boolean apiVerdict(UUID uuid) {
        if (uuid == null || !resolveApi()) return null;
        try {
            return Boolean.TRUE.equals(isBedrockMethod.invoke(apiInstance, uuid));
        } catch (Throwable t) {
            return null;
        }
    }
}
