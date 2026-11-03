package com.discordlogger;

import com.discordlogger.alert.OpAlert;
import com.discordlogger.command.Status;
import com.discordlogger.command.Test;
import com.discordlogger.command.Doctor;
import com.discordlogger.command.Lookup;
import com.discordlogger.command.Commands;
import com.discordlogger.command.Regen;
import com.discordlogger.command.Reload;
import com.discordlogger.command.Webhook;
import com.discordlogger.config.ConfigMigrator;
import com.discordlogger.config.ConfigVersionNotice;
import com.discordlogger.event.EventRegistry;
import com.discordlogger.explosion.ExplosionLog;
import com.discordlogger.custom.CustomLogs;
import com.discordlogger.filter.Filters;
import com.discordlogger.util.Placeholders;
import com.discordlogger.lang.Lang;
import com.discordlogger.log.Log;
import com.discordlogger.metrics.PluginMetrics;
import com.discordlogger.update.BuildInfo;
import com.discordlogger.update.NightlyNotice;
import com.discordlogger.update.UpdateChecker;
import com.discordlogger.util.Chat;
import com.discordlogger.util.Compat;
import com.discordlogger.webhook.WebhookQueue;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class DiscordLogger extends JavaPlugin {

    private EventRegistry events;

    @Override
    public void onEnable() {
        BuildInfo.load(this);
        // Before anything can fail, so an early failure still has somewhere to go.
        OpAlert.init(this);

        // Adventure is bundled now rather than borrowed from Paper, so it has to be
        // started before anything can send a chat line.
        Chat.init(this);

        saveDefaultConfig();
        ConfigMigrator.Result configState = ConfigMigrator.migrateIfVersionChanged(
                this, "config.yml", new File(getDataFolder(), "config.yml"));
        reloadConfig();

        // Reported after reloadConfig so the AHEAD warning describes the config
        // actually in effect. Registers a join listener only in that one case.
        ConfigVersionNotice.report(this, configState);

        new NightlyNotice(this).activate(this);

        // Apply config (no hard-disable on missing webhook). A missing webhook is
        // reported by the config check below, which says the same thing once instead
        // of three times and says what to do about it.
        applyRuntimeConfig();

        // Start the webhook sender before any listener can produce a message.
        WebhookQueue.start(this);

        // Same reasoning for the explosion log: a listener must never find it absent,
        // and nothing it does happens on the main thread.
        ExplosionLog.start(this);

        // Register events/commands regardless, so reload works
        events = new EventRegistry(this);
        events.registerAll();

        if (getCommand("discordlogger") != null) {
            Commands router = new Commands(new Reload(this), new Webhook(this),
                    new Regen(this), new Status(this), new Test(this), new Doctor(this),
                    new Lookup(this));
            getCommand("discordlogger").setExecutor(router);
            getCommand("discordlogger").setTabCompleter(router);
            // Strips Bukkit's plugin:command duplicates from tab-completion, and
            // hides the command from players who hold none of its permissions.
            // PlayerCommandSendEvent arrived in 1.13. Older servers simply keep the
            // plugin:command duplicates in tab-completion, which is cosmetic.
            final org.bukkit.event.Listener visibility = Compat.listenerIfPresent(
                    Compat.COMMAND_SEND_EVENT, Compat.COMMAND_SEND_LISTENER,
                    new Class<?>[] { JavaPlugin.class, Commands.class }, this, router);
            if (visibility != null) {
                getServer().getPluginManager().registerEvents(visibility, this);
            }
        }

        // Say out loud what is wrong with the config. Every check is valid YAML
        // holding a valid value, so nothing else finds them -- and they have been
        // findable by /discordlogger doctor since it shipped, which across 80
        // reporting servers was run by none of them. Printed after the command is
        // registered, so the pointer to re-run it is true by the time it is read.
        reportConfigProblems();

        // Anonymous usage metrics (bstats.org). Opt out via plugins/bStats/config.yml.
        // Started after the config is loaded so its charts can read live values.
        PluginMetrics.start(this);

        // Async update check (console + Discord notice if newer version available)
        UpdateChecker.checkAsync(this);

        // Confirms the configured webhooks still exist, rather than finding out from
        // the first event that 404s. Async: it must not hold up the server starting.
        Log.validateWebhooksAsync();

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
        // After the queue: the stop message matters more than the last explosion row,
        // and this drain is bounded separately so one cannot eat the other's budget.
        ExplosionLog.shutdown();
        Chat.close();
        getLogger().info("Disabled.");
    }

    /**
     * Prints {@link Doctor}'s findings, or nothing at all when there are none.
     *
     * <p>Silent on a clean config: a line on every boot saying all is well is a line
     * people learn to skip, which is how the warnings beside it get skipped too.
     */
    private void reportConfigProblems() {
        final java.util.List<String> findings;
        try {
            findings = Doctor.diagnose(this);
        } catch (RuntimeException unreadable) {
            // A diagnostic must never be the reason a server does not start.
            getLogger().warning("Could not check the config for contradictions: " + unreadable);
            return;
        }
        if (findings.isEmpty()) return;

        getLogger().warning(Doctor.headline(findings.size()) + ":");
        for (String finding : findings) getLogger().warning("  - " + finding);
        getLogger().warning("Run /discordlogger doctor to check again after editing config.yml.");
    }

    public boolean applyRuntimeConfig() {
        // Before Log.init, so a reload cannot briefly log something the new config
        // says to filter.
        Filters.reload(this);
        // Same point as Filters, and for the same reason: a reload must not be able
        // to log something under rules the new config has already changed.
        CustomLogs.reload(this);
        // Reads format.placeholders and resolves PlaceholderAPI once, so a reload
        // picks up both the toggle and a plugin installed since startup.
        Placeholders.reload(this);
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