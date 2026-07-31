package com.discordlogger.command;

import com.discordlogger.DiscordLogger;
import com.discordlogger.util.ClientPlatform;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reports what the plugin can actually see about a player's platform.
 *
 * <p>Bedrock detection has several ways to come back "no" that look identical from
 * the outside: Floodgate not installed, installed but not visible to this plugin's
 * classloader, visible but not yet initialised, or a genuinely undetectable player
 * (Geyser standalone, or a Bedrock account linked to a Java one). Without this,
 * distinguishing them means guessing.
 */
public final class PlatformInfo implements Subcommand {

    private final DiscordLogger plugin;

    public PlatformInfo(DiscordLogger plugin) { this.plugin = plugin; }

    @Override public String name() { return "platform"; }
    @Override public String description() { return "Shows what the plugin detects about player platforms."; }
    @Override public String permission() { return "discordlogger.reload"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.AQUA + "DiscordLogger — Bedrock detection");

        // Asking the plugin manager needs no access to Floodgate's classes, so it
        // separates "not installed" from "installed but not reachable".
        final boolean floodgatePlugin = Bukkit.getPluginManager().getPlugin("floodgate") != null;
        final boolean geyserPlugin = Bukkit.getPluginManager().getPlugin("Geyser-Spigot") != null;

        line(sender, "Floodgate plugin installed", floodgatePlugin);
        line(sender, "Geyser plugin installed", geyserPlugin);
        line(sender, "Floodgate API class visible", ClientPlatform.floodgateClassVisible());
        line(sender, "Floodgate API usable", ClientPlatform.floodgateApiAvailable());

        if (floodgatePlugin && !ClientPlatform.floodgateClassVisible()) {
            sender.sendMessage(ChatColor.RED + "Floodgate is installed but its API is not visible "
                    + "to this plugin — check softdepend in plugin.yml.");
        }
        if (!floodgatePlugin && geyserPlugin) {
            sender.sendMessage(ChatColor.YELLOW + "Geyser without Floodgate: Bedrock players "
                    + "authenticate as ordinary Java accounts and cannot be identified by anything.");
        }

        final List<Player> targets = args.length > 0
                ? Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.getName().equalsIgnoreCase(args[0]))
                    .collect(Collectors.toList())
                : List.copyOf(Bukkit.getOnlinePlayers());

        if (targets.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + (args.length > 0
                    ? "No online player called " + args[0] + "."
                    : "No players online to inspect."));
            return true;
        }

        for (Player p : targets) {
            final UUID id = p.getUniqueId();
            final Boolean api = ClientPlatform.apiVerdict(id);
            sender.sendMessage(ChatColor.WHITE + p.getName() + ChatColor.GRAY + "  " + id);
            sender.sendMessage(ChatColor.GRAY + "  most-significant bits: "
                    + (id.getMostSignificantBits() == 0L
                        ? ChatColor.GREEN + "0 (Floodgate shape)"
                        : ChatColor.GRAY + "non-zero (ordinary Java UUID)"));
            sender.sendMessage(ChatColor.GRAY + "  Floodgate API says: "
                    + (api == null ? "unavailable" : (api ? ChatColor.GREEN + "Bedrock" : "not Bedrock")));
            sender.sendMessage(ChatColor.GRAY + "  verdict: "
                    + (ClientPlatform.isBedrock(id)
                        ? ChatColor.GREEN + "Bedrock — the join field would show"
                        : ChatColor.YELLOW + "not detected as Bedrock — no field"));
        }
        return true;
    }

    private static void line(CommandSender sender, String label, boolean value) {
        sender.sendMessage(ChatColor.GRAY + "  " + label + ": "
                + (value ? ChatColor.GREEN + "yes" : ChatColor.RED + "no"));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length != 1) return List.of();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
    }
}
