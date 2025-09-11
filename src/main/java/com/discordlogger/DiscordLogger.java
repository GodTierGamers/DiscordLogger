package com.discordlogger;

import org.bukkit.plugin.java.JavaPlugin;

public final class DiscordLogger extends JavaPlugin {

    @Override
    public void onEnable() {
        // Called when the plugin is enabled
        getLogger().info("DiscordLogger enabled.");
    }

    @Override
    public void onDisable() {
        // Called when the plugin is disabled
        getLogger().info("DiscordLogger disabled.");
    }
}
