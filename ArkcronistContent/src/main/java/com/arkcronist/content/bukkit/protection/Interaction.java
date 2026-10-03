package com.arkcronist.content.bukkit.protection;

/**
 * What a player is about to do to a block, as protection plugins tell those things apart: a
 * region can let visitors open chests and still forbid them to build.
 */
public enum Interaction {
    /** Putting something into the world: planting a crop. */
    PLACE,
    /** Taking something out of it: harvesting a crop, breaking furniture, uprooting a crop with a bucket. */
    BREAK,
    /** Changing something in place: bone meal on a crop, trampling the farmland under one. */
    BUILD,
    /** Sitting on it: riding the seat a chair puts under its sitter. */
    SIT,
    /** Opening its inventory: storage furniture. */
    CONTAINER
}
