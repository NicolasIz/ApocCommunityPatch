package com.arkcronist.gen.core.cave;

import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.Hashing;
import com.arkcronist.gen.core.terrain.BlockWriter;

/**
 * Dresses the underground.
 *
 * <p>Runs inside the single column pass, at the exact moment a floor or a ceiling is discovered, so
 * it costs nothing extra to find surfaces: the generator already knows where rock meets air. Each
 * cave biome gets its own floor material, its own hanging growth and its own light source, which is
 * what turns a carved void into a place worth exploring.</p>
 */
public final class CaveDecorator {

    private static final long SALT = 0xCA7EDEC0L;

    private final long seed;

    public CaveDecorator(long seed) {
        this.seed = seed;
    }

    /**
     * Decorates the top face of a floor block.
     *
     * @param y the y of the solid floor block itself; growth goes at {@code y + 1}
     * @param headroom how much open space there is above the floor
     * @return replacement block for the floor itself, or -1 to keep the stone that is already there
     */
    public int decorateFloor(int x, int y, int z, CaveBiome biome, int headroom, BlockWriter writer,
                             int localX, int localZ) {
        double roll = Hashing.value3(seed ^ SALT, x, y, z);
        double roll2 = Hashing.value3(seed ^ (SALT + 17), x, y, z);
        int above = y + 1;

        switch (biome) {
            case LUSH -> {
                if (roll < 0.55) {
                    if (roll2 < 0.30 && headroom >= 2) {
                        writer.set(localX, above, localZ, Blocks.MOSS_CARPET);
                    } else if (roll2 < 0.38 && headroom >= 3) {
                        writer.set(localX, above, localZ, roll < 0.20 ? Blocks.FLOWERING_AZALEA : Blocks.AZALEA);
                    } else if (roll2 < 0.45 && headroom >= 3) {
                        writer.set(localX, above, localZ, Blocks.SMALL_DRIPLEAF_LOWER);
                        writer.set(localX, above + 1, localZ, Blocks.SMALL_DRIPLEAF_UPPER);
                    } else if (roll2 < 0.50 && headroom >= 3) {
                        writer.set(localX, above, localZ, Blocks.BIG_DRIPLEAF);
                    } else if (roll2 < 0.56) {
                        writer.set(localX, above, localZ, Blocks.SHORT_GRASS);
                    }
                    return roll < 0.42 ? Blocks.MOSS_BLOCK : Blocks.ROOTED_DIRT;
                }
                if (roll < 0.62) {
                    return Blocks.CLAY;
                }
            }
            case DRIPSTONE -> {
                if (roll < 0.16 && headroom >= 2) {
                    // Stalagmites: taller ones get a proper base and frustum.
                    int height = roll2 < 0.55 ? 1 : roll2 < 0.85 ? 2 : 3;
                    growDripstone(writer, localX, above, localZ, height, headroom, true);
                }
                if (roll < 0.35) {
                    return Blocks.DRIPSTONE;
                }
            }
            case DEEP_DARK -> {
                if (roll < 0.62) {
                    if (roll2 < 0.030 && headroom >= 2) {
                        writer.set(localX, above, localZ, Blocks.SCULK_SHRIEKER);
                    } else if (roll2 < 0.075 && headroom >= 2) {
                        writer.set(localX, above, localZ, Blocks.SCULK_SENSOR);
                    } else if (roll2 < 0.090 && headroom >= 2) {
                        writer.set(localX, above, localZ, Blocks.SCULK_CATALYST);
                    } else if (roll2 < 0.16 && headroom >= 2) {
                        writer.set(localX, above, localZ, Blocks.SCULK_VEIN_UP);
                    }
                    return Blocks.SCULK;
                }
            }
            case ICE -> {
                if (roll < 0.14 && headroom >= 2) {
                    writer.set(localX, above, localZ, Blocks.SNOW_LAYER_DEEP);
                }
                if (roll < 0.55) {
                    return roll2 < 0.18 ? Blocks.BLUE_ICE : roll2 < 0.62 ? Blocks.PACKED_ICE : Blocks.ICE;
                }
            }
            case MUSHROOM -> {
                if (roll < 0.10 && headroom >= 5) {
                    growMushroom(writer, localX, above, localZ, roll2 < 0.5, headroom);
                } else if (roll < 0.22 && headroom >= 2) {
                    writer.set(localX, above, localZ, roll2 < 0.5 ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM);
                } else if (roll < 0.24 && headroom >= 2) {
                    writer.set(localX, above, localZ, Blocks.SHROOMLIGHT);
                }
                if (roll < 0.50) {
                    return roll2 < 0.4 ? Blocks.MYCELIUM : Blocks.PODZOL;
                }
            }
            case CRYSTAL -> {
                if (roll < 0.10 && headroom >= 2) {
                    writer.set(localX, above, localZ, roll2 < 0.4 ? Blocks.AMETHYST_CLUSTER_UP
                            : roll2 < 0.7 ? Blocks.LARGE_AMETHYST_BUD_UP : Blocks.SMALL_AMETHYST_BUD_UP);
                }
                if (roll < 0.40) {
                    return roll2 < 0.35 ? Blocks.CALCITE : roll2 < 0.6 ? Blocks.SMOOTH_BASALT : Blocks.AMETHYST_BLOCK;
                }
            }
            case MAGMA -> {
                if (roll < 0.10 && headroom >= 2) {
                    writer.set(localX, above, localZ, Blocks.SOUL_FIRE);
                    return Blocks.SOUL_SOIL;
                }
                if (roll < 0.30) {
                    return roll2 < 0.35 ? Blocks.MAGMA_BLOCK : roll2 < 0.7 ? Blocks.BASALT : Blocks.BLACKSTONE;
                }
            }
            case STONE -> {
                if (roll < 0.05 && headroom >= 2) {
                    growDripstone(writer, localX, above, localZ, 1, headroom, true);
                } else if (roll < 0.075 && headroom >= 2) {
                    writer.set(localX, above, localZ, roll2 < 0.5 ? Blocks.BROWN_MUSHROOM : Blocks.RED_MUSHROOM);
                } else if (roll < 0.10) {
                    return Blocks.GRAVEL;
                }
            }
        }
        return -1;
    }

