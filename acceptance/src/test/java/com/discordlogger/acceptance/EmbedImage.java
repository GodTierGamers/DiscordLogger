package com.discordlogger.acceptance;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws a captured webhook payload the way Discord would show it.
 *
 * <h2>Why bother</h2>
 *
 * <p>Every assertion in this suite is about JSON, and JSON is a poor way to review
 * something whose entire purpose is how it looks. An embed can satisfy every
 * assertion and still be wrong in a way only a person notices -- a colour that reads
 * as an error, a field that wraps badly, a footer nobody wanted. A picture per run
 * makes that reviewable in a glance.
 *
 * <p>Java2D rather than a headless browser: the runner has no display, and adding a
 * browser to render a rectangle and some text would be a large dependency for the
 * benefit. Logical fonts are used because CI images carry almost none, and a missing
 * font renders as blank boxes rather than as an error.
 *
 * <p>This deliberately approximates Discord rather than reproducing it. The point is
 * to make a wrong embed obvious, not to be pixel-accurate.
 */
final class EmbedImage {

    // Discord's dark theme, sampled from the client.
    private static final Color CHAT_BG    = new Color(0x31, 0x33, 0x38);
    private static final Color EMBED_BG   = new Color(0x2B, 0x2D, 0x31);
    private static final Color TEXT       = new Color(0xDB, 0xDE, 0xE1);
    private static final Color MUTED      = new Color(0x94, 0x9B, 0xA4);
    private static final Color WHITE      = new Color(0xF2, 0xF3, 0xF5);

    // Proportions taken from a real client screenshot rather than chosen: an embed
    // rendered at the wrong scale is harder to judge than one that is plainly wrong,
    // and the whole point of the picture is that a person can eyeball it.
    private static final int WIDTH   = 520;
    private static final int PAD     = 16;
    private static final int BAR     = 5;
    private static final int EMBED_X = 12;
    private static final int THUMB   = 100;
    private static final int FOOTER_ICON = 22;
    private static final int TEXT_X  = EMBED_X + BAR + 24;

    /** Images are fetched once per run; a nightly sweep renders many embeds. */
    private static final Map<String, BufferedImage> IMAGE_CACHE = new HashMap<>();

    private EmbedImage() {}

    /** Renders the first embed in a webhook payload. Plain-text payloads render too. */
    static void render(String payloadJson, Path out, String caption) throws Exception {
        final JsonObject payload = JsonParser.parseString(payloadJson).getAsJsonObject();

        // Measure first, then draw: the height depends on how the text wraps, and
        // guessing it leaves either a cropped embed or a lake of empty space.
        final BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        final Graphics2D pg = probe.createGraphics();
        applyHints(pg);
        final List<Line> lines = layout(payload, pg, thumbUrl(payload) != null);
        final int height = (lines.isEmpty() ? 120
                : lines.get(lines.size() - 1).y + 30 + PAD)
                + (caption == null ? 0 : 22);
        pg.dispose();

        final BufferedImage img = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = img.createGraphics();
        applyHints(g);

        g.setColor(CHAT_BG);
        g.fillRect(0, 0, WIDTH, height);

        final int captionH = caption == null ? 0 : 22;
        final int embedTop = (lines.isEmpty() ? PAD : lines.get(0).y - 26) + captionH;
        final int embedBottom = height - PAD;
        final Color accent = accentOf(payload);
        g.setColor(EMBED_BG);
        g.fill(new RoundRectangle2D.Float(EMBED_X, embedTop,
                WIDTH - EMBED_X - PAD, embedBottom - embedTop, 8, 8));
        g.setColor(accent);
        g.fill(new RoundRectangle2D.Float(EMBED_X, embedTop, BAR + 4,
                embedBottom - embedTop, 8, 8));
        g.fillRect(EMBED_X + BAR, embedTop, 4, embedBottom - embedTop);

        // The player head, fetched from the URL the plugin itself put in the payload.
        // Fetching keeps the picture honest: it is the same image a reader would see in
        // Discord, not an approximation of it. The suite drives events as a player who
        // does not exist, and mc-heads answers any unknown id with Steve, so no real
        // account is involved and the result is stable.
        //
        // A drawn Steve stands in when the fetch fails, because a nightly run should not
        // go red over an image host being slow.
        final String thumb = thumbUrl(payload);
        if (thumb != null) {
            final BufferedImage head = fetch(thumb);
            if (head != null) {
                final java.awt.Shape clip = g.getClip();
                g.setClip(new RoundRectangle2D.Float(
                        WIDTH - PAD - THUMB - 4, embedTop + 18, THUMB, THUMB, 8, 8));
                g.drawImage(head, WIDTH - PAD - THUMB - 4, embedTop + 18, THUMB, THUMB, null);
                g.setClip(clip);
            } else {
                drawSteveHead(g, WIDTH - PAD - THUMB - 4, embedTop + 18, THUMB);
            }
        }
        drawFooterIcon(g, lines, captionH);

        if (caption != null) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            g.setColor(MUTED);
            g.drawString(caption, EMBED_X, PAD + 4);
        }
        for (Line l : lines) {
            g.setFont(l.font);
            g.setColor(l.color);
            g.drawString(l.text, l.x, l.y + captionH);
        }

