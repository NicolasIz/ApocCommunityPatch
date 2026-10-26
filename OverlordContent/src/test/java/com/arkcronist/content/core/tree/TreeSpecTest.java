package com.arkcronist.content.core.tree;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeSpecTest {

    private static TreeSpec tree(TreeSpec.Shape shape, int min, int max, int radius) {
        return new TreeSpec("demo:log", "minecraft:oak_leaves", shape, min, max, radius, List.of());
    }

    @Test
    void theSameSeedGrowsTheSameTree() {
        TreeSpec oak = tree(TreeSpec.Shape.ROUND, 4, 7, 2);
        assertEquals(oak.plan(42), oak.plan(42));
        Set<List<TreeSpec.Part>> different = new HashSet<>();
        for (long seed = 0; seed < 20; seed++) {
            different.add(oak.plan(seed));
        }
        assertTrue(different.size() > 1, "different seeds give different trees");
    }

    @Test
    void everyShapeHasItsTrunkFirstAndNoLeafInItsPlace() {
        for (TreeSpec.Shape shape : TreeSpec.Shape.values()) {
            if (shape == TreeSpec.Shape.BLUEPRINT) {
                // Drawn, not grown to a shape: BlueprintTest.
                continue;
            }
            TreeSpec spec = tree(shape, 3, 9, 3);
            for (long seed = 0; seed < 50; seed++) {
                List<TreeSpec.Part> parts = spec.plan(seed);
                int logs = (int) parts.stream().filter(TreeSpec.Part::log).count();
                assertTrue(logs >= 3 && logs <= 9, shape + ": a trunk of its height, got " + logs);
                for (int i = 0; i < logs; i++) {
                    assertTrue(parts.get(i).log(), shape + ": trunk first");
                }
                assertEquals(new TreeSpec.Part(0, 0, 0, true), parts.get(0), shape + ": from the sapling's block");
                Set<List<Integer>> places = new HashSet<>();
                for (TreeSpec.Part part : parts) {
                    assertTrue(places.add(List.of(part.dx(), part.dy(), part.dz())), shape + ": one block a place");
                    assertTrue(part.dy() >= 0, shape + ": nothing under the sapling");
                    assertTrue(Math.abs(part.dx()) <= spec.reach() && Math.abs(part.dz()) <= spec.reach(),
                            shape + ": within its reach");
                }
                assertTrue(parts.size() > logs, shape + ": it has leaves");
            }
        }
    }

    @Test
    void onlyTheFlatCrownsTrunkLeans() {
        assertTrue(tree(TreeSpec.Shape.ROUND, 5, 5, 2).plan(1).stream().filter(TreeSpec.Part::log)
                .allMatch(part -> part.dx() == 0 && part.dz() == 0));
        assertTrue(tree(TreeSpec.Shape.FLAT, 6, 6, 3).plan(1).stream().filter(TreeSpec.Part::log)
                .anyMatch(part -> part.dx() != 0 || part.dz() != 0));
    }

    @Test
    void shapesAreReadByTheirNamesAndTheTreesTheyLookLike() {
        assertEquals(TreeSpec.Shape.CONE, TreeSpec.Shape.parse("Spruce").orElseThrow());
        assertEquals(TreeSpec.Shape.FLAT, TreeSpec.Shape.parse("acacia").orElseThrow());
        assertEquals(TreeSpec.Shape.TALL, TreeSpec.Shape.parse(" tall ").orElseThrow());
        assertFalse(TreeSpec.Shape.parse("weeping").isPresent());
    }

    @Test
    void heightsAndRadiiOutsideTheLimitsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> tree(TreeSpec.Shape.ROUND, 0, 4, 2));
        assertThrows(IllegalArgumentException.class, () -> tree(TreeSpec.Shape.ROUND, 5, 4, 2));
        assertThrows(IllegalArgumentException.class, () -> tree(TreeSpec.Shape.ROUND, 4, TreeSpec.MAX_HEIGHT + 1, 2));
        assertThrows(IllegalArgumentException.class, () -> tree(TreeSpec.Shape.ROUND, 4, 6, TreeSpec.MAX_RADIUS + 1));
    }

    @Test
    void generationGoesToTheOverworldUnlessTold() {
        TreeSpec.Generation anywhere = new TreeSpec.Generation(Set.of(), Set.of(), Set.of(), 1, 0.5, Set.of("minecraft:grass_block"));
        assertTrue(anywhere.appliesTo("world", "NORMAL"));
        assertFalse(anywhere.appliesTo("world_nether", "NETHER"));
        assertTrue(anywhere.inBiome("minecraft:plains"));
        TreeSpec.Generation forests = new TreeSpec.Generation(Set.of(), Set.of(), Set.of("minecraft:forest"), 1, 0.5,
                Set.of("minecraft:grass_block"));
        assertTrue(forests.inBiome("minecraft:forest"));
        assertFalse(forests.inBiome("minecraft:desert"));
    }
}
