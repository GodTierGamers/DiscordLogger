package com.discordlogger.explosion;

import java.util.Locale;

/**
 * How far an explosion reached, which is what makes a lookup by place possible.
 *
 * <h2>Measured first, guessed second</h2>
 *
 * <p>The radius stored with a record is the distance from the centre to the furthest
 * block the explosion actually destroyed. That is not an estimate: the server hands
 * the listener the exact block list, so a charged creeper, a TNT stack, a blast
 * stopped dead by obsidian and one that went off in open air all record what they
 * really did rather than what their type usually does.
 *
 * <p><b>The per-type floor is for the case where nothing broke.</b> A creeper that
 * killed somebody standing in a stone corridor may destroy no blocks at all, and a
 * record with radius 0 can never be found by the person standing in the spot it
 * happened — which is the entire feature. So the floor is what that type of explosion
 * ordinarily reaches, and the stored radius is whichever is larger.
 *
 * <p>Figures are Minecraft's own explosion power doubled, which is the outer bound of
 * what a blast of that power can reach: TNT is power 4, a creeper 3, an end crystal
 * and a charged creeper 6, a bed and a respawn anchor 5, a ghast or wither projectile
 * 1. Matching by name rather than by enum constant keeps this working across versions
 * that renamed the types, which is the same reason the icon lookup does it.
 *
 * <h2>This is not the search radius</h2>
 *
 * <p>A lookup searches {@link #DEFAULT_SEARCH_RADIUS} blocks by default, far wider than
 * any blast, because somebody standing at the edge of a crater needs to see what went
 * off <em>near</em> them and not only what reached the exact block they are on. The
 * stored radius is what turns that list into an answer: a result within it is one that
 * reached you, and one outside it happened nearby. Both are worth seeing; they are not
 * the same finding.
 */
public final class Blast {

    private Blast() {}

    /** Used when a source is not recognised: a creeper's reach, the commonest case. */
    static final int DEFAULT_FLOOR = 6;

    /**
     * How far a lookup looks when no radius is given.
     *
     * <p>Deliberately much larger than any blast. The question behind the command is
     * "what happened around here", asked by someone who has just found a hole, and the
     * edge of a crater is not inside the blast that made it. Fifty blocks is a few
     * seconds' walk and three chunks, so it covers a base without covering a continent.
     */
    public static final int DEFAULT_SEARCH_RADIUS = 50;

    /** Upper bound on a requested radius, so one command cannot scan a whole world. */
    public static final int MAX_SEARCH_RADIUS = 500;

    /**
     * The radius a source of this name ordinarily reaches, in blocks.
     *
     * @param name an {@code EntityType} or {@code Material} name, in any case
     */
    public static int floorFor(String name) {
        if (name == null) return DEFAULT_FLOOR;
        final String n = name.toUpperCase(Locale.ROOT);

        // Order matters: TNT_MINECART contains "TNT", and DRAGON_FIREBALL contains
        // "FIREBALL", so the more specific name has to be asked about first.
        if (n.contains("TNT_MINECART") || n.contains("MINECART_TNT")) return 8;
        if (n.contains("TNT")) return 8;
        if (n.contains("CREEPER")) return 6;
        if (n.contains("END_CRYSTAL") || n.contains("ENDER_CRYSTAL")) return 12;
        if (n.contains("RESPAWN_ANCHOR")) return 10;
        if (n.endsWith("_BED") || n.equals("BED")) return 10;
        if (n.contains("DRAGON_FIREBALL")) return 2;
        if (n.contains("WITHER_SKULL")) return 2;
        if (n.contains("FIREBALL")) return 2;
        return DEFAULT_FLOOR;
    }

    /**
     * The radius to store: what the explosion reached, or its type's floor.
     *
     * @param measured distance from the centre to the furthest destroyed block, or 0
     * @param sourceName the entity or block that exploded
     */
    public static int radiusFor(double measured, String sourceName) {
        final int rounded = (int) Math.ceil(Math.max(0.0, measured));
        return Math.max(rounded, floorFor(sourceName));
    }
}
