package com.arkcronist.content.bukkit.cosmetic;

import com.arkcronist.content.core.cosmetic.BackPhysics;
import com.arkcronist.content.core.cosmetic.BackPose;
import com.arkcronist.content.core.cosmetic.CosmeticDefinition;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One cosmetic a player has on: what it is, its spring state, and the latest that is known of its
 * wearer.
 *
 * <p>Shared by two threads: the main thread writes {@link #sample} and {@link #hidden} as the wearer
 * moves; {@code ArkContent-Physics} owns {@link #physics} and reads those two. Nothing else is
 * changed after it is made.</p>
 */
public final class Worn {

    final UUID player;
    final int wearerEntityId;
    final String itemId;
    volatile CosmeticDefinition definition;
    final ItemStack item;
    final BackPhysics physics;
    /** The display's entity id: from the server's own counter, so it never clashes with a real entity. */
    final int displayId;
    final UUID displayUuid = UUID.randomUUID();
    volatile @Nullable BackPhysics.Sample sample;
    volatile boolean hidden;
    volatile @Nullable BackPose.Transform latest;

    Worn(UUID player, int wearerEntityId, String itemId, CosmeticDefinition definition, ItemStack item, int displayId) {
        this.player = player;
        this.wearerEntityId = wearerEntityId;
        this.itemId = itemId;
        this.definition = definition;
        this.item = item;
        this.physics = new BackPhysics(definition.physics());
        this.displayId = displayId;
    }

    public UUID player() {
        return player;
    }

    public int wearerEntityId() {
        return wearerEntityId;
    }

    public int displayId() {
        return displayId;
    }

    public UUID displayUuid() {
        return displayUuid;
    }

    public ItemStack item() {
        return item;
    }

    public String itemId() {
        return itemId;
    }

    public @Nullable BackPose.Transform latest() {
        return latest;
    }
}
