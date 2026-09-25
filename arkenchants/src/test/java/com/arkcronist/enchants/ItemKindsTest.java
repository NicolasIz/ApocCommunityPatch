package com.arkcronist.enchants;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.arkcronist.enchants.model.ItemKinds;
import org.junit.jupiter.api.Test;

class ItemKindsTest {

    @Test
    void groupsOfItems() {
        assertTrue(ItemKinds.matches("ALL_SWORD", "NETHERITE_SWORD", false));
        assertTrue(ItemKinds.matches("ALL_AXE", "DIAMOND_AXE", false));
        assertFalse(ItemKinds.matches("ALL_AXE", "DIAMOND_PICKAXE", false));
        assertTrue(ItemKinds.matches("ALL_ARMOR", "NETHERITE_BOOTS", false));
        assertTrue(ItemKinds.matches("ALL_HELMET", "TURTLE_HELMET", false));
        assertTrue(ItemKinds.matches("ALL_SPADE", "IRON_SHOVEL", false));
        assertTrue(ItemKinds.matches("DIAMOND_ARMOR", "DIAMOND_CHESTPLATE", false));
        assertTrue(ItemKinds.matches("CHAIN_ARMOR", "CHAINMAIL_HELMET", false));
        assertTrue(ItemKinds.matches("GOLD_ARMOR", "GOLDEN_LEGGINGS", false));
        assertFalse(ItemKinds.matches("DIAMOND_ARMOR", "IRON_CHESTPLATE", false));
        assertTrue(ItemKinds.matches("BOW", "BOW", false));
        assertTrue(ItemKinds.matches("ALL_EDIBLE", "APPLE", true));
        assertFalse(ItemKinds.matches("ALL_TOOLS", "BOW", false));
    }
}
