package com.arkcronist.content.core.furniture;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 3.1: the ItemsAdder id read back from an item's components. */
class LegacyIdsTest {

    @Test
    void theIdIsReadFromTheCustomDataInEitherOrder() {
        assertEquals(Optional.of("dungeon_furniture:barrel"), LegacyIds.itemsAdderId(
                "[minecraft:custom_data={itemsadder:{id:\"barrel\",namespace:\"dungeon_furniture\"}},"
                        + "minecraft:custom_model_data={floats:[10004.0f]}]"));
        assertEquals(Optional.of("lk:cleric_helmet"), LegacyIds.itemsAdderId(
                "[minecraft:custom_data={\"itemsadder\":{'namespace':'lk','id':'cleric_helmet'}}]"));
    }

    @Test
    void anythingElseIsNotItemsAdders() {
        assertEquals(Optional.empty(), LegacyIds.itemsAdderId("[minecraft:custom_model_data={floats:[1.0f]}]"));
        assertEquals(Optional.empty(), LegacyIds.itemsAdderId("[minecraft:custom_data={itemsadder:{id:\"x\"}}]"));
        assertEquals(Optional.empty(), LegacyIds.itemsAdderId("[minecraft:custom_data={other:{id:\"x\",namespace:\"y\"}}]"));
        assertEquals(Optional.empty(), LegacyIds.itemsAdderId(null));
    }
}
