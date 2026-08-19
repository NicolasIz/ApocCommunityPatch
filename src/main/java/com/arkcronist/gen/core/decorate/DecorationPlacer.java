package com.arkcronist.gen.core.decorate;

import com.arkcronist.gen.core.biome.ArkBiome;
import com.arkcronist.gen.core.biome.DecorationProfile;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.RegionWriter;
import com.arkcronist.gen.core.terrain.ChunkTerrain;
import com.arkcronist.gen.core.terrain.TerrainEngine;

/**
 * Single column decoration: ground cover, plants, snow, ice and everything that lives underwater.
 *
 * <p>Each column decides its own contents from a position seeded generator, so decoration is
 * order independent and identical no matter when the chunk is populated. Densities come from the
 * biome's own {@link DecorationProfile}, which is what gives each region a recognisable ground
 * texture rather than the same grass everywhere.</p>
 */
public final class DecorationPlacer {

    private static final long SALT = 0xDEC0_5EEDL;

    private final TerrainEngine engine;

    public DecorationPlacer(TerrainEngine engine) {
        this.engine = engine;
    }

    public void decorate(int chunkX, int chunkZ, RegionWriter writer) {
        ChunkTerrain terrain = engine.terrain(chunkX, chunkZ);
        double density = engine.settings().decorationDensity;
        int maxY = writer.maxY();

        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int index = ChunkTerrain.index(localX, localZ);
                int x = (chunkX << 4) + localX;
                int z = (chunkZ << 4) + localZ;
                double water = terrain.water[index];
                ArkBiome biome = engine.biomes().byId(terrain.biome[index]);
                DecorationProfile profile = biome.decoration;

                // Decoration follows the block that is really there, so nothing hovers over an
                // overhang or sinks into a ledge.
                int groundY = engine.surfaceHeight(x, z);
                double height = groundY;
                if (groundY + 2 >= maxY) {
                    continue;
                }
                FastRandom random = FastRandom.forBlock(engine.seed(), x, 0, z, SALT);

                if (height < water - 0.4) {
                    decorateUnderwater(random, profile, density, x, groundY, z, (int) Math.floor(water), writer);
                } else {
                    decorateLand(random, biome, profile, density, x, groundY, z, (int) Math.floor(water), writer);
                }
            }
        }
    }

    private void decorateLand(FastRandom random, ArkBiome biome, DecorationProfile profile, double density,
                              int x, int groundY, int z, int waterY, RegionWriter writer) {
        int y = groundY + 1;

        if (random.chance(profile.snowLayer)) {
            writer.set(x, y, z, Blocks.SNOW_LAYER);
            return;
        }
        if (random.chance(profile.cactus * density)) {
            int tall = random.nextInt(1, 3);
            for (int i = 0; i < tall; i++) {
                writer.set(x, y + i, z, Blocks.CACTUS);
            }
            return;
        }
        if (groundY <= waterY + 1 && random.chance(profile.sugarCane * density)) {
            int tall = random.nextInt(1, 4);
            for (int i = 0; i < tall; i++) {
                writer.set(x, y + i, z, Blocks.SUGAR_CANE);
            }
            return;
        }
        if (random.chance(profile.bamboo * density)) {
            int tall = random.nextInt(4, 13);
            for (int i = 0; i < tall; i++) {
                writer.set(x, y + i, z, Blocks.BAMBOO);
            }
            return;
        }
        if (random.chance(profile.berryBush * density)) {
            writer.set(x, y, z, Blocks.SWEET_BERRY_BUSH);
            return;
        }
        if (random.chance(profile.pumpkinPatch * density)) {
            writer.set(x, y, z, Blocks.HAY_BLOCK);
            return;
        }
        if (random.chance(profile.mushrooms * density)) {
            writer.set(x, y, z, random.nextBoolean() ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM);
            return;
        }
        if (random.chance(profile.deadBush * density)) {
            writer.set(x, y, z, Blocks.DEAD_BUSH);
            return;
        }
        if (random.chance(profile.flowers * density)) {
            writer.set(x, y, z, flower(random));
            return;
        }
        if (random.chance(profile.tallGrass * density)) {
            writer.set(x, y, z, Blocks.TALL_GRASS_LOWER);
            writer.set(x, y + 1, z, Blocks.TALL_GRASS_UPPER);
            return;
        }
        if (random.chance(profile.fern * density)) {
            writer.set(x, y, z, Blocks.FERN);
            return;
        }
        if (random.chance(profile.grass * density)) {
            writer.set(x, y, z, Blocks.SHORT_GRASS);
            return;
        }
        if (random.chance(profile.mossPatches * density)) {
            writer.set(x, y, z, Blocks.MOSS_CARPET);
        }
    }

    private void decorateUnderwater(FastRandom random, DecorationProfile profile, double density,
                                    int x, int groundY, int z, int waterY, RegionWriter writer) {
        int depth = waterY - groundY;
        int y = groundY + 1;

        if (depth <= 1 && random.chance(profile.iceSheet)) {
            writer.set(x, waterY, z, Blocks.ICE);
            return;
        }
        if (random.chance(profile.magmaVents * density)) {
            writer.set(x, groundY, z, Blocks.MAGMA_BLOCK);
            return;
        }
        if (depth > 3 && random.chance(profile.kelp * density)) {
            int tall = Math.min(depth - 2, random.nextInt(3, 18));
            for (int i = 0; i < tall; i++) {
                writer.set(x, y + i, z, i == tall - 1 ? Blocks.KELP : Blocks.KELP_PLANT);
            }
            return;
        }
        if (depth < 14 && random.chance(profile.coral * density)) {
            writer.set(x, groundY, z, coralBlock(random));
            if (random.chance(0.5)) {
                writer.set(x, y, z, coralFan(random));
            }
            return;
        }
        if (random.chance(profile.seaPickles * density)) {
            writer.set(x, y, z, Blocks.SEA_PICKLE);
            return;
        }
        if (random.chance(profile.sponges * density)) {
            writer.set(x, y, z, Blocks.WET_SPONGE);
            return;
        }
        if (random.chance(profile.seagrass * density)) {
            if (depth > 2 && random.chance(0.3)) {
                writer.set(x, y, z, Blocks.TALL_SEAGRASS_LOWER);
                writer.set(x, y + 1, z, Blocks.TALL_SEAGRASS_UPPER);
            } else {
                writer.set(x, y, z, Blocks.SEAGRASS);
            }
        }
    }

    private static int flower(FastRandom random) {
        return switch (random.nextInt(8)) {
            case 0 -> Blocks.POPPY;
            case 1 -> Blocks.DANDELION;
            case 2 -> Blocks.CORNFLOWER;
            case 3 -> Blocks.AZURE_BLUET;
            case 4 -> Blocks.OXEYE_DAISY;
            case 5 -> Blocks.ALLIUM;
            case 6 -> Blocks.LILY_OF_THE_VALLEY;
            default -> Blocks.BLUE_ORCHID;
        };
    }

    private static int coralBlock(FastRandom random) {
        return switch (random.nextInt(5)) {
            case 0 -> Blocks.TUBE_CORAL_BLOCK;
            case 1 -> Blocks.BRAIN_CORAL_BLOCK;
            case 2 -> Blocks.BUBBLE_CORAL_BLOCK;
            case 3 -> Blocks.FIRE_CORAL_BLOCK;
            default -> Blocks.HORN_CORAL_BLOCK;
        };
    }

    private static int coralFan(FastRandom random) {
        return switch (random.nextInt(3)) {
            case 0 -> Blocks.TUBE_CORAL_FAN;
            case 1 -> Blocks.BRAIN_CORAL_FAN;
            default -> Blocks.FIRE_CORAL_FAN;
        };
    }
}
