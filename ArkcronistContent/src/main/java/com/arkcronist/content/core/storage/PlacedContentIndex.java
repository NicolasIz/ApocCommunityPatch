package com.arkcronist.content.core.storage;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every placed custom block and piece of furniture of the loaded worlds, in memory, by position.
 *
 * <p>This is what listeners ask on every block event, so a lookup is two hash lookups and never
 * touches the disk. Per world, positions are keyed by {@link BlockKey}. Entries stay when their
 * chunk unloads - a chunk comes back far more often than a world goes away, and re-reading it from
 * the database each time would put disk work back on the hot path - and go only when their world
 * unloads.</p>
 *
 * <p>Reads are lock-free. Writes to one world synchronise on that world, which costs nothing
 * uncontended and closes one race: the database rows of a world arrive from the storage thread
 * while players may already be breaking blocks in it. A position removed while its world is loading
 * is remembered, and the stale row for it the load brings back is not let in.</p>
 */
public final class PlacedContentIndex {

    private static final class WorldEntries {
        final ConcurrentHashMap<Long, PlacedContent> entries = new ConcurrentHashMap<>();
        /** Positions removed since a load began; null when no load is running. */
        Set<Long> removedDuringLoad;
        int loadsRunning;
    }

    private final ConcurrentHashMap<UUID, WorldEntries> worlds = new ConcurrentHashMap<>();

    public PlacedContent get(UUID world, int x, int y, int z) {
        WorldEntries entries = worlds.get(world);
        return entries == null ? null : entries.entries.get(BlockKey.pack(x, y, z));
    }

    public void put(PlacedContent content) {
        WorldEntries world = worlds.computeIfAbsent(content.world(), ignored -> new WorldEntries());
        synchronized (world) {
            world.entries.put(content.key(), content);
        }
    }

    /** @return what stood there, or null */
    public PlacedContent remove(UUID world, int x, int y, int z) {
        WorldEntries entries = worlds.get(world);
        if (entries == null) {
            return null;
        }
        long key = BlockKey.pack(x, y, z);
        synchronized (entries) {
            if (entries.removedDuringLoad != null) {
                entries.removedDuringLoad.add(key);
            }
            return entries.entries.remove(key);
        }
    }

    /** Call before asking storage for a world's rows, on the thread that decides to load it. */
    public void beginLoad(UUID world) {
        WorldEntries entries = worlds.computeIfAbsent(world, ignored -> new WorldEntries());
        synchronized (entries) {
            if (entries.loadsRunning++ == 0) {
                entries.removedDuringLoad = new HashSet<>();
            }
        }
    }

    /**
     * Adds a world's stored rows. A position already present is newer than the row and wins; a
     * position removed since {@link #beginLoad} stays removed.
     *
     * @return how many rows were let in
     */
    public int finishLoad(UUID world, Collection<PlacedContent> rows) {
        WorldEntries entries = worlds.get(world);
        if (entries == null) {
            // The world unloaded while its rows were on their way.
            return 0;
        }
        int added = 0;
        synchronized (entries) {
            for (PlacedContent row : rows) {
                long key = row.key();
                boolean removed = entries.removedDuringLoad != null && entries.removedDuringLoad.contains(key);
                if (!removed && entries.entries.putIfAbsent(key, row) == null) {
                    added++;
                }
            }
            if (--entries.loadsRunning <= 0) {
                entries.loadsRunning = 0;
                entries.removedDuringLoad = null;
            }
        }
        return added;
    }

    /** Forgets a world's entries; its rows stay in storage. */
    public void dropWorld(UUID world) {
        worlds.remove(world);
    }

    public int size(UUID world) {
        WorldEntries entries = worlds.get(world);
        return entries == null ? 0 : entries.entries.size();
    }

    public int size() {
        return worlds.values().stream().mapToInt(entries -> entries.entries.size()).sum();
    }
}
