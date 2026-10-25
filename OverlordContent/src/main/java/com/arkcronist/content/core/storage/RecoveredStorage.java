package com.arkcronist.content.core.storage;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * What a storage furniture held when the integrity check took the furniture out - its blocks were
 * gone, by a rollback, {@code /fill} or a world editor - kept for an admin to hand back rather than
 * dropped in a chunk nobody may be in, or lost.
 *
 * @param id          the row's number, by which it is claimed
 * @param reason      why the furniture was taken out, for the admin reading the list
 * @param claimedBy   who took the contents, once taken; null while they wait
 */
public record RecoveredStorage(long id, UUID world, int x, int y, int z, String furnitureId, int slots,
                               byte[] contents, String reason, long recoveredAtMillis, @Nullable String claimedBy,
                               long claimedAtMillis) {

    public boolean claimed() {
        return claimedBy != null;
    }

    public StoredInventory inventory() {
        return new StoredInventory(world, x, y, z, furnitureId, slots, contents);
    }
}
