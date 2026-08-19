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
 *
 * <h2>Composing an embed by hand</h2>
 *
 * <p>With quoted {@code key="value"} parts it will render any embed asked of it, which
 * is how the listing galleries get filled. Screenshots of a logging plugin have to show
 * deaths, bans and joins, and the alternative was staging those on a live server or
 * publishing screenshots containing real players' names and skins. Invented players
 * avoid both. See {@link MockEmbed} for the syntax.
 */
public final class Test implements Subcommand {

    private final JavaPlugin plugin;

    public Test(JavaPlugin plugin) { this.plugin = plugin; }

    @Override public String name() { return "test"; }
    @Override public String description() { return "Send a test or hand-made embed to a category"; }
    @Override public String permission() { return "discordlogger.test"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!Log.isReady()) {
            sender.sendMessage(ChatColor.RED + "No valid webhook is configured, so there is "
                    + "nowhere to send a test. Set one with /discordlogger webhook <url>.");
            return true;
        }

        final MockEmbed mock = MockEmbed.parse(String.join(" ", args), sender.getName());

        if (mock.isPlain() && mock.title().isEmpty()) {
            // No detail asked for: the original one-line check that the webhook works.
            Log.event(mock.category(), "Test message from " + Log.mdEscape(sender.getName())
                    + " — if you can read this, `" + Log.mdEscape(mock.category())
                    + "` is working.");
        } else {
            Log.eventFieldsWithThumb(
                    mock.category(),
                    mock.title().isEmpty() ? "Test" : mock.title(),
                    mock.description(),
                    mock.author(),
                    mock.fields(),
                    mock.thumbnail());
        }

        sender.sendMessage(ChatColor.GREEN + "Sent a test to " + ChatColor.WHITE
                + mock.category() + ChatColor.GREEN + ". It uses that category's own webhook "
                + "and colour, so where it appears tells you whether routing is set up as "
                + "you intended.");
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
        // Past the category the syntax is free-form, so suggest the shape once rather
        // than nothing at all -- an undiscoverable feature is one nobody uses.
        if (args.length == 2 && args[1].isEmpty()) {
            return List.of("player=\"Steve\"", "title=\"Player Death\"",
                    "desc=\"...\"", "field=\"Cause:Fall\"");
        }
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
