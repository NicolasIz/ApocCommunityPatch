package com.arkcronist.content.core.storage;

import java.util.UUID;

/**
 * The contents of one storage furniture, as the database keeps them.
 *
 * <p>{@code contents} is opaque here: the server serialises the item stacks - with their data
 * version, so a later Minecraft upgrades them on reading - and only the server reads them back.
 * Treat the array as read-only once handed over; the record shares it.</p>
 *
 * @param furnitureId the furniture standing there when it was saved, for an admin reading the table
 * @param slots       the inventory's size when it was saved
 */
public record StoredInventory(UUID world, int x, int y, int z, String furnitureId, int slots, byte[] contents) {
}
