package com.arkcronist.gen.core.bench;

import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.structure.RegionWriter;
import com.arkcronist.gen.core.terrain.BlockWriter;

/**
 * A block sink that only counts, used by benchmarks and tests to exercise the full generation path
 * without a server attached.
 */
public final class CountingWriter implements BlockWriter, RegionWriter {

    private final int minY;
    private final int maxY;
    private final int chunkX;
    private final int chunkZ;
    private long blocks;
    private long solids;

    public CountingWriter(int minY, int maxY) {
        this(minY, maxY, 0, 0);
    }

    public CountingWriter(int minY, int maxY, int chunkX, int chunkZ) {
        this.minY = minY;
        this.maxY = maxY;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    @Override
    public void set(int localX, int y, int localZ, int blockId) {
        blocks++;
        if (!Blocks.isAir(blockId) && !Blocks.isLiquid(blockId)) {
            solids++;
        }
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return x >= (chunkX << 4) && x <= (chunkX << 4) + 15
                && z >= (chunkZ << 4) && z <= (chunkZ << 4) + 15
                && y >= minY && y < maxY;
    }

    @Override
    public boolean intersectsColumn(int boxMinX, int boxMinZ, int boxMaxX, int boxMaxZ) {
        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;
        return boxMaxX >= x0 && boxMinX <= x0 + 15 && boxMaxZ >= z0 && boxMinZ <= z0 + 15;
    }

    @Override
    public int minY() {
        return minY;
    }

    @Override
    public int maxY() {
        return maxY;
    }

    public long blocks() {
        return blocks;
    }

    public long solids() {
        return solids;
    }
}
