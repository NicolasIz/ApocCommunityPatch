package com.arkcronist.gen.core;

import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.terrain.Preset;
import com.arkcronist.gen.core.terrain.TerrainEngine;
import com.arkcronist.gen.core.terrain.TerrainSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Water exists where it is connected to a body of water, and nowhere else.
 *
 * <p>There used to be a water <em>table</em>: a height chosen per region, with every cavity below it
 * filled in. That is what put water inside sealed caves, left water sitting under caves, and gave
 * one hillside several water surfaces at different heights. There is no table any more - the column
 * pass tracks whether it is still open to the water above it, and the first solid block seals
 * everything under it.</p>
 */
class CaveWaterTest {

    private static final int MIN_Y = GenerationTest.MIN_Y;
    private static final int MAX_Y = GenerationTest.MAX_Y;

    /** Top of the ground column, clear of floating islands. */
    private static int top(TerrainEngine engine, int chunkX, int chunkZ, int localX, int localZ) {
        int ground = engine.heightmapHeight((chunkX << 4) + localX, (chunkZ << 4) + localZ);
        return Math.min(MAX_Y - 1, Math.max(ground, engine.settings().seaLevel) + 8);
    }

    private static boolean isSolid(int block) {
        return block != 0 && block != Blocks.AIR && block != Blocks.WATER && block != Blocks.LAVA;
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("a closed cave is air and rock: no water under a ceiling of stone")
    void sealedCavitiesAreDry(Preset preset) {
        TerrainEngine engine = new TerrainEngine(20260823L, preset, TerrainSettings.forPreset(preset), 512);

        long buried = 0;
        long water = 0;
        for (int cx = 0; cx < 10; cx++) {
            for (int cz = 0; cz < 10; cz++) {
                GenerationTest.ChunkCapture chunk = new GenerationTest.ChunkCapture();
                engine.generateChunk(cx * 11, cz * 11, chunk);
                for (int x = 0; x < 16; x += 2) {
                    for (int z = 0; z < 16; z += 2) {
                        boolean sealed = false;
                        // Start at the ground, not at the build limit: a floating island is its own
                        // column of rock hundreds of blocks up, and counting it as "rock above" would
                        // call every ocean underneath it sealed.
                        for (int y = top(engine, cx * 11, cz * 11, x, z); y >= MIN_Y; y--) {
                            int block = chunk.at(x, y, z);
                            if (block == Blocks.WATER) {
                                water++;
                                if (sealed) {
                                    buried++;
                                }
                            } else if (isSolid(block)) {
                                sealed = true;
                            }
                        }
                    }
                }
            }
        }

        assertTrue(water > 1000, preset + ": no water generated at all, the sample is not meaningful");
        assertEquals(0, buried,
                preset + ": " + buried + " water blocks are cut off from the surface by rock above them");
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("every water surface in a column is one unbroken run")
    void waterIsOneRunPerColumn(Preset preset) {
        // Two separate runs of water in the same column means two surfaces at different heights,
        // which is the artifact this replaced. One run, or none.
        TerrainEngine engine = new TerrainEngine(31415L, preset, TerrainSettings.forPreset(preset), 512);
        long columns = 0;
        long multiRun = 0;
        for (int cx = 0; cx < 8; cx++) {
            for (int cz = 0; cz < 8; cz++) {
                GenerationTest.ChunkCapture chunk = new GenerationTest.ChunkCapture();
                engine.generateChunk(cx * 13, cz * 13, chunk);
                for (int x = 0; x < 16; x += 2) {
                    for (int z = 0; z < 16; z += 2) {
                        int runs = 0;
                        boolean inWater = false;
                        for (int y = top(engine, cx * 13, cz * 13, x, z); y >= MIN_Y; y--) {
                            boolean w = chunk.at(x, y, z) == Blocks.WATER;
                            if (w && !inWater) {
                                runs++;
                            }
                            inWater = w;
                        }
                        columns++;
                        if (runs > 1) {
                            multiRun++;
                        }
                    }
                }
            }
        }
        assertTrue(columns > 3000, preset + ": sample too small");
        assertEquals(0, multiRun,
                preset + ": " + multiRun + " columns carry more than one separate body of water");
    }
}
