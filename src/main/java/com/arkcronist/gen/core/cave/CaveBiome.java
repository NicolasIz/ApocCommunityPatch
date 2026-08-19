package com.arkcronist.gen.core.cave;

/**
 * Underground regions with their own rock, vegetation and light.
 *
 * <p>A cave in ArkcronistGenerator is never just "a hole in the stone". Every cavity belongs to a
 * cave biome that decides what the floor is made of, what grows on the ceiling, what lights the
 * room, and which structures may hide inside it.</p>
 */
public enum CaveBiome {

    /** Ordinary stone caves: gravel floors, a little lichen, the occasional dripstone. */
    STONE,
    /** Moss, azalea, glow berries hanging from the ceiling, spore blossoms, clay and water. */
    LUSH,
    /** Forests of pointed dripstone above and below, dripstone blocks, dry and quiet. */
    DRIPSTONE,
    /** Sculk, catalysts, shriekers, no natural light at all. Home of the ancient city. */
    DEEP_DARK,
    /** Packed and blue ice, powder snow, frozen floors. Sits under cold surface regions. */
    ICE,
    /** Giant mushrooms, shroomlight, mycelium and podzol floors. */
    MUSHROOM,
    /** Amethyst geodes, calcite and smooth basalt shells, budding walls. */
    CRYSTAL,
    /** Basalt and blackstone, magma floors, lava falls and soul fire. */
    MAGMA;

    public boolean lit() {
        return this == LUSH || this == MUSHROOM || this == CRYSTAL || this == MAGMA;
    }

    public boolean allowsWater() {
        return this != MAGMA && this != DEEP_DARK;
    }
}
