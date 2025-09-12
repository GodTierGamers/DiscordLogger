package com.discordlogger.command;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;
import java.util.stream.Collectors;

public final class Commands implements CommandExecutor, TabCompleter {
    private final Map<String, Subcommand> subs = new LinkedHashMap<>();

    public Commands(Subcommand... subcommands) {
        for (Subcommand sc : subcommands) {
            subs.put(sc.name().toLowerCase(Locale.ROOT), sc);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String key = args[0].toLowerCase(Locale.ROOT);
        Subcommand sc = subs.get(key);
        if (sc == null) {
            sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + args[0]);
            sendHelp(sender, label);
            return true;
        }

        // Permission check
        String perm = sc.permission();
        if (perm != null && !perm.isBlank() && !sender.hasPermission(perm)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use /" + label + " " + sc.name());
            return true;
        }

        // Pass remaining args
        String[] tail = Arrays.copyOfRange(args, 1, args.length);
        return sc.execute(sender, tail);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return subs.values().stream()
                    .filter(sc -> {
                        String perm = sc.permission();
                        return perm == null || perm.isBlank() || sender.hasPermission(perm);
                    })
                    .map(Subcommand::name)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .collect(Collectors.toList());
        }

        // Delegate to subcommand completer
        Subcommand sc = subs.get(args[0].toLowerCase(Locale.ROOT));
        if (sc == null) return Collections.emptyList();

        String perm = sc.permission();
        if (perm != null && !perm.isBlank() && !sender.hasPermission(perm)) {
            return Collections.emptyList();
        }

        String[] tail = Arrays.copyOfRange(args, 1, args.length);
        return sc.tabComplete(sender, tail);
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.AQUA + "DiscordLogger Commands:");
        for (Subcommand sc : subs.values()) {
            String perm = sc.permission();
            if (perm == null || perm.isBlank() || sender.hasPermission(perm)) {
                sender.sendMessage(ChatColor.GRAY + "  /" + label + " " + sc.name()
                        + ChatColor.DARK_GRAY + " — " + ChatColor.WHITE + sc.description());
            }
        }
    }
}
