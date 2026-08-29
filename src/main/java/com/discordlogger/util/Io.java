package com.discordlogger.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;

/**
 * File and stream helpers that exist only because the plugin targets Java 8.
 *
 * <p>{@code InputStream.readAllBytes()} is Java 9 and {@code Files.writeString} is
 * Java 11. Both are used where a config file is read or written, which is the code
 * least tolerant of a behaviour change — so these do exactly what the originals did:
 * UTF-8 throughout, the same {@link OpenOption}s passed straight through, and no
 * silent charset default anywhere.
 */
public final class Io {

    private Io() {}

    /** Java 9's {@code InputStream.readAllBytes()}. */
    public static byte[] readAllBytes(InputStream in) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
        final byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }

    /** The whole stream as UTF-8 text. */
    public static String readString(InputStream in) throws IOException {
        return new String(readAllBytes(in), StandardCharsets.UTF_8);
    }

    /** The whole file as UTF-8 text. */
    public static String readString(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /** Java 11's {@code Files.writeString}, UTF-8, options passed through unchanged. */
    public static void writeString(Path path, String text, OpenOption... options)
            throws IOException {
        Files.write(path, text.getBytes(StandardCharsets.UTF_8), options);
    }

    /**
     * As above with an explicit charset, mirroring
     * {@code Files.writeString(Path, CharSequence, Charset, OpenOption...)}.
     *
     * <p>The overload exists so call sites read exactly as they did before the port.
     * Rewriting them to drop the charset argument would have been fewer lines here and
     * a worse diff to review: every one of these writes a config file, and the
     * charset is the last thing that should become implicit in that code.
     */
    public static void writeString(Path path, String text, Charset charset,
                                   OpenOption... options) throws IOException {
        Files.write(path, text.getBytes(charset), options);
    }
}
