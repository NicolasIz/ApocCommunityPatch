package com.arkcronist.gen.core.biome;

/** Coarse grouping used by decoration, structures and mob rules. */
public enum BiomeCategory {

    OCEAN_TRENCH(true),
    OCEAN_DEEP(true),
    OCEAN(true),
    OCEAN_SHELF(true),
    BEACH(false),
    RIVER(false),
    LAKE(false),
    PLAINS(false),
    FOREST(false),
    JUNGLE(false),
    SAVANNA(false),
    DESERT(false),
    BADLANDS(false),
    SWAMP(false),
    TAIGA(false),
    TUNDRA(false),
    HIGHLAND(false),
    MOUNTAIN(false),
    PEAK(false),
    VOLCANIC(false),
    MUSHROOM(false),
    EXTREME(false),
    FLOATING(false);

    private final boolean oceanic;

    BiomeCategory(boolean oceanic) {
        this.oceanic = oceanic;
    }

    public boolean oceanic() {
        return oceanic;
    }

    public boolean mountainous() {
        return this == MOUNTAIN || this == PEAK || this == HIGHLAND || this == EXTREME;
    }
}
