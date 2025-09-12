package com.discordlogger.command;

import org.bukkit.command.CommandSender;
import java.util.List;

public interface Subcommand {
    String name();                    // e.g., "reload"
    String description();             // short help text
    String permission();              // e.g., "discordlogger.reload" (null/empty = no perm required)

    boolean execute(CommandSender sender, String[] args);
    List<String> tabComplete(CommandSender sender, String[] args);
}
