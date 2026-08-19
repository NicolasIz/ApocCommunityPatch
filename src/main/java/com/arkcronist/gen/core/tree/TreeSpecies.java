package com.arkcronist.gen.core.tree;

import com.arkcronist.gen.core.block.Blocks;

/**
 * The tree archetypes the procedural builder knows how to grow.
 *
 * <p>A species is a set of materials plus shape ranges; the actual trunk, branches, roots and canopy
 * are generated per instance from the seed, so no two trees of the same species are identical.</p>
 */
public enum TreeSpecies {

    OAK(Blocks.OAK_LOG, Blocks.OAK_LOG_X, Blocks.OAK_LOG_Z, Blocks.OAK_LEAVES, Blocks.OAK_WOOD,
            5, 9, 1, 0.55, Canopy.ROUND),
    BIG_OAK(Blocks.OAK_LOG, Blocks.OAK_LOG_X, Blocks.OAK_LOG_Z, Blocks.OAK_LEAVES, Blocks.OAK_WOOD,
            9, 17, 2, 0.85, Canopy.BROAD),
    BIRCH(Blocks.BIRCH_LOG, Blocks.BIRCH_LOG_X, Blocks.BIRCH_LOG_Z, Blocks.BIRCH_LEAVES, Blocks.OAK_WOOD,
            9, 16, 1, 0.25, Canopy.SPIRE),
    SPRUCE(Blocks.SPRUCE_LOG, Blocks.SPRUCE_LOG_X, Blocks.SPRUCE_LOG_Z, Blocks.SPRUCE_LEAVES, Blocks.SPRUCE_WOOD,
            8, 16, 1, 0.30, Canopy.PAGODA),
    GIANT_SPRUCE(Blocks.SPRUCE_LOG, Blocks.SPRUCE_LOG_X, Blocks.SPRUCE_LOG_Z, Blocks.SPRUCE_LEAVES, Blocks.SPRUCE_WOOD,
            18, 34, 2, 0.55, Canopy.PAGODA),
    JUNGLE(Blocks.JUNGLE_LOG, Blocks.JUNGLE_LOG_X, Blocks.JUNGLE_LOG_Z, Blocks.JUNGLE_LEAVES, Blocks.JUNGLE_WOOD,
            10, 20, 1, 0.75, Canopy.BROAD),
    GIANT_JUNGLE(Blocks.JUNGLE_LOG, Blocks.JUNGLE_LOG_X, Blocks.JUNGLE_LOG_Z, Blocks.JUNGLE_LEAVES, Blocks.JUNGLE_WOOD,
            20, 32, 3, 1.0, Canopy.MEGA),
    ACACIA(Blocks.ACACIA_LOG, Blocks.ACACIA_LOG_X, Blocks.ACACIA_LOG_Z, Blocks.ACACIA_LEAVES, Blocks.OAK_WOOD,
            6, 11, 1, 0.95, Canopy.PLATE),
    DARK_OAK(Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_LOG_X, Blocks.DARK_OAK_LOG_Z, Blocks.DARK_OAK_LEAVES, Blocks.DARK_OAK_WOOD,
            9, 15, 3, 0.70, Canopy.MEGA),
    PALE_OAK(Blocks.PALE_OAK_LOG, Blocks.PALE_OAK_LOG_X, Blocks.PALE_OAK_LOG_Z, Blocks.PALE_OAK_LEAVES, Blocks.DARK_OAK_WOOD,
            10, 16, 3, 0.65, Canopy.MEGA),
    MANGROVE(Blocks.MANGROVE_LOG, Blocks.MANGROVE_LOG_X, Blocks.MANGROVE_LOG_Z, Blocks.MANGROVE_LEAVES, Blocks.OAK_WOOD,
            7, 13, 1, 0.80, Canopy.WILLOW),
    CHERRY(Blocks.CHERRY_LOG, Blocks.CHERRY_LOG_X, Blocks.CHERRY_LOG_Z, Blocks.CHERRY_LEAVES, Blocks.OAK_WOOD,
            7, 12, 1, 0.70, Canopy.ROUND),
    AZALEA(Blocks.OAK_LOG, Blocks.OAK_LOG_X, Blocks.OAK_LOG_Z, Blocks.FLOWERING_AZALEA_LEAVES, Blocks.OAK_WOOD,
            4, 7, 1, 0.45, Canopy.ROUND),
    DEAD(Blocks.SPRUCE_LOG, Blocks.SPRUCE_LOG_X, Blocks.SPRUCE_LOG_Z, Blocks.AIR, Blocks.SPRUCE_WOOD,
            5, 10, 1, 0.60, Canopy.NONE);

    /**
     * How the crown is assembled once the skeleton exists.
     *
     * <p>These are silhouettes, not just radii: the shape of the crown is what tells a spruce from an
     * acacia at a hundred blocks away.</p>
     */
    public enum Canopy {
        /** Small rounded ball on a single stem. Young oaks, azaleas. */
        ROUND,
        /** Tall narrow spire on a thin trunk. Birch. */
        SPIRE,
        /** Stacked discs separated by gaps, narrowing upwards. Spruce. */
        PAGODA,
        /** Thick trunk, several main limbs, one large fused crown with lobes. Grown oak, jungle. */
        BROAD,
        /** Trunk splits into inclined limbs, each capped by a flat plate. Acacia. */
        PLATE,
        /** Very thick trunk and a wide, flat, heavy crown. Dark oak, giant jungle. */
        MEGA,
        /** Wide crown with long strands hanging from its edge. Mangrove, willow. */
        WILLOW,
        /** Bare wood, no leaves at all. */
        NONE
    }

    public final int log;
    public final int logX;
    public final int logZ;
    public final int leaves;
    public final int wood;
    public final int minHeight;
    public final int maxHeight;
    public final int trunkRadius;
    public final double branchiness;
    public final Canopy canopy;

    TreeSpecies(int log, int logX, int logZ, int leaves, int wood, int minHeight, int maxHeight,
                int trunkRadius, double branchiness, Canopy canopy) {
        this.log = log;
        this.logX = logX;
        this.logZ = logZ;
        this.leaves = leaves;
        this.wood = wood;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.trunkRadius = trunkRadius;
        this.branchiness = branchiness;
        this.canopy = canopy;
    }

    /** Worst case horizontal reach, used to decide how far populators must look for neighbours. */
    public int maxRadius() {
        return switch (canopy) {
            case PLATE -> 9 + trunkRadius * 2;
            case BROAD -> 8 + trunkRadius * 2;
            case MEGA -> 10 + trunkRadius * 2;
            case WILLOW -> 9;
            case PAGODA -> 5 + trunkRadius;
            case SPIRE -> 3;
            case ROUND -> 4 + trunkRadius;
            case NONE -> 4;
        };
    }

    /** Worst case vertical reach including a leaning or giant variant. */
    public int maxTreeHeight() {
        return (int) (maxHeight * 1.35) + 6;
    }
}