    /**
     * Decorates the underside of a ceiling block.
     *
     * @param y the y of the first air block below the ceiling
     */
    public int decorateCeiling(int x, int y, int z, CaveBiome biome, int headroom, BlockWriter writer,
                               int localX, int localZ) {
        double roll = Hashing.value3(seed ^ (SALT + 91), x, y, z);
        double roll2 = Hashing.value3(seed ^ (SALT + 137), x, y, z);

        switch (biome) {
            case LUSH -> {
                if (roll < 0.22 && headroom >= 3) {
                    // Glow berry vines: the light source of a lush cave.
                    int length = 1 + (int) (roll2 * Math.min(6, headroom - 1));
                    for (int i = 0; i < length; i++) {
                        boolean tip = i == length - 1;
                        writer.set(localX, y - i, localZ,
                                tip ? Blocks.CAVE_VINES_BERRIES
                                        : (roll2 < 0.6 ? Blocks.CAVE_VINES_PLANT_BERRIES : Blocks.CAVE_VINES_PLANT));
                    }
                    return Blocks.MOSS_BLOCK;
                }
                if (roll < 0.30 && headroom >= 2) {
                    writer.set(localX, y, localZ, Blocks.SPORE_BLOSSOM);
                    return Blocks.MOSS_BLOCK;
                }
                if (roll < 0.42 && headroom >= 2) {
                    writer.set(localX, y, localZ, Blocks.HANGING_ROOTS);
                    return Blocks.ROOTED_DIRT;
                }
                if (roll < 0.55) {
                    return Blocks.MOSS_BLOCK;
                }
            }
            case DRIPSTONE -> {
                if (roll < 0.30 && headroom >= 2) {
                    int height = roll2 < 0.4 ? 1 : roll2 < 0.75 ? 2 : roll2 < 0.93 ? 3 : 5;
                    growDripstone(writer, localX, y, localZ, height, headroom, false);
                    return Blocks.DRIPSTONE;
                }
                if (roll < 0.45) {
                    return Blocks.DRIPSTONE;
                }
            }
            case DEEP_DARK -> {
                if (roll < 0.18 && headroom >= 2) {
                    writer.set(localX, y, localZ, Blocks.SCULK_VEIN_DOWN);
                }
                if (roll < 0.55) {
                    return Blocks.SCULK;
                }
            }
            case ICE -> {
                if (roll < 0.30 && headroom >= 2) {
                    int length = 1 + (int) (roll2 * Math.min(4, headroom - 1));
                    for (int i = 0; i < length; i++) {
                        writer.set(localX, y - i, localZ, Blocks.ICE);
                    }
                }
                if (roll < 0.60) {
                    return roll2 < 0.5 ? Blocks.PACKED_ICE : Blocks.BLUE_ICE;
                }
            }
            case MUSHROOM -> {
                if (roll < 0.10 && headroom >= 2) {
                    writer.set(localX, y, localZ, Blocks.SHROOMLIGHT);
                }
                if (roll < 0.30) {
                    return roll2 < 0.5 ? Blocks.MUSHROOM_STEM : Blocks.BROWN_MUSHROOM_BLOCK;
                }
            }
            case CRYSTAL -> {
                if (roll < 0.12 && headroom >= 2) {
                    writer.set(localX, y, localZ, Blocks.AMETHYST_CLUSTER_DOWN);
                }
                if (roll < 0.35) {
                    return roll2 < 0.5 ? Blocks.CALCITE : Blocks.AMETHYST_BLOCK;
                }
            }
            case MAGMA -> {
                if (roll < 0.12 && headroom >= 2) {
                    writer.set(localX, y, localZ, Blocks.GLOW_LICHEN_DOWN);
                }
                if (roll < 0.30) {
                    return roll2 < 0.5 ? Blocks.BLACKSTONE : Blocks.BASALT;
                }
            }
            case STONE -> {
                if (roll < 0.10 && headroom >= 2) {
                    writer.set(localX, y, localZ, Blocks.GLOW_LICHEN_DOWN);
                } else if (roll < 0.16 && headroom >= 2) {
                    growDripstone(writer, localX, y, localZ, roll2 < 0.7 ? 1 : 2, headroom, false);
                } else if (roll < 0.20 && headroom >= 2) {
                    writer.set(localX, y, localZ, Blocks.HANGING_ROOTS);
                }
            }
        }
        return -1;
    }

