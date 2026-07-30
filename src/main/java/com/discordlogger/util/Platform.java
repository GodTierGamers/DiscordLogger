package com.discordlogger.util;

/**
 * Startup guard for the Paper APIs this plugin genuinely requires.
 *
 * <p>Without this check, a Spigot/CraftBukkit server fails in a way that tells the
 * admin nothing useful: {@code EventRegistry.registerAll()} reflects over
 * {@code PlayerChat}'s handler parameters, hits the missing
 * {@code AsyncChatEvent}, and throws {@link NoClassDefFoundError} out of
 * {@code onEnable()} — Bukkit then disables the plugin with a raw stack trace and
 * no explanation. Detecting it up front turns that into an answerable question.
 *
 * <p>Deliberately probes the exact classes we depend on rather than "is this
 * Paper?", so a fork missing one of them is reported accurately instead of being
 * waved through on a brand name.
 */
public final class Platform {

    /** Paper/Adventure classes the plugin cannot run without. */
    private static final String[] REQUIRED_CLASSES = {
            // Paper's modern chat event — used by listener/player/PlayerChat
            "io.papermc.paper.event.player.AsyncChatEvent",
            // Adventure, bundled by Paper — used to flatten chat components to text
            "net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer",
    };

    private Platform() {}

    /**
     * @return the first required class this server doesn't provide, or {@code null}
     *         when everything needed is present.
     */
    public static String missingRequirement() {
        for (String className : REQUIRED_CLASSES) {
            try {
                Class.forName(className);
            } catch (ClassNotFoundException | LinkageError e) {
                return className;
            }
        }
        return null;
    }
}