        g.dispose();
        ImageIO.write(img, "png", out.toFile());
    }

    /** Downloads an image, or returns null rather than failing the run. */
    private static BufferedImage fetch(String url) {
        final BufferedImage cached = IMAGE_CACHE.get(url);
        if (cached != null) return cached;
        try {
            final URLConnection c = new URL(url).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            c.setRequestProperty("User-Agent", "DiscordLogger-acceptance");
            try (InputStream in = c.getInputStream()) {
                final BufferedImage img = ImageIO.read(in);
                if (img != null) IMAGE_CACHE.put(url, img);
                return img;
            }
        } catch (Exception unreachable) {
            return null;
        }
    }

    /** The plugin's own icon, as Discord shows it beside the footer. */
    private static BufferedImage pluginIcon() {
        return fetchResource("/discordlogger-icon.png");
    }

    private static BufferedImage fetchResource(String path) {
        final BufferedImage cached = IMAGE_CACHE.get(path);
        if (cached != null) return cached;
        try (InputStream in = EmbedImage.class.getResourceAsStream(path)) {
            if (in == null) return null;
            final BufferedImage img = ImageIO.read(in);
            if (img != null) IMAGE_CACHE.put(path, img);
            return img;
        } catch (Exception missing) {
            return null;
        }
    }

    private static String thumbUrl(JsonObject payload) {
        try {
            final JsonObject e = payload.getAsJsonArray("embeds").get(0).getAsJsonObject();
            final JsonObject t = e.getAsJsonObject("thumbnail");
            return t == null ? null : str(t, "url");
        } catch (Exception none) {
            return null;
        }
    }

    /**
     * A default Steve head, as pixels.
     *
     * <p>Eight by eight, scaled up, which is what a Minecraft skin's face actually is --
     * so the blockiness is accurate rather than a shortcut. Any real avatar would have
     * to be downloaded, and the renderer is used inside tests that must not depend on
     * a third-party image host being reachable.
     */
    private static void drawSteveHead(Graphics2D g, int x, int y, int size) {
        final Color hair  = new Color(0x35, 0x24, 0x18);
        final Color skin  = new Color(0xC6, 0x99, 0x74);
        final Color shade = new Color(0xB1, 0x86, 0x63);
        final Color eyeW  = new Color(0xEE, 0xEE, 0xEE);
        final Color eyeB  = new Color(0x33, 0x55, 0xA5);
        final Color mouth = new Color(0x7A, 0x4B, 0x3A);

        final Color[][] face = {
            {hair, hair, hair, hair, hair, hair, hair, hair},
            {hair, hair, hair, hair, hair, hair, hair, hair},
            {hair, skin, skin, skin, skin, skin, skin, hair},
            {skin, eyeW, eyeB, skin, skin, eyeB, eyeW, skin},
            {skin, skin, skin, shade, shade, skin, skin, skin},
            {skin, skin, mouth, mouth, mouth, mouth, skin, skin},
            {skin, skin, skin, skin, skin, skin, skin, skin},
            {shade, skin, skin, skin, skin, skin, skin, shade},
        };

        final int px = size / 8;
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                g.setColor(face[row][col]);
                g.fillRect(x + col * px, y + row * px, px, px);
            }
        }
    }

    /** The small round icon Discord shows beside the footer text. */
    private static void drawFooterIcon(Graphics2D g, List<Line> lines, int captionH) {
        Line footer = null;
        for (Line l : lines) if (l.footer) footer = l;
        if (footer == null) return;
        final Line last = footer;
        final int size = FOOTER_ICON;
        final int x = TEXT_X;
        final int y = last.y + captionH - 15;
        final BufferedImage icon = pluginIcon();
        if (icon != null) {
            // Discord clips footer icons to a circle.
            final java.awt.Shape clip = g.getClip();
            g.setClip(new java.awt.geom.Ellipse2D.Float(x, y, size, size));
            g.drawImage(icon, x, y, size, size, null);
            g.setClip(clip);
        } else {
            g.setColor(new Color(0x5A, 0x4A, 0x3A));
            g.fillOval(x, y, size, size);
        }
    }

    /** "2026-08-31T14:19:55Z" as the client shows it. */
    private static String friendlyTime(String iso) {
        final int t = iso.indexOf('T');
        if (t < 0 || iso.length() < t + 6) return iso;
        return "Today at " + iso.substring(t + 1, t + 6);
    }

    // ------------------------------------------------------------------ layout

    private static final class Line {
        final String text; final int x; final int y; final Font font; final Color color;
        /** Set on the footer, so its icon is placed without guessing from font size. */
        final boolean footer;
        Line(String text, int x, int y, Font font, Color color) {
            this(text, x, y, font, color, false);
        }
        Line(String text, int x, int y, Font font, Color color, boolean footer) {
            this.text = text; this.x = x; this.y = y; this.font = font;
            this.color = color; this.footer = footer;
        }
    }

    private static List<Line> layout(JsonObject payload, Graphics2D g, boolean hasThumb) {
        final List<Line> out = new ArrayList<>();
        final int x = TEXT_X;
        final int wrapWidth = WIDTH - x - PAD - (hasThumb ? THUMB + 20 : 0);
        int y = PAD + 34;

        // Sized against a real client screenshot rather than guessed: an embed that
        // renders at the wrong scale is harder to judge than one that is plainly wrong.
        final Font small   = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
        final Font smallB  = new Font(Font.SANS_SERIF, Font.BOLD, 16);
        final Font body    = new Font(Font.SANS_SERIF, Font.PLAIN, 16);
        final Font titleF  = new Font(Font.SANS_SERIF, Font.BOLD, 21);
        final Font authorF = new Font(Font.SANS_SERIF, Font.BOLD, 16);

        final JsonArray embeds = payload.has("embeds") && payload.get("embeds").isJsonArray()
                ? payload.getAsJsonArray("embeds") : null;

        if (embeds == null || embeds.isEmpty()) {
            // A plain-text payload is a legitimate output mode, so show it as one.
            final String content = str(payload, "content");
            for (String l : wrap(content == null ? "(empty payload)" : content, body, g, wrapWidth)) {
                out.add(new Line(l, x, y, body, TEXT));
                y += 20;
            }
            return out;
        }

        final JsonObject e = embeds.get(0).getAsJsonObject();

        final JsonObject author = e.has("author") && e.get("author").isJsonObject()
                ? e.getAsJsonObject("author") : null;
        if (author != null && author.has("name")) {
            out.add(new Line(author.get("name").getAsString(), x, y, authorF, WHITE));
            y += 38;
        }
        final String title = str(e, "title");
        if (title != null) {
            for (String l : wrap(title, titleF, g, wrapWidth)) {
                out.add(new Line(l, x, y, titleF, WHITE));
                y += 34;
            }
        }
        final String desc = str(e, "description");
        if (desc != null) {
            for (String l : wrap(desc, body, g, wrapWidth)) {
                out.add(new Line(l, x, y, body, TEXT));
                y += 24;
            }
            y += 14;
        }

        if (e.has("fields") && e.get("fields").isJsonArray()) {
            y += 6;
            final JsonArray fields = e.getAsJsonArray("fields");
            int column = 0;
            int rowTop = y;
            for (JsonElement fe : fields) {
                final JsonObject f = fe.getAsJsonObject();
                final boolean inline = f.has("inline") && f.get("inline").getAsBoolean();
                final int colWidth = inline ? (wrapWidth / 2) - 8 : wrapWidth;
                final int fx = inline && column == 1 ? x + wrapWidth / 2 : x;

                if (!inline || column == 0) rowTop = y;
                int fy = rowTop;

                out.add(new Line(str(f, "name"), fx, fy, smallB, WHITE));
                fy += 26;
                for (String l : wrap(String.valueOf(str(f, "value")), body, g, colWidth)) {
                    out.add(new Line(l, fx, fy, body, TEXT));
                    fy += 24;
                }

                if (inline && column == 0) {
                    column = 1;
                    y = Math.max(y, fy);
                } else {
                    column = 0;
                    y = Math.max(y, fy) + 12;
                }
            }
        }

        final JsonObject footer = e.has("footer") && e.get("footer").isJsonObject()
                ? e.getAsJsonObject("footer") : null;
        if (footer != null && footer.has("text")) {
            y += 18;
            String text = footer.get("text").getAsString();
            final String ts = str(e, "timestamp");
            if (ts != null) text = text + "  •  " + friendlyTime(ts);
            // Indented past the footer icon drawn in render().
            out.add(new Line(text, x + FOOTER_ICON + 10, y, small, MUTED, true));
            y += 20;
        }
        return out;
    }

    private static Color accentOf(JsonObject payload) {
        try {
            final JsonObject e = payload.getAsJsonArray("embeds").get(0).getAsJsonObject();
            if (e.has("color")) return new Color(e.get("color").getAsInt());
        } catch (Exception notAnEmbed) {
            // Plain text has no colour; Discord shows no bar at all.
        }
        return new Color(0x4F, 0x54, 0x5C);
    }

    private static String str(JsonObject o, String key) {
        return o != null && o.has(key) && !o.get(key).isJsonNull()
                ? o.get(key).getAsString() : null;
    }

    private static List<String> wrap(String text, Font font, Graphics2D g, int width) {
        final List<String> out = new ArrayList<>();
        if (text == null) return out;
        final FontRenderContext frc = g.getFontRenderContext();
        for (String paragraph : text.split("\n")) {
            final StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                final String candidate = line.length() == 0 ? word : line + " " + word;
                if (font.getStringBounds(candidate, frc).getWidth() > width && line.length() > 0) {
                    out.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                } else {
                    line.setLength(0);
                    line.append(candidate);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    private static void applyHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setStroke(new BasicStroke(1f));
    }
}
