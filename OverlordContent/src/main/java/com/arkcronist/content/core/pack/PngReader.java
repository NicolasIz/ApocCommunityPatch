package com.arkcronist.content.core.pack;

import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * 3.3.1: reads a PNG as Minecraft's client does - without checking its checksums.
 *
 * <p>ItemsAdder's protected packs ship pictures with an {@code iaPR} chunk, wrong chunk CRCs and a
 * wrong zlib Adler-32 after the picture data. The game draws them; ImageIO refuses them ("incorrect
 * data check"). A picture the game shows has to be read here too - for Bedrock, and to measure a
 * glyph - so what ImageIO refuses is put together again from the same bytes, with the checksums
 * worked out, and read once more. Nothing of the picture changes.</p>
 */
public final class PngReader {

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    /** Larger than any texture a pack holds; past it the data is not unpacked. */
    private static final long MAX_PIXELS = 16_384L * 16_384L;

    private PngReader() {
    }

    /**
     * The picture; null when the bytes are no image ImageIO knows, as {@link ImageIO#read} says.
     *
     * @throws IOException when it is a PNG that cannot be read even without its checksums
     */
    public static @Nullable BufferedImage read(byte[] bytes) throws IOException {
        IOException refused;
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image != null || !isPng(bytes)) {
                return image;
            }
            refused = new IOException("not a PNG ImageIO can read");
        } catch (IOException | RuntimeException failure) {
            if (!isPng(bytes)) {
                throw failure instanceof IOException io ? io : new IOException(failure);
            }
            refused = failure instanceof IOException io ? io : new IOException(failure);
        }
        try {
            return ImageIO.read(new ByteArrayInputStream(repaired(bytes)));
        } catch (IOException | RuntimeException alsoFailed) {
            refused.addSuppressed(alsoFailed);
            throw refused;
        }
    }

    static boolean isPng(byte[] bytes) {
        if (bytes.length < SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < SIGNATURE.length; i++) {
            if (bytes[i] != SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * The same picture as a PNG ImageIO takes: the header, palette and transparency kept, the picture
     * data unpacked without its Adler-32 and packed again, every chunk with its CRC. Chunks the
     * picture does not need, ItemsAdder's among them, are left out.
     */
    static byte[] repaired(byte[] png) throws IOException {
        if (!isPng(png)) {
            throw new IOException("not a PNG");
        }
        byte[] header = null;
        byte[] palette = null;
        byte[] transparency = null;
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int at = SIGNATURE.length;
        while (at + 8 <= png.length) {
            long length = Integer.toUnsignedLong(readInt(png, at));
            String type = new String(png, at + 4, 4, StandardCharsets.ISO_8859_1);
            if (at + 12 + length > png.length) {
                if (type.equals("IDAT") || data.size() == 0) {
                    throw new IOException("the " + type + " chunk is cut short");
                }
                break; // what follows the picture data does not matter to it
            }
            int start = at + 8;
            int size = (int) length;
            switch (type) {
                case "IHDR" -> header = copy(png, start, size);
                case "PLTE" -> palette = copy(png, start, size);
                case "tRNS" -> transparency = copy(png, start, size);
                case "IDAT" -> data.write(png, start, size);
                default -> {
                }
            }
            at = start + size + 4;
            if (type.equals("IEND")) {
                break;
            }
        }
        if (header == null || header.length != 13) {
            throw new IOException("no IHDR chunk");
        }
        if (data.size() == 0) {
            throw new IOException("no picture data");
        }
        long width = Integer.toUnsignedLong(readInt(header, 0));
        long height = Integer.toUnsignedLong(readInt(header, 4));
        if (width == 0 || height == 0 || width * height > MAX_PIXELS) {
            throw new IOException("a " + width + "x" + height + " picture");
        }
        // Eight bytes a pixel at most (16-bit RGBA), a filter byte a row, room for interlacing.
        long most = (width * 8 + 1) * height * 2 + 1024;
        byte[] pixels = inflate(data.toByteArray(), most);

        ByteArrayOutputStream out = new ByteArrayOutputStream(pixels.length / 2 + 256);
        out.write(SIGNATURE);
        chunk(out, "IHDR", header);
        if (palette != null) {
            chunk(out, "PLTE", palette);
        }
        if (transparency != null) {
            chunk(out, "tRNS", transparency);
        }
        chunk(out, "IDAT", deflate(pixels));
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    /** The zlib stream's data, its two header bytes skipped and its Adler-32 never looked at. */
    private static byte[] inflate(byte[] zlib, long most) throws IOException {
        if (zlib.length < 2 || (zlib[0] & 0x0F) != 8 || (zlib[1] & 0x20) != 0) {
            throw new IOException("picture data that is not plain zlib");
        }
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(zlib, 2, zlib.length - 2);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16_384];
            while (!inflater.finished()) {
                int read = inflater.inflate(buffer);
                if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break; // cut short: what there is, as the client draws it
                }
                out.write(buffer, 0, read);
                if (out.size() > most) {
                    throw new IOException("more picture data than such a picture holds");
                }
            }
            return out.toByteArray();
        } catch (DataFormatException broken) {
            throw new IOException("broken picture data: " + broken.getMessage(), broken);
        } finally {
            inflater.end();
        }
    }

    private static byte[] deflate(byte[] pixels) {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try {
            deflater.setInput(pixels);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(pixels.length / 2 + 64);
            byte[] buffer = new byte[16_384];
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] name = type.getBytes(StandardCharsets.ISO_8859_1);
        writeInt(out, data.length);
        out.writeBytes(name);
        out.writeBytes(data);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        writeInt(out, (int) crc.getValue());
    }

    private static byte[] copy(byte[] from, int start, int size) {
        byte[] copy = new byte[size];
        System.arraycopy(from, start, copy, 0, size);
        return copy;
    }

    private static int readInt(byte[] bytes, int at) {
        return ((bytes[at] & 0xFF) << 24) | ((bytes[at + 1] & 0xFF) << 16) | ((bytes[at + 2] & 0xFF) << 8)
                | (bytes[at + 3] & 0xFF);
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value >>> 24);
        out.write(value >>> 16);
        out.write(value >>> 8);
        out.write(value);
    }
}
