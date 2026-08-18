package com.arkcronist.gen.bukkit.mobs;

import com.arkcronist.gen.core.structure.MobSpawn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds mob requests produced during generation until the chunk is live on the main thread.
 *
 * <p>Generation runs on Paper's worker threads where spawning entities is not allowed, so structures
 * record what they want and this queue hands it over on {@code ChunkLoadEvent}. Entries are removed
 * as they are consumed, and the queue is bounded per chunk key so an unvisited backlog cannot grow
 * without limit.</p>
 */
public final class MobQueue {

    /** Hard cap so chunks that are generated but never visited cannot grow the queue forever. */
    private static final int MAX_CHUNKS = 8192;

    private final Map<Long, List<MobSpawn>> pending = new ConcurrentHashMap<>();

    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public void enqueue(int chunkX, int chunkZ, List<MobSpawn> spawns) {
        if (spawns.isEmpty() || pending.size() >= MAX_CHUNKS) {
            return;
        }
        pending.merge(key(chunkX, chunkZ), new ArrayList<>(spawns), (existing, added) -> {
            existing.addAll(added);
            return existing;
        });
    }

    public List<MobSpawn> drain(int chunkX, int chunkZ) {
        List<MobSpawn> spawns = pending.remove(key(chunkX, chunkZ));
        return spawns == null ? List.of() : spawns;
    }

    public int size() {
        return pending.size();
    }

    public void clear() {
        pending.clear();
    }
}
