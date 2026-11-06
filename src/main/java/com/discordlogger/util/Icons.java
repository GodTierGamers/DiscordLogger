package com.discordlogger.util;

/**
 * Embed thumbnails that are not derived from the event's own content.
 *
 * <p>Here rather than repeated as a constant in each listener: the five server
 * events shared one URL written out five times, and the first change to it found
 * four of the five.
 *
 * <p>Icons that vary with the event — an explosion's source, a bucket's liquid, a
 * fire's cause — stay in their own listener, where the mapping lives.
 */
public final class Icons {
    private Icons() {}

    /** Everything the site serves, so the host appears once. */
    private static final String BASE = "https://discordlogger.godtiergamers.xyz/assets/icons/";

    /**
     * A command block, for every server event with nothing more specific to show:
     * start, stop, a console command, a plugin toggling, a custom log rule.
     *
     * <p>Animated, because its lights are, and every frame reads the same — which
     * is what a reader with GIF autoplay off sees.
     *
     * <p><strong>{@code server.png} is still on the site and must stay there.</strong>
     * It is what this used to be, and every release already installed on a server
     * hot-links it. Deleting it would blank the thumbnail on all of them.
     */
    public static final String SERVER = BASE + "server.gif";
}
