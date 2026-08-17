package com.discordlogger.command;

import com.discordlogger.lang.Lang;
import com.discordlogger.metrics.Counters;
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
            sender.sendMessage(Lang.chat("chat.unknown-subcommand", "input", args[0]));
            sendHelp(sender, label);
            return true;
        }

        // Permission check
        String perm = sc.permission();
        if (perm != null && !perm.isBlank() && !sender.hasPermission(perm)) {
            sender.sendMessage(Lang.chat("chat.no-permission", "label", label, "command", sc.name()));
            return true;
        }

        // Which subcommand ran, never who ran it -- see metrics/Counters.
        Counters.commandUsed(sc.name().toLowerCase(Locale.ROOT));

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

    /**
     * Whether this sender can run anything at all.
     *
     * <p>The root command is deliberately ungated — gating it would lock a
     * {@code regen}-only admin out of the whole command — so "can use this plugin"
     * is not a single permission but "holds at least one subcommand's". Used by
     * {@link CommandVisibility} to keep the command out of tab-completion for
     * players it would do nothing for.
     */
    public boolean isUsableBy(CommandSender sender) {
        for (Subcommand sc : subs.values()) {
            final String perm = sc.permission();
            if (perm == null || perm.isBlank() || sender.hasPermission(perm)) return true;
        }
        return false;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Lang.chat("chat.help-header"));
        for (Subcommand sc : subs.values()) {
            String perm = sc.permission();
            if (perm == null || perm.isBlank() || sender.hasPermission(perm)) {
                sender.sendMessage(Lang.chat("chat.help-entry",
                        "label", label, "command", sc.name(), "description", sc.description()));
            }
        }
    }
}
