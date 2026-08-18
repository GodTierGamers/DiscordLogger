package com.discordlogger.command;

import com.discordlogger.custom.CustomLogs;
import com.discordlogger.log.Log;
import com.discordlogger.update.BuildInfo;
import com.discordlogger.webhook.WebhookQueue;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;

/**
 * {@code /discordlogger status} — is it working, and if not, where is it stuck.
 *
 * <p>Everything here was previously unobservable from in game or console. The gap was
 * not theoretical: a webhook deleted in Discord produced 345 discarded messages across
 * three and a half hours in the live metrics, and the only signal was one console line
 * nobody was watching.
 *
 * <p><b>No URL is ever printed.</b> A status readout is exactly what gets pasted into
 * a support thread, and a webhook URL is a bearer credential — anyone holding it can
 * post as the server. Destinations are named by their webhook id, which distinguishes
 * them and grants nothing.
 */
public final class Status implements Subcommand {

    private final JavaPlugin plugin;

    public Status(JavaPlugin plugin) { this.plugin = plugin; }

    @Override public String name() { return "status"; }
    @Override public String description() { return "Show queue, webhook and build health"; }
    @Override public String permission() { return "discordlogger.status"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        line(sender, ChatColor.AQUA + "DiscordLogger status");

        line(sender, "  Build: " + ChatColor.WHITE + BuildInfo.version()
                + ChatColor.GRAY + " (" + BuildInfo.channel() + ")");

        final boolean ready = Log.isReady();
        line(sender, "  Discord: " + (ready
                ? ChatColor.GREEN + "connected"
                : ChatColor.RED + "not configured" + ChatColor.GRAY
                  + " — console only until webhook.url is set"));

        line(sender, "  Queue: " + (WebhookQueue.isRunning()
                ? ChatColor.GREEN + "running" : ChatColor.RED + "stopped"));

        final List<WebhookQueue.Health> health = WebhookQueue.health();
        if (health.isEmpty()) {
            // No destination exists until something has been sent to it, so this is the
            // normal state on a quiet server -- worth saying, or it reads as a fault.
            line(sender, "  Destinations: " + ChatColor.GRAY + "none active yet");
        } else {
            line(sender, "  Destinations: " + ChatColor.WHITE + health.size());
            for (WebhookQueue.Health h : health) {
                final String queued = h.queued() == 0
                        ? ChatColor.GREEN + "empty"
                        : (h.queued() > h.capacity() / 2 ? ChatColor.RED : ChatColor.YELLOW)
                          + String.valueOf(h.queued()) + "/" + h.capacity();
                final String wait = h.waitMs() > 0
                        ? ChatColor.YELLOW + "  rate-limited for " + (h.waitMs() / 1000) + "s"
                        : "";
                line(sender, "    " + ChatColor.GRAY + h.id() + ": " + queued + wait);
            }
        }

        line(sender, "  Custom log rules: " + ChatColor.WHITE + CustomLogs.rules().size());
        // Asked of Bukkit rather than of this plugin's own expansion helper: a status
        // readout should say what is on the server, and that stays true whether or not
        // placeholder support itself is switched on.
        line(sender, "  PlaceholderAPI: "
                + (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null
                   ? ChatColor.GREEN + "installed" : ChatColor.GRAY + "not installed"));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    private static void line(CommandSender to, String msg) {
        to.sendMessage(ChatColor.GRAY + msg);
    }
}
