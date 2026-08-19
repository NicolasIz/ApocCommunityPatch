package com.arkcronist.gen.core.prefab;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * A dependency free reader for Minecraft's NBT format, gzip aware.
 *
 * <p>Only reading is implemented, and only into plain Java containers: a compound becomes a
 * {@code Map<String,Object>}, a list becomes a {@code List<Object>}, and the numeric tags become
 * their boxed equivalents. That is everything a schematic needs, and it keeps the generator free of
 * any third party library - the plugin still ships as a single jar with no shaded dependencies.</p>
 */
public final class NbtReader {

    private static final int TAG_END = 0;
    private static final int TAG_BYTE = 1;
    private static final int TAG_SHORT = 2;
    private static final int TAG_INT = 3;
    private static final int TAG_LONG = 4;
    private static final int TAG_FLOAT = 5;
    private static final int TAG_DOUBLE = 6;
    private static final int TAG_BYTE_ARRAY = 7;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;
    private static final int TAG_INT_ARRAY = 11;
    private static final int TAG_LONG_ARRAY = 12;

    /** Guard against a corrupt or hostile file claiming a billion element array. */
    private static final int MAX_ARRAY = 1 << 26;

    private NbtReader() {
    }

    /** Reads a (possibly gzipped) NBT stream and returns the root compound. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> read(InputStream raw) throws IOException {
        InputStream stream = maybeGunzip(raw);
        DataInputStream in = new DataInputStream(new BufferedInputStream(stream, 1 << 16));
        int type = in.readUnsignedByte();
        if (type != TAG_COMPOUND) {
            throw new IOException("NBT root is tag " + type + ", expected a compound");
        }
        in.readUTF(); // root name, unused
        Object root = payload(in, TAG_COMPOUND, 0);
        return (Map<String, Object>) root;
    }

    private static InputStream maybeGunzip(InputStream raw) throws IOException {
        PushbackInputStream pushback = new PushbackInputStream(raw, 2);
        int first = pushback.read();
        if (first < 0) {
            throw new EOFException("empty NBT stream");
        }
        int second = pushback.read();
        if (second >= 0) {
            pushback.unread(second);
        }
        pushback.unread(first);
        boolean gzipped = first == 0x1F && second == 0x8B;
        return gzipped ? new GZIPInputStream(pushback, 1 << 16) : pushback;
    }

    private static Object payload(DataInputStream in, int type, int depth) throws IOException {
        if (depth > 64) {
            throw new IOException("NBT nested deeper than 64 levels");
        }
        switch (type) {
            case TAG_END:
                return null;
            case TAG_BYTE:
                return in.readByte();
            case TAG_SHORT:
                return in.readShort();
            case TAG_INT:
                return in.readInt();
            case TAG_LONG:
                return in.readLong();
            case TAG_FLOAT:
                return in.readFloat();
            case TAG_DOUBLE:
                return in.readDouble();
            case TAG_BYTE_ARRAY: {
                byte[] bytes = new byte[length(in)];
                in.readFully(bytes);
                return bytes;
            }
            case TAG_STRING:
                return in.readUTF();
            case TAG_LIST: {
                int elementType = in.readUnsignedByte();
                int count = in.readInt();
                if (count < 0) {
                    count = 0;
                }
                if (count > MAX_ARRAY) {
                    throw new IOException("NBT list of " + count + " elements is too large");
                }
                List<Object> list = new ArrayList<>(Math.min(count, 1024));
                for (int i = 0; i < count; i++) {
                    list.add(payload(in, elementType, depth + 1));
                }
                return list;
            }
            case TAG_COMPOUND: {
                Map<String, Object> map = new HashMap<>();
                while (true) {
                    int childType = in.readUnsignedByte();
                    if (childType == TAG_END) {
                        return map;
                    }
                    String name = in.readUTF();
                    map.put(name, payload(in, childType, depth + 1));
                }
            }
            case TAG_INT_ARRAY: {
                int[] values = new int[length(in)];
                for (int i = 0; i < values.length; i++) {
                    values[i] = in.readInt();
                }
                return values;
            }
            case TAG_LONG_ARRAY: {
                long[] values = new long[length(in)];
                for (int i = 0; i < values.length; i++) {
                    values[i] = in.readLong();
                }
                return values;
            }
            default:
                throw new IOException("Unknown NBT tag type " + type);
        }
    }

    private static int length(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_ARRAY) {
            throw new IOException("NBT array length " + length + " is out of range");
        }
        return length;
    }

    /** Reads an integer valued tag, whatever numeric width it was written with. */
    public static int intAt(Map<String, Object> compound, String key, int fallback) {
        Object value = compound.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }
}
