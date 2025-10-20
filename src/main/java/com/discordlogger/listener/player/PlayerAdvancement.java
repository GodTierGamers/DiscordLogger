package com.discordlogger.listener.player;

import com.discordlogger.log.Log;
import com.discordlogger.util.Names;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PlayerAdvancement implements Listener {
    private final JavaPlugin plugin;
    public PlayerAdvancement(JavaPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        if (!plugin.getConfig().getBoolean("log.player.advancement", false)) return;

        final NamespacedKey key = e.getAdvancement().getKey();
        final String ns = key.getNamespace();   // usually "minecraft"
        final String path = key.getKey();       // e.g., "adventure/sleep_in_bed"

        // Always ignore recipe/root advancements to avoid spam
        if (path.startsWith("recipes/") || path.startsWith("recipe/")) return;
        if (path.endsWith("/root") || path.equals("story/root")) return;

        final String playerName = Names.display(e.getPlayer(), plugin);
        final String pretty = prettyTitle(path);

        final UUID uuid = e.getPlayer().getUniqueId();
        final String thumb = Log.playerAvatarUrl(uuid);

        List<Log.Field> fields = new ArrayList<>();
        fields.add(new Log.Field("Player:", playerName));
        fields.add(new Log.Field("Advancement:", pretty));
        fields.add(new Log.Field("Key:", ns + ":" + path, true));

        // Category key -> "player_advancement" (matches embeds.colors.player.advancement)
        Log.eventFieldsWithThumb(
                "player_advancement",
                "Advancement Unlocked",
                null,   // author -> default embeds.author
                fields,
                thumb
        );
    }

    private static String prettyTitle(String path) {
        String seg = path;
        int slash = path.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < path.length()) seg = path.substring(slash + 1);
        seg = seg.replace('_', ' ').trim();
        if (seg.isEmpty()) return path;

        String[] words = seg.split("\\s+");
        StringBuilder sb = new StringBuilder(seg.length());
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) sb.append(w.substring(1).toLowerCase(Locale.ROOT));
            }
            if (i + 1 < words.length) sb.append(' ');
        }
        return sb.toString();
    }
}
