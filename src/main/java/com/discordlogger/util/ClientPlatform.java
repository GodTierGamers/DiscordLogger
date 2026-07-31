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

    /** Resolved once: the API instance and its isFloodgatePlayer method, or null. */
    private static final Object API_INSTANCE;
    private static final Method IS_BEDROCK;

    static {
        Object instance = null;
        Method method = null;
        try {
            Class<?> api = Class.forName(FLOODGATE_API);
            instance = api.getMethod("getInstance").invoke(null);
            if (instance != null) {
                method = api.getMethod("isFloodgatePlayer", UUID.class);
            }
        } catch (ClassNotFoundException | LinkageError e) {
            // Floodgate isn't installed. Normal on most servers.
        } catch (Exception e) {
            // Installed but the API doesn't look how we expect — fall back rather
            // than let an optional integration break joins.
            instance = null;
            method = null;
        }
        API_INSTANCE = instance;
        IS_BEDROCK = method;
    }

    private ClientPlatform() {}

    /** True when this player is known to have connected from Bedrock. */
    public static boolean isBedrock(UUID uuid) {
        if (uuid == null) return false;

        if (API_INSTANCE != null && IS_BEDROCK != null) {
            try {
                return Boolean.TRUE.equals(IS_BEDROCK.invoke(API_INSTANCE, uuid));
            } catch (Exception e) {
                // Fall through to the shape check rather than failing the join.
            }
        }
        return looksLikeFloodgateUuid(uuid);
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
        return API_INSTANCE != null && IS_BEDROCK != null;
    }
}
