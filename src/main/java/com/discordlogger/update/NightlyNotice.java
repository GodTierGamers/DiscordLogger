package com.discordlogger.update;

import com.discordlogger.util.Io;

import com.discordlogger.lang.Lang;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * On a nightly-channel build: warns in console on every start, and messages
 * ops in chat once per nightly version (tracked via a marker file so repeat
 * restarts on the same build don't re-spam ops). No-op entirely on stable/dev
 * builds -- BuildInfo.isNightly() is the only gate, see its class comment for
 * why that's build-time-baked rather than parsed from the version string.
 */
public final class NightlyNotice implements Listener {
    /** Console form: the logger already prefixes "[DiscordLogger]", so no prefix here. */
    private static final String MESSAGE =
            "This is a nightly release, it may be unstable and have bugs, "
                    + "it is recommended to upgrade frequently, you can upgrade at "
                    + "https://discordlogger.godtiergamers.xyz";

    /** Chat form: nothing prefixes in-game messages, so say who is talking. */
    private static final String CHAT_MESSAGE =
            ChatColor.GOLD + "[DiscordLogger] " + ChatColor.YELLOW
                    + "This is a nightly build — it may be unstable. Upgrade often: "
                    + ChatColor.WHITE + "https://discordlogger.godtiergamers.xyz";

    private final boolean announceToOpsThisBoot;

    public NightlyNotice(JavaPlugin plugin) {
        this.announceToOpsThisBoot = BuildInfo.isNightly() && isFirstBootOfThisVersion(plugin);
    }

    /** Call once from onEnable. Registers the join listener only if needed. */
    public void activate(JavaPlugin plugin) {
        if (!BuildInfo.isNightly()) return;

        plugin.getLogger().warning(MESSAGE);

        if (announceToOpsThisBoot) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (p.isOp()) {
            p.sendMessage(Lang.chat("chat.nightly-notice"));
        }
    }

    private static boolean isFirstBootOfThisVersion(JavaPlugin plugin) {
        File marker = new File(plugin.getDataFolder(), ".nightly-notice");
        String version = BuildInfo.version();
        try {
            if (marker.exists()) {
                String seen = Io.readString(marker.toPath()).trim();
                if (seen.equals(version)) return false;
            }
            Files.createDirectories(marker.getParentFile().toPath());
            Io.writeString(marker.toPath(), version, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // If we can't persist the marker, fail open (announce) rather than closed.
        }
        return true;
    }
}
