package com.discordlogger.command;

import com.discordlogger.custom.CustomLogs;
import com.discordlogger.log.Log;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code /discordlogger doctor} — the contradictions a schema cannot catch.
 *
 * <p>Every problem here is valid YAML holding a valid value, which is precisely why
 * nothing else finds them. A config with every event disabled parses perfectly and
 * logs nothing; an allow-list beside a deny-list is two rules that read as if they
 * cooperate and do not. These are the states where the plugin looks broken and is
 * doing exactly what it was told.
 *
 * <p>Checks are pure and split out so each can be tested without a server.
 */
public final class Doctor implements Subcommand {

    private final JavaPlugin plugin;

    public Doctor(JavaPlugin plugin) { this.plugin = plugin; }

    @Override public String name() { return "doctor"; }
    @Override public String description() { return "Check the config for contradictions"; }
    @Override public String permission() { return "discordlogger.doctor"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        final List<String> findings = diagnose(plugin);
        if (findings.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "No problems found.");
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "Found " + findings.size()
                + (findings.size() == 1 ? " thing" : " things") + " worth checking:");
        for (String f : findings) sender.sendMessage(ChatColor.GRAY + "  • " + f);
        return true;
    }

    /**
     * Everything wrong with the current config, in plain words.
     *
     * <p>Each finding names the key AND what it will actually do, because "only_log_commands
     * is set" tells an admin nothing they did not already know. The useful half is that
     * their deny-list is now dead.
     */
    public static List<String> diagnose(JavaPlugin plugin) {
        final List<String> out = new ArrayList<>();

        if (!Log.isReady()) {
            out.add("webhook.url is not set or is not a valid Discord webhook, so nothing "
                    + "reaches Discord. Everything still logs to console.");
        }

        final List<String> only = plugin.getConfig().getStringList("filters.only_log_commands");
        final List<String> ignored = plugin.getConfig().getStringList("filters.ignored_commands");
        if (!only.isEmpty() && !ignored.isEmpty()) {
            out.add("filters.only_log_commands is set, so it wins outright and the "
                    + ignored.size() + " entries in ignored_commands only matter inside it. "
                    + "If you meant to block just a few commands, clear only_log_commands.");
        }

        // The security default, and the one worth naming individually.
        for (String risky : new String[]{"login", "register", "msg"}) {
            if (!ignored.contains(risky) && only.isEmpty()) {
                out.add("filters.ignored_commands no longer contains \"" + risky + "\". Command "
                        + "logging posts the line as typed, so that command's arguments will "
                        + "be published to Discord.");
            }
        }

        final int enabled = countEnabled(plugin);
        if (enabled == 0) {
            out.add("Every event under log.* is disabled, so the plugin has nothing to send. "
                    + "That is a valid config, just probably not the intended one.");
        }

        if (plugin.getConfig().getConfigurationSection("log.custom") != null
                && plugin.getConfig().getConfigurationSection("log.custom").getKeys(false).size()
                   > CustomLogs.rules().size()) {
            out.add("Some log.custom rules were skipped at load — check the console on "
                    + "startup. A rule with no 'match', or one whose command is in "
                    + "filters.ignored_commands, never fires.");
        }
        return out;
    }

    /** How many events are switched on, across every group including custom rules. */
    static int countEnabled(JavaPlugin plugin) {
        final ConfigurationSection log = plugin.getConfig().getConfigurationSection("log");
        if (log == null) return 0;
        int n = 0;
        for (String group : log.getKeys(false)) {
            final ConfigurationSection sec = log.getConfigurationSection(group);
            if (sec == null) continue;
            for (String event : sec.getKeys(false)) {
                final ConfigurationSection ev = sec.getConfigurationSection(event);
                if (ev != null && ev.getBoolean("enabled", true)) n++;
            }
        }
        return n;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
