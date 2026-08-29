package com.discordlogger.command;

import com.discordlogger.DiscordLogger;
import com.discordlogger.lang.Lang;
import com.discordlogger.util.Chat;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import com.discordlogger.config.ConfigMigrator;

import java.util.Collections;
import java.util.List;

public final class Reload implements Subcommand {
    private final DiscordLogger plugin;

    public Reload(DiscordLogger plugin) {
        this.plugin = plugin;
    }

    @Override public String name() { return "reload"; }
    @Override public String description() { return "Reloads the DiscordLogger configuration."; }
    @Override public String permission() { return "discordlogger.reload"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {

        long start = System.currentTimeMillis();

        ConfigMigrator.Result configState = ConfigMigrator.migrateIfVersionChanged(
                plugin, "config.yml", new java.io.File(plugin.getDataFolder(), "config.yml"));
        plugin.reloadConfig();
        boolean ok = plugin.applyRuntimeConfig();

        // A reload is exactly when someone has just swapped a config in, so the
        // same schema check that runs at startup has to run here too.
        if (configState.status() == ConfigMigrator.Status.AHEAD) {
            Chat.send(sender, Lang.chat("chat.reload-config-ahead",
                    "installed", configState.installed(), "shipped", configState.shipped()));
            Chat.send(sender, Lang.chat("chat.reload-config-ahead-fix"));
        } else if (configState.migrated()) {
            Chat.send(sender, Lang.chat("chat.reload-config-upgraded",
                    "from", configState.installed(), "to", configState.shipped()));
        }

        if (ok) {
            long ms = System.currentTimeMillis() - start;
            Chat.send(sender, Lang.chat("chat.reload-ok", "ms", ms));
        } else {
            Chat.send(sender, Lang.chat("chat.reload-no-webhook"));
            Chat.send(sender, Lang.chat("chat.reload-no-webhook-hint"));
        }
        return true;
    }


    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        // no extra args for reload
        return Collections.emptyList();
    }
}
