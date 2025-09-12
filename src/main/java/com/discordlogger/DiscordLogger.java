package com.discordlogger;

import com.discordlogger.event.EventRegistry;
import com.discordlogger.log.Log;
import org.bukkit.plugin.java.JavaPlugin;

public final class DiscordLogger extends JavaPlugin {

    private EventRegistry events;

    @Override
    public void onEnable() {
        // Ensure config.yml exists
        saveDefaultConfig();

        // Validate required webhook URL (hard fail if missing/invalid)
        final String url = getConfig().getString("webhook.url", "");
        if (url == null || url.isBlank() || !isLikelyDiscordWebhook(url)) {
            getLogger().severe("No valid Discord webhook URL set inside of config.yml. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Init logging core with the configured time format (fallback if invalid)
        Log.init(this, url, getConfig().getString("format.time", "[HH:mm:ss dd:MM:yyyy]"));

        // Register current event set (none yet, we’re doing server lifecycle first)
        events = new EventRegistry(this);
        events.registerAll();

        // Respect config toggle for server start
        events.fireServerStart();

        getLogger().info("Core loaded.");
    }

    @Override
    public void onDisable() {
        // Respect config toggle for server stop
        if (events != null) {
            events.fireServerStop();
        }
        getLogger().info("Disabled.");
    }

    /** Simple sanity check to avoid obvious misconfigurations. */
    private boolean isLikelyDiscordWebhook(String url) {
        return url.startsWith("https://discord.com/api/webhooks/")
                || url.startsWith("https://discordapp.com/api/webhooks/")
                || url.startsWith("https://ptb.discord.com/api/webhooks/")
                || url.startsWith("https://canary.discord.com/api/webhooks/");
    }
}
