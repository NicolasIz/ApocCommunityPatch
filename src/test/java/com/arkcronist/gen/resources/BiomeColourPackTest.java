package com.arkcronist.gen.resources;

import com.arkcronist.gen.bukkit.colour.BiomeColourPack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The colour datapack has to be valid JSON in the shape the game expects.
 *
 * <p>It is written into a live world folder, so a malformed file is not a cosmetic problem. The
 * saving grace is that nothing depends on it - a pack that fails to load leaves the biome on its
 * vanilla key - but "it degrades safely" is not a reason to ship something broken.</p>
 */
class BiomeColourPackTest {

    private static final Logger QUIET = Logger.getLogger("test");

    private static Map<String, BiomeColourPack.Colours> table() {
        Map<String, BiomeColourPack.Colours> map = new LinkedHashMap<>();
        map.put("scarlet_forest", new BiomeColourPack.Colours(
                "minecraft:dark_forest", 0xA32020, 0x8E1B1B, null, null, 0x7A4242, 0x5C2626,
                0.6, 0.8, true));
        map.put("cherry_grove", new BiomeColourPack.Colours(
                "minecraft:cherry_grove", 0xB9D67F, null, null, null, null, null,
                0.5, 0.8, true));
        return map;
    }

    @Test
    @DisplayName("the pack is written where the game looks for it")
    void writesThePackLayout(@TempDir Path world) {
        assertTrue(BiomeColourPack.install(world, table(), QUIET));
        Path root = world.resolve("datapacks").resolve("arkcronist_colours");
        assertTrue(Files.isRegularFile(root.resolve("pack.mcmeta")), "pack.mcmeta missing");
        Path biomes = root.resolve("data").resolve("arkcronist").resolve("worldgen").resolve("biome");
        assertTrue(Files.isRegularFile(biomes.resolve("scarlet_forest.json")), "scarlet_forest.json missing");
        assertTrue(Files.isRegularFile(biomes.resolve("cherry_grove.json")), "cherry_grove.json missing");
    }

    @Test
    @DisplayName("every file it writes parses, and carries the fields a biome needs")
    void writesValidBiomeJson(@TempDir Path world) throws Exception {
        BiomeColourPack.install(world, table(), QUIET);
        Path biomes = world.resolve("datapacks").resolve("arkcronist_colours")
                .resolve("data").resolve("arkcronist").resolve("worldgen").resolve("biome");

        for (String name : new String[]{"scarlet_forest", "cherry_grove"}) {
            String body = Files.readString(biomes.resolve(name + ".json"));
            assertBalanced(body, name);
            for (String required : new String[]{"has_precipitation", "temperature", "downfall",
                    "effects", "spawners", "spawn_costs", "carvers", "features"}) {
                assertTrue(body.contains("\"" + required + "\""),
                        name + ".json is missing the '" + required + "' field");
            }
            assertTrue(body.contains("\"grass_color\""), name + " asked for grass but none was written");
        }

        // A colour left unset must not appear at all, so the base biome's own value is kept.
        String cherry = Files.readString(biomes.resolve("cherry_grove.json"));
        assertTrue(!cherry.contains("\"foliage_color\""),
                "cherry_grove set no foliage colour, so the key should be absent");

        String meta = Files.readString(world.resolve("datapacks").resolve("arkcronist_colours")
                .resolve("pack.mcmeta"));
        assertBalanced(meta, "pack.mcmeta");
        assertTrue(meta.contains("supported_formats"),
                "the pack should declare a format range, not a single version");
    }

    @Test
    @DisplayName("an empty table writes nothing at all")
    void emptyTableWritesNothing(@TempDir Path world) {
        assertTrue(!BiomeColourPack.install(world, Map.of(), QUIET));
        assertTrue(!Files.exists(world.resolve("datapacks")), "a datapack folder was created for nothing");
    }

    /** Cheap structural check: braces and quotes must balance. */
    private static void assertBalanced(String json, String what) {
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString && c == '{') {
                depth++;
            } else if (!inString && c == '}') {
                depth--;
                assertTrue(depth >= 0, what + ": a closing brace with nothing open");
            }
        }
        assertEquals(0, depth, what + ": braces do not balance");
        assertTrue(!inString, what + ": a string was left open");
        assertTrue(!json.contains(",\n  }") && !json.contains(",\n}"), what + ": trailing comma");
    }
}
