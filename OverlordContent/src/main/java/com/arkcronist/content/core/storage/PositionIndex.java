package com.arkcronist.content.core.storage;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything of one kind standing in the loaded worlds, in memory, by position - placed blocks and
 * furniture in one index, planted crops in another.
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
public class PositionIndex<T extends PositionIndex.Positioned> {

    /** Something that stands at one block of one world. */
    public interface Positioned {

        UUID world();

        /** The position packed as {@link BlockKey} does it. */
        long key();
    }


    private static final class WorldEntries<T> {
        final ConcurrentHashMap<Long, T> entries = new ConcurrentHashMap<>();
        /** Positions removed since a load began; null when no load is running. */
        Set<Long> removedDuringLoad;
        int loadsRunning;
    }

    private final ConcurrentHashMap<UUID, WorldEntries<T>> worlds = new ConcurrentHashMap<>();

    public T get(UUID world, int x, int y, int z) {
        WorldEntries<T> entries = worlds.get(world);
        return entries == null ? null : entries.entries.get(BlockKey.pack(x, y, z));
    }

    public void put(T content) {
        WorldEntries<T> world = worlds.computeIfAbsent(content.world(), ignored -> new WorldEntries<>());
        synchronized (world) {
            world.entries.put(content.key(), content);
        }
    }

    /** @return what stood there, or null */
    public T remove(UUID world, int x, int y, int z) {
        WorldEntries<T> entries = worlds.get(world);
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
        WorldEntries<T> entries = worlds.computeIfAbsent(world, ignored -> new WorldEntries<>());
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
    public int finishLoad(UUID world, Collection<T> rows) {
        WorldEntries<T> entries = worlds.get(world);
        if (entries == null) {
            // The world unloaded while its rows were on their way.
            return 0;
        }
        int added = 0;
        synchronized (entries) {
            for (T row : rows) {
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

    /**
     * Swaps {@code current} for {@code updated} if {@code current} is still what stands there - the
     * way to change an entry from a thread other than the one that removes it, without bringing
     * back one that was removed in between.
     *
     * @return whether the swap happened
     */
    public boolean replace(T current, T updated) {
        WorldEntries<T> entries = worlds.get(current.world());
        if (entries == null) {
            return false;
        }
        synchronized (entries) {
            return entries.entries.replace(current.key(), current, updated);
        }
    }

    /**
     * Removes {@code expected} only if it is still what stands there: a purge decided on another
     * thread never takes away an entry that was replaced in the meantime.
     *
     * @return whether it was removed
     */
    public boolean removeExact(T expected) {
        WorldEntries<T> entries = worlds.get(expected.world());
        if (entries == null) {
            return false;
        }
        synchronized (entries) {
            boolean removed = entries.entries.remove(expected.key(), expected);
            if (removed && entries.removedDuringLoad != null) {
                entries.removedDuringLoad.add(expected.key());
            }
            return removed;
        }
    }

    /** A live view of one world's entries; safe to walk from any thread. */
    public Collection<T> entries(UUID world) {
        WorldEntries<T> entries = worlds.get(world);
        return entries == null ? List.of() : Collections.unmodifiableCollection(entries.entries.values());
    }

    /** Whether a world's stored rows are still on their way: until they are, memory is not the whole truth. */
    public boolean loading(UUID world) {
        WorldEntries<T> entries = worlds.get(world);
        if (entries == null) {
            return false;
        }
        synchronized (entries) {
            return entries.loadsRunning > 0;
        }
    }

    /** The worlds with entries in memory. */
    public Set<UUID> worlds() {
        return Collections.unmodifiableSet(worlds.keySet());
    }

    /** Forgets a world's entries; its rows stay in storage. */
    public void dropWorld(UUID world) {
        worlds.remove(world);
    }

    public int size(UUID world) {
        WorldEntries<T> entries = worlds.get(world);
        return entries == null ? 0 : entries.entries.size();
    }

    public int size() {
        return worlds.values().stream().mapToInt(entries -> entries.entries.size()).sum();
    }
}
