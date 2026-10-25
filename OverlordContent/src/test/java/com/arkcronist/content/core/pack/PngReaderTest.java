package com.arkcronist.content.core.pack;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 3.3.1: ItemsAdder's protected pictures read as the game reads them. */
class PngReaderTest {

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /**
     * As ItemsAdder protects a picture: every CRC wrong, the Adler-32 at the end of the picture data
     * wrong, and its own chunk after the data.
     */
    private static byte[] protect(byte[] png) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(png, 0, 8);
        int at = 8;
        while (at < png.length) {
            int length = ((png[at] & 0xFF) << 24) | ((png[at + 1] & 0xFF) << 16) | ((png[at + 2] & 0xFF) << 8)
                    | (png[at + 3] & 0xFF);
            String type = new String(png, at + 4, 4, StandardCharsets.ISO_8859_1);
            byte[] chunk = new byte[12 + length];
            System.arraycopy(png, at, chunk, 0, chunk.length);
            if (type.equals("IDAT")) {
                chunk[8 + length - 1] ^= 0x5A;
            }
            chunk[chunk.length - 1] ^= 0x33;
            if (type.equals("IEND")) {
                byte[] own = {0, 0, 0, 2, 'i', 'a', 'P', 'R', 1, 2, 9, 9, 9, 9};
                out.writeBytes(own);
            }
            out.writeBytes(chunk);
            at += chunk.length;
        }
        return out.toByteArray();
    }

    private static void assertSamePixels(BufferedImage expected, BufferedImage actual) {
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                assertEquals(expected.getRGB(x, y), actual.getRGB(x, y), "pixel " + x + "," + y);
            }
        }
    }

    @Test
    void aProtectedPictureReadsAsTheOriginal() throws IOException {
        BufferedImage original = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                original.setRGB(x, y, ((x * 8) << 24) | (x * 7 << 16) | (y * 5 << 8) | ((x ^ y) * 3));
            }
        }
        byte[] protectedPng = protect(png(original));
        assertThrows(IOException.class, () -> ImageIO.read(new ByteArrayInputStream(protectedPng)),
                "ImageIO refuses it, as it refuses ItemsAdder's");
        BufferedImage read = PngReader.read(protectedPng);
        assertNotNull(read);
        assertSamePixels(original, read);
    }

    @Test
    void aPaletteAndItsTransparencyAreKept() throws IOException {
        byte[] reds = {0, (byte) 255, 0};
        byte[] greens = {0, 0, (byte) 200};
        byte[] blues = {0, 0, 0};
        byte[] alphas = {0, (byte) 255, (byte) 128};
        BufferedImage original = new BufferedImage(16, 16, BufferedImage.TYPE_BYTE_INDEXED,
                new IndexColorModel(8, 3, reds, greens, blues, alphas));
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                original.getRaster().setSample(x, y, 0, (x + y) % 3);
            }
        }
        BufferedImage read = PngReader.read(protect(png(original)));
        assertNotNull(read);
        assertSamePixels(original, read);
    }

    @Test
    void aSoundPictureIsReadByImageIoAndSomethingElseIsNoPicture() throws IOException {
        BufferedImage original = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        original.setRGB(1, 2, 0xFF123456);
        assertSamePixels(original, PngReader.read(png(original)));
        assertNull(PngReader.read("not a picture at all".getBytes(StandardCharsets.UTF_8)));
        byte[] cut = protect(png(original));
        byte[] half = new byte[40];
        System.arraycopy(cut, 0, half, 0, half.length);
        assertThrows(IOException.class, () -> PngReader.read(half), "a PNG cut short is still refused");
    }
}
