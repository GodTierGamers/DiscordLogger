package com.discordlogger.command;

import com.discordlogger.DiscordLogger;
import com.discordlogger.config.ConfigMigrator;
import com.discordlogger.log.Log;
import com.discordlogger.lang.Lang;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Sets {@code webhook.url} and reloads, so a server can be wired up without
 * opening a file at all — the step most people get stuck on.
 *
 * <p>The URL is a bearer credential: anyone holding it can post to that channel.
 * Two consequences are handled here rather than left to the user:
 *
 * <ul>
 *   <li>It is never echoed back. Confirmation names the channel id only, so the
 *       secret does not end up in a screenshot or a stream.</li>
 *   <li>Command logging redacts it ({@link Log#redactWebhooks}), so running this
 *       does not publish the new URL to the channel the plugin is currently
 *       posting to — which, when changing webhooks, is the old one.</li>
 * </ul>
 *
 * <p>The file is edited surgically rather than through Bukkit's config writer,
 * which would strip every comment and the schema trailer. See
 * {@link ConfigMigrator#setScalar}.
 */
public final class Webhook implements Subcommand {

    private final DiscordLogger plugin;

    public Webhook(DiscordLogger plugin) {
        this.plugin = plugin;
    }

    @Override public String name() { return "webhook"; }
    @Override public String description() { return "Sets the Discord webhook URL and reloads."; }
    @Override public String permission() { return "discordlogger.webhook"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Lang.chat("chat.webhook-usage"));
            sender.sendMessage(Lang.chat("chat.webhook-where"));
            if (sender instanceof Player) {
                sender.sendMessage(Lang.chat("chat.webhook-private"));
            }
            return true;
        }

        final String url = args[0].trim();

        if (!Log.isValidWebhookUrl(url)) {
            sender.sendMessage(Lang.chat("chat.webhook-invalid"));
            sender.sendMessage(Lang.chat("chat.webhook-expected"));
            return true;
        }

        final File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!ConfigMigrator.setScalar(configFile, "webhook.url", url)) {
            sender.sendMessage(Lang.chat("chat.webhook-write-failed"));
            return true;
        }

        plugin.reloadConfig();
        final boolean ok = plugin.applyRuntimeConfig();

        if (ok) {
            sender.sendMessage(Lang.chat("chat.webhook-set", "channel", channelId(url)));
        } else {
            // setScalar succeeded and the URL passed the format check, so this means
            // the reload rejected it for some other reason -- worth saying plainly
            // rather than reporting success.
            sender.sendMessage(Lang.chat("chat.webhook-rejected"));
        }

        // Console record without the token, so the server log is safe to share.
        plugin.getLogger().info("webhook.url updated by " + sender.getName()
                + " (channel " + channelId(url) + ")");
        return true;
    }

    /** The id half of the URL — enough to confirm the right channel, useless as a credential. */
    private static String channelId(String url) {
        String[] parts = url.split("/");
        return parts.length >= 2 ? parts[parts.length - 2] : "unknown";
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        // Never suggest anything: a completion list is exactly where a previously
        // typed webhook URL would resurface in front of someone else.
        return Collections.emptyList();
    }
}
