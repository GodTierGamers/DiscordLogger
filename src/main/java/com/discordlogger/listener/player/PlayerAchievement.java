package com.discordlogger.listener.player;

import com.discordlogger.filter.Filters;
import com.discordlogger.log.Log;
import com.discordlogger.util.Names;
import org.bukkit.Achievement;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAchievementAwardedEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Achievements, which is what 1.8 to 1.11 had instead of advancements.
 *
 * <h2>Why this is a second class and not a second method</h2>
 *
 * <p>{@code PlayerAchievementAwardedEvent} and {@link Achievement} exist from 1.8 to
 * <b>1.14</b> and were removed in 1.15; {@code PlayerAdvancementDoneEvent} arrived in
 * 1.12. Neither type can be named on a server that lacks it without the class failing
 * to load, so the two live apart and {@code com.discordlogger.util.Compat} registers
 * whichever the server actually has.
 *
 * <p>Both exist together on 1.12 to 1.14, where this one is registered and simply
 * never fires: achievements stopped being a game concept in 1.12 even though the API
 * outlived them by three versions. Nothing special is done about that, because
 * "registered but never fired" costs nothing and the alternative is a version check
 * that would have to be right about the exact release the game changed under it.
 *
 * <h2>One switch, one category</h2>
 *
 * <p>Reads {@code log.player.advancement.enabled} and reports under the same category
 * key, so it shares the colour, the webhook routing and the filters. To someone
 * reading the channel an achievement and an advancement are the same event with
 * different wording, and a second setting would have made them prove otherwise.
 */
public final class PlayerAchievement implements Listener {
    private final JavaPlugin plugin;

    public PlayerAchievement(JavaPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAchievement(PlayerAchievementAwardedEvent e) {
        if (!plugin.getConfig().getBoolean("log.player.advancement.enabled", true)) return;
        if (Filters.blocksPlayer(e.getPlayer()) || Filters.blocksWorld(e.getPlayer().getWorld().getName())) return;

        final Achievement achievement = e.getAchievement();
        if (achievement == null) return;

        final String path = achievement.name().toLowerCase(Locale.ROOT);   // "mine_wood"
        final String key  = "minecraft:" + path;

        // Achievements have no namespace -- that idea arrived with advancements. One is
        // synthesised so a server's ignored_advancements list means the same thing on
        // either side of 1.12, rather than silently applying to only half the range.
        if (Filters.blocksAdvancement(key, path)) return;

        final String playerName = Names.display(e.getPlayer(), plugin);
        final UUID uuid = e.getPlayer().getUniqueId();

        List<Log.Field> fields = new ArrayList<>();
        fields.add(new Log.Field("Player:", playerName));
        fields.add(new Log.Field("Achievement:", prettyTitle(path)));
        fields.add(new Log.Field("Key:", key, true));

        Log.eventFieldsWithThumb(
                "player_advancement",
                "Achievement Unlocked",
                null,   // author -> default embeds.author
                fields,
                Log.playerAvatarUrl(uuid)
        );
    }

    /** {@code mine_wood} to {@code Mine Wood}, matching how advancement titles read. */
    static String prettyTitle(String path) {
        final String seg = path.replace('_', ' ').trim();
        if (seg.isEmpty()) return path;

        final String[] words = seg.split("\\s+");
        final StringBuilder sb = new StringBuilder(seg.length());
        for (int i = 0; i < words.length; i++) {
            final String w = words[i];
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) sb.append(w.substring(1).toLowerCase(Locale.ROOT));
            }
            if (i + 1 < words.length) sb.append(' ');
        }
        return sb.toString();
    }
}
