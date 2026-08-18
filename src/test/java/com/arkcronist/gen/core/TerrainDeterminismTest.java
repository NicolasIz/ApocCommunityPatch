package com.arkcronist.gen.core;

import com.arkcronist.gen.core.terrain.ChunkTerrain;
import com.arkcronist.gen.core.terrain.ColumnData;
import com.arkcronist.gen.core.terrain.Preset;
import com.arkcronist.gen.core.terrain.TerrainEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TerrainDeterminismTest {

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("the same seed always produces the same terrain")
    void sameSeedSameWorld(Preset preset) {
        TerrainEngine first = new TerrainEngine(20240815L, preset);
        TerrainEngine second = new TerrainEngine(20240815L, preset);
        ColumnData a = new ColumnData();
        ColumnData b = new ColumnData();
        for (int i = 0; i < 400; i++) {
            int x = i * 37 - 5000;
            int z = i * -53 + 2500;
            first.sampler().sample(x, z, a);
            second.sampler().sample(x, z, b);
            assertEquals(a.height, b.height, "height differs at " + x + "," + z);
            assertEquals(a.temperature, b.temperature);
            assertEquals(a.waterLevel, b.waterLevel);
        }
    }

    @Test
    @DisplayName("different seeds produce different worlds")
    void differentSeeds() {
        TerrainEngine first = new TerrainEngine(1L, Preset.BASE);
        TerrainEngine second = new TerrainEngine(2L, Preset.BASE);
        int differences = 0;
        ColumnData a = new ColumnData();
        ColumnData b = new ColumnData();
        for (int i = 0; i < 200; i++) {
            first.sampler().sample(i * 64, 0, a);
            second.sampler().sample(i * 64, 0, b);
            if (Math.abs(a.height - b.height) > 0.5) {
                differences++;
            }
        }
        assertTrue(differences > 150, "worlds should not correlate, " + differences + " of 200 differed");
    }

    @Test
    @DisplayName("parallel generation produces identical chunks")
    void parallelGenerationIsStable() throws Exception {
        TerrainEngine engine = new TerrainEngine(555L, Preset.CHAOTIC);
        List<int[]> coordinates = new ArrayList<>();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                coordinates.add(new int[]{x, z});
            }
        }

        float[][] single = new float[coordinates.size()][];
        for (int i = 0; i < coordinates.size(); i++) {
            int[] c = coordinates.get(i);
            single[i] = new TerrainEngine(555L, Preset.CHAOTIC).terrain(c[0], c[1]).height.clone();
        }

        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<float[]>> futures = new ArrayList<>();
        for (int[] c : coordinates) {
            futures.add(pool.submit(() -> engine.terrain(c[0], c[1]).height.clone()));
        }
        for (int i = 0; i < futures.size(); i++) {
            assertArrayEquals(single[i], futures.get(i).get(),
                    "parallel result differs for chunk " + coordinates.get(i)[0] + "," + coordinates.get(i)[1]);
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("terrain never escapes the world's height limits")
    void heightsStayInsideWorld() {
        for (Preset preset : Preset.values()) {
            TerrainEngine engine = new TerrainEngine(4242L, preset);
            int minY = engine.settings().minY;
            int maxY = engine.settings().maxY;
            for (int cx = -6; cx <= 6; cx++) {
                for (int cz = -6; cz <= 6; cz++) {
                    ChunkTerrain terrain = engine.terrain(cx * 3, cz * 3);
                    for (int i = 0; i < 256; i++) {
                        assertTrue(terrain.height[i] > minY + 1,
                                preset + " height below world floor: " + terrain.height[i]);
                        assertTrue(terrain.height[i] < maxY - 2,
                                preset + " height above build limit: " + terrain.height[i]);
                        assertFalse(Double.isNaN(terrain.height[i]), "NaN height");
                    }
                }
            }
        }
    }
}
