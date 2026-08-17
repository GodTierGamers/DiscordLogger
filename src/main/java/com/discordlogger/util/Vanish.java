package com.discordlogger.util;

import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

/**
 * Whether a player is currently hidden by a vanish plugin.
 *
 * <h2>Metadata, not an API per plugin</h2>
 *
 * <p>EssentialsX, SuperVanish, PremiumVanish and CMI all set a {@code vanished}
 * metadata value on the player, and all of them document it as the way other plugins
 * should ask. Reading it covers every one of them with no soft-depend, no reflection
 * and no version coupling — and it keeps working for vanish plugins nobody here has
 * heard of, provided they follow the same convention.
 *
 * <p>The alternative was four reflective API calls that each break when the plugin
 * they target reorganises a package. This asks the question once, in the language all
 * of them already speak.
 *
 * <h2>Any "yes" wins</h2>
 *
 * <p>More than one plugin can set the value, so the first {@code true} settles it
 * rather than the last writer winning. The asymmetry is deliberate and matches
 * {@link ClientPlatform}: a false negative announces to Discord something a staff
 * member deliberately hid, while a false positive merely omits a line from a log.
 * Those are not equally bad, so the check leans toward silence.
 *
 * <p><b>Invisibility is not vanish.</b> A potion effect is a game mechanic other
 * players can still see through in the tab list, and treating it as vanish would
 * silently stop logging anyone who drank one.
 */
public final class Vanish {

    /** The key every mainstream vanish plugin writes. */
    private static final String KEY = "vanished";

    private Vanish() {}

    /**
     * True when any plugin currently reports this player as vanished.
     *
     * <p>Never throws. A vanish plugin misbehaving must not take event logging down
     * with it, so anything unexpected is treated as "not vanished" — the plugin
     * carries on doing its job, which is the failure the admin can actually see and
     * report.
     */
    public static boolean isVanished(Player player) {
        if (player == null) return false;
        try {
            for (MetadataValue meta : player.getMetadata(KEY)) {
                if (meta != null && meta.asBoolean()) return true;
            }
        } catch (Throwable misbehavingVanishPlugin) {
            return false;
        }
        return false;
    }
}
