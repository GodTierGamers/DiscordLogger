package com.discordlogger;

import com.discordlogger.command.Commands;
import com.discordlogger.command.Reload;
import com.discordlogger.event.EventRegistry;
import com.discordlogger.log.Log;
import org.bukkit.plugin.java.JavaPlugin;

public final class DiscordLogger extends JavaPlugin {

    private EventRegistry events;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!applyRuntimeConfig()) {
            getLogger().severe("No valid Discord webhook URL set inside of config.yml. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        events = new EventRegistry(this);
        events.registerAll();

        // Command router with subcommands (add more later)
        if (getCommand("discordlogger") != null) {
            Commands router = new Commands(
                    new Reload(this)
                    // , new SomeOtherSubcommand(this)
            );
            getCommand("discordlogger").setExecutor(router);
            getCommand("discordlogger").setTabCompleter(router);
        } else {
            getLogger().warning("Command 'discordlogger' not found in plugin.yml.");
        }

        events.fireServerStart();
        getLogger().info("Core loaded.");
    }

    @Override
    public void onDisable() {
        if (events != null) events.fireServerStop();
        getLogger().info("Disabled.");
    }

    /** Re-read config and (if valid) re-initialize logging settings. */
    public boolean applyRuntimeConfig() {
        final String url = getConfig().getString("webhook.url", "");
        if (!isLikelyDiscordWebhook(url)) {
            getLogger().severe("Invalid or missing webhook.url in config.yml.");
            return false;
        }
        final String timePattern = getConfig().getString("format.time", "[HH:mm:ss dd:MM:yyyy]");
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
