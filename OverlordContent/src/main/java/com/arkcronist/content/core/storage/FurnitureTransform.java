package com.arkcronist.content.core.storage;

import com.arkcronist.content.core.furniture.DisplayTransform;

import java.util.UUID;

/**
 * One piece of furniture drawn with a transform of its own rather than its type's: one row of
 * {@code furniture_transforms}, kept for the piece's anchor block.
 *
 * @param furnitureId the furniture it was set for; a different piece placed there later ignores it
 */
public record FurnitureTransform(UUID world, int x, int y, int z, String furnitureId, DisplayTransform transform)
        implements PositionIndex.Positioned {

    @Override
    public long key() {
        return BlockKey.pack(x, y, z);
    }
}
