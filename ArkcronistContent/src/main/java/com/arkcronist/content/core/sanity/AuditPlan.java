package com.arkcronist.content.core.sanity;

import com.arkcronist.content.core.storage.PlacedContent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What one audit looks at: the stored rows of a world, grouped by the chunk they stand in, so the
 * server thread can check a loaded chunk's rows together and pass over an unloaded chunk's in one
 * step.
 */
public final class AuditPlan {

    private AuditPlan() {
    }

    /** {@code chunkX, chunkZ} packed the way Paper's {@code Chunk#getChunkKey} packs them. */
    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkZ << 32) | (chunkX & 0xFFFFFFFFL);
    }

    /** The key of the chunk a block position is in. */
    public static long chunkOf(int x, int z) {
        return chunkKey(x >> 4, z >> 4);
    }

    /** Rows grouped by chunk. {@code rows} may be a live view; it is copied once. */
    public static Map<Long, List<PlacedContent>> byChunk(Collection<PlacedContent> rows) {
        Map<Long, List<PlacedContent>> chunks = new HashMap<>();
        for (PlacedContent row : List.copyOf(rows)) {
            chunks.computeIfAbsent(chunkOf(row.x(), row.z()), ignored -> new ArrayList<>()).add(row);
        }
        return chunks;
    }
}