    private void growDripstone(BlockWriter writer, int localX, int startY, int localZ,
                               int height, int headroom, boolean upwards) {
        int limit = Math.min(height, Math.max(1, headroom - 1));
        for (int i = 0; i < limit; i++) {
            int y = upwards ? startY + i : startY - i;
            boolean tip = i == limit - 1;
            int block;
            if (limit == 1) {
                block = upwards ? Blocks.DRIPSTONE_TIP_UP : Blocks.DRIPSTONE_TIP_DOWN;
            } else if (tip) {
                block = upwards ? Blocks.DRIPSTONE_TIP_UP : Blocks.DRIPSTONE_TIP_DOWN;
            } else if (i == 0) {
                block = upwards ? Blocks.DRIPSTONE_BASE_UP : Blocks.DRIPSTONE_BASE_DOWN;
            } else if (i == 1) {
                block = upwards ? Blocks.DRIPSTONE_MIDDLE_UP : Blocks.DRIPSTONE_MIDDLE_DOWN;
            } else {
                block = upwards ? Blocks.DRIPSTONE_FRUSTUM_UP : Blocks.DRIPSTONE_FRUSTUM_DOWN;
            }
            writer.set(localX, y, localZ, block);
        }
    }

    private void growMushroom(BlockWriter writer, int localX, int startY, int localZ,
                              boolean red, int headroom) {
        int stem = Math.min(3, headroom - 2);
        for (int i = 0; i < stem; i++) {
            writer.set(localX, startY + i, localZ, Blocks.MUSHROOM_STEM);
        }
        writer.set(localX, startY + stem, localZ, red ? Blocks.RED_MUSHROOM_BLOCK : Blocks.BROWN_MUSHROOM_BLOCK);
    }
}
