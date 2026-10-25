package com.arkcronist.content.core.advancement;

import com.arkcronist.content.core.definition.Equipment;
import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Whether a set of armour is worn - the decision behind a {@code wear} advancement, apart from the
 * server so it can be tested on its own.
 *
 * <p>Each piece has to be in its slot and be the real thing: the plugin's item (its id, which a
 * renamed or re-modelled vanilla piece does not carry), on the material the item is made from, with
 * the {@code equippable} the plugin gives it - the right slot and the right armour asset. A stack
 * that only looks the part does not count.</p>
 */
public final class WornSet {

    /**
     * What is in one armour slot, as far as the check cares.
     *
     * @param itemId    the plugin's id on the stack, or null for any other item
     * @param material  the stack's material, a Bukkit name
     * @param slot      the slot its {@code equippable} component names, or null without one
     * @param asset     the armour asset that component names, or null for none
     */
    public record Worn(@Nullable String itemId, String material, @Nullable Equipment.Slot slot,
                       @Nullable ResourceLocation asset) {
    }

    private WornSet() {
    }

    /**
     * @param pieces each piece of the set, as defined
     * @param slots  what each slot holds; null for an empty slot
     */
    public static boolean worn(List<ItemDefinition> pieces, Function<Equipment.Slot, Worn> slots) {
        if (pieces.isEmpty()) {
            return false;
        }
        for (ItemDefinition piece : pieces) {
            Equipment equipment = piece.equipment();
            if (equipment == null) {
                return false;
            }
            Worn worn = slots.apply(equipment.slot());
            if (worn == null
                    || !piece.fullId().equals(worn.itemId())
                    || !piece.material().equals(worn.material())
                    || worn.slot() != equipment.slot()
                    || !Objects.equals(worn.asset(), equipment.asset())) {
                return false;
            }
        }
        return true;
    }
}
