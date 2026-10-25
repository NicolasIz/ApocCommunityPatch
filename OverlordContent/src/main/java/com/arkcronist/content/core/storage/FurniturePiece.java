package com.arkcronist.content.core.storage;

import java.util.UUID;

/**
 * One piece of furniture as a whole, kept beside the rows of the blocks it stands on: where its
 * anchor is, what it is and which way it faces.
 *
 * <p>The rows say a block is taken; only the piece's display knew which way it faced. With this
 * kept too, a piece whose display was killed - {@code /kill}, an entity cleaner, a rollback of
 * entities but not blocks - can be drawn again exactly as it stood, instead of leaving invisible
 * barriers behind (schema 8).</p>
 *
 * @param yaw the display's yaw in degrees, as it was placed
 */
public record FurniturePiece(UUID world, int x, int y, int z, String furnitureId, float yaw) {

    public long key() {
        return BlockKey.pack(x, y, z);
    }
}
