package com.arkcronist.gen.core;

import com.arkcronist.gen.core.biome.ArkBiome;
import com.arkcronist.gen.core.biome.BiomeRegistry;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.prefab.Prefab;
import com.arkcronist.gen.core.prefab.PrefabRegistry;
import com.arkcronist.gen.core.prefab.TreeKind;
import com.arkcronist.gen.core.terrain.Preset;
import com.arkcronist.gen.core.terrain.TerrainEngine;
import com.arkcronist.gen.core.terrain.TerrainSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cherry grove and the scarlet forest, and the room the desert needs.
 *
 * <p>Both new biomes are built around one tree each, and that exclusivity is the point: a scarlet
 * tree anywhere but the scarlet forest would make the biome meaningless. Scoring alone will not do
 * it - a small weight is a long tail, not a promise - so both species are on the opt-in list and
 * score zero outside the biome that asked for them.</p>
 */
class NewBiomesTest {

    private static PrefabRegistry prefabs() {
        return PrefabRegistry.fromDirectory(Path.of("src", "main", "resources", "prefabs"), m -> {
        });
    }

    @Test
    @DisplayName("both biomes exist and are distinct from the ones they resemble")
    void bothBiomesAreRegistered() {
        BiomeRegistry registry = new BiomeRegistry();
        ArkBiome cherry = find(registry, "cherry_grove");
        ArkBiome scarlet = find(registry, "scarlet_forest");
        assertNotNull(cherry, "cherry_grove is not registered");
        assertNotNull(scarlet, "scarlet_forest is not registered");

        // cherry_hills already existed and stays: an upland with mixed trees. cherry_grove is the
        // flat grove of nothing but the schematic cherry, which is why they are two biomes.
        ArkBiome hills = find(registry, "cherry_hills");
        assertNotNull(hills, "cherry_hills was lost");
        assertTrue(cherry.maxHeight < hills.minHeight || cherry.minHeight < hills.minHeight,
                "cherry_grove and cherry_hills occupy the same band, so one will never be seen");
    }

    private static ArkBiome find(BiomeRegistry registry, String name) {
        for (ArkBiome biome : registry.all()) {
            if (biome.name.equals(name)) {
                return biome;
            }
        }
        return null;
    }

    @Test
    @DisplayName("each new biome asks for exactly one species, and nothing else may use it")
    void treeSpeciesAreExclusive() {
        PrefabRegistry registry = prefabs();
        assertTrue(pick(registry, TreeKind.SCARLET).stream().allMatch(id -> id.contains("scarlet")),
                "something other than a scarlet tree was drawn for the scarlet forest");
        assertTrue(pick(registry, TreeKind.CHERRY).stream().allMatch(id -> id.contains("cherry")),
                "something other than a cherry tree was drawn for the cherry grove");

        // The half that matters more: an ordinary forest must never draw one of these.
        Set<String> ordinary = pick(registry, TreeKind.OAK);
        assertTrue(ordinary.stream().noneMatch(id -> id.contains("scarlet")),
                "a scarlet tree turned up in an ordinary forest: " + ordinary);
        assertTrue(ordinary.stream().noneMatch(id -> id.contains("cherry")),
                "a cherry tree turned up in an ordinary forest: " + ordinary);
    }

    private static Set<String> pick(PrefabRegistry registry, String species) {
        Set<String> found = new LinkedHashSet<>();
        for (int i = 0; i < 200; i++) {
            Prefab tree = registry.pickTree(species, null, new FastRandom(i * 7919L + 13));
            if (tree != null) {
                found.add(tree.id);
            }
        }
        assertTrue(!found.isEmpty(), "no tree at all was drawn for " + species);
        return found;
    }

    @Test
    @DisplayName("scarlet canopies are a leaf the biome can actually tint")
    void scarletLeavesTakeTheBiomeTint() {
        // They were nether wart block, which is red because its texture is red - it takes no tint,
        // so the biome's colour could never reach it. Birch is no good either: birch and spruce
        // leaves carry a hardcoded colour in vanilla and ignore the biome outright. Oak does follow
        // it, which is why the canopies are oak over a birch trunk.
        //
        // Matched by block name rather than by exact state: the reader rewrites a leaf's decay
        // distance on the way in, so pinning the whole state string here would be testing that
        // normalisation instead of the thing that matters.
        PrefabRegistry registry = prefabs();
        int checked = 0;
        for (Prefab tree : registry.category("trees")) {
            if (!tree.id.contains("scarlet")) {
                continue;
            }
            checked++;
            assertTrue(count(tree, "minecraft:oak_leaves") > 100,
                    tree.id + " has almost no tintable leaves in it");
            assertTrue(count(tree, "minecraft:nether_wart_block") == 0,
                    tree.id + " still carries nether wart block, which no biome can tint");
            assertTrue(count(tree, "minecraft:birch_leaves") == 0,
                    tree.id + " uses birch leaves, whose colour is fixed and ignores the biome");
            assertTrue(count(tree, "minecraft:spruce_leaves") == 0,
                    tree.id + " uses spruce leaves, whose colour is fixed and ignores the biome");
            // The trunk is meant to stay birch: it is a birch tree with a red canopy.
            assertTrue(count(tree, "minecraft:birch_wood") + count(tree, "minecraft:birch_log") > 100,
                    tree.id + " lost its birch trunk");
        }
        assertTrue(checked >= 10, "only " + checked + " scarlet trees were found");
    }

    /** Blocks of a prefab whose name starts with the given prefix, across every state of it. */
    private static int count(Prefab prefab, String namePrefix) {
        int total = 0;
        for (int id = 0; id < com.arkcronist.gen.core.block.Blocks.REGISTRY.size(); id++) {
            if (com.arkcronist.gen.core.block.Blocks.REGISTRY.key(id).startsWith(namePrefix)) {
                total += prefab.blockCount(id, 0);
            }
        }
        return total;
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("both biomes actually occur in a world, and the desert has room for a pyramid")
    void theyOccurInAWorld(Preset preset) {
        TerrainEngine engine = new TerrainEngine(20260823L, preset, TerrainSettings.forPreset(preset), 4096);
        long total = 0;
        long cherry = 0;
        long scarlet = 0;
        long desert = 0;
        for (int z = -3000; z < 3000; z += 17) {
            for (int x = -3000; x < 3000; x += 17) {
                ArkBiome biome = engine.biomeAt(x, z);
                total++;
                switch (biome.name) {
                    case "cherry_grove" -> cherry++;
                    case "scarlet_forest" -> scarlet++;
                    default -> {
                    }
                }
                if (biome.name.contains("desert")) {
                    desert++;
                }
            }
        }
        assertTrue(cherry > 0, preset + ": cherry_grove never appears");
        assertTrue(scarlet > 0, preset + ": scarlet_forest never appears");
        // Both are meant to be finds, not scenery - but a biome nobody ever stumbles on is a bug.
        assertTrue(cherry / (double) total > 0.001, preset + ": cherry_grove is vanishingly rare");
        assertTrue(scarlet / (double) total > 0.001, preset + ": scarlet_forest is vanishingly rare");
        // The desert was widened so a pyramid has somewhere to stand; it used to be 3% of the world.
        assertTrue(desert / (double) total > 0.045,
                preset + ": desert is only " + Math.round(1000.0 * desert / total) / 10.0 + "% of the world");
    }
}
