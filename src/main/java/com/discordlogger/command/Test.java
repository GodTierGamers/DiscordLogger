package com.discordlogger.command;

import com.discordlogger.log.Log;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /discordlogger test [event]} — send a real message and see where it lands.
 *
 * <p>Answers the two questions the plugin could not previously answer without waiting
 * for something to happen: does the webhook work, and did my per-event routing and
 * colour actually take effect. Tuning either used to mean dying, banning someone, or
 * rebuilding the config on the website to preview it.
 *
 * <p>It sends through the ordinary path — same queue, same routing, same colours — so
 * a passing test is evidence about the real thing rather than about a test harness.
 */
public final class Test implements Subcommand {

    private final JavaPlugin plugin;

    public Test(JavaPlugin plugin) { this.plugin = plugin; }

    @Override public String name() { return "test"; }
    @Override public String description() { return "Send a test message to a category's webhook"; }
    @Override public String permission() { return "discordlogger.test"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!Log.isReady()) {
            sender.sendMessage(ChatColor.RED + "No valid webhook is configured, so there is "
                    + "nowhere to send a test. Set one with /discordlogger webhook <url>.");
            return true;
        }

        final String category = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "server";
        final String who = sender.getName();

        Log.event(category, "Test message from " + Log.mdEscape(who)
                + " — if you can read this, `" + Log.mdEscape(category) + "` is working.");

        sender.sendMessage(ChatColor.GREEN + "Sent a test to " + ChatColor.WHITE + category
                + ChatColor.GREEN + ". It uses that category's own webhook and colour, so "
                + "where it appears tells you whether routing is set up as you intended.");
        return true;
    }

    /**
     * Every category the config defines, so the routing being tested is discoverable.
     *
     * <p>Read from the config rather than a hard-coded list: {@code log.custom.*} is
     * named by the admin, and a list that could not offer their own rules would be
     * wrong for exactly the events they are most likely to want to check.
     */
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length > 1) return List.of();
        final List<String> out = new ArrayList<>();
        final ConfigurationSection log = plugin.getConfig().getConfigurationSection("log");
        if (log != null) {
            for (String group : log.getKeys(false)) {
                final ConfigurationSection sec = log.getConfigurationSection(group);
                if (sec == null) continue;
                for (String event : sec.getKeys(false)) out.add(group + "_" + event);
            }
        }
        final String prefix = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
        out.removeIf(c -> !c.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
