package com.arkcronist.gen.core.structure;

/** A spawner block that should be configured with a specific entity type once the chunk is live. */
public record SpawnerMarker(int x, int y, int z, String entityType) {
}
