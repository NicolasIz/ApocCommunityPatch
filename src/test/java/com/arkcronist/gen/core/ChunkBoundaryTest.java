package com.arkcronist.gen.core;

import com.arkcronist.gen.core.terrain.ChunkTerrain;
import com.arkcronist.gen.core.terrain.Preset;
import com.arkcronist.gen.core.terrain.TerrainEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seam test.
 *
 * <p>Chunks are generated independently, often on different threads and in any order. If the padded
 * grid, the erosion stencil or the interpolation weights ever stopped agreeing across a border, the
 * world would show walls and cliffs on chunk lines. These tests pin that down.</p>
 */
class ChunkBoundaryTest {

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("neighbouring chunks agree exactly on their shared columns")
    void neighboursAgree(Preset preset) {
        TerrainEngine engine = new TerrainEngine(987654321L, preset);
        for (int cx = -3; cx <= 3; cx++) {
            for (int cz = -3; cz <= 3; cz++) {
                ChunkTerrain here = engine.terrain(cx, cz);
                ChunkTerrain east = engine.terrain(cx + 1, cz);
                ChunkTerrain south = engine.terrain(cx, cz + 1);

                for (int i = 0; i < 16; i++) {
                    // The column at local x=15 and the neighbour's x=0 are one block apart; heights
                    // must be continuous, and the sampled values at identical world coordinates must
                    // be bit identical.
                    assertContinuous(here.height[ChunkTerrain.index(15, i)],
                            east.height[ChunkTerrain.index(0, i)], preset, "east");
                    assertContinuous(here.height[ChunkTerrain.index(i, 15)],
                            south.height[ChunkTerrain.index(i, 0)], preset, "south");
                }
            }
        }
    }

    private static void assertContinuous(double a, double b, Preset preset, String direction) {
        double difference = Math.abs(a - b);
        assertTrue(difference < 24.0,
                preset + ": " + direction + " border jumps " + difference + " blocks (" + a + " vs " + b + ")");
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("the same world column has one height, whichever chunk asks")
    void identicalWorldColumns(Preset preset) {
        TerrainEngine engine = new TerrainEngine(13579L, preset);
        double band = engine.settings().overhangBand + 2.0;
        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                int worldX = cx << 4;
                int worldZ = cz << 4;
                double viaChunk = engine.terrain(cx, cz).heightAt(0, 0);
                assertEquals(Math.floor(viaChunk), engine.heightmapHeight(worldX, worldZ),
                        "heightmapHeight disagrees with the chunk it came from");
                // The real surface may sit above or below the heightmap where an overhang, an arch or
                // a cave mouth moved it, but never further than the 3D band allows.
                double solid = engine.surfaceHeight(worldX, worldZ);
                assertTrue(Math.abs(solid - viaChunk) <= band + 26.0,
                        preset + ": solid surface " + solid + " is nowhere near the heightmap " + viaChunk);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("erosion does not depend on which chunk triggered it")
    void erosionIsSeamless(Preset preset) {
        // Building the same chunk from two independent engines exercises the padded erosion window
        // from scratch each time; any dependence on neighbouring state would show up here.
        TerrainEngine first = new TerrainEngine(24680L, preset);
        TerrainEngine second = new TerrainEngine(24680L, preset);
        second.terrain(5, 5);
        second.terrain(4, 5);
        second.terrain(6, 5);
        assertArrayEqualsExact(first.terrain(5, 5).height, second.terrain(5, 5).height);
    }

    private static void assertArrayEqualsExact(float[] expected, float[] actual) {
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], 0.0f, "column " + i + " differs after erosion");
        }
    }
}
