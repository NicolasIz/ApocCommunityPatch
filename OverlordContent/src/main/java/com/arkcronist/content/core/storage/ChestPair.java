package com.arkcronist.content.core.storage;

import java.util.UUID;

/**
 * Two chests of furniture joined into a double chest.
 *
 * @param x the first half: vanilla's RIGHT half, rows 1-3 of the double inventory
 * @param px its partner: rows 4-6
 */
public record ChestPair(UUID world, int x, int y, int z, int px, int py, int pz, String furnitureId) {

    /** Whether one of its halves is at the position. */
    public boolean has(int x, int y, int z) {
        return (this.x == x && this.y == y && this.z == z) || (px == x && py == y && pz == z);
    }

    /** Whether the position is its first half. */
    public boolean isFirst(int x, int y, int z) {
        return this.x == x && this.y == y && this.z == z;
    }

    /** The other half from the one at the position: {x, y, z}. */
    public int[] other(int x, int y, int z) {
        return isFirst(x, y, z) ? new int[] {px, py, pz} : new int[] {this.x, this.y, this.z};
    }

    /** One half of a pair, as the position index holds it - so a pair is found from either half. */
    public record Half(UUID world, int x, int y, int z, ChestPair pair) implements PositionIndex.Positioned {

        @Override
        public long key() {
            return BlockKey.pack(x, y, z);
        }
    }
}
