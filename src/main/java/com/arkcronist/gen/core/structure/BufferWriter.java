package com.arkcronist.gen.core.structure;

/**
 * A {@link RegionWriter} that pours straight into a {@link StructureBuffer}.
 *
 * <p>Prefabs stamp themselves through a writer, and structures are built once into a buffer that is
 * later blitted per chunk. This is the joint between the two: it clips nothing horizontally, because
 * the buffer <em>is</em> the whole structure, and only guards the world's height limits.</p>
 */
public record BufferWriter(StructureBuffer buffer, int minY, int maxY) implements RegionWriter {

    @Override
    public void set(int x, int y, int z, int blockId) {
        if (y >= minY && y < maxY) {
            buffer.set(x, y, z, blockId);
        }
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return y >= minY && y < maxY;
    }

    @Override
    public boolean intersectsColumn(int minX, int minZ, int maxX, int maxZ) {
        return true;
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
