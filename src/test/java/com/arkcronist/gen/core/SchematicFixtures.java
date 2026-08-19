package com.arkcronist.gen.core;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Builds small Sponge schematics on the fly so tests can exercise prefab categories the bundled
 * files do not cover.
 *
 * <p>Writing the file rather than constructing a {@code Prefab} directly is deliberate: it puts the
 * gzip layer, the NBT parser, the varint block stream and the folder loader all under test, which is
 * exactly the path a server owner's own schematic travels.</p>
 */
final class SchematicFixtures {

    private final int width;
    private final int height;
    private final int length;
    private final Map<String, Integer> palette = new LinkedHashMap<>();
    private final int[] blocks;

    SchematicFixtures(int width, int height, int length) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.blocks = new int[width * height * length];
        id("minecraft:air"); // palette entry 0, which is what the array is already full of
    }

    private int id(String state) {
        return palette.computeIfAbsent(state, key -> palette.size());
    }

    void set(int x, int y, int z, String state) {
        if (x < 0 || y < 0 || z < 0 || x >= width || y >= height || z >= length) {
            return;
        }
        blocks[y * width * length + z * width + x] = id(state);
    }

    void box(int x0, int y0, int z0, int x1, int y1, int z1, String state) {
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    set(x, y, z, state);
                }
            }
        }
    }

    /** A cottage with walls, a roof, a door, a bed, a chest and a lantern. */
    static SchematicFixtures house(int size, int wallHeight, String wall, String roof) {
        SchematicFixtures schematic = new SchematicFixtures(size, wallHeight + 3, size);
        int last = size - 1;
        schematic.box(0, 0, 0, last, 0, last, "minecraft:cobblestone");
        for (int y = 1; y <= wallHeight; y++) {
            schematic.box(0, y, 0, last, y, 0, wall);
            schematic.box(0, y, last, last, y, last, wall);
            schematic.box(0, y, 0, 0, y, last, wall);
            schematic.box(last, y, 0, last, y, last, wall);
        }
        schematic.box(0, wallHeight + 1, 0, last, wallHeight + 1, last, roof);
        schematic.box(1, wallHeight + 2, 1, last - 1, wallHeight + 2, last - 1, roof);
        int middle = size / 2;
        // A door facing north, so a rotation test has something directional to check.
        schematic.set(middle, 1, 0, "minecraft:oak_door[facing=north,half=lower,hinge=left,open=false,powered=false]");
        schematic.set(middle, 2, 0, "minecraft:oak_door[facing=north,half=upper,hinge=left,open=false,powered=false]");
        schematic.set(1, 1, 1, "minecraft:chest[facing=south,type=single,waterlogged=false]");
        schematic.set(last - 1, 1, 1, "minecraft:red_bed[facing=south,occupied=false,part=head]");
        schematic.set(middle, wallHeight, middle, "minecraft:lantern[hanging=true,waterlogged=false]");
        return schematic;
    }

    /** A tower: a round-ish shaft with battlements and a spawner in the base. */
    static SchematicFixtures tower(int size, int height) {
        SchematicFixtures schematic = new SchematicFixtures(size, height, size);
        int centre = size / 2;
        double radius = size / 2.0 - 0.5;
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    double distance = Math.hypot(x - centre, z - centre);
                    boolean shell = distance <= radius && distance > radius - 1.2;
                    boolean floor = distance <= radius && (y == 0 || y == height - 1);
                    if (shell && y == height - 1 && (x + z) % 2 == 0) {
                        continue; // battlement gaps
                    }
                    if (shell || floor) {
                        schematic.set(x, y, z, "minecraft:stone_bricks");
                    }
                }
            }
        }
        schematic.set(centre, 1, centre, "minecraft:spawner");
        schematic.set(centre - 1, 2, centre, "minecraft:chest[facing=east,type=single,waterlogged=false]");
        schematic.set(centre, height - 2, centre, "minecraft:torch");
        return schematic;
    }

    /** A keep: a walled square with a taller block in the middle. */
    static SchematicFixtures castle(int size, int wallHeight, int keepHeight) {
        SchematicFixtures schematic = new SchematicFixtures(size, keepHeight, size);
        int last = size - 1;
        for (int y = 0; y <= wallHeight; y++) {
            schematic.box(0, y, 0, last, y, 0, "minecraft:stone_bricks");
            schematic.box(0, y, last, last, y, last, "minecraft:stone_bricks");
            schematic.box(0, y, 0, 0, y, last, "minecraft:stone_bricks");
            schematic.box(last, y, 0, last, y, last, "minecraft:stone_bricks");
        }
        int inner = size / 4;
        schematic.box(inner, 0, inner, size - inner, keepHeight - 1, size - inner, "minecraft:stone_bricks");
        schematic.box(inner + 1, 1, inner + 1, size - inner - 1, keepHeight - 2, size - inner - 1,
                "minecraft:air");
        schematic.set(inner + 1, 1, inner + 1, "minecraft:chest[facing=south,type=single,waterlogged=false]");
        schematic.set(size / 2, wallHeight, size / 2, "minecraft:lantern[hanging=false,waterlogged=false]");
        schematic.set(1, wallHeight, 1, "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]");
        return schematic;
    }

    /** Writes this schematic where the prefab registry will find it. */
    void write(Path directory, String name) throws IOException {
        Files.createDirectories(directory);
        Files.write(directory.resolve(name + ".schem"), toBytes());
    }

    byte[] toBytes() throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(body);
        out.writeByte(10);
        writeString(out, "Schematic");

        writeNamed(out, 3, "Version");
        out.writeInt(2);
        writeNamed(out, 3, "DataVersion");
        out.writeInt(3120);
        writeNamed(out, 2, "Width");
        out.writeShort(width);
        writeNamed(out, 2, "Height");
        out.writeShort(height);
        writeNamed(out, 2, "Length");
        out.writeShort(length);
        writeNamed(out, 3, "PaletteMax");
        out.writeInt(palette.size());

        writeNamed(out, 10, "Palette");
        for (Map.Entry<String, Integer> entry : palette.entrySet()) {
            writeNamed(out, 3, entry.getKey());
            out.writeInt(entry.getValue());
        }
        out.writeByte(0);

        byte[] data = varints(blocks);
        writeNamed(out, 7, "BlockData");
        out.writeInt(data.length);
        out.write(data);

        out.writeByte(0); // end of root compound
        out.flush();

        ByteArrayOutputStream gzipped = new ByteArrayOutputStream();
        try (GZIPOutputStream zip = new GZIPOutputStream(gzipped)) {
            zip.write(body.toByteArray());
        }
        return gzipped.toByteArray();
    }

    private static void writeNamed(DataOutputStream out, int tag, String name) throws IOException {
        out.writeByte(tag);
        writeString(out, name);
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static byte[] varints(int[] values) {
        List<Byte> bytes = new ArrayList<>(values.length);
        for (int value : values) {
            int remaining = value;
            while (true) {
                int piece = remaining & 0x7F;
                remaining >>>= 7;
                if (remaining != 0) {
                    bytes.add((byte) (piece | 0x80));
                } else {
                    bytes.add((byte) piece);
                    break;
                }
            }
        }
        byte[] out = new byte[bytes.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = bytes.get(i);
        }
        return out;
    }
}
