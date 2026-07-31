package com.discordlogger.command;

import com.discordlogger.DiscordLogger;
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
            sender.sendMessage(ChatColor.RED + "Your config.yml (schema v" + configState.installed()
                    + ") is newer than this build (v" + configState.shipped()
                    + "). Keys it doesn't recognise are ignored.");
            sender.sendMessage(ChatColor.RED + "Update the plugin, or run "
                    + ChatColor.WHITE + "/discordlogger regen" + ChatColor.RED + " to start fresh.");
        } else if (configState.migrated()) {
            sender.sendMessage(ChatColor.GREEN + "config.yml upgraded from schema v"
                    + configState.installed() + " to v" + configState.shipped()
                    + " (previous file saved as config.old.yml).");
        }

        if (ok) {
            long ms = System.currentTimeMillis() - start;
            sender.sendMessage(ChatColor.GREEN + "DiscordLogger configuration reloaded (" + ms + " ms).");
        } else {
            sender.sendMessage(ChatColor.RED + "Config reloaded, but webhook.url is missing/invalid.");
            sender.sendMessage(ChatColor.RED + "Set a valid Discord webhook URL in config.yml and try again.");
        }
        return true;
    }


    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        // no extra args for reload
        return Collections.emptyList();
    }
}
