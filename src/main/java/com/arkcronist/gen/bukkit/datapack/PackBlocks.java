package com.arkcronist.gen.bukkit.datapack;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The blocks a datapack's structures are built out of.
 *
 * <p>This exists to answer one question before a pack is forced onto a server it was not built for:
 * does it use blocks this game has? A pack made for a later version loads happily once its declared
 * format is changed, and then every block the game does not recognise is dropped from the structure
 * as it generates. The building comes out with holes in it, nothing is logged, and the cause is three
 * versions away from the symptom.</p>
 *
 * <h2>Reading the palette without an NBT library</h2>
 *
 * <p>A structure file is gzipped NBT, and the block ids live in {@code palette[].Name}. Rather than
 * parse the whole format, this looks for the exact bytes of a string tag named {@code Name}: the tag
 * type {@code 0x08}, the two-byte length {@code 4}, the letters {@code Name}, then the two-byte
 * length and text of the value. Nothing else in a structure file is a string tag under that exact
 * name - a jigsaw block's fields are {@code name}, {@code target} and {@code pool}, all lower case,
 * and an entity's is {@code id} - so the match is the palette and only the palette.</p>
 *
 * <p>The one thing that does slip through is an attribute modifier's {@code Name}, which is free
 * text like {@code generic.attack_damage} or {@code Random spawn bonus}. Those are dropped by
 * shape: a block id is a namespace and a path, lower case, no spaces and no dots.</p>
 */
public final class PackBlocks {

    /** The bytes of a string tag named "Name": type 0x08, name length 4, then the name. */
    private static final byte[] NAME_TAG = {0x08, 0x00, 0x04, 'N', 'a', 'm', 'e'};

    private PackBlocks() {
    }

    /**
     * Every block id used in the structures inside a datapack.
     *
     * @return the ids, in the order first seen; empty when the zip has no structures or cannot be
     *     read, which is not an error - plenty of datapacks contain no structures at all
     */
    public static Set<String> paletteBlocks(Path zip) {
        Set<String> blocks = new LinkedHashSet<>();
        try (ZipFile file = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".nbt")) {
                    continue;
                }
                try (InputStream in = file.getInputStream(entry)) {
                    collect(decompress(in.readAllBytes()), blocks);
                } catch (IOException ignored) {
                    // One unreadable structure is not a reason to abandon the other four hundred.
                }
            }
        } catch (IOException | IllegalStateException | SecurityException exception) {
            return blocks;
        }
        return blocks;
    }

    /** A structure file is gzipped; a few are not. */
    private static byte[] decompress(byte[] raw) {
        boolean gzipped = raw.length > 1 && (raw[0] & 0xFF) == 0x1F && (raw[1] & 0xFF) == 0x8B;
        if (!gzipped) {
            return raw;
        }
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(raw))) {
            return in.readAllBytes();
        } catch (IOException exception) {
            return raw;
        }
    }

    private static void collect(byte[] nbt, Set<String> into) {
        int at = 0;
        while ((at = indexOf(nbt, NAME_TAG, at)) >= 0) {
            int valueLength = at + NAME_TAG.length;
            if (valueLength + 2 > nbt.length) {
                return;
            }
            int length = ((nbt[valueLength] & 0xFF) << 8) | (nbt[valueLength + 1] & 0xFF);
            int start = valueLength + 2;
            if (length > 0 && start + length <= nbt.length) {
                String value = new String(nbt, start, length, StandardCharsets.UTF_8);
                if (looksLikeBlockId(value)) {
                    into.add(value);
                }
            }
            at = valueLength;
        }
    }

    /**
     * Whether a string is shaped like a block id rather than free text.
     *
     * <p>{@code minecraft:copper_wall_torch} yes; {@code generic.attack_damage} and
     * {@code Random spawn bonus} no.</p>
     */
    public static boolean looksLikeBlockId(String value) {
        int colon = value.indexOf(':');
        if (colon <= 0 || colon == value.length() - 1 || value.indexOf(':', colon + 1) >= 0) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '/' || c == '-' || c == ':';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = Math.max(0, from); i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
