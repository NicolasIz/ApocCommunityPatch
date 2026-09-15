package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.bukkit.datapack.PackBlocks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading the block palette out of a datapack's structures.
 *
 * <p>This is what decides whether forcing a pack onto an older game gives working buildings or
 * buildings with holes in them, so the thing it must not do is quietly return an empty answer.</p>
 */
class PackBlocksTest {

    @Test
    @DisplayName("palette block ids are found in a gzipped structure")
    void readsPalette(@TempDir Path root) throws IOException {
        Path zip = root.resolve("pack.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            entry(out, "pack.mcmeta", "{\"pack\": {\"pack_format\": 81}}".getBytes(StandardCharsets.UTF_8));
            entry(out, "data/x/structure/tower.nbt", gzip(nbt(
                    "minecraft:stone_bricks", "minecraft:copper_wall_torch", "minecraft:chest")));
        }

        assertEquals(Set.of("minecraft:stone_bricks", "minecraft:copper_wall_torch", "minecraft:chest"),
                PackBlocks.paletteBlocks(zip));
    }

    @Test
    @DisplayName("an ungzipped structure is read too")
    void readsUncompressed(@TempDir Path root) throws IOException {
        Path zip = root.resolve("pack.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            entry(out, "data/x/structure/plain.nbt", nbt("minecraft:oak_planks"));
        }
        assertEquals(Set.of("minecraft:oak_planks"), PackBlocks.paletteBlocks(zip));
    }

    @Test
    @DisplayName("an attribute modifier's name is not a block")
    void ignoresFreeText() {
        // These are real values of the same NBT tag - AttributeModifiers carry a Name too - and
        // counting them turned a clean pack into a page of warnings.
        assertFalse(PackBlocks.looksLikeBlockId("generic.attack_damage"));
        assertFalse(PackBlocks.looksLikeBlockId("Random spawn bonus"));
        assertFalse(PackBlocks.looksLikeBlockId("minecraft:horse.jump_strength"));
        assertFalse(PackBlocks.looksLikeBlockId("[{\"text\":\"Emerald Sword\"}]"));
        assertFalse(PackBlocks.looksLikeBlockId("no_namespace"));
        assertFalse(PackBlocks.looksLikeBlockId("a:b:c"));

        assertTrue(PackBlocks.looksLikeBlockId("minecraft:waxed_oxidized_copper_chest"));
        assertTrue(PackBlocks.looksLikeBlockId("somepack:some/nested_block"));
    }

    @Test
    @DisplayName("a pack with no structures reads as empty, not as an error")
    void noStructures(@TempDir Path root) throws IOException {
        Path zip = root.resolve("pack.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            entry(out, "pack.mcmeta", "{}".getBytes(StandardCharsets.UTF_8));
        }
        assertTrue(PackBlocks.paletteBlocks(zip).isEmpty());
        assertTrue(PackBlocks.paletteBlocks(root.resolve("absent.zip")).isEmpty());
    }

    /**
     * The bytes of a structure file's palette: a string tag named "Name" per entry.
     *
     * <p>Written by hand rather than with an NBT library, for the same reason the reader is: the
     * shape being relied on is the shape being tested.</p>
     */
    private static byte[] nbt(String... blocks) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x0A);                       // TAG_Compound, the root
        out.write(new byte[]{0, 0});           // with an empty name
        for (String block : blocks) {
            out.write(0x08);                   // TAG_String
            out.write(new byte[]{0, 4});       // name length
            out.write("Name".getBytes(StandardCharsets.UTF_8));
            byte[] value = block.getBytes(StandardCharsets.UTF_8);
            out.write(value.length >> 8);
            out.write(value.length & 0xFF);
            out.write(value);
        }
        // A jigsaw block's own fields are lower case, so they must not be mistaken for a palette.
        out.write(0x08);
        out.write(new byte[]{0, 4});
        out.write("pool".getBytes(StandardCharsets.UTF_8));
        byte[] pool = "minecraft:not_a_block".getBytes(StandardCharsets.UTF_8);
        out.write(pool.length >> 8);
        out.write(pool.length & 0xFF);
        out.write(pool);
        out.write(0x00);                       // TAG_End
        return out.toByteArray();
    }

    private static byte[] gzip(byte[] raw) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(raw);
        }
        return out.toByteArray();
    }

    private static void entry(ZipOutputStream out, String name, byte[] body) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(body);
        out.closeEntry();
    }
}
