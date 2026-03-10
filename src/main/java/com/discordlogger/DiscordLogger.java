package com.discordlogger;

import com.discordlogger.command.Commands;
import com.discordlogger.command.Reload;
import com.discordlogger.config.ConfigMigrator;
import com.discordlogger.event.EventRegistry;
import com.discordlogger.log.Log;
import com.discordlogger.update.UpdateChecker;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class DiscordLogger extends JavaPlugin {

    private EventRegistry events;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ConfigMigrator.migrateIfVersionChanged(this, "config.yml", new File(getDataFolder(), "config.yml"));
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
            Commands router = new Commands(new Reload(this));
            getCommand("discordlogger").setExecutor(router);
            getCommand("discordlogger").setTabCompleter(router);
        }

        // Async update check (console + Discord notice if newer version available)
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

        // Always initialize Log so degraded mode works even if the webhook URL is invalid
        Log.init(this, url, timePattern);

        if (!Log.isReady()) {
            getLogger().severe("Invalid or missing webhook.url in config.yml.");
            return false;
        }

        return true;
    }
}