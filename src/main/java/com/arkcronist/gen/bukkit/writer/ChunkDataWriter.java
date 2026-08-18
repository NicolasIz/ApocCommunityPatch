package com.arkcronist.gen.bukkit.writer;

import com.arkcronist.gen.bukkit.BlockBridge;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.terrain.BlockWriter;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;

/** Adapts Paper's {@code ChunkData} to the core's {@link BlockWriter}. */
public final class ChunkDataWriter implements BlockWriter {

    private final ChunkGenerator.ChunkData data;

    public ChunkDataWriter(ChunkGenerator.ChunkData data) {
        this.data = data;
    }

    @Override
    public void set(int localX, int y, int localZ, int blockId) {
        if (y < data.getMinHeight() || y >= data.getMaxHeight()) {
            return;
        }
        // Chunks start empty, so writing air during terrain generation would be pure overhead.
        if (blockId == Blocks.AIR) {
            return;
        }
        BlockData block = BlockBridge.get(blockId);
        if (block != null) {
            data.setBlock(localX, y, localZ, block);
        }
    }

    @Override
    public int minY() {
        return data.getMinHeight();
    }

    @Override
    public int maxY() {
        return data.getMaxHeight();
    }
}
