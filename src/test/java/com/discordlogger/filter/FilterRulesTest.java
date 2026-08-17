package com.discordlogger.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The matching behaviour of every filter.
 *
 * <p>These are decisions about what never reaches Discord, and most of them are
 * invisible when wrong: an over-broad filter silently drops events nobody notices
 * are missing, and an under-broad one only shows up as noise. Neither produces an
 * error, so the rules are pinned here.
 *
 * <p>{@code Filters} reads its state from a live plugin config, which does not exist
 * in a test, so each case installs a snapshot directly.
 */
class FilterRulesTest {

    /** Installs a filter state without needing a server. */
    private static void install(
            Set<String> ignoredCommands, Set<String> onlyCommands,
            List<String> chatPatterns, int minChatLength,
            List<String> advancements, boolean recipes,
            Set<String> teleportCauses, double minTeleport,
            Set<String> deathCauses, Set<String> explosionSources, int minBlocks) throws Exception {

        Class<?> snapshot = Class.forName("com.discordlogger.filter.Filters$Snapshot");
        Constructor<?> ctor = snapshot.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object snap = ctor.newInstance(
                Set.of(), Set.of(), ignoredCommands, onlyCommands, Set.of(),
                chatPatterns, minChatLength, advancements, recipes,
                teleportCauses, minTeleport, deathCauses, explosionSources, minBlocks, "",
                // respectVanish: these rules are about config, and vanish is live state
                // no unit test can produce -- false keeps it out of the way entirely.
                false);

        Field current = Filters.class.getDeclaredField("current");
        current.setAccessible(true);
        current.set(null, snap);
    }

    @BeforeEach
    void reset() throws Exception {
        install(Set.of(), Set.of(), List.of(), 0, List.of(), false,
                Set.of(), 0d, Set.of(), Set.of(), 0);
    }

    // ---------------------------------------------------------------- commands --

    @Test
    @DisplayName("an allow-list wins outright, and the deny-list still applies inside it")
    void allowListAndDenyListInteract() throws Exception {
        install(Set.of("msg"), Set.of("ban", "kick", "msg"), List.of(), 0, List.of(), false,
                Set.of(), 0d, Set.of(), Set.of(), 0);

        assertFalse(Filters.blocksCommand("/ban Steve"), "on the allow-list");
        assertTrue(Filters.blocksCommand("/gamemode 1"), "not on the allow-list");
        assertTrue(Filters.blocksCommand("/msg hi"),
                "allow-listed but also denied — the deny-list has to win, or it would be "
                        + "impossible to allow a set and exclude one from it");
    }

    // ------------------------------------------------------------ advancements --

    @Test
    @DisplayName("a trailing * matches a whole advancement tab")
    void advancementWildcard() throws Exception {
        install(Set.of(), Set.of(), List.of(), 0,
                List.of("minecraft:husbandry/*", "minecraft:story/mine_stone"), false,
                Set.of(), 0d, Set.of(), Set.of(), 0);

        assertTrue(Filters.blocksAdvancement("minecraft:husbandry/plant_seed", "husbandry/plant_seed"));
        assertTrue(Filters.blocksAdvancement("minecraft:story/mine_stone", "story/mine_stone"));
        assertFalse(Filters.blocksAdvancement("minecraft:end/kill_dragon", "end/kill_dragon"),
                "an unrelated advancement must still be logged");
    }

    @ParameterizedTest
    @ValueSource(strings = {"recipes/misc/charcoal", "recipe/building/stairs", "story/root", "husbandry/root"})
    @DisplayName("recipe unlocks and tab roots are skipped by default")
    void recipesAndRootsSkipped(String path) {
        assertTrue(Filters.blocksAdvancement("minecraft:" + path, path),
                path + " fires constantly and means nothing to a reader");
    }

    @Test
    @DisplayName("recipes can be turned back on")
    void recipesOptIn() throws Exception {
        install(Set.of(), Set.of(), List.of(), 0, List.of(), true,
                Set.of(), 0d, Set.of(), Set.of(), 0);
        assertFalse(Filters.blocksAdvancement("minecraft:recipes/misc/charcoal", "recipes/misc/charcoal"));
    }

    // ---------------------------------------------------------------- teleport --

    @Test
    @DisplayName("teleports filter by cause")
    void teleportByCause() throws Exception {
        install(Set.of(), Set.of(), List.of(), 0, List.of(), false,
                Set.of("PLUGIN", "EXIT_BED"), 0d, Set.of(), Set.of(), 0);

        assertTrue(Filters.blocksTeleport("PLUGIN", 500d));
        assertTrue(Filters.blocksTeleport("exit_bed", 1d), "matching is case-insensitive");
        assertFalse(Filters.blocksTeleport("NETHER_PORTAL", 900d));
    }

