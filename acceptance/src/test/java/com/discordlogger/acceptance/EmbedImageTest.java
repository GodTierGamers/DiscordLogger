package com.discordlogger.acceptance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The renderer, checked against payloads shaped exactly as the plugin builds them. */
class EmbedImageTest {

    /** A death embed, in the shape DiscordWebhook.buildEmbedJson produces. */
    private static final String DEATH = "{\"content\":null,\"embeds\":[{"
            + "\"title\":\"Player Death\","
            + "\"color\":15548997,"
            + "\"author\":{\"name\":\"Server Logs\"},"
            + "\"fields\":["
            + "{\"name\":\"Player:\",\"value\":\"LVCHLANN\",\"inline\":true},"
            + "{\"name\":\"Cause:\",\"value\":\"Fell from a high place\",\"inline\":true},"
            + "{\"name\":\"World:\",\"value\":\"world\",\"inline\":false},"
            + "{\"name\":\"Location:\",\"value\":\"world (-252, 66, 252)\",\"inline\":false}],"
            + "\"footer\":{\"text\":\"DiscordLogger v2.3.1\"},"
            + "\"timestamp\":\"2026-08-31T14:19:55Z\"}]}";

    @Test
    @DisplayName("an embed payload renders to a readable image")
    void rendersAnEmbed(@TempDir Path tmp) throws Exception {
        final Path png = tmp.resolve("death.png");
        EmbedImage.render(DEATH, png, "log.player.death.enabled = true");

        assertTrue(Files.size(png) > 2000, "image looks empty: " + Files.size(png) + " bytes");
        final BufferedImage img = ImageIO.read(png.toFile());
        assertTrue(img.getWidth() > 400 && img.getHeight() > 120,
                "unexpected canvas " + img.getWidth() + "x" + img.getHeight());

        // The accent bar must carry the embed's own colour, since that is the one part
        // of an embed a reader takes meaning from before reading any of it.
        boolean foundAccent = false;
        for (int y = 0; y < img.getHeight(); y++) {
            final int rgb = img.getRGB(18, y) & 0xFFFFFF;
            if (rgb == 15548997) { foundAccent = true; break; }
        }
        assertTrue(foundAccent, "the embed colour does not appear in the accent bar");
    }

    @Test
    @DisplayName("a plain-text payload renders too, rather than failing")
    void rendersPlainText(@TempDir Path tmp) throws Exception {
        // Plain text is a supported output mode, so it has to be reviewable as well.
        final Path png = tmp.resolve("plain.png");
        EmbedImage.render(
                "{\"content\":\"`19:51:49 31:08:2026` - **Server Start**: Server Started\"}",
                png, "embeds.enabled = false");
        assertTrue(Files.size(png) > 800, "image looks empty");
    }
}
