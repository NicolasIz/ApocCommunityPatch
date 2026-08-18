package com.arkcronist.gen.core.structure;

/**
 * A container placed by a structure that should be filled with loot when the chunk loads.
 *
 * @param tier loot quality tier, 0 = camp scraps, 3 = boss hoard
 */
public record LootMarker(int x, int y, int z, int tier, String theme) {
}
