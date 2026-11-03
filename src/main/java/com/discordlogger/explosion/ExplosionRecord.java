package com.discordlogger.explosion;

import java.util.Locale;

/**
 * One explosion, as written to and read back from {@code explosions.csv}.
 *
 * <p>A Discord channel answers "what happened", in order, as prose. It cannot answer
 * "was anything blown up <em>here</em>", which is the question somebody standing in a
 * new hole in their base actually has — scrolling a log for coordinates they have to
 * compare by eye is not an answer. This record exists so that question can be asked
 * of a place instead of a timeline.
 *
 * <p><b>Fields are read back from a file people may have edited.</b> Every parse is
 * lenient: a malformed row is skipped rather than failing the lookup, because the
 * alternative is one bad line making every other explosion unfindable.
 */
public final class ExplosionRecord {

    /** CSV header, also the field order. Written once when the file is created. */
    static final String HEADER = "at,world,x,y,z,radius,source,blocks,yield,nearby";

    private final long at;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final int radius;
    private final String source;
    private final int blocks;
    private final float yield;
    private final String nearby;

    public ExplosionRecord(long at, String world, int x, int y, int z, int radius,
                           String source, int blocks, float yield, String nearby) {
        this.at = at;
        this.world = world == null ? "unknown" : world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.source = source == null ? "Unknown" : source;
        this.blocks = blocks;
        this.yield = yield;
        this.nearby = nearby == null ? "" : nearby;
    }

    public long at()        { return at; }
    public String world()   { return world; }
    public int x()          { return x; }
    public int y()          { return y; }
    public int z()          { return z; }
    /** How far this explosion reached, in blocks — see {@link Blast}. */
    public int radius()     { return radius; }
    public String source()  { return source; }
    public int blocks()     { return blocks; }
    public float yield()    { return yield; }
    /** Who was within 20 blocks when it went off, already rendered. May be empty. */
    public String nearby()  { return nearby; }

    /**
     * Whether a point falls inside this explosion's reach, extended by {@code extra}.
     *
     * <p>Compared in squared distance, so a lookup over thousands of rows does no
     * square roots, and on block coordinates rather than exact positions — the
     * question is "did this explosion touch where I am standing", and a fractional
     * offset within one block is not a different answer.
     */
    public boolean touches(String otherWorld, int px, int py, int pz, int extra) {
        if (!world.equalsIgnoreCase(otherWorld == null ? "" : otherWorld)) return false;
        final long reach = (long) radius + Math.max(0, extra);
        final long dx = px - x;
        final long dy = py - y;
        final long dz = pz - z;
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    /** Distance from this explosion's centre to a point, in blocks. */
    public double distanceTo(int px, int py, int pz) {
        final double dx = px - x;
        final double dy = py - y;
        final double dz = pz - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** One CSV row, no trailing newline. */
    public String toCsv() {
        final StringBuilder sb = new StringBuilder(96);
        sb.append(at).append(',')
          .append(escape(world)).append(',')
          .append(x).append(',').append(y).append(',').append(z).append(',')
          .append(radius).append(',')
          .append(escape(source)).append(',')
          .append(blocks).append(',')
          .append(String.format(Locale.ROOT, "%.2f", yield)).append(',')
          .append(escape(nearby));
        return sb.toString();
    }

    /**
     * Parses one row, or {@code null} if it is not one.
     *
     * <p>Null rather than an exception: this reads a file on disk that an admin may
     * have opened, truncated or edited, and a lookup that throws on the first odd line
     * is a lookup that stops working the moment anyone touches the file.
     */
    public static ExplosionRecord fromCsv(String line) {
        if (line == null) return null;
        final String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("at,")) return null;

        final String[] f = split(trimmed);
        if (f.length < 10) return null;
        try {
            return new ExplosionRecord(
                    Long.parseLong(f[0].trim()),
                    f[1],
                    Integer.parseInt(f[2].trim()),
                    Integer.parseInt(f[3].trim()),
                    Integer.parseInt(f[4].trim()),
                    Integer.parseInt(f[5].trim()),
                    f[6],
                    Integer.parseInt(f[7].trim()),
                    Float.parseFloat(f[8].trim()),
                    f[9]);
        } catch (NumberFormatException notARow) {
            return null;
        }
    }

    /**
     * Quotes a value only when it would otherwise break the row.
     *
     * <p>World names allow a wide character set and the nearby-player list is built
     * from display names, so both can contain a comma or a quote. Unquoted values stay
     * unquoted, because the file is meant to be readable in a terminal.
     */
    static String escape(String value) {
        final String v = value == null ? "" : value;
        if (v.indexOf(',') < 0 && v.indexOf('"') < 0 && v.indexOf('\n') < 0) return v;
        return '"' + v.replace("\"", "\"\"") + '"';
    }

    /** Splits a row, honouring the quoting {@link #escape} applies. */
    static String[] split(String line) {
        final java.util.List<String> out = new java.util.ArrayList<String>(10);
        final StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            final char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }
}
