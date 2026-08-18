package com.arkcronist.gen.core.structure;

/**
 * World coordinate block sink used by trees and structures.
 *
 * <p>Implementations clip to whatever area the caller is allowed to touch (usually one chunk plus
 * the populator's neighbourhood). Features always draw their <em>complete</em> shape and let the
 * writer discard what falls outside; that is the whole trick behind features that are never cut off
 * at a chunk border.</p>
 */
public interface RegionWriter {

    void set(int x, int y, int z, int blockId);

    /** True when the position would actually be written; lets callers skip expensive work. */
    boolean contains(int x, int y, int z);

    /**
     * True when any part of the given horizontal box could be written.
     *
     * <p>Features use this to reject neighbours' work early: a chunk recomputes the trees of the
     * chunks around it, and the overwhelming majority of those trees never reach into it.</p>
     */
    boolean intersectsColumn(int minX, int minZ, int maxX, int maxZ);

    int minY();

    int maxY();
}
