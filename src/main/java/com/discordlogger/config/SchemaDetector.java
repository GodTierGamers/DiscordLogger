package com.discordlogger.config;

import java.util.Map;

/**
 * Works out which config schema a file actually is, from its keys.
 *
 * <p>The schema used to be declared only by a comment on the last line. Comments are
 * the least durable thing in a config: editors strip them, formatters move them,
 * and anyone tidying the bottom of a file deletes one without noticing. When that
 * marker went missing the migrator saw {@code UNKNOWN}, skipped migration entirely,
 * and the plugin then read a file whose shape it did not match — every option
 * silently falling back to a default.
 *
 * <p>A file's schema is not really a declaration, though. It is a fact about which
 * keys are present, and that cannot be wrong: a config claiming to be v10 while
 * lacking every v10 key is a v9 file with a bad label. So the declaration is a hint
 * and this is the arbiter.
 *
 * <p>Each version is identified by a key that first appeared in it. Deleting one
 * unrelated option therefore cannot drop a file a version — only removing the
 * marker itself would, and at that point the file genuinely has lost the thing that
 * distinguishes it.
 */
public final class SchemaDetector {

    /** Unknown, i.e. not recognisable as any schema this plugin has ever shipped. */
    public static final int UNKNOWN = -1;

    private SchemaDetector() {}

    /**
     * The newest schema whose marker is present.
     *
     * <p>Newest-first because schemas are additive: a v10 file also contains v9's
     * keys, so testing oldest-first would always answer v2.
     *
     * @param flat the config flattened to dotted paths, as {@link ConfigMigrator#flattenYaml} produces
     */
    public static int infer(Map<String, Object> flat) {
        if (flat == null || flat.isEmpty()) return UNKNOWN;

        // v10 restructured events into sections and added filters. Either is decisive.
        if (flat.containsKey("filters.exempt_permission")
                || flat.containsKey("log.player.join.enabled")) return 10;

        if (flat.containsKey("embeds.colors.server.explosion")) return 9;
        if (flat.containsKey("embeds.colors.player.gamemode")) return 8;
        if (flat.containsKey("embeds.colors.moderation.ban")) return 7;

        // v6 introduced moderation colours as flat keys, before v7 nested them.
        if (flat.containsKey("embeds.colors.ban")) return 6;

        // v4 and v5 have identical key sets, so they are genuinely indistinguishable.
        // Reporting the newer is safe: nothing changed between them, so the migration
        // from either is the same, and step(4) is an identity step.
        if (flat.containsKey("format.name")) return 5;

        if (flat.containsKey("embeds.author")) return 3;

        // Oldest on record. Requires the two keys that have existed since the start,
        // so an unrelated YAML file is not mistaken for an ancient config.
        if (flat.containsKey("webhook.url") && flat.containsKey("format.time")) return 2;

        return UNKNOWN;
    }
}
