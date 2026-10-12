package com.arkcronist.content.core.pack;

import com.arkcronist.content.core.definition.Equipment;
import com.arkcronist.content.core.definition.ItemBehaviour;
import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.ModelSource;
import com.arkcronist.content.core.definition.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WornAssetsTest {

    private static final Path ROOT = Path.of("contents", "darksteel");

    /** A piece imported from a generated pack: an item model, and nothing about how it is worn. */
    private static ItemDefinition piece(String namespace, String id, String material) {
        return new ItemDefinition(namespace, id, material, id, List.of(),
                new ModelSource.Definition(ROOT, new ResourceLocation(namespace, id)), ItemBehaviour.DEFAULT, null, ROOT);
    }

    private static List<ResourceLocation> assets(String... ids) {
        return java.util.Arrays.stream(ids).map(id -> ResourceLocation.parse(id, "minecraft")).toList();
    }

    private static final Map<String, byte[]> NO_FILES = Map.of();

    @Test
    void mythicArmorsPiecesAreWornByTheImportedSetOfTheSameName() {
        // MythicArmor's darksteel_knight_armor.bbmodel, and the ItemsAdder pack's icons of the set.
        List<ItemDefinition> items = List.of(
                piece("darksteel", "darksteel_knight_chestplate_item", "NETHERITE_CHESTPLATE"),
                piece("darksteel", "darksteel_knight_leggings_item", "NETHERITE_LEGGINGS"),
                piece("darksteel", "darksteel_knight_boots_item", "NETHERITE_BOOTS"));
        List<ResourceLocation> assets = assets(
                "mythicarmor:darksteel_knight_armor_chest_piece", "mythicarmor:darksteel_knight_armor_chest_piece_far",
                "mythicarmor:darksteel_knight_armor_legs_piece", "mythicarmor:darksteel_knight_armor_legs_piece_far",
                "mythicarmor:darksteel_knight_armor_feet_piece", "mythicarmor:darksteel_knight_armor_feet_piece_far",
                "mythicarmor:cleric_armor_chest_piece", "mythicarmor:mythical_dragon_armor_chest_piece");

        WornAssets.Result result = WornAssets.link(items, assets, NO_FILES::get);

        assertEquals(3, result.equipment().size());
        Equipment chest = result.equipment().get("darksteel:darksteel_knight_chestplate_item");
        assertEquals(Equipment.Slot.CHEST, chest.slot());
        assertEquals(new ResourceLocation("mythicarmor", "darksteel_knight_armor_chest_piece"), chest.asset());
        assertEquals(new ResourceLocation("mythicarmor", "darksteel_knight_armor_legs_piece"),
                result.equipment().get("darksteel:darksteel_knight_leggings_item").asset());
        assertEquals(new ResourceLocation("mythicarmor", "darksteel_knight_armor_feet_piece"),
                result.equipment().get("darksteel:darksteel_knight_boots_item").asset());
        assertEquals(Equipment.Slot.FEET, result.equipment().get("darksteel:darksteel_knight_boots_item").slot());
        assertTrue(result.linked().contains(
                "darksteel:darksteel_knight_chestplate_item -> mythicarmor:darksteel_knight_armor_chest_piece"));
        assertTrue(result.ambiguous().isEmpty());
    }

    @Test
    void aTierIsPartOfTheName() {
        List<ItemDefinition> items = List.of(
                piece("lk", "cleric_chestplate_t2", "IRON_CHESTPLATE"),
                piece("lk", "cleric_chestplate", "IRON_CHESTPLATE"));
        List<ResourceLocation> assets = assets("mythicarmor:cleric_armor_chest_piece",
                "mythicarmor:cleric_armor_t2_chest_piece", "mythicarmor:cleric_armor_t3_chest_piece");

        WornAssets.Result result = WornAssets.link(items, assets, NO_FILES::get);

        assertEquals("cleric_armor_t2_chest_piece", result.equipment().get("lk:cleric_chestplate_t2").asset().path());
        assertEquals("cleric_armor_chest_piece", result.equipment().get("lk:cleric_chestplate").asset().path());
    }

    @Test
    void anAssetNamedForAnotherSlotIsNeverWorn() {
        List<ItemDefinition> items = List.of(piece("darksteel", "darksteel_knight_helmet", "NETHERITE_HELMET"));
        List<ResourceLocation> assets = assets("mythicarmor:darksteel_knight_armor_chest_piece",
                "mythicarmor:darksteel_knight_armor_legs_piece");

        assertTrue(WornAssets.link(items, assets, NO_FILES::get).equipment().isEmpty());
    }

    @Test
    void aWholeSetsAssetFitsEverySlotButOneNamedForTheSlotComesFirst() {
        List<ItemDefinition> items = List.of(
                piece("epic", "astralion_peto", "NETHERITE_CHESTPLATE"),
                piece("epic", "astralion_casco", "NETHERITE_HELMET"));
        List<ResourceLocation> assets = assets("dragones_epicos:astralion_armadura", "other:astralion_chest");

        WornAssets.Result result = WornAssets.link(items, assets, NO_FILES::get);

        assertEquals("other:astralion_chest", result.equipment().get("epic:astralion_peto").asset().toString());
        assertEquals("dragones_epicos:astralion_armadura",
                result.equipment().get("epic:astralion_casco").asset().toString());
    }

    @Test
    void severalFittingAreSettledByTheItemsNamespaceOrLeftAlone() {
        List<ItemDefinition> items = List.of(
                piece("knights", "royal_chestplate", "IRON_CHESTPLATE"),
                piece("other", "royal_chestplate", "IRON_CHESTPLATE"));
        List<ResourceLocation> assets = assets("knights:royal_armor", "mythicarmor:royal_armor");

        WornAssets.Result result = WornAssets.link(items, assets, NO_FILES::get);

        assertEquals("knights:royal_armor", result.equipment().get("knights:royal_chestplate").asset().toString());
        assertNull(result.equipment().get("other:royal_chestplate"));
        assertEquals(1, result.ambiguous().size());
        assertTrue(result.ambiguous().getFirst().startsWith("other:royal_chestplate"));
    }

    @Test
    void piecesThatSayHowTheyAreWornAndLeatherAreLeftAlone() {
        ItemDefinition explicit = piece("darksteel", "darksteel_knight_chestplate_item", "NETHERITE_CHESTPLATE")
                .withEquipment(new Equipment(Equipment.Slot.CHEST, new ResourceLocation("minecraft", "netherite"),
                        List.of(), null, ROOT));
        ItemDefinition leather = piece("darksteel", "darksteel_knight_leggings_item", "LEATHER_LEGGINGS");
        ItemDefinition sword = piece("darksteel", "darksteel_knight_sword", "NETHERITE_SWORD");
        List<ResourceLocation> assets = assets("mythicarmor:darksteel_knight_armor_chest_piece",
                "mythicarmor:darksteel_knight_armor_legs_piece", "mythicarmor:darksteel_knight_armor");

        assertTrue(WornAssets.link(List.of(explicit, leather, sword), assets, NO_FILES::get).equipment().isEmpty());
    }

    @Test
    void aHelmetWithA3dModelForTheHeadIsWornAsThatModel() {
        Map<String, byte[]> pack = new HashMap<>();
        pack.put("assets/darksteel/items/darksteel_knight_helmet.json", bytes(
                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"darksteel:item/darksteel_knight_helmet\"}}"));
        pack.put("assets/darksteel/models/item/darksteel_knight_helmet.json", bytes(
                "{\"textures\":{\"0\":\"ia:184\"},\"elements\":[{\"from\":[3.5,0,3.5],\"to\":[12.5,9,12.5],\"faces\":{}}],"
                        + "\"display\":{\"head\":{\"translation\":[0,6.75,-0.25],\"scale\":[1.7,1.7,1.7]}}}"));
        // A flat icon: no elements of its own nor in a parent the pack has.
        pack.put("assets/darksteel/items/darksteel_flat_helmet.json", bytes(
                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"darksteel:item/darksteel_flat_helmet\"}}"));
        pack.put("assets/darksteel/models/item/darksteel_flat_helmet.json", bytes(
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"ia:183\"}}"));
        // Its elements in a parent, its head display in the child.
        pack.put("assets/darksteel/items/darksteel_crown.json", bytes(
                "{\"model\":{\"type\":\"minecraft:select\",\"property\":\"minecraft:display_context\",\"cases\":[],"
                        + "\"fallback\":{\"type\":\"minecraft:model\",\"model\":\"darksteel:item/crown\"}}}"));
        pack.put("assets/darksteel/models/item/crown.json", bytes(
                "{\"parent\":\"darksteel:item/crown_base\",\"display\":{\"head\":{}}}"));
        pack.put("assets/darksteel/models/item/crown_base.json", bytes(
                "{\"elements\":[{\"from\":[0,0,0],\"to\":[16,4,16],\"faces\":{}}]}"));

        List<ItemDefinition> items = List.of(
                piece("darksteel", "darksteel_knight_helmet", "NETHERITE_HELMET"),
                piece("darksteel", "darksteel_flat_helmet", "IRON_HELMET"),
                piece("darksteel", "darksteel_crown", "GOLDEN_HELMET"),
                piece("darksteel", "darksteel_knight_chestplate_item", "NETHERITE_CHESTPLATE"));

        WornAssets.Result result = WornAssets.link(items, assets("mythicarmor:darksteel_knight_armor_chest_piece"),
                pack::get);

        Equipment helmet = result.equipment().get("darksteel:darksteel_knight_helmet");
        assertEquals(Equipment.Slot.HEAD, helmet.slot());
        assertNull(helmet.asset(), "an asset would be drawn instead of the model");
        assertNull(helmet.worn());
        assertFalse(result.equipment().containsKey("darksteel:darksteel_flat_helmet"));
        assertEquals(Equipment.Slot.HEAD, result.equipment().get("darksteel:darksteel_crown").slot());
        assertEquals(List.of("darksteel:darksteel_knight_helmet", "darksteel:darksteel_crown"), result.onHead());
        assertEquals(1, result.linked().size());
    }

    @Test
    void namesAreReducedToWhatTellsTheSetApart() {
        java.util.Set<Equipment.Slot> slots = java.util.EnumSet.noneOf(Equipment.Slot.class);
        assertEquals("darksteel_knight", WornAssets.key("darksteel_knight_armor_chest_piece", slots));
        assertEquals(java.util.Set.of(Equipment.Slot.CHEST), slots);
        assertEquals("darksteel_knight", WornAssets.key("darksteel_knight_chestplate_item", null));
        assertEquals("darksteel_knight_far", WornAssets.key("darksteel_knight_armor_chest_piece_far", null));
        assertNull(WornAssets.key("armor_piece", null));
        assertEquals(Equipment.Slot.LEGS, WornAssets.slotOf("chainmail_leggings"));
        assertNull(WornAssets.slotOf("ELYTRA"));
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
