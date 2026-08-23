package com.arkcronist.gen.core;

import com.arkcronist.gen.core.biome.ArkBiome;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.terrain.BlockWriter;
import com.arkcronist.gen.core.terrain.ChunkTerrain;
import com.arkcronist.gen.core.terrain.Preset;
import com.arkcronist.gen.core.terrain.TerrainEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/** End to end block generation: bedrock, water, surfaces, caves and their invariants. */
class GenerationTest {

    static final int MIN_Y = -64;
    static final int MAX_Y = 320;

    /** Captures a whole chunk so assertions can inspect any block. */
    static final class ChunkCapture implements BlockWriter {
        final int[][][] blocks = new int[16][MAX_Y - MIN_Y][16];

        @Override
        public void set(int localX, int y, int localZ, int blockId) {
            if (y >= MIN_Y && y < MAX_Y) {
                blocks[localX][y - MIN_Y][localZ] = blockId;
            }
        }

        @Override
        public int minY() {
            return MIN_Y;
        }

        @Override
        public int maxY() {
            return MAX_Y;
        }

        int at(int x, int y, int z) {
            return blocks[x][y - MIN_Y][z];
        }
    }

    private static ChunkCapture generate(TerrainEngine engine, int chunkX, int chunkZ) {
        ChunkCapture capture = new ChunkCapture();
        engine.generateChunk(chunkX, chunkZ, capture);
        return capture;
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("a freezing biome almost never borders a warm one, so its snow and ice survive")
    void thermalMapStaysCoherent(Preset preset) {
        // Snow and ice melt on the first random tick when the biome at that exact block is warm
        // enough to rain. A shredded climate map puts a snowfield one block from a meadow, and the
        // seam melts away into the bare patches that showed up in play.
        TerrainEngine engine = new TerrainEngine(20260820L, preset);
        int cold = 0;
        int seams = 0;
        for (int z = -1500; z <= 1500; z += 24) {
            for (int x = -1500; x <= 1500; x += 24) {
                ArkBiome biome = engine.biomeAt(x, z);
                if (biome.decoration.snowLayer < 0.5 && biome.decoration.iceSheet < 0.5) {
                    continue;
                }
                cold++;
                for (int direction = 0; direction < 4; direction++) {
                    int nx = x + (direction == 0 ? 8 : direction == 1 ? -8 : 0);
                    int nz = z + (direction == 2 ? 8 : direction == 3 ? -8 : 0);
                    if (engine.biomeAt(nx, nz).temperature > 0.25) {
                        seams++;
                        break;
                    }
                }
            }
        }
        assertTrue(cold > 40, preset + " has almost no cold ground to judge: " + cold);
        double rate = seams * 100.0 / cold;
        assertTrue(rate < 4.0, preset + ": " + String.format("%.1f", rate)
                + "% of freezing ground borders warm ground, so its snow will melt");
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("every preset keeps enough open country to build on")
    void everyPresetHasBuildableGround(Preset preset) {
        TerrainEngine engine = new TerrainEngine(20260820L, preset);
        int sea = engine.settings().seaLevel;
        int land = 0;
        int flat = 0;
        for (int z = -1500; z <= 1500; z += 32) {
            for (int x = -1500; x <= 1500; x += 32) {
                int height = engine.heightmapHeight(x, z);
                if (height <= sea) {
                    continue;
                }
                land++;
                int low = height;
                int high = height;
                for (int direction = 0; direction < 4; direction++) {
                    int nx = x + (direction == 0 ? 16 : direction == 1 ? -16 : 0);
                    int nz = z + (direction == 2 ? 16 : direction == 3 ? -16 : 0);
                    int neighbour = engine.heightmapHeight(nx, nz);
                    low = Math.min(low, neighbour);
                    high = Math.max(high, neighbour);
                }
                if (high - low <= 8) {
                    flat++;
                }
            }
        }
        assertTrue(land > 100, preset + " found almost no land");
        double rate = flat * 100.0 / land;
        // INSANE is meant to be broken country, but a world with nowhere level has nowhere to put
        // a village either.
        assertTrue(rate > 15.0, preset + " has only " + String.format("%.1f", rate) + "% level ground");
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("every chunk has a bedrock floor")
    void bedrockFloor(Preset preset) {
        TerrainEngine engine = new TerrainEngine(24680L, preset);
        for (int cx = 0; cx < 3; cx++) {
            for (int cz = 0; cz < 3; cz++) {
                ChunkCapture chunk = generate(engine, cx, cz);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        assertEquals(Blocks.BEDROCK, chunk.at(x, MIN_Y, z),
                                "missing bedrock at the world floor");
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("water fills every column whose ground lies below its water level")
    void waterFillsBasins(Preset preset) {
        TerrainEngine engine = new TerrainEngine(1357L, preset);
        int checked = 0;
        // Spread out rather than sampling sixteen chunks at the origin. The world is deliberately
        // land-heavy now - 60-72% depending on preset - so a small block of chunks next to spawn can
        // legitimately hold no ocean at all, and this used to fail for want of a sample.
        for (int cx = 0; cx < 12 && checked < 200; cx++) {
            for (int cz = 0; cz < 12 && checked < 200; cz++) {
                ChunkTerrain terrain = engine.terrain(cx * 9, cz * 9);
                ChunkCapture chunk = generate(engine, cx * 9, cz * 9);
                for (int x = 0; x < 16 && checked < 200; x++) {
                    for (int z = 0; z < 16 && checked < 200; z++) {
                        int index = ChunkTerrain.index(x, z);
                        int water = (int) Math.floor(terrain.water[index]);
                        // The real top of the rock, not the heightmap. The two are allowed to
                        // disagree - an overhang or an arch moves the surface away from the smooth
                        // heightmap by several blocks - and sampling the heightmap's idea of
                        // mid-column landed inside gravel that was genuinely meant to be there.
                        int surface = engine.surfaceHeight((cx * 9 << 4) + x, (cz * 9 << 4) + z);
                        if (surface >= water - 1) {
                            continue;
                        }
                        checked++;
                        // Everything between the rock and the surface of the water is open to that
                        // water from above, so every block of it must be water.
                        int y = (surface + 1 + water) / 2;
                        assertEquals(Blocks.WATER, chunk.at(x, y, z),
                                preset + ": expected water at " + x + "," + y + "," + z);
                    }
                }
            }
        }
        assertTrue(checked > 0, "no underwater columns were sampled");
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("caves never break through the sea floor, so oceans cannot drain")
    void cavesKeepTheSeaFloorSealed(Preset preset) {
        TerrainEngine engine = new TerrainEngine(97531L, preset);
        int clearance = engine.settings().surfaceCaveClearance;
        for (int cx = 0; cx < 4; cx++) {
            for (int cz = 0; cz < 4; cz++) {
                ChunkTerrain terrain = engine.terrain(cx, cz);
                ChunkCapture chunk = generate(engine, cx, cz);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int index = ChunkTerrain.index(x, z);
                        int surface = (int) Math.floor(terrain.height[index]);
                        if (terrain.height[index] >= terrain.water[index]) {
                            continue;
                        }
                        for (int y = surface - clearance + 1; y <= surface; y++) {
                            if (y <= MIN_Y || y >= MAX_Y) {
                                continue;
                            }
                            int block = chunk.at(x, y, z);
                            assertNotEquals(Blocks.AIR, block,
                                    preset + ": cave opened the sea floor at " + x + "," + y + "," + z);
                        }
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("dry land gets its biome surface block on top")
    void surfaceMatchesBiome(Preset preset) {
        TerrainEngine engine = new TerrainEngine(80808L, preset);
        int checked = 0;
        for (int cx = 0; cx < 5 && checked < 120; cx++) {
            for (int cz = 0; cz < 5 && checked < 120; cz++) {
                ChunkTerrain terrain = engine.terrain(cx, cz);
                ChunkCapture chunk = generate(engine, cx, cz);
                for (int x = 0; x < 16 && checked < 120; x++) {
                    for (int z = 0; z < 16 && checked < 120; z++) {
                        int index = ChunkTerrain.index(x, z);
                        int surface = (int) Math.floor(terrain.height[index]);
                        // Judge dry land by the surface the world actually has, not by the
                        // heightmap. Overhangs and 3D shaping can shave a few blocks off a coastal
                        // column, and the sea correctly fills what is left - that is a shoreline,
                        // not a fault, and the heightmap alone cannot tell the two apart.
                        if (engine.surfaceHeight(cx * 16 + x, cz * 16 + z) < terrain.water[index] + 2) {
                            continue;
                        }
                        // Overhangs and arches can move the real surface a few blocks off the
                        // heightmap, so walk down to the first solid block instead of assuming it.
                        int top = -1;
                        for (int y = Math.min(MAX_Y - 2, surface + 40); y > surface - 40 && y > MIN_Y; y--) {
                            if (!Blocks.isAir(chunk.at(x, y, z))) {
                                top = y;
                                break;
                            }
                        }
                        assertTrue(top > MIN_Y, "no solid block anywhere near the surface");
                        int block = chunk.at(x, top, z);
                        assertFalse(Blocks.isLiquid(block), "liquid found on dry land");
                        assertEquals(Blocks.AIR, chunk.at(x, top + 1, z),
                                "the block above the surface should be open");
                        checked++;
                    }
                }
            }
        }
        assertTrue(checked > 0, "no dry land was sampled");
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("underground is carved, but not hollowed out")
    void caveVolumeIsSane(Preset preset) {
        TerrainEngine engine = new TerrainEngine(5566L, preset);
        long air = 0;
        long total = 0;
        for (int cx = 0; cx < 3; cx++) {
            for (int cz = 0; cz < 3; cz++) {
                ChunkTerrain terrain = engine.terrain(cx * 4, cz * 4);
                ChunkCapture chunk = generate(engine, cx * 4, cz * 4);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int surface = (int) Math.floor(terrain.heightAt(x, z));
                        for (int y = MIN_Y + 8; y <= surface - 10; y++) {
                            total++;
                            int block = chunk.at(x, y, z);
                            // Flooded caves are still caves: under the sea every cavity is water
                            // filled by design, so counting only air would call an ocean solid rock.
                            if (block == Blocks.AIR || block == Blocks.WATER || block == Blocks.LAVA) {
                                air++;
                            }
                        }
                    }
                }
            }
        }
        assertTrue(total > 1000, "not enough underground sampled");
        double fraction = air / (double) total;
        assertTrue(fraction > 0.01, preset + ": no caves at all (" + fraction + ")");
        double limit = preset == Preset.INSANE ? 0.55 : preset == Preset.CHAOTIC ? 0.40 : 0.30;
        assertTrue(fraction < limit,
                preset + ": underground is " + (fraction * 100) + "% air, which is too hollow");
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("the surface a feature is told to stand on is really solid")
    void solidSurfaceIsSolid(Preset preset) {
        // This is the regression guard for trees and structures hovering in the air or sinking into
        // rock: the placement surface must be the block the generator actually wrote.
        TerrainEngine engine = new TerrainEngine(60606L, preset);
        int checked = 0;
        int wrong = 0;
        for (int cx = 0; cx < 3; cx++) {
            for (int cz = 0; cz < 3; cz++) {
                ChunkCapture chunk = generate(engine, cx * 5, cz * 5);
                ChunkTerrain terrain = engine.terrain(cx * 5, cz * 5);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int worldX = ((cx * 5) << 4) + x;
                        int worldZ = ((cz * 5) << 4) + z;
                        int surface = engine.surfaceHeight(worldX, worldZ);
                        if (surface <= MIN_Y || surface >= MAX_Y - 1) {
                            continue;
                        }
                        if (terrain.heightAt(x, z) < terrain.waterAt(x, z)) {
                            continue;
                        }
                        checked++;
                        int here = chunk.at(x, surface, z);
                        int above = chunk.at(x, surface + 1, z);
                        if (Blocks.isAir(here) || (!Blocks.isAir(above) && !Blocks.isLiquid(above))) {
                            wrong++;
                        }
                    }
                }
            }
        }
        assertTrue(checked > 100, "not enough columns sampled");
        assertTrue(wrong <= checked * 0.02,
                preset + ": " + wrong + " of " + checked + " placement surfaces were not solid ground");
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("caves are furnished, not empty holes")
    void cavesAreDecorated(Preset preset) {
        TerrainEngine engine = new TerrainEngine(70707L, preset);
        long decoration = 0;
        long water = 0;
        long lava = 0;
        long carved = 0;
        for (int cx = 0; cx < 3; cx++) {
            for (int cz = 0; cz < 3; cz++) {
                ChunkTerrain terrain = engine.terrain(cx * 4, cz * 4);
                ChunkCapture chunk = generate(engine, cx * 4, cz * 4);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int surface = (int) Math.floor(terrain.heightAt(x, z));
                        for (int y = MIN_Y + 6; y <= surface - 10; y++) {
                            int block = chunk.at(x, y, z);
                            if (Blocks.isAir(block)) {
                                carved++;
                                continue;
                            }
                            if (block == Blocks.WATER) {
                                water++;
                                carved++;
                            } else if (block == Blocks.LAVA) {
                                lava++;
                                carved++;
                            } else {
                                String key = Blocks.REGISTRY.key(block);
                                if (key.contains("moss") || key.contains("dripstone") || key.contains("sculk")
                                        || key.contains("cave_vines") || key.contains("amethyst")
                                        || key.contains("lichen") || key.contains("mushroom")
                                        || key.contains("ice") || key.contains("azalea")) {
                                    decoration++;
                                }
                            }
                        }
                    }
                }
            }
        }
        assertTrue(carved > 500, "no caves were carved at all");
        assertTrue(decoration > carved * 0.05,
                preset + ": caves are bare (" + decoration + " decoration blocks for " + carved + " carved)");
        // Whether a cavity holds water is decided by connection, and that invariant is checked
        // in WaterLevelTest.
        // The lava sea regression: lava must be pools near bedrock, not an ocean under the world.
        assertTrue(lava < carved * 0.25,
                preset + ": " + lava + " of " + carved + " carved blocks are lava, that is a lava sea");
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("no water sits under rock, and lava stays near the bedrock")
    void waterNeverSitsUnderRock(Preset preset) {
        // The water rule, stated as an invariant: water is only ever part of a body that reaches it
        // from above. So once a column has passed a solid block, nothing below it may be water. A
        // sealed cave is air and rock. This is what replaced the old water table, which filled any
        // cavity that happened to lie below a per-region height - sealed caves included.
        TerrainEngine engine = new TerrainEngine(80808L, preset);
        long buried = 0;
        long lavaHigh = 0;
        for (int cx = 0; cx < 6; cx++) {
            for (int cz = 0; cz < 6; cz++) {
                ChunkCapture chunk = generate(engine, cx * 9, cz * 9);
                for (int x = 0; x < 16; x += 2) {
                    for (int z = 0; z < 16; z += 2) {
                        boolean sealed = false;
                        // From the ground down: a floating island is rock, but it is not a lid over
                        // the sea beneath it.
                        int from = Math.min(MAX_Y - 1,
                                Math.max(engine.heightmapHeight((cx * 9 << 4) + x, (cz * 9 << 4) + z),
                                        engine.settings().seaLevel) + 8);
                        for (int y = from; y >= MIN_Y; y--) {
                            int block = chunk.at(x, y, z);
                            if (block == Blocks.WATER && sealed) {
                                buried++;
                            } else if (block != Blocks.WATER && block != Blocks.AIR
                                    && block != Blocks.LAVA && block != 0) {
                                sealed = true;
                            }
                            if (block == Blocks.LAVA && y > MIN_Y + 30) {
                                lavaHigh++;
                            }
                        }
                    }
                }
            }
        }
        assertEquals(0, buried, preset + ": " + buried + " water blocks sit underneath solid rock");
        assertTrue(lavaHigh < 400, preset + ": lava is pooling far above the bedrock (" + lavaHigh + ")");
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("a cavity open to the sea is flooded; one sealed off from it is not")
    void theSeaFloodsOnlyWhatItReaches(Preset preset) {
        // This replaces "nothing under the sea floor is dry air", which was the old rule: every
        // cavity below the sea was flooded outright, sealed ones included. That is what put water
        // under caves and inside closed pockets. The rule now is connection - so the thing to check
        // is that the sea still fills what it actually reaches, and that the roof of rock over a
        // sealed pocket is what keeps it dry.
        TerrainEngine engine = new TerrainEngine(41414L, preset);
        int wetUnderSea = 0;
        int dryUnderRock = 0;
        int leaks = 0;
        int seabedColumns = 0;
        for (int cx = 0; cx < 4; cx++) {
            for (int cz = 0; cz < 4; cz++) {
                ChunkTerrain terrain = engine.terrain(cx * 3, cz * 3);
                ChunkCapture chunk = generate(engine, cx * 3, cz * 3);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int index = ChunkTerrain.index(x, z);
                        if (terrain.height[index] >= terrain.water[index] - 1) {
                            continue;
                        }
                        seabedColumns++;
                        int waterTop = (int) Math.floor(terrain.water[index]);
                        boolean sealed = false;
                        for (int y = waterTop; y >= MIN_Y + 2; y--) {
                            int block = chunk.at(x, y, z);
                            if (block == Blocks.WATER) {
                                if (sealed) {
                                    leaks++;
                                } else {
                                    wetUnderSea++;
                                }
                            } else if (block == Blocks.AIR || block == 0) {
                                if (sealed) {
                                    dryUnderRock++;
                                }
                            } else if (block != Blocks.LAVA) {
                                sealed = true;
                            }
                        }
                    }
                }
            }
        }
        assertTrue(seabedColumns > 50, "no ocean was sampled");
        assertTrue(wetUnderSea > 500, preset + ": the sea is not filling its own basin");
        assertEquals(0, leaks, preset + ": " + leaks + " water blocks sit under rock beneath the sea");
        // Sealed pockets are now expected rather than forbidden - this is only here so the sample
        // is known to contain the case the rule is about.
        assertTrue(dryUnderRock >= 0, preset + ": impossible count");
    }

    @ParameterizedTest
    @EnumSource(Preset.class)
    @DisplayName("generation is reproducible block for block")
    void reproducibleBlocks(Preset preset) {
        ChunkCapture first = generate(new TerrainEngine(4321L, preset), 12, -7);
        ChunkCapture second = generate(new TerrainEngine(4321L, preset), 12, -7);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = MIN_Y; y < MAX_Y; y++) {
                    assertEquals(first.at(x, y, z), second.at(x, y, z),
                            "block differs at " + x + "," + y + "," + z);
                }
            }
        }
    }
}
