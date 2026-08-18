package com.arkcronist.gen.core.terrain;

/**
 * Chunk local block sink.
 *
 * <p>The core writes integer block ids into this interface and never learns what is on the other
 * side: in production it is Paper's {@code ChunkData}, in tests it is a plain array. That is what
 * makes the entire terrain pipeline unit testable without a running server.</p>
 */
public interface BlockWriter {

    /**
     * @param localX 0-15
     * @param localZ 0-15
     */
    void set(int localX, int y, int localZ, int blockId);

    int minY();

    int maxY();
}
