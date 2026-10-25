package com.arkcronist.content.core.storage;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 4.0: a vehicle in the world, as kept in {@code vehicles}: where it was last left and which way it
 * faced. Its entities are never saved with the chunk; this row is what puts it back.
 *
 * @param id     the vehicle's own id
 * @param itemId the item it is, {@code namespace:id}
 * @param x      the middle of its footprint
 * @param y      the bottom of its footprint
 * @param owner  who placed it; null for no one in particular
 */
public record StoredVehicle(UUID id, String itemId, UUID world, double x, double y, double z, float yaw,
                            @Nullable UUID owner) {

    /** The chunk it is in, as {@code BlockKey} packs a chunk: x and z. */
    public long chunk() {
        return BlockKey.pack(Math.floorDiv((int) Math.floor(x), 16), 0, Math.floorDiv((int) Math.floor(z), 16));
    }
}
