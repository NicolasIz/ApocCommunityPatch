package com.arkcronist.content.core.hud;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * How far the client moves on after drawing a bitmap glyph - which a HUD has to know to the pixel,
 * to put the next icon where it belongs and to step back over the whole bar.
 *
 * <p>The client does not use the image's width: it scans for the rightmost column holding any
 * pixel that is not fully transparent, scales that to the glyph's height, rounds, and adds one
 * pixel of spacing. This repeats that sum.</p>
 */
public final class GlyphMetrics {

    private GlyphMetrics() {
    }

    /**
     * The advance of the glyph drawn from {@code image} at {@code height} font pixels.
     *
     * @throws IOException when the file is not an image Java can read
     */
    public static int advance(Path image, int height) throws IOException {
        BufferedImage read;
        try (var in = Files.newInputStream(image)) {
            read = ImageIO.read(in);
        }
        if (read == null) {
            throw new IOException(image.getFileName() + " is not a readable image");
        }
        return advance(read, height);
    }

    public static int advance(BufferedImage image, int height) {
        float scale = (float) height / image.getHeight();
        return (int) (0.5 + actualWidth(image) * scale) + 1;
    }

    /** One past the rightmost column with a pixel that is not fully transparent; 0 for an empty image. */
    static int actualWidth(BufferedImage image) {
        for (int x = image.getWidth() - 1; x >= 0; x--) {
            for (int y = 0; y < image.getHeight(); y++) {
                if ((image.getRGB(x, y) >>> 24) != 0) {
                    return x + 1;
                }
            }
        }
        return 0;
    }
}
