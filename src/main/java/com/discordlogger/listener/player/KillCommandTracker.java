package com.discordlogger.listener.player;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes {@code /kill} read as a command on servers too old to say so themselves.
 *
 * <p>{@code DamageCause.KILL} arrived in Minecraft 1.20. Before that the server
 * reports {@code /kill} as {@code VOID} — the same cause as genuinely falling out
 * of the world — so a death by command was logged as "Fell into the void". The
 * plugin was reporting exactly what it was told; the server simply could not
 * distinguish the two.
 *
 * <p>This watches for the command and lets {@link PlayerDeath} correlate a `VOID`
 * death against it, which is the same trick the moderation listeners use: watch
 * the command, then confirm against what actually happened.
 *
 * <h2>It does nothing on 1.20 and newer</h2>
 *
 * <p>{@link #ACTIVE} is false wherever {@code KILL} exists, and then nothing is
 * recorded and nothing is consulted. Correlation is a workaround for missing
 * information, so running it where the information exists could only ever turn a
 * correct answer into a guess.
 */
public final class KillCommandTracker implements Listener {

    /**
     * How long after the command a `VOID` death still counts as caused by it.
     *
     * <p>The damage and the death happen in the same tick as the command, so this
     * only has to survive one tick (50ms) plus whatever the server is behind by.
     * Deliberately short: the longer the window, the more likely a genuine void
     * death nearby gets mislabelled, and being wrong is worse than being vague.
     */
    private static final long WINDOW_MS = 250L;

    /** Recorded when a selector was used and the victim cannot be named up front. */
    private static final String ANY_PLAYER = "*";

    /** False on 1.20+, where the server distinguishes KILL from VOID by itself. */
    static final boolean ACTIVE = !killCauseExists();

    /** Lower-cased victim name (or {@link #ANY_PLAYER}) to when the command ran. */
    private static final Map<String, Long> PENDING = new ConcurrentHashMap<>();

    private static boolean killCauseExists() {
        try {
            EntityDamageEvent.DamageCause.valueOf("KILL");
            return true;
        } catch (IllegalArgumentException absentBefore120) {
            return false;
        }
    }

    // ------------------------------------------------------------------ capture

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        if (!ACTIVE) return;
        record(targetOf(e.getMessage()), e.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent e) {
        // Console has no self to kill, so a bare "/kill" from it targets nobody.
        if (!ACTIVE) return;
        record(targetOf("/" + e.getCommand()), null);
    }

    /**
     * @param target  what {@link #targetOf} returned, or null when not a kill
     * @param selfName the sender's own name, used when the command had no target
     */
    private static void record(String target, String selfName) {
        if (target == null) return;
        final String who = target.isEmpty() ? selfName : target;
        if (who == null) return;                       // bare /kill from console
        PENDING.put(who.toLowerCase(Locale.ROOT), System.currentTimeMillis());
    }

    // ------------------------------------------------------------------- query

    /**
     * True when this player was the target of {@code /kill} a moment ago.
     *
     * <p>Consuming: a record is only good for the one death it explains, so a
     * second void death seconds later is reported honestly as a void death.
     */
    static boolean wasKilledByCommand(Player victim) {
        if (!ACTIVE || victim == null) return false;
        final long now = System.currentTimeMillis();
        prune(now);
        return consume(victim.getName().toLowerCase(Locale.ROOT), now)
                || consume(ANY_PLAYER, now);
    }

    private static boolean consume(String key, long now) {
        final Long at = PENDING.get(key);
        if (at == null || now - at > WINDOW_MS) return false;
        PENDING.remove(key);
        return true;
    }

    /** Entries only ever matter for a quarter of a second, so drop the rest. */
    private static void prune(long now) {
        PENDING.entrySet().removeIf(entry -> now - entry.getValue() > WINDOW_MS);
    }

    // ------------------------------------------------------------------ parsing

    /**
     * The target of a {@code /kill}, or null when the line is not one.
     *
     * <p>Returns an empty string for a bare {@code /kill}, meaning "the sender",
     * and {@link #ANY_PLAYER} for a selector, which cannot be resolved from the
     * text alone. A selector is treated as matching whoever dies next inside the
     * window — within 250ms of a {@code /kill @a} that is what happened.
     *
     * <p>Split out from the listener so it can be tested without a server, since
     * the awkward cases are all textual: plugin prefixes, trailing spaces, and
     * commands that merely start with the letters "kill".
     */
    static String targetOf(String rawWithSlash) {
        if (rawWithSlash == null) return null;
        // Trim BEFORE stripping the slash: a leading space would otherwise leave
        // the slash attached and the verb would never match.
        String raw = rawWithSlash.trim();
        if (raw.startsWith("/")) raw = raw.substring(1).trim();
        if (raw.isEmpty()) return null;

        final String[] parts = raw.split("\\s+");
        String verb = parts[0].toLowerCase(Locale.ROOT);

        // "/minecraft:kill" and "/essentials:kill" are the same command.
        final int colon = verb.indexOf(':');
        if (colon >= 0) verb = verb.substring(colon + 1);

        // Exact match only -- "/killall" and "/killchest" are other plugins' commands.
        if (!verb.equals("kill")) return null;

        if (parts.length < 2) return "";                       // bare /kill = self
        return parts[1].startsWith("@") ? ANY_PLAYER : parts[1];
    }
}
