package com.arkcronist.content.core.crop;

import com.arkcronist.content.core.storage.BlockKey;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CropGrowthTest {

    private static final UUID WORLD = UUID.fromString("5f0e3b1c-0d4e-4a53-9a0b-9f3c1d2e4b61");

    @Test
    void aStageTakesItsTimeGiveOrTakeAFifthAndAlwaysTheSameForTheSameCrop() {
        Set<Long> seen = new HashSet<>();
        for (int x = 0; x < 200; x++) {
            long duration = CropGrowth.stageDuration(100, BlockKey.pack(x, 64, 0), 0);
            assertTrue(duration >= 80 && duration <= 120, "duration " + duration);
            assertEquals(duration, CropGrowth.stageDuration(100, BlockKey.pack(x, 64, 0), 0));
            seen.add(duration);
        }
        // A field does not ripen all in one tick.
        assertTrue(seen.size() > 20, seen.toString());
        assertEquals(1, CropGrowth.stageDuration(1, 0, 0));
    }

    @Test
    void growthStopsAtTheEndOfTheStageUntilItIsAdvanced() {
        PlantedCrop crop = new PlantedCrop(WORLD, 3, 64, 7, "demo:ruby_seeds", 0, 0);
        long duration = CropGrowth.stageDuration(60, crop.key(), 0);

        PlantedCrop partway = CropGrowth.grow(crop, duration - 1, 60, 2);
        assertEquals(duration - 1, partway.progress());
        assertFalse(CropGrowth.due(partway, 60, 2));

        PlantedCrop full = CropGrowth.grow(partway, 1_000, 60, 2);
        assertEquals(duration, full.progress());
        assertEquals(0, full.stage());
        assertTrue(CropGrowth.due(full, 60, 2));

        PlantedCrop next = CropGrowth.advance(full, 2);
        assertEquals(1, next.stage());
        assertEquals(0, next.progress());
    }

    @Test
    void aFullyGrownCropNeitherGrowsNorAdvances() {
        PlantedCrop grown = new PlantedCrop(WORLD, 0, 64, 0, "demo:ruby_seeds", 2, 0);

        assertSame(grown, CropGrowth.grow(grown, 10_000, 60, 2));
        assertFalse(CropGrowth.due(grown, 60, 2));
        assertEquals(2, CropGrowth.advance(grown, 2).stage());
    }

    @Test
    void chunkKeysKeepNegativeCoordinatesApart() {
        PlantedCrop a = new PlantedCrop(WORLD, -1, 64, 15, "x:y", 0, 0);
        PlantedCrop b = new PlantedCrop(WORLD, 15, 64, -1, "x:y", 0, 0);
        assertEquals(PlantedCrop.chunkKey(-1, 0), a.chunk());
        assertEquals(PlantedCrop.chunkKey(0, -1), b.chunk());
        assertTrue(a.chunk() != b.chunk());
    }
}
