package com.discordlogger.util;

/**
 * Central registry of icon / thumbnail URLs used in Discord embeds.
 *
 * Keep all shared icon constants here so every listener references
 * a single source of truth.
 */
public final class Icons {
    private Icons() {}

    /** Generic server icon (start, stop, console commands). */
    public static final String SERVER =
            "https://discordlogger.godtiergamers.xyz/assets/icons/server.png";
}