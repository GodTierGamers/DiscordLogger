package com.discordlogger.command;

import com.discordlogger.explosion.Blast;
import com.discordlogger.explosion.Expiry;
import com.discordlogger.explosion.ExplosionLog;
import com.discordlogger.explosion.ExplosionRecord;
import com.discordlogger.log.Log;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /discordlogger lookup} — what exploded around here.
 *
 * <p>Discord answers "what happened", in order, as prose. It cannot answer "was
 * anything blown up <em>here</em>", which is the question somebody standing in a new
 * hole in their base actually has. This reads {@code explosions.csv} and answers that
 * one, nearest first.
 *
 * <h2>Why the default radius is so much wider than a blast</h2>
 *
 * <p>{@link Blast#DEFAULT_SEARCH_RADIUS} blocks, against a TNT blast that reaches
 * eight. Searching only the blast itself would mean standing on precisely the right
 * block to find anything, and the edge of a crater is not inside the explosion that
 * made it. The stored radius is still what turns the list into an answer: a result
 * within it reached you, one outside it happened nearby, and both are worth seeing.
 *
 * <h2>Off the main thread</h2>
 *
 * <p>The file can hold a month of rows, so the read runs asynchronously and the reply
 * is sent back on the main thread. The sender's position is taken before that hop,
 * because a {@code Player}'s location is not safe to ask for from another thread.
 */
public final class Lookup implements Subcommand {

    /** Results shown unless {@code limit:} says otherwise. */
    private static final int DEFAULT_LIMIT = 8;
    private static final int MAX_LIMIT = 50;

    private final JavaPlugin plugin;

    public Lookup(JavaPlugin plugin) { this.plugin = plugin; }

    @Override public String name() { return "lookup"; }
    @Override public String description() { return "Find explosions logged near a place"; }
    @Override public String permission() { return "discordlogger.lookup"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        final Query q = parse(sender, args);
        if (q.error != null) {
            sender.sendMessage(ChatColor.RED + q.error);
            sender.sendMessage(ChatColor.GRAY + "Usage: " + ChatColor.WHITE
                    + "/discordlogger lookup [x y z] [r:50] [t:7d] [type:tnt] [world:name] [limit:8]");
            sender.sendMessage(ChatColor.DARK_GRAY + "t: takes minutes to years — 30m, 2h, 7d, 3w, 6mo, 1y.");
            return true;
        }

        sender.sendMessage(ChatColor.AQUA + "Searching " + ChatColor.WHITE + q.radius
                + ChatColor.AQUA + " blocks around " + ChatColor.WHITE + fmtCoords(q.x, q.y, q.z)
                + ChatColor.GRAY + " in " + q.world + "…");

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override public void run() {
                final List<ExplosionRecord> hits = ExplosionLog.near(
                        q.world, q.x, q.y, q.z, q.radius, q.since, q.type, q.limit);
                plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                    @Override public void run() { report(sender, q, hits); }
                });
            }
        });
        return true;
    }

    // ----------------------------------------------------------------- parsing

    /** Everything a lookup needs, or the one thing wrong with how it was asked. */
    static final class Query {
        String world;
        int x, y, z;
        int radius = Blast.DEFAULT_SEARCH_RADIUS;
        int limit = DEFAULT_LIMIT;
        long since;
        String type;
        String error;
    }

    private Query parse(CommandSender sender, String[] args) {
        final Query q = new Query();

        // Coordinates come first when given as three bare numbers; everything else is
        // a key:value in any order, so "r:80 type:creeper" and the reverse both work.
        final List<String> options = new ArrayList<String>();
        final List<String> bare = new ArrayList<String>();
        for (int i = 0; i < args.length; i++) {
            final String a = args[i];
            if (a.indexOf(':') > 0) options.add(a);
            else bare.add(a);
        }

        if (bare.size() >= 3) {
            try {
                q.x = (int) Math.floor(Double.parseDouble(bare.get(0)));
                q.y = (int) Math.floor(Double.parseDouble(bare.get(1)));
                q.z = (int) Math.floor(Double.parseDouble(bare.get(2)));
            } catch (NumberFormatException notCoords) {
                q.error = "Those are not coordinates: " + bare.get(0) + " " + bare.get(1)
                        + " " + bare.get(2);
                return q;
            }
            if (sender instanceof Player) q.world = ((Player) sender).getWorld().getName();
        } else if (!bare.isEmpty()) {
            q.error = "Give three coordinates, or none to search where you are standing.";
            return q;
        } else if (sender instanceof Player) {
            final Location at = ((Player) sender).getLocation();
            q.world = at.getWorld() == null ? null : at.getWorld().getName();
            q.x = at.getBlockX();
            q.y = at.getBlockY();
            q.z = at.getBlockZ();
        } else {
            q.error = "From console, give coordinates and a world: lookup 128 71 -344 world:world";
            return q;
        }

        for (int i = 0; i < options.size(); i++) {
            final String opt = options.get(i);
            final int split = opt.indexOf(':');
            final String key = opt.substring(0, split).toLowerCase(Locale.ROOT);
            final String value = opt.substring(split + 1);
            if (key.equals("r") || key.equals("radius")) {
                final int r = parseInt(value, -1);
                if (r <= 0) { q.error = "r: needs a positive number of blocks."; return q; }
                // Capped rather than refused: a big number is someone asking for "all
                // of it", and the cap is what keeps one command off the whole world.
                q.radius = Math.min(r, Blast.MAX_SEARCH_RADIUS);
            } else if (key.equals("t") || key.equals("time") || key.equals("since")) {
                // Resolved against now rather than turned into a length, so t:6mo means
                // six calendar months back, the same as expiry_time does.
                final long from = Expiry.cutoff(value, System.currentTimeMillis());
                if (from == Expiry.NEVER) {
                    q.error = "t: needs a duration like 30m, 2h, 7d, 3w, 6mo or 1y.";
                    return q;
                }
                q.since = from;
            } else if (key.equals("type") || key.equals("source")) {
                q.type = value.isEmpty() ? null : value;
            } else if (key.equals("world") || key.equals("w")) {
                q.world = value;
            } else if (key.equals("limit") || key.equals("n")) {
                final int n = parseInt(value, -1);
                if (n <= 0) { q.error = "limit: needs a positive number."; return q; }
                q.limit = Math.min(n, MAX_LIMIT);
            } else {
                q.error = "I do not know the option \"" + key + "\".";
                return q;
            }
        }

        if (q.world == null || q.world.isEmpty()) {
            q.error = "Which world? Add world:<name>.";
        }
        return q;
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }

    // --------------------------------------------------------------- reporting

    private void report(CommandSender sender, Query q, List<ExplosionRecord> hits) {
        if (hits.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No explosions logged within " + q.radius
                    + " blocks of there.");
            final String configured = ExplosionLog.configuredExpiry(plugin);
            sender.sendMessage(ChatColor.DARK_GRAY + "Records are " + Expiry.describe(configured)
                    + ", and only explosions since this was enabled are in the log.");
            return;
        }

        sender.sendMessage(ChatColor.AQUA + "" + hits.size() + ChatColor.AQUA
                + (hits.size() == 1 ? " explosion" : " explosions") + " near "
                + ChatColor.WHITE + fmtCoords(q.x, q.y, q.z)
                + ChatColor.DARK_GRAY + " (nearest first)");

        for (int i = 0; i < hits.size(); i++) {
            final ExplosionRecord r = hits.get(i);
            final double distance = r.distanceTo(q.x, q.y, q.z);
            final boolean reached = distance <= r.radius();

            // Red means this one reached the spot asked about, which is the finding.
            // Gold is close enough to be related, grey is context.
            final ChatColor tone = reached ? ChatColor.RED
                    : distance <= r.radius() * 2 ? ChatColor.GOLD : ChatColor.YELLOW;

            sender.sendMessage(ChatColor.DARK_GRAY + " " + (i + 1) + ". "
                    + tone + String.format(Locale.ROOT, "%.1fm", distance)
                    + (reached ? ChatColor.RED + " (reached here)" : "")
                    + ChatColor.GRAY + " — " + ChatColor.WHITE + r.source()
                    + ChatColor.GRAY + " at " + ChatColor.WHITE + fmtCoords(r.x(), r.y(), r.z())
                    // Both, because they answer different questions: the relative form
                    // is what gets asked, the absolute one is what gets written into a
                    // ban note or lined up against another log.
                    + ChatColor.DARK_GRAY + ", " + Log.formatTime(r.at())
                    + " (" + ago(r.at()) + ")");

            final StringBuilder detail = new StringBuilder(64);
            detail.append(ChatColor.DARK_GRAY).append("      ")
                  .append(r.blocks()).append(r.blocks() == 1 ? " block" : " blocks")
                  .append(" destroyed, reach ").append(r.radius()).append('m');
            if (!r.nearby().isEmpty()) {
                detail.append(ChatColor.GRAY).append(" — nearby: ")
                      .append(ChatColor.WHITE).append(r.nearby());
            }
            sender.sendMessage(detail.toString());
        }
    }

    /** "3d ago", "2h ago", "just now" — relative, because that is how it gets asked. */
    static String ago(long at) {
        final long ms = Math.max(0L, System.currentTimeMillis() - at);
        final long minutes = ms / 60_000L;
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + "m ago";
        final long hours = minutes / 60L;
        if (hours < 24) return hours + "h ago";
        return (hours / 24L) + "d ago";
    }

    static String fmtCoords(int x, int y, int z) {
        return x + ", " + y + ", " + z;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 0) return java.util.Collections.emptyList();
        final String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        final List<String> out = new ArrayList<String>();
        for (String hint : Arrays.asList("r:", "t:", "type:", "world:", "limit:")) {
            if (hint.startsWith(last)) out.add(hint);
        }
        return out;
    }
}
