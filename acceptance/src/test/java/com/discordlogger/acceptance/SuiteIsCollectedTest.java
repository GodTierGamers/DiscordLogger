package com.discordlogger.acceptance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every test class in this module is one Surefire will actually run.
 *
 * <p>PlayerCategorySweep was written, compiled, committed and never executed. Surefire
 * collects only {@code *Test}, {@code Test*}, {@code *Tests} and {@code *TestCase}, so a
 * class named for what it does rather than for the runner is skipped without being
 * reported as skipped. The suite stayed green across four versions while the sweep it
 * was built for did nothing.
 *
 * <p>That failure mode is invisible by construction, so it needs a test of its own.
 */
class SuiteIsCollectedTest {

    @Test
    @DisplayName("no test class is named in a way Surefire ignores")
    void everyTestClassIsCollectable() throws Exception {
        final Path src = Path.of("src", "test", "java", "com", "discordlogger", "acceptance");
        final List<String> uncollected = new ArrayList<>();

        try (Stream<Path> files = Files.list(src)) {
            for (Path f : files.toList()) {
                final String name = f.getFileName().toString();
                if (!name.endsWith(".java")) continue;
                final String cls = name.substring(0, name.length() - ".java".length());

                // Only classes that actually declare tests need a runnable name.
                final String body = Files.readString(f);
                final boolean hasTests = body.contains("@Test") || body.contains("@ParameterizedTest");
                if (!hasTests) continue;

                final boolean collectable = cls.endsWith("Test") || cls.startsWith("Test")
                        || cls.endsWith("Tests") || cls.endsWith("TestCase");
                if (!collectable) uncollected.add(cls);
            }
        }

        assertEquals(List.of(), uncollected,
                "these classes declare tests but Surefire will never collect them, so they "
                        + "run silently as nothing:\n  " + String.join("\n  ", uncollected)
                        + "\n\nRename to end in Test.");
    }
}
