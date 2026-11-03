package com.discordlogger.command;

import com.discordlogger.explosion.Blast;
import com.discordlogger.explosion.Expiry;
import com.discordlogger.explosion.ExplosionLog;
import com.discordlogger.explosion.ExplosionRecord;
import com.discordlogger.log.Log;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

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
            // Shortest useful form first. Someone who got this wrong wants to know
            // what to type, not to read the whole grammar.
            sender.sendMessage(ChatColor.GRAY + "Just " + ChatColor.WHITE
                    + "/discordlogger lookup" + ChatColor.GRAY + " searches "
                    + Blast.DEFAULT_SEARCH_RADIUS + " blocks around you.");
            sender.sendMessage(ChatColor.DARK_GRAY + "Or: lookup <radius> · lookup <x y z> · "
                    + "r:120 · t:2h · type:creeper · world:name · limit:20");
            return true;
        }

        // No "searching…" line. The read takes milliseconds and the answer arrives on
        // its own; announcing the question just pushes the answer up the chat box.
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override public void run() {
                final ExplosionLog.Results found = ExplosionLog.near(
                        q.world, q.x, q.y, q.z, q.radius, q.since, q.type, q.limit);
                plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                    @Override public void run() { report(sender, q, found); }
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
        /** True when the search is centred on the sender, so messages can say "you". */
        boolean here;
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
        } else if (bare.size() == 1 && sender instanceof Player) {
            // "lookup 200" is someone widening the search, not someone mistyping
            // coordinates. It is the second most likely thing to type after nothing.
            final int r = parseInt(bare.get(0), -1);
            if (r <= 0) {
                q.error = "\"" + bare.get(0) + "\" is not a radius or a set of coordinates.";
                return q;
            }
            q.radius = Math.min(r, Blast.MAX_SEARCH_RADIUS);
            final Location at = ((Player) sender).getLocation();
            q.world = at.getWorld() == null ? null : at.getWorld().getName();
            q.x = at.getBlockX();
            q.y = at.getBlockY();
            q.z = at.getBlockZ();
            q.here = true;
        } else if (!bare.isEmpty()) {
            q.error = "Give three coordinates, one radius, or nothing at all.";
            return q;
        } else if (sender instanceof Player) {
            final Location at = ((Player) sender).getLocation();
            q.world = at.getWorld() == null ? null : at.getWorld().getName();
            q.x = at.getBlockX();
            q.y = at.getBlockY();
            q.z = at.getBlockZ();
            q.here = true;
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
                if (r <= 0) { q.error = "r: needs a number of blocks, like r:120."; return q; }
                // Capped rather than refused: a big number is someone asking for "all
                // of it", and the cap is what keeps one command off the whole world.
                q.radius = Math.min(r, Blast.MAX_SEARCH_RADIUS);
            } else if (key.equals("t") || key.equals("time") || key.equals("since")) {
                // Resolved against now rather than turned into a length, so t:6mo means
                // six calendar months back, the same as expiry_time does.
                final long from = Expiry.cutoff(value, System.currentTimeMillis());
                if (from == Expiry.NEVER) {
                    q.error = "t: needs a length of time — 30m, 2h, 7d, 4w, 6mo, 1y.";
                    return q;
                }
                q.since = from;
            } else if (key.equals("type") || key.equals("source")) {
                q.type = value.isEmpty() ? null : value;
            } else if (key.equals("world") || key.equals("w")) {
                q.world = value;
            } else if (key.equals("limit") || key.equals("n")) {
                final int n = parseInt(value, -1);
                if (n <= 0) { q.error = "limit: needs a number, like limit:20."; return q; }
                q.limit = Math.min(n, MAX_LIMIT);
            } else {
                q.error = "There is no \"" + key + ":\" option. Try r:, t:, type:, world: or limit:.";
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

    private void report(CommandSender sender, Query q, ExplosionLog.Results found) {
        final List<ExplosionRecord> hits = found.shown();

        if (hits.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Nothing exploded within "
                    + ChatColor.WHITE + q.radius + ChatColor.GRAY + " blocks of "
                    + (q.here ? "you" : fmtCoords(q.x, q.y, q.z)) + ".");
            sender.sendMessage(ChatColor.DARK_GRAY + "Look wider with "
                    + ChatColor.GRAY + "/discordlogger lookup " + (q.radius * 4));
            return;
        }

        // The header says what was searched, so no separate "searching..." line is
        // needed and the answer is the first thing on screen.
        sender.sendMessage(ChatColor.AQUA + "Explosions within " + ChatColor.WHITE + q.radius
                + ChatColor.AQUA + " blocks of "
                + (q.here ? "you" : ChatColor.WHITE + fmtCoords(q.x, q.y, q.z) + ChatColor.AQUA)
                + ChatColor.DARK_GRAY + "  (nearest first)");

        for (int i = 0; i < hits.size(); i++) {
            final ExplosionRecord r = hits.get(i);
            final double distance = r.distanceTo(q.x, q.y, q.z);
            final boolean reached = distance <= r.radius();

            // The headline is the answer: what it was, how far, how long ago. Red when
            // the blast actually reached the spot asked about, which is the finding.
            sender.sendMessage(" " + ChatColor.DARK_GRAY + (i + 1) + ". "
                    + (reached ? ChatColor.RED : ChatColor.YELLOW) + r.source()
                    + ChatColor.GRAY + " — " + ChatColor.WHITE
                    + String.format(Locale.ROOT, "%.0f", distance) + " blocks away"
                    + ChatColor.GRAY + ", " + ChatColor.WHITE + ago(r.at())
                    + (reached ? ChatColor.RED + "  ← reached here" : ""));

            // The evidence line: where, how big, who saw it, and the exact time. One
            // line so a list of eight stays readable in a chat box.
            final StringBuilder detail = new StringBuilder(96);
            detail.append("    ").append(ChatColor.DARK_GRAY)
                  .append("at ").append(fmtCoords(r.x(), r.y(), r.z()))
                  .append(" · ").append(r.blocks())
                  .append(r.blocks() == 1 ? " block" : " blocks");
            final String who = names(r.nearby());
            if (!who.isEmpty()) {
                detail.append(" · ").append(who);
            }
            detail.append(" · ").append(Log.formatTime(r.at()));
            sender.sendMessage(detail.toString());
        }

        if (found.total() > hits.size()) {
            // Results are sorted by distance, so a truncated list can hide the recent
            // one somebody is actually asking about behind older, closer ones.
            sender.sendMessage(ChatColor.DARK_GRAY + "Showing " + hits.size() + " of "
                    + found.total() + " — add " + ChatColor.GRAY + "limit:"
                    + Math.min(found.total(), MAX_LIMIT) + ChatColor.DARK_GRAY
                    + " for more, or " + ChatColor.GRAY + "t:1h" + ChatColor.DARK_GRAY
                    + " for just the recent ones.");
        }
    }

    /**
     * Turns the stored {@code <uuid>@<blocks>} list into current names.
     *
     * <p>The file stores UUIDs so a rename cannot rot the record; this is the other
     * half of that trade — what gets printed is who they are called <em>now</em>,
     * resolved at read time rather than frozen when the explosion happened.
     *
     * <p>An unresolvable UUID prints its first eight characters. That is still enough
     * to match against another row or to look up by hand, and far better than dropping
     * the entry, which would read as "nobody was there".
     */
    static String names(String stored) {
        if (stored == null || stored.trim().isEmpty()) return "";
        final StringBuilder out = new StringBuilder(64);
        for (String entry : stored.trim().split("\\s+")) {
            final int at = entry.lastIndexOf('@');
            final String id = at < 0 ? entry : entry.substring(0, at);
            final String distance = at < 0 ? null : entry.substring(at + 1);

            String name = null;
            try {
                final UUID uuid = UUID.fromString(id);
                final Player online = Bukkit.getPlayer(uuid);
                name = (online != null) ? online.getName() : Bukkit.getOfflinePlayer(uuid).getName();
            } catch (IllegalArgumentException notAUuid) {
                // A hand-edited file, or a row from before this column held UUIDs.
                name = id;
            } catch (Throwable noServer) {
                name = null;
            }
            if (name == null || name.isEmpty()) {
                name = id.length() > 8 ? id.substring(0, 8) : id;
            }

            if (out.length() > 0) out.append(", ");
            out.append(name);
            if (distance != null && !distance.isEmpty()) out.append(" (").append(distance).append("m)");
        }
        return out.toString();
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
