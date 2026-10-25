package com.arkcronist.content.core.storage;

import java.util.UUID;

/**
 * A custom block or piece of furniture standing at one position in one world: one row of
 * {@code custom_blocks_world}.
 *
 * @param world     the world's UID, which survives renaming the world folder
 * @param contentId {@code namespace:id} of what stands there
 */
public record PlacedContent(UUID world, int x, int y, int z, String contentId, Kind kind)
        implements PositionIndex.Positioned {

    /** The {@code type} column. Stored by name, so the order here can change freely. */
    public enum Kind {
        BLOCK,
        FURNITURE
    }

    @Override
    public long key() {
        return BlockKey.pack(x, y, z);
    }
}
