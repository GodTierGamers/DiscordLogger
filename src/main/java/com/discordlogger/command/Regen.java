package com.discordlogger.command;

import com.discordlogger.DiscordLogger;
import com.discordlogger.config.ConfigMigrator;
import com.discordlogger.lang.Lang;
import com.discordlogger.util.Chat;
import com.discordlogger.util.Io;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * Replaces config.yml with this build's shipped default, keeping a backup.
 *
 * <p>Exists for the one case {@link ConfigMigrator} deliberately refuses to handle:
 * a config whose schema is newer than the running plugin. Migration only ever runs
 * forward, so there is no automatic way back — this is the manual escape hatch.
 *
 * <p>It is destructive by design, so it does not migrate, merge or preserve
 * anything: the point is to get back to a file this build fully understands. The
 * old file is renamed rather than deleted, with a timestamp, so an existing
 * config.old.yml from a previous migration is never overwritten.
 */
public final class Regen implements Subcommand {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final DiscordLogger plugin;

    public Regen(DiscordLogger plugin) {
        this.plugin = plugin;
    }

    @Override public String name() { return "regen"; }
    @Override public String description() { return "Rebuilds config.yml and lang.yml from this build's defaults (backs up the current ones)."; }
    @Override public String permission() { return "discordlogger.regen"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // Destructive, so require the word rather than acting on a bare typo of
        // "reload" — the two commands are one keystroke apart.
        if (args.length == 0 || !args[0].equalsIgnoreCase("confirm")) {
            Chat.send(sender, Lang.chat("chat.regen-warning"));
            Chat.send(sender, Lang.chat("chat.regen-confirm"));
            return true;
        }

        final File dataFolder = plugin.getDataFolder();

        // Both managed files, not just config.yml. They share a version and migrate
        // together, so rebuilding one and leaving the other is how you end up with a
        // fresh config beside a lang file that predates it.
        final String[] managed = { "config.yml", "lang.yml" };
        final List<String> backups = new ArrayList<>();

        for (String name : managed) {
            final String defaultText;
            try (InputStream in = plugin.getResource(name)) {
                if (in == null) {
                    Chat.send(sender, Lang.chat("chat.regen-no-bundled", "file", name));
                    return true;
                }
                defaultText = Io.readString(in);
            } catch (Exception ex) {
                Chat.send(sender, Lang.chat("chat.regen-read-failed",
                        "file", name, "error", String.valueOf(ex.getMessage())));
                return true;
            }

            final File target = new File(dataFolder, name);
            try {
                if (target.exists()) {
                    final String backupName = name.replace(".yml", "")
                            + ".backup-" + LocalDateTime.now().format(STAMP) + ".yml";
                    Files.move(target.toPath(), new File(dataFolder, backupName).toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                    backups.add(backupName);
                }
                Files.createDirectories(dataFolder.toPath());
                Io.writeString(target.toPath(), defaultText, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (Exception ex) {
                Chat.send(sender, Lang.chat("chat.regen-write-failed",
                        "file", name, "error", String.valueOf(ex.getMessage())));
                plugin.getLogger().severe("Regen of " + name + " failed: " + ex);
                return true;
            }
        }

        plugin.reloadConfig();
        // Reloaded before the confirmation is sent, so the reply below is worded by the
        // file that was just written rather than the one it replaced.
        Lang.reload(plugin);
        boolean ok = plugin.applyRuntimeConfig();

        Integer shipped = ConfigMigrator.shippedVersion(plugin, "config.yml");
        Chat.send(sender, Lang.chat("chat.regen-done",
                "schema", shipped != null ? "v" + shipped : "unknown"));
        for (String backupName : backups) {
            Chat.send(sender, Lang.chat("chat.regen-backup", "file", backupName));
        }
        if (!ok) {
            Chat.send(sender, Lang.chat("chat.regen-no-webhook"));
        }
        plugin.getLogger().info("config.yml and lang.yml were regenerated by "
                + sender.getName()
                + (backups.isEmpty() ? "" : " (backups: " + String.join(", ", backups) + ")"));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? Collections.singletonList("confirm") : Collections.emptyList();
    }
}
