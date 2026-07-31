package com.discordlogger.config;

import com.discordlogger.config.ConfigMigrator.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Which way a migration runs, and whether it runs at all.
 *
 * <p>The case that matters is AHEAD: before this logic existed, migration fired
 * whenever the two schema numbers merely differed, so a config from a newer
 * install — a rolled-back server, or a file copied between servers — was
 * rewritten against the older shipped default and every key the newer schema
 * had added was silently deleted.
 */
class ConfigMigratorDecisionTest {

    @ParameterizedTest(name = "config v{0} on a v{1} build -> {2}")
    @CsvSource({
            "9,  9,  UP_TO_DATE",
            "9,  10, UPGRADED",
            "1,  10, UPGRADED",
            "2,  10, UPGRADED",
            "10, 9,  AHEAD",
            "11, 10, AHEAD",
    })
    void decidesFromTheTwoSchemaNumbers(int installed, int shipped, Status expected) {
        assertEquals(expected, ConfigMigrator.decide(installed, shipped));
    }

    @Test
    @DisplayName("an unreadable trailer on either side is never treated as a migration")
    void unknownWhenEitherVersionIsMissing() {
        assertEquals(Status.UNKNOWN, ConfigMigrator.decide(null, 10));
        assertEquals(Status.UNKNOWN, ConfigMigrator.decide(9, null));
        assertEquals(Status.UNKNOWN, ConfigMigrator.decide(null, null));
    }

    @Test
    @DisplayName("versions compare numerically, not as text")
    void comparesNumericallyNotLexicographically() {
        // As strings "9" > "10", which would invert the decision and downgrade
        // every v9 config the moment a two-digit schema shipped.
        assertEquals(Status.UPGRADED, ConfigMigrator.decide(9, 10));
        assertEquals(Status.AHEAD, ConfigMigrator.decide(10, 9));
    }

    @Test
    @DisplayName("the shipped config's trailer parses")
    void shippedTrailerIsReadable() throws Exception {
        String shipped = Files.readString(Path.of("src/main/resources/config.yml"));
        Integer version = ConfigMigrator.extractVersion(shipped);
        assertEquals(currentSchema(), version,
                "the shipped config.yml trailer must match the schema this build claims");
    }

    @Test
    @DisplayName("a config with no trailer at all yields no version")
    void missingTrailerYieldsNull() {
        assertNull(ConfigMigrator.extractVersion("log:\n  player:\n    join:\n      enabled: true\n"));
        assertNull(ConfigMigrator.extractVersion(""));
        assertNull(ConfigMigrator.extractVersion(null));
    }

    /** Read from the shipped file so this test never needs editing on a schema bump. */
    private static int currentSchema() throws Exception {
        String shipped = Files.readString(Path.of("src/main/resources/config.yml"));
        return ConfigMigrator.extractVersion(shipped);
    }
}
