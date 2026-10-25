package com.arkcronist.content.core.advancement;

import com.arkcronist.content.core.definition.Equipment;
import com.arkcronist.content.core.definition.ItemBehaviour;
import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The trigger of a wear advancement: the Blood Ruby set, worn for real or not. */
class WornSetTest {

    private static final ResourceLocation RUBY_ARMOR = new ResourceLocation("demo", "ruby_armor");
    private static final List<ItemDefinition> SET = List.of(
            piece("ruby_helmet", "NETHERITE_HELMET", Equipment.Slot.HEAD, null),
            piece("ruby_chestplate", "NETHERITE_CHESTPLATE", Equipment.Slot.CHEST, RUBY_ARMOR),
            piece("ruby_leggings", "NETHERITE_LEGGINGS", Equipment.Slot.LEGS, RUBY_ARMOR),
            piece("ruby_boots", "NETHERITE_BOOTS", Equipment.Slot.FEET, RUBY_ARMOR));

    @Test
    void theWholeSetCompletesIt() {
        assertTrue(WornSet.worn(SET, wearing()::get));
    }

    @Test
    void threePiecesDoNot() {
        Map<Equipment.Slot, WornSet.Worn> slots = wearing();
        slots.remove(Equipment.Slot.FEET);
        assertFalse(WornSet.worn(SET, slots::get));
    }

    @Test
    void aCounterfeitWithTheRubyLookDoesNot() {
        Map<Equipment.Slot, WornSet.Worn> slots = wearing();
        // Netherite boots given the ruby model and asset by hand: everything but the plugin's id.
        slots.put(Equipment.Slot.FEET, new WornSet.Worn(null, "NETHERITE_BOOTS", Equipment.Slot.FEET, RUBY_ARMOR));
        assertFalse(WornSet.worn(SET, slots::get));
    }

    @Test
    void aPieceWhoseComponentsWereChangedDoesNot() {
        Map<Equipment.Slot, WornSet.Worn> wrongMaterial = wearing();
        wrongMaterial.put(Equipment.Slot.LEGS, new WornSet.Worn("demo:ruby_leggings", "DIAMOND_LEGGINGS",
                Equipment.Slot.LEGS, RUBY_ARMOR));
        assertFalse(WornSet.worn(SET, wrongMaterial::get), "the stats come from netherite");

        Map<Equipment.Slot, WornSet.Worn> wrongAsset = wearing();
        wrongAsset.put(Equipment.Slot.CHEST, new WornSet.Worn("demo:ruby_chestplate", "NETHERITE_CHESTPLATE",
                Equipment.Slot.CHEST, new ResourceLocation("minecraft", "netherite")));
        assertFalse(WornSet.worn(SET, wrongAsset::get), "drawn as plain netherite");

        Map<Equipment.Slot, WornSet.Worn> noEquippable = wearing();
        noEquippable.put(Equipment.Slot.HEAD, new WornSet.Worn("demo:ruby_helmet", "NETHERITE_HELMET", null, null));
        assertFalse(WornSet.worn(SET, noEquippable::get));
    }

    @Test
    void theHelmetCountsWithoutAnAssetBecauseItIsWornAsAModel() {
        Map<Equipment.Slot, WornSet.Worn> slots = wearing();
        slots.put(Equipment.Slot.HEAD, new WornSet.Worn("demo:ruby_helmet", "NETHERITE_HELMET", Equipment.Slot.HEAD,
                RUBY_ARMOR));
        assertFalse(WornSet.worn(SET, slots::get), "an asset on the helmet would hide its horns");
        assertTrue(WornSet.worn(SET, wearing()::get));
    }

    private static Map<Equipment.Slot, WornSet.Worn> wearing() {
        Map<Equipment.Slot, WornSet.Worn> slots = new EnumMap<>(Equipment.Slot.class);
        for (ItemDefinition piece : SET) {
            Equipment equipment = piece.equipment();
            slots.put(equipment.slot(), new WornSet.Worn(piece.fullId(), piece.material(), equipment.slot(),
                    equipment.asset()));
        }
        return slots;
    }

    private static ItemDefinition piece(String id, String material, Equipment.Slot slot, ResourceLocation asset) {
        Path root = Path.of("contents/demo");
        return new ItemDefinition("demo", id, material, null, List.of(), null, ItemBehaviour.DEFAULT, null,
                root.resolve("armor.yml")).withEquipment(new Equipment(slot, asset, List.of(), null, root));
    }
}
