package com.discordlogger.listener.moderation;

import com.discordlogger.log.Log;
import com.discordlogger.util.Names;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class Whitelist implements Listener {
    private final JavaPlugin plugin;
    public Whitelist(JavaPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        // handle both toggle and edits, but honor toggles individually later
        if (!plugin.getConfig().getBoolean("log.moderation.whitelist_toggle", true)
                && !plugin.getConfig().getBoolean("log.moderation.whitelist_edit", true)) return;
        handle(e.getPlayer(), e.getMessage()); // includes leading "/"
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent e) {
        if (!plugin.getConfig().getBoolean("log.moderation.whitelist_toggle", true)
                && !plugin.getConfig().getBoolean("log.moderation.whitelist_edit", true)) return;
        final String raw = "/" + e.getCommand(); // ServerCommandEvent lacks leading "/"
        handle(null, raw);
    }

    private void handle(Player actorPlayer, String rawWithSlash) {
        final String raw = rawWithSlash.startsWith("/") ? rawWithSlash.substring(1) : rawWithSlash;
        if (raw.isBlank()) return;

        // Format: whitelist <sub> [player]
        final String[] parts = raw.split("\\s+", 3);
        final String cmd = parts[0].toLowerCase(Locale.ROOT);
        if (!cmd.equals("whitelist")) return;

        // Permission gate (console allowed)
        if (actorPlayer != null && !hasAny(actorPlayer,
                "minecraft.command.whitelist", "bukkit.command.whitelist", "essentials.whitelist")) {
            return;
        }

        final String sub = (parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "");

        switch (sub) {
            case "on":
            case "enable":
            case "true":
                if (!plugin.getConfig().getBoolean("log.moderation.whitelist_toggle", true)) return;
                toggleWhitelist(actorPlayer, true);
                break;

            case "off":
            case "disable":
            case "false":
                if (!plugin.getConfig().getBoolean("log.moderation.whitelist_toggle", true)) return;
                toggleWhitelist(actorPlayer, false);
                break;

            case "add":
                if (!plugin.getConfig().getBoolean("log.moderation.whitelist_edit", true)) return;
                if (parts.length < 3) return;
                editWhitelist(actorPlayer, parts[2], true);
                break;

            case "remove":
            case "rm":
                if (!plugin.getConfig().getBoolean("log.moderation.whitelist_edit", true)) return;
                if (parts.length < 3) return;
                editWhitelist(actorPlayer, parts[2], false);
                break;

            // Noise-y subcommands we intentionally skip
            case "reload":
            case "list":
            default:
                break;
        }
    }

    /* ---------- toggle (on/off) ---------- */

    private void toggleWhitelist(Player actorPlayer, boolean desired) {
        final boolean was = Bukkit.hasWhitelist();

        // Verify success on next tick
        Bukkit.getScheduler().runTask(plugin, () -> {
            final boolean now = Bukkit.hasWhitelist();
            if (was != desired && now == desired) {
                final String moderatorName = (actorPlayer != null)
                        ? Names.display(actorPlayer, plugin)
                        : "CONSOLE";

                final List<Log.Field> fields = new ArrayList<>();
                fields.add(new Log.Field("Whitelist Status:", desired ? "Enabled" : "Disabled"));
                fields.add(new Log.Field("Changed by:", moderatorName));

                Log.eventFieldsWithThumb(
                        "whitelist_toggle",                  // color key
                        "Whitelist Toggled",
                        null,                                 // author -> default
                        fields,
                        null                                  // no player thumbnail
                );
            }
        });
    }

    /* ---------- edit (add/remove) ---------- */

    private void editWhitelist(Player actorPlayer, String targetName, boolean adding) {
        if (targetName == null || targetName.isBlank()) return;

        final OfflinePlayer off = Bukkit.getOfflinePlayer(targetName);
        final boolean was = off.isWhitelisted();

        // Verify success next tick
        Bukkit.getScheduler().runTask(plugin, () -> {
            final boolean now = off.isWhitelisted();
            if ((adding && !was && now) || (!adding && was && !now)) {
                final String moderatorName = (actorPlayer != null)
                        ? Names.display(actorPlayer, plugin)
                        : "CONSOLE";

                String thumb = null;
                UUID uuid = off.getUniqueId();
                if (uuid != null) thumb = Log.playerAvatarUrl(uuid);

                final List<Log.Field> fields = new ArrayList<>();
                if (adding) {
                    fields.add(new Log.Field("Player Name:", targetName));
                    fields.add(new Log.Field("Whitelisted by:", moderatorName));

                    Log.eventFieldsWithThumb(
                            "whitelist",                      // color key
                            "Player Whitelisted",
                            null,
                            fields,
                            thumb
                    );
                } else {
                    fields.add(new Log.Field("Player Name:", targetName));
                    fields.add(new Log.Field("Removed by:", moderatorName));

                    Log.eventFieldsWithThumb(
                            "whitelist",                      // reuse same color for edits
                            "Player Removed from Whitelist",
                            null,
                            fields,
                            thumb
                    );
                }
            }
        });
    }

    /* ---------- helpers ---------- */

    private static boolean hasAny(Player p, String... nodes) {
        if (p.isOp()) return true;
        for (String n : nodes) if (p.hasPermission(n)) return true;
        return false;
    }
}
