package com.discordlogger.command;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Keeps this plugin's noise out of a player's tab-completion.
 *
 * <h2>Why the plugin's own tab completer cannot fix this</h2>
 *
 * <p>Two different completions look identical in game. Typing {@code /discordlogger }
 * and pressing tab completes <em>arguments</em>, which {@link Commands#onTabComplete}
 * handles and has always handled correctly. Pressing tab before that space completes
 * <em>command names</em>, and the plugin is never consulted — the server answers from
 * its own registry.
 *
 * <p>That registry holds more than was asked for. Bukkit registers every plugin
 * command a second time under a {@code plugin:command} alias, so a command declared
 * once with two aliases produces six entries, and the player is offered
 * {@code discordlogger:discordlogger}, {@code discordlogger:dlogger} and
 * {@code discordlogger:dlog} beside the three real ones.
 *
 * <p>{@link PlayerCommandSendEvent} is the only place this can be corrected: it fires
 * as the command list is sent and its collection can be removed from.
 *
 * <h2>Removing an entry does not remove the command</h2>
 *
 * <p>This affects suggestions only. {@code /discordlogger:reload} still runs, which is
 * the point of the namespaced form — it exists so a command can still be reached when
 * two plugins claim the same name. Hiding it costs nothing and keeps it available for
 * the one case it was designed for.
 */
public final class CommandVisibility implements Listener {

    private final Commands router;

    /** {@code "discordlogger:"} — every namespaced alias this plugin owns starts with it. */
    private final String namespace;

    /** {@code discordlogger}, {@code dlogger}, {@code dlog} — read from plugin.yml. */
    private final Set<String> labels;

    public CommandVisibility(JavaPlugin plugin, Commands router) {
        this.router = router;
        this.namespace = plugin.getName().toLowerCase(Locale.ROOT) + ":";
        this.labels = declaredLabels(plugin);
    }

    /**
     * Every name this plugin's commands answer to, taken from plugin.yml.
     *
     * <p>Read rather than hard-coded so adding an alias stays a one-line descriptor
     * change. A hard-coded list would drift silently, and the symptom — one stray
     * {@code discordlogger:} entry reappearing in tab-complete — is exactly the kind
     * nobody reports.
     */
    private static Set<String> declaredLabels(JavaPlugin plugin) {
        final Set<String> out = new HashSet<>();
        for (Map.Entry<String, Map<String, Object>> e
                : plugin.getDescription().getCommands().entrySet()) {
            out.add(e.getKey().toLowerCase(Locale.ROOT));
            final Object aliases = e.getValue().get("aliases");
            if (aliases instanceof List<?>) {
                final List<?> list = (List<?>) aliases;
                for (Object a : list) out.add(String.valueOf(a).toLowerCase(Locale.ROOT));
            } else if (aliases != null) {
                out.add(String.valueOf(aliases).toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommandSend(PlayerCommandSendEvent e) {
        final boolean usable = router.isUsableBy(e.getPlayer());
        e.getCommands().removeIf(entry -> isNoise(entry, namespace, labels, usable));
    }

    /**
     * Whether an entry should be dropped from this player's command list.
     *
     * <p>Pure so the decision can be tested without a server, and deliberately narrow
     * in two ways. It only ever touches <em>this</em> plugin's entries — another
     * plugin's namespaced aliases are its business, and a plugin that quietly edits
     * them is a plugin nobody can debug. And it matches the suffix against the
     * declared labels rather than dropping everything under the namespace, so an
     * unrelated command that happens to start with {@code discordlogger:} survives.
     *
     * @param usable whether the player can run any subcommand; when false the plain
     *               names go too, since a command offering nothing is the same noise
     *               in a friendlier hat
     */
    static boolean isNoise(String entry, String namespace, Set<String> labels, boolean usable) {
        if (entry == null) return false;
        final String s = entry.toLowerCase(Locale.ROOT);
        if (s.startsWith(namespace)) {
            return labels.contains(s.substring(namespace.length()));
        }
        return !usable && labels.contains(s);
    }
}
