package com.arkcronist.gen.core.biome;

/** Which families of structures a biome accepts. */
public enum StructureTag {
    // Settlements and strongholds of the living
    VILLAGE,
    CITY,
    CASTLE,
    FORTRESS,
    OUTPOST,
    CAMP,
    // Towers and monuments
    TOWER,
    BATTLE_TOWER,
    TEMPLE,
    PYRAMID,
    JUNGLE_TEMPLE,
    // Ruins and remains
    RUINS,
    TRAIL_RUINS,
    RUINED_PORTAL,
    FOSSIL,
    // Cold and swamp specialities
    IGLOO,
    WITCH_HUT,
    // Water
    UNDERWATER,
    MONUMENT,
    SHIPWRECK,
    TREASURE,
    /** Schematic vessels: floating fleets in deep water, wrecks on shelves and beaches. */
    SHIP,
    // Air
    SKY,
    BRIDGE,
    // Underground
    UNDERGROUND,
    DUNGEON,
    MINESHAFT,
    STRONGHOLD,
    ANCIENT_CITY,
    TRIAL_CHAMBER,
    GEODE,
    // Forest specialities
    MANSION,
    /** Schematic landmarks loaded from {@code prefabs/ruins/}. */
    PREFAB_RUIN
}
