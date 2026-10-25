package com.arkcronist.content.core.storage;

import java.util.UUID;

/**
 * A cosmetic a player wears, as kept in {@code player_cosmetics}: the stack taken from their hand,
 * serialized, given back as it was when they take it off.
 *
 * @param slot  where it is worn - {@code back}
 * @param item  the stack's bytes, as Paper serializes an item
 */
public record StoredCosmetic(UUID player, String slot, String itemId, byte[] item, long equippedAtMillis) {
}
