package com.discordlogger;

import com.discordlogger.command.Commands;
import com.discordlogger.command.Regen;
import com.discordlogger.command.Reload;
import com.discordlogger.command.Webhook;
import com.discordlogger.config.ConfigMigrator;
import com.discordlogger.config.ConfigVersionNotice;
import com.discordlogger.event.EventRegistry;
import com.discordlogger.filter.Filters;
import com.discordlogger.lang.Lang;
import com.discordlogger.log.Log;
import com.discordlogger.metrics.PluginMetrics;
import com.discordlogger.update.BuildInfo;
import com.discordlogger.update.NightlyNotice;
import com.discordlogger.update.UpdateChecker;
import com.discordlogger.util.Platform;
import com.discordlogger.webhook.WebhookQueue;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class DiscordLogger extends JavaPlugin {

    private EventRegistry events;

    @Override
    public void onEnable() {
        BuildInfo.load(this);

        // Bail out before touching the data folder — no point writing a config
        // to a server that can't run the plugin anyway.
        final String missing = Platform.missingRequirement();
        if (missing != null) {
            reportUnsupportedPlatform(missing);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        ConfigMigrator.Result configState = ConfigMigrator.migrateIfVersionChanged(
                this, "config.yml", new File(getDataFolder(), "config.yml"));
        reloadConfig();

        // Reported after reloadConfig so the AHEAD warning describes the config
        // actually in effect. Registers a join listener only in that one case.
        ConfigVersionNotice.report(this, configState);

        new NightlyNotice(this).activate(this);

        // Apply config (no hard-disable on missing webhook)
        boolean ok = applyRuntimeConfig();
        if (!ok) {
            getLogger().warning("No valid Discord webhook URL in config.yml. Please add the webhook URL.");
            getLogger().warning("Set it with /discordlogger webhook <url>, or edit config.yml and run /discordlogger reload.");
        }

        // Start the webhook sender before any listener can produce a message.
        WebhookQueue.start(this);

        // Register events/commands regardless, so reload works
        events = new EventRegistry(this);
        events.registerAll();

        if (getCommand("discordlogger") != null) {
            Commands router = new Commands(new Reload(this), new Webhook(this), new Regen(this));
            getCommand("discordlogger").setExecutor(router);
            getCommand("discordlogger").setTabCompleter(router);
        }

        // Anonymous usage metrics (bstats.org). Opt out via plugins/bStats/config.yml.
        // Started after the config is loaded so its charts can read live values.
        PluginMetrics.start(this);

        // Async update check (console + Discord notice if newer version available)
        UpdateChecker.checkAsync(this);

        // Server start log will go to console; to Discord only if webhook is valid
        events.fireServerStart();
        getLogger().info("Core loaded.");
    }

    @Override
    public void onDisable() {
        // Queue the stop message FIRST, then drain — shutdown() flushes what's
        // pending, so anything queued after it would be lost.
        if (events != null) events.fireServerStop();
        WebhookQueue.shutdown();
        getLogger().info("Disabled.");
    }

    /** Explain, in plain terms, why the plugin can't start on this server. */
    private void reportUnsupportedPlatform(String missingClass) {
        final String bar = "============================================================";
        getLogger().severe(bar);
        getLogger().severe("DiscordLogger requires Paper, or a Paper fork such as Purpur.");
        getLogger().severe("This server does not provide the Paper API, so the plugin");
        getLogger().severe("cannot start.");
        getLogger().severe("");
        getLogger().severe("Missing: " + missingClass);
        getLogger().severe("");
        getLogger().severe("Download Paper from https://papermc.io/downloads — it is a");
        getLogger().severe("drop-in replacement, so your worlds, plugins and configs");
        getLogger().severe("keep working as they are.");
        getLogger().severe(bar);
    }

    public boolean applyRuntimeConfig() {
        // Before Log.init, so a reload cannot briefly log something the new config
        // says to filter.
        Filters.reload(this);
        Lang.reload(this);

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