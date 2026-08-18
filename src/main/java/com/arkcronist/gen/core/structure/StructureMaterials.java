package com.arkcronist.gen.core.structure;

import com.arkcronist.gen.core.biome.ArkBiome;
import com.arkcronist.gen.core.biome.BiomeCategory;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;

/**
 * Regional building materials.
 *
 * <p>The same structure blueprint built in a desert, a volcanic range and a taiga must not come out
 * of the same stone. Materials are chosen from the biome so architecture reads as local: sandstone
 * and smooth sandstone in the dunes, blackstone and basalt on the volcano, spruce and cobble in the
 * conifer belt.</p>
 */
public final class StructureMaterials {

    public final int wall;
    public final int wallAccent;
    public final int floor;
    public final int roof;
    public final int slab;
    public final int stairs;
    public final int plank;
    public final int fence;
    public final int light;
    public final int hangingLight;
    public final int pillar;
    public final int glass;

    private StructureMaterials(int wall, int wallAccent, int floor, int roof, int slab, int stairs,
                               int plank, int fence, int light, int hangingLight, int pillar, int glass) {
        this.wall = wall;
        this.wallAccent = wallAccent;
        this.floor = floor;
        this.roof = roof;
        this.slab = slab;
        this.stairs = stairs;
        this.plank = plank;
        this.fence = fence;
        this.light = light;
        this.hangingLight = hangingLight;
        this.pillar = pillar;
        this.glass = glass;
    }

    public static StructureMaterials forBiome(ArkBiome biome, FastRandom random) {
        BiomeCategory category = biome.category;
        return switch (category) {
            case DESERT, BADLANDS -> new StructureMaterials(
                    Blocks.SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CHISELED_SANDSTONE,
                    Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_STAIRS_NORTH, Blocks.BIRCH_PLANKS,
                    Blocks.OAK_FENCE, Blocks.LANTERN, Blocks.HANGING_LANTERN, Blocks.CHISELED_SANDSTONE, Blocks.GLASS);
            case VOLCANIC, OCEAN_TRENCH -> new StructureMaterials(
                    Blocks.BLACKSTONE, Blocks.POLISHED_DEEPSLATE, Blocks.SMOOTH_BASALT, Blocks.DEEPSLATE_TILES,
                    Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_STAIRS_NORTH, Blocks.DARK_OAK_PLANKS,
                    Blocks.DARK_OAK_FENCE, Blocks.LANTERN, Blocks.HANGING_LANTERN, Blocks.BASALT, Blocks.GLASS);
            case TAIGA, TUNDRA, PEAK -> new StructureMaterials(
                    Blocks.COBBLESTONE, Blocks.STONE_BRICKS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS,
                    Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_STAIRS_NORTH, Blocks.SPRUCE_PLANKS,
                    Blocks.SPRUCE_FENCE, Blocks.LANTERN, Blocks.HANGING_LANTERN, Blocks.SPRUCE_LOG, Blocks.GLASS);
            case JUNGLE, SWAMP -> new StructureMaterials(
                    Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.DARK_OAK_PLANKS,
                    Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_STAIRS_NORTH, Blocks.OAK_PLANKS,
                    Blocks.OAK_FENCE, Blocks.TORCH, Blocks.HANGING_LANTERN, Blocks.JUNGLE_LOG, Blocks.GLASS);
            case OCEAN, OCEAN_DEEP, OCEAN_SHELF -> new StructureMaterials(
                    Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.PRISMARINE_BRICKS,
                    Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_STAIRS_NORTH, Blocks.DARK_PRISMARINE,
                    Blocks.IRON_BARS, Blocks.SEA_LANTERN, Blocks.SEA_LANTERN, Blocks.DARK_PRISMARINE, Blocks.GLASS);
            case MOUNTAIN, HIGHLAND -> new StructureMaterials(
                    Blocks.STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.DARK_OAK_PLANKS,
                    Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_STAIRS_NORTH, Blocks.SPRUCE_PLANKS,
                    Blocks.SPRUCE_FENCE, Blocks.LANTERN, Blocks.HANGING_LANTERN, Blocks.STONE_BRICKS, Blocks.GLASS);
            default -> random.chance(0.5)
                    ? new StructureMaterials(Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.OAK_PLANKS,
                    Blocks.DARK_OAK_PLANKS, Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_STAIRS_NORTH,
                    Blocks.OAK_PLANKS, Blocks.OAK_FENCE, Blocks.TORCH, Blocks.HANGING_LANTERN,
                    Blocks.OAK_LOG, Blocks.GLASS)
                    : new StructureMaterials(Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.OAK_PLANKS,
                    Blocks.BRICKS, Blocks.OAK_SLAB, Blocks.OAK_STAIRS_NORTH, Blocks.OAK_PLANKS,
                    Blocks.OAK_FENCE, Blocks.TORCH, Blocks.HANGING_LANTERN, Blocks.OAK_LOG, Blocks.GLASS_PANE);
        };
    }
}
