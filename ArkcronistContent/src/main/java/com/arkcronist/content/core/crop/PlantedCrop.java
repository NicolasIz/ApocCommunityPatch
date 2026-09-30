package com.arkcronist.content.core.crop;

import com.arkcronist.content.core.storage.BlockKey;
import com.arkcronist.content.core.storage.PositionIndex;

import java.util.UUID;

/**
 * One crop growing in the world: one row of {@code custom_crops}. Never changed in place - growing
 * makes a new one - so any thread may read it.
 *
 * @param cropId   {@code namespace:id} of the crop
 * @param stage    0 for just planted, up to the crop's last stage
 * @param progress seconds grown into the current stage
 */
public record PlantedCrop(UUID world, int x, int y, int z, String cropId, int stage, long progress)
        implements PositionIndex.Positioned {

    @Override
    public long key() {
        return BlockKey.pack(x, y, z);
    }

    /** The chunk it is in, packed as {@link #chunkKey(int, int)} does it. */
    public long chunk() {
        return chunkKey(x >> 4, z >> 4);
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public PlantedCrop withGrowth(int stage, long progress) {
        return new PlantedCrop(world, x, y, z, cropId, stage, progress);
    }
}
