package com.arkcronist.gen.core;

import com.arkcronist.gen.core.block.Palette;
import com.arkcronist.gen.core.terrain.Preset;
import com.arkcronist.gen.core.terrain.TerrainEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The surface has to read as ground, not as static.
 *
 * <p>Biome surface palettes mix several materials - a snowy slope is snow, grass and packed ice at
 * 6:3:1. When each block drew its own material from a hash, those three came out interleaved block
 * by block and the hillsides were visibly speckled brown through white. The roll is coherent noise
 * now, and these tests pin the two things that has to get right: neighbouring columns usually agree
 * (patches), and the palette weights still mean what they say (shares).</p>
 */
class SurfacePatchTest {

    /** Reads the top solid block of every column in a square of chunks. */
    private static int[][] surfaceOf(TerrainEngine engine, int chunkRadius) {
        int span = chunkRadius * 2 * 16;
        int[][] top = new int[span][span];
        for (int cx = -chunkRadius; cx < chunkRadius; cx++) {
            for (int cz = -chunkRadius; cz < chunkRadius; cz++) {
                GenerationTest.ChunkCapture capture = new GenerationTest.ChunkCapture();
                engine.generateChunk(cx, cz, capture);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int found = 0;
                        for (int y = GenerationTest.MAX_Y - 1; y >= GenerationTest.MIN_Y; y--) {
                            int block = capture.at(lx, y, lz);
                            if (block != 0) {
                                found = block;
                                break;
                            }
                        }
                        top[(cx + chunkRadius) * 16 + lx][(cz + chunkRadius) * 16 + lz] = found;
                    }
                }
            }
        }
        return top;
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("the surface comes out in patches, not block-by-block confetti")
    void surfaceIsCoherent(Preset preset) {
        TerrainEngine engine = new TerrainEngine(20260822L, preset);
        int[][] top = surfaceOf(engine, 2);

        int pairs = 0;
        int agreeing = 0;
        for (int x = 0; x < top.length - 1; x++) {
            for (int z = 0; z < top[x].length - 1; z++) {
                int here = top[x][z];
                if (here == 0) {
                    continue;
                }
                for (int[] step : new int[][]{{1, 0}, {0, 1}}) {
                    int other = top[x + step[0]][z + step[1]];
                    if (other == 0) {
                        continue;
                    }
                    pairs++;
                    if (other == here) {
                        agreeing++;
                    }
                }
            }
        }

        assertTrue(pairs > 5000, preset + ": not enough surface sampled, got " + pairs);
        double agreement = agreeing / (double) pairs;
        // Measured on this seed: the old per-block hash gave 63-65% agreement, the coherent roll
        // gives 92-93%. Those averages flatter the old behaviour, because a biome with a
        // single-material palette agrees with itself either way - it was the mixed palettes, the
        // snowy ones, that came out as confetti. The remaining disagreement is biome borders and
        // shoreline, which genuinely do change material from one block to the next.
        assertTrue(agreement > 0.80,
                preset + ": surface is speckled, only " + Math.round(agreement * 100)
                        + "% of neighbouring columns share a material");
    }

    @Test
    @DisplayName("palette weights are honoured, tail entries included")
    void paletteSharesMatchTheirWeights() {
        // The roll is flattened through the normal CDF before it reaches a palette. Without that
        // step fractal noise never leaves its middle band: a 6/3/1 palette handed everything to the
        // first two entries and the third - the packed ice, the blue ice - never appeared at all.
        Palette palette = Palette.of(1, 6.0, 2, 3.0, 3, 1.0);
        TerrainEngine engine = new TerrainEngine(998877L, Preset.BASE);

        Map<Integer, Integer> counts = new HashMap<>();
        int samples = 0;
        for (int x = -400; x < 400; x += 3) {
            for (int z = -400; z < 400; z += 3) {
                counts.merge(palette.pick(engine.surfaceRollAt(x, z)), 1, Integer::sum);
                samples++;
            }
        }

        double[] wanted = {0.60, 0.30, 0.10};
        for (int i = 0; i < 3; i++) {
            double share = counts.getOrDefault(i + 1, 0) / (double) samples;
            assertTrue(Math.abs(share - wanted[i]) < 0.05,
                    "palette entry " + (i + 1) + " wanted " + wanted[i] + " of the surface, got " + share);
        }
    }
}