    @Test
    @DisplayName("a cross-world teleport is never treated as a short hop")
    void crossWorldIsNeverShort() throws Exception {
        install(Set.of(), Set.of(), List.of(), 0, List.of(), false,
                Set.of(), 50d, Set.of(), Set.of(), 0);

        // Distance is null across worlds. Treating that as 0 would silently drop every
        // nether portal on a server that set a minimum distance.
        assertFalse(Filters.blocksTeleport("NETHER_PORTAL", null));
        assertTrue(Filters.blocksTeleport("ENDER_PEARL", 5d));
        assertFalse(Filters.blocksTeleport("ENDER_PEARL", 500d));
    }

    // --------------------------------------------------- death / explosion / chat --

    @Test
    @DisplayName("deaths filter by damage cause")
    void deathByCause() throws Exception {
        install(Set.of(), Set.of(), List.of(), 0, List.of(), false,
                Set.of(), 0d, Set.of("VOID"), Set.of(), 0);

        assertTrue(Filters.blocksDeath("VOID"));
        assertTrue(Filters.blocksDeath("void"));
        assertFalse(Filters.blocksDeath("FALL"));
        assertFalse(Filters.blocksDeath(null), "an unknown cause must not be silently dropped");
    }

    @Test
    @DisplayName("explosions filter by source and by how much they destroyed")
    void explosionFilters() throws Exception {
        install(Set.of(), Set.of(), List.of(), 0, List.of(), false,
                Set.of(), 0d, Set.of(), Set.of("CREEPER"), 5);

        assertTrue(Filters.blocksExplosion("CREEPER", 40), "blocked by source regardless of size");
        assertTrue(Filters.blocksExplosion("PRIMED_TNT", 2), "too small to matter");
        assertFalse(Filters.blocksExplosion("PRIMED_TNT", 40), "a real blast is still logged");
        assertFalse(Filters.blocksExplosion(null, 40), "a block explosion with no source name");
    }

    @Test
    @DisplayName("chat filters on content and on length")
    void chatFilters() throws Exception {
        install(Set.of(), Set.of(), List.of("discord.gg"), 3, List.of(), false,
                Set.of(), 0d, Set.of(), Set.of(), 0);

        assertTrue(Filters.blocksChat("hi"), "shorter than the minimum");
        assertTrue(Filters.blocksChat("  ?  "), "whitespace is trimmed before counting");
        assertTrue(Filters.blocksChat("join DISCORD.GG/x"), "substring match, case-insensitive");
        assertFalse(Filters.blocksChat("hello everyone"));
    }

    // ----------------------------------------------------------- shipped config --

    @Test
    @DisplayName("the shipped defaults exclude the teleports that are not really teleports")
    void shippedTeleportDefaults() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) new Yaml()
                .load(Files.readString(Path.of("src/main/resources/config.yml")));
        @SuppressWarnings("unchecked")
        Map<String, Object> filters = (Map<String, Object>) root.get("filters");
        @SuppressWarnings("unchecked")
        List<String> causes = (List<String>) filters.get("ignored_teleport_causes");

        // Minecraft reports these as teleports, but the player moved a block or two.
        // Logging them produces a Discord message for getting out of bed.
        assertTrue(causes.contains("EXIT_BED"), "standing up from a bed is not a teleport");
        assertTrue(causes.contains("DISMOUNT"), "getting off a horse is not a teleport");

        assertFalse(causes.contains("NETHER_PORTAL"), "a dimension change is worth logging");
        assertFalse(causes.contains("COMMAND"), "an admin using /tp is worth logging");
    }

    @Test
    @DisplayName("filters that would hide real events ship empty")
    void riskyFiltersShipEmpty() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) new Yaml()
                .load(Files.readString(Path.of("src/main/resources/config.yml")));
        @SuppressWarnings("unchecked")
        Map<String, Object> f = (Map<String, Object>) root.get("filters");

        assertEquals(List.of(), f.get("ignored_death_causes"), "deaths are the point of the plugin");
        assertEquals(List.of(), f.get("ignored_explosion_sources"));
        assertEquals(List.of(), f.get("only_log_commands"), "an allow-list default would hide everything else");
        assertEquals(0, f.get("minimum_chat_length"));
        assertEquals(0, f.get("minimum_explosion_blocks"));
        assertEquals(0, f.get("minimum_teleport_distance"));
    }
}
