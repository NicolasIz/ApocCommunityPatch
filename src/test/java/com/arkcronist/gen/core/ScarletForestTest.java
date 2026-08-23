package com.arkcronist.gen.core;

import com.arkcronist.gen.core.biome.ArkBiome;
import com.arkcronist.gen.core.biome.BiomeRegistry;
import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.prefab.Prefab;
import com.arkcronist.gen.core.prefab.PrefabRegistry;
import com.arkcronist.gen.core.terrain.Preset;
import com.arkcronist.gen.core.terrain.TerrainEngine;
import com.arkcronist.gen.core.terrain.TerrainSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scarlet forest, and the one file its wood comes out of.
 *
 * <p>Two things are being held to here, and they are the two things that were asked for.</p>
 *
 * <p><b>The grove is one object.</b> The file is a stand of four giant trees leaning into each
 * other over the ground they grew from. It is never cut into four schematics, because what makes it
 * worth having is exactly what cutting it would destroy - the canopies interlock and the trunks
 * lean. So: one file in {@code prefabs/groves/}, nothing scarlet in {@code prefabs/trees/}, and the
 * dimensions in the world match the dimensions on disk.</p>
 *
 * <p><b>Red is a colour, not a block.</b> No block in this biome was swapped for a red one. The
 * ground is grass over dirt and the canopies are oak leaves, the same blocks as any other forest,
 * and the red comes from the biome's grass and foliage tint. That only works on blocks the client
 * actually tints, so the surface palette is checked for the ones it does not: podzol, coarse dirt
 * and red sand take no tint in any biome in the game, vanilla included, and every one of them in
 * the palette would be a brown patch in a red wood.</p>
 */
class ScarletForestTest {

    private static final Path PREFABS = Path.of("src", "main", "resources", "prefabs");

    private static PrefabRegistry prefabs() {
        return PrefabRegistry.fromDirectory(PREFABS, message -> {
        });
    }

    private static ArkBiome scarlet() {
        ArkBiome biome = new BiomeRegistry().byName("scarlet_forest");
        assertNotNull(biome, "the scarlet_forest biome is not registered");
        return biome;
    }

    @Test
    @DisplayName("the grove ships as exactly one file, and nothing scarlet is loose among the trees")
    void theGroveIsOneFile() throws Exception {
        PrefabRegistry registry = prefabs();
        List<Prefab> groves = registry.category("groves");
        assertEquals(1, groves.size(),
                "prefabs/groves/ should hold the one stand, not " + groves.size() + " of them");

        Prefab grove = groves.get(0);
        assertEquals(121, grove.width, "the stand is not the width of the file it came from");
        assertEquals(53, grove.height, "the stand is not the height of the file it came from");
        assertEquals(97, grove.length, "the stand is not the length of the file it came from");

        // And it was never taken apart into trees. This is the whole instruction, so it is checked
        // on disk rather than through the registry: no scarlet file anywhere in the tree folder.
        try (var files = Files.list(PREFABS.resolve("trees"))) {
            List<String> scarlet = files.map(p -> p.getFileName().toString())
                    .filter(name -> name.contains("scarlet"))
                    .toList();
            assertTrue(scarlet.isEmpty(),
                    "the grove was split into separate trees: " + scarlet);
        }
    }

    @Test
    @DisplayName("the grove's canopy is a leaf the game tints, so the biome can turn it red")
    void theCanopyTakesTheTint() {
        Prefab grove = prefabs().category("groves").get(0);
        // Counted by block name, not by one exact state. A leaf in a schematic carries distance,
        // persistent and waterlogged with it, the reader normalises those, and asking the registry
        // for the bare "minecraft:oak_leaves" gets a different entry that nothing in the file uses.
        // That is not a hypothetical: it is what made the first render of this forest report zero
        // canopy over a wood that was standing there.
        assertTrue(countByName(grove, "minecraft:oak_leaves") > 5000,
                "the canopy is not oak leaves, so no biome colour can reach it");

        // Nothing red standing in for red. The earlier attempt at this biome built the canopies out
        // of nether wart block, which is red because the texture is red - a different thing, and not
        // what was asked for.
        assertEquals(0, countByName(grove, "minecraft:nether_wart_block"),
                "the canopy is made of nether blocks instead of leaves the biome tints");
    }

    @Test
    @DisplayName("the biome's ground is ordinary dirt and grass, with nothing in it that cannot be tinted")
    void theGroundIsOrdinaryAndTintable() {
        ArkBiome biome = scarlet();
        int grass = Blocks.REGISTRY.id("minecraft:grass_block");
        int dirt = Blocks.REGISTRY.id("minecraft:dirt");

        // Sampled rather than read off the palette: what matters is what actually lands on the
        // ground, and a weight of one in fifteen still turns up.
        int seen = 0;
        for (int i = 0; i < 4000; i++) {
            int block = biome.surface.pickAt(1234L, i * 7, 64, i * 13);
            assertTrue(block == grass || block == dirt,
                    "the surface put down " + Blocks.REGISTRY.key(block)
                            + ", which the game does not tint - the ground would not go red");
            if (block == grass) {
                seen++;
            }
        }
        assertTrue(seen > 3000, "the surface is barely grass, so there is little for the tint to colour");
    }

    @Test
    @DisplayName("the biome grows no trees of its own: its wood is the grove")
    void theBiomeGrowsNoTreesOfItsOwn() {
        ArkBiome biome = scarlet();
        assertEquals(0, biome.trees.length,
                "the scarlet forest is planting ordinary trees as well as the grove");
        assertTrue(biome.structures.contains(StructureTag.GROVE),
                "the scarlet forest does not ask for the grove, so it would have no trees at all");

        // And no other biome does. A stand of red giants in an ordinary wood would make the biome
        // mean nothing.
        for (ArkBiome other : new BiomeRegistry().all()) {
            if (!other.name.equals("scarlet_forest")) {
                assertTrue(!other.structures.contains(StructureTag.GROVE),
                        other.name + " also asks for the grove");
            }
        }
    }

    /** How many cells of a prefab are this block, whatever state the file gave it. */
    private static int countByName(Prefab prefab, String blockName) {
        int total = 0;
        for (String key : Blocks.REGISTRY.keys()) {
            if (key.equals(blockName) || key.startsWith(blockName + "[")) {
                total += prefab.blockCount(Blocks.REGISTRY.id(key), 0);
            }
        }
        return total;
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("the biome actually occurs, on every preset")
    void theBiomeOccurs(Preset preset) {
        TerrainEngine engine = new TerrainEngine(20260823L, preset,
                TerrainSettings.forPreset(preset), 4096);
        // A coarse sweep, deliberately. The claim is only that the biome exists in an ordinary
        // world, and at 2.4% to 3.4% of it that shows up in a few thousand samples; the fine sweep
        // this started as took three minutes to prove the same thing, which is not what that
        // sentence is worth.
        int hits = 0;
        for (int z = -3000; z < 3000; z += 32) {
            for (int x = -3000; x < 3000; x += 32) {
                if (engine.biomeAt(x, z).name.equals("scarlet_forest")) {
                    hits++;
                }
            }
        }
        assertTrue(hits > 20, preset + ": the scarlet forest barely occurs - only " + hits
                + " of 35156 samples across 6000 blocks of world");
    }
}
