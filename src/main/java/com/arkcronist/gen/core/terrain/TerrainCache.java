package com.arkcronist.gen.core.terrain;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Bounded cache of per chunk terrain data.
 *
 * <p>Paper calls the generator several times for the same chunk (terrain, then populators for
 * decoration, trees and structures, then neighbours asking about heights). Recomputing the noise
 * stack every time would multiply the cost by five or more, so results are memoised with a hard
 * entry cap: memory stays predictable instead of growing with the explored area.</p>
 */
public final class TerrainCache {

    private final ConcurrentHashMap<Long, ChunkTerrain> entries = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Long> insertionOrder = new ConcurrentLinkedQueue<>();
    private final AtomicInteger size = new AtomicInteger();
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final int maxEntries;

    public TerrainCache(int maxEntries) {
        this.maxEntries = Math.max(64, maxEntries);
    }

    public static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public ChunkTerrain get(int chunkX, int chunkZ, Function<Long, ChunkTerrain> loader) {
        long key = key(chunkX, chunkZ);
        ChunkTerrain cached = entries.get(key);
        if (cached != null) {
            hits.incrementAndGet();
            return cached;
        }
        misses.incrementAndGet();
        ChunkTerrain created = entries.computeIfAbsent(key, k -> {
            insertionOrder.add(k);
            size.incrementAndGet();
            return loader.apply(k);
        });
        evictIfNeeded();
        return created;
    }

    private void evictIfNeeded() {
        while (size.get() > maxEntries) {
            Long oldest = insertionOrder.poll();
            if (oldest == null) {
                return;
            }
            if (entries.remove(oldest) != null) {
                size.decrementAndGet();
            }
        }
    }

    public void clear() {
        entries.clear();
        insertionOrder.clear();
        size.set(0);
    }

    public int size() {
        return size.get();
    }

    public long hits() {
        return hits.get();
    }

    public long misses() {
        return misses.get();
    }

    public double hitRate() {
        long total = hits.get() + misses.get();
        return total == 0 ? 0.0 : hits.get() / (double) total;
    }
}
