package com.discordlogger.listener.custom;

import com.discordlogger.custom.CustomLogs;
import com.discordlogger.custom.CustomTemplate;
import com.discordlogger.filter.Filters;
import com.discordlogger.log.Log;
import com.discordlogger.util.Names;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Fires the admin-defined rules in {@link CustomLogs}.
 *
 * <p>A command sniffer, exactly like the moderation listeners, and subject to the same
 * filters every other player event is: {@code ignored_players}, {@code ignored_worlds},
 * vanish and the command deny-list all apply. A custom rule is a new <em>event</em>,
 * not a new route around the rules — an admin who has hidden a player expects that to
 * hold everywhere, and a feature that quietly exempted itself would be the one place
 * it silently did not.
 */
public final class CustomCommandLog implements Listener {

    private static final String THUMB_SERVER =
            "https://discordlogger.godtiergamers.xyz/assets/icons/server.png";

    private final JavaPlugin plugin;

    public CustomCommandLog(JavaPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        final Player p = e.getPlayer();
        if (Filters.blocksPlayer(p) || Filters.blocksWorld(p.getWorld().getName())) return;
        if (Filters.blocksCommand(e.getMessage())) return;
        fire(e.getMessage(), Names.display(p, plugin), p.getWorld().getName(),
                Log.playerAvatarUrl(p.getUniqueId()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent e) {
        // Console has no player and no world, so only the command filter can apply.
        if (Filters.blocksCommand(e.getCommand())) return;
        fire("/" + e.getCommand(), e.getSender().getName(), "", THUMB_SERVER);
    }

    private void fire(String raw, String who, String world, String thumb) {
        final List<CustomLogs.Rule> rules = CustomLogs.rules();
        if (rules.isEmpty()) return;

        final CustomLogs.Rule rule = CustomLogs.match(rules, raw);
        if (rule == null) return;

        // Read live, like every other event gate, so /discordlogger reload takes effect
        // without re-registering anything.
        if (!plugin.getConfig().getBoolean(
                "log.custom." + rule.name() + ".enabled", true)) return;

        final List<String> words = CustomLogs.words(
                raw.startsWith("/") ? raw.substring(1) : raw);

        final String title = CustomTemplate.render(rule.title(), who, words, world);
        final String body = CustomTemplate.render(rule.message(), who, words, world);
        if (body.isEmpty()) return;

        Log.eventWithThumb(rule.category(),
                title.isEmpty() ? body : "**" + title + "**\n" + body, thumb);
    }
}
