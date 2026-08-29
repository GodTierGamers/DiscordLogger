package com.discordlogger.config;

import com.discordlogger.lang.Lang;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Reports a config whose schema is NEWER than this build understands.
 *
 * <p>An older config is not this class's problem — {@link ConfigMigrator} upgrades
 * that automatically and silently, which is the whole point of the schema trailer.
 * The reverse case cannot be fixed automatically: the plugin has no idea what the
 * newer schema's keys mean, and rewriting the file against its own older default
 * would delete them. So the file is left alone and the situation is reported.
 *
 * <p>This happens when a server is rolled back to an older plugin JAR, or when a
 * config is copied across from an install running a newer version. Both are easy
 * to do by accident and produce no obvious symptom otherwise: the plugin keeps
 * running, quietly ignoring every key it does not recognise.
 *
 * <p>Console gets it on every start, since that is the record an admin reads when
 * something looks wrong. Ops get it in chat on join, because whoever is actually
 * playing is the one who notices settings not taking effect.
 */
public final class ConfigVersionNotice implements Listener {

    private final int installed;
    private final int shipped;

    private ConfigVersionNotice(int installed, int shipped) {
        this.installed = installed;
        this.shipped = shipped;
    }

    /**
     * Reports the outcome of a migration check, and registers the join listener
     * only in the one case that needs a human to act.
     */
    public static void report(JavaPlugin plugin, ConfigMigrator.Result result) {
        switch (result.status()) {
            case UPGRADED:
                plugin.getLogger().info(
                        "config.yml was upgraded from schema v" + result.installed()
                                + " to v" + result.shipped() + ". Your settings were carried over; "
                                + "the previous file is saved as config.old.yml.");
                break;

            case AHEAD: {
                ConfigVersionNotice notice =
                        new ConfigVersionNotice(result.installed(), result.shipped());
                notice.warnConsole(plugin);
                plugin.getServer().getPluginManager().registerEvents(notice, plugin);
                break;
            }

            case UNKNOWN:
                plugin.getLogger().warning(
                        "Could not determine the config schema version. If config.yml has been "
                                + "edited heavily, check that its last line still reads "
                                + "\"# CONFIG VERSION V<number>, SHIPPED WITH v<plugin version>\".");
                break;

            // FRESH_INSTALL and UP_TO_DATE are the normal paths and say nothing.
            default:
                break;
        }
    }

    private void warnConsole(JavaPlugin plugin) {
        final String bar = "============================================================";
        plugin.getLogger().warning(bar);
        plugin.getLogger().warning("Your config.yml is NEWER than this build of DiscordLogger.");
        plugin.getLogger().warning("");
        plugin.getLogger().warning("  config.yml schema : v" + installed);
        plugin.getLogger().warning("  this build expects: v" + shipped);
        plugin.getLogger().warning("");
        plugin.getLogger().warning("The file has been left untouched — rewriting it against an");
        plugin.getLogger().warning("older schema would delete settings this build cannot read.");
        plugin.getLogger().warning("Options that only exist in v" + installed + " are being ignored.");
        plugin.getLogger().warning("");
        plugin.getLogger().warning("Either update the plugin, or run /discordlogger regen to");
        plugin.getLogger().warning("start again from this build's config (a backup is kept).");
        plugin.getLogger().warning(bar);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!p.isOp()) return;
        p.sendMessage(Lang.chat("chat.config-ahead", "installed", installed, "shipped", shipped));
        p.sendMessage(Lang.chat("chat.config-ahead-fix"));
    }
}
