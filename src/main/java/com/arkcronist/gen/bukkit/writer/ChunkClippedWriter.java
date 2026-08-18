package com.arkcronist.gen.bukkit.writer;

import com.arkcronist.gen.bukkit.BlockBridge;
import com.arkcronist.gen.core.structure.RegionWriter;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.LimitedRegion;

/**
 * Writes into one chunk of a populator's {@link LimitedRegion}.
 *
 * <p>Clipping to a single chunk (rather than the whole region Paper allows) is what keeps features
 * idempotent: every chunk draws its own blocks and only its own, even though it recomputes the
 * features of its neighbours to find the parts that reach into it.</p>
 */
public final class ChunkClippedWriter implements RegionWriter {

    private final LimitedRegion region;
    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;
    private final int minY;
    private final int maxY;

    public ChunkClippedWriter(LimitedRegion region, int chunkX, int chunkZ, int minY, int maxY) {
        this.region = region;
        this.minX = chunkX << 4;
        this.minZ = chunkZ << 4;
        this.maxX = minX + 15;
        this.maxZ = minZ + 15;
        this.minY = minY;
        this.maxY = maxY;
    }

    @Override
    public void set(int x, int y, int z, int blockId) {
        if (!contains(x, y, z)) {
            return;
        }
        BlockData block = BlockBridge.get(blockId);
        if (block != null) {
            region.setBlockData(x, y, z, block);
        }
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ
                && y >= minY() && y < maxY()
                && region.isInRegion(x, y, z);
    }

    @Override
    public boolean intersectsColumn(int boxMinX, int boxMinZ, int boxMaxX, int boxMaxZ) {
        return boxMaxX >= minX && boxMinX <= maxX && boxMaxZ >= minZ && boxMinZ <= maxZ;
    }

    @Override
    public int minY() {
        return minY;
    }

    @Override
    public int maxY() {
        return maxY;
    }
}
