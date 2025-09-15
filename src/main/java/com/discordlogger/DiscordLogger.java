package com.discordlogger;

import com.discordlogger.command.Commands;
import com.discordlogger.command.Reload;
import com.discordlogger.event.EventRegistry;
import com.discordlogger.log.Log;
import org.bukkit.plugin.java.JavaPlugin;
import com.discordlogger.config.ConfigMigrator;

import java.io.File;

// NEW: import the update checker
import com.discordlogger.update.UpdateChecker;

public final class DiscordLogger extends JavaPlugin {

    private EventRegistry events;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        com.discordlogger.config.ConfigMigrator.migrateIfVersionChanged(this, "config.yml", new java.io.File(getDataFolder(), "config.yml"));
        reloadConfig();

        // Apply config (no hard-disable on missing webhook)
        boolean ok = applyRuntimeConfig();
        if (!ok) {
            getLogger().warning("No valid Discord webhook URL in config.yml. Please add the webhook URL.");
            getLogger().warning("Set webhook.url and run /discordlogger reload to enable Discord posting.");
        }

        // Register events/commands regardless, so reload works
        events = new EventRegistry(this);
        events.registerAll();

        if (getCommand("discordlogger") != null) {
            com.discordlogger.command.Commands router = new com.discordlogger.command.Commands(new com.discordlogger.command.Reload(this));
            getCommand("discordlogger").setExecutor(router);
            getCommand("discordlogger").setTabCompleter(router);
        }

        // NEW: async update check (console + Discord notice if newer available)
        UpdateChecker.checkAsync(this);

        // Server start log will go to console; to Discord only if webhook is valid
        events.fireServerStart();
        getLogger().info("Core loaded.");
    }

    @Override
    public void onDisable() {
        if (events != null) events.fireServerStop();
        getLogger().info("Disabled.");
    }

    public boolean applyRuntimeConfig() {
        final String url = getConfig().getString("webhook.url", "");
        final String timePattern = getConfig().getString("format.time", "[HH:mm:ss dd:MM:yyyy]");

        // NEW: always initialize Log first so degraded mode works (even if URL invalid)
        Log.init(this, url, timePattern);

        if (!isLikelyDiscordWebhook(url)) {
            getLogger().severe("Invalid or missing webhook.url in config.yml.");
            return false;
        }

        // (kept from your original code) re-init is harmless when valid
        Log.init(this, url, timePattern);
        return true;
    }

    private boolean isLikelyDiscordWebhook(String url) {
        if (url == null || url.isBlank()) return false;
        return url.startsWith("https://discord.com/api/webhooks/")
                || url.startsWith("https://discordapp.com/api/webhooks/")
                || url.startsWith("https://ptb.discord.com/api/webhooks/")
                || url.startsWith("https://canary.discord.com/api/webhooks/");
    }
}
