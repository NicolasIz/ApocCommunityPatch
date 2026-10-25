package com.arkcronist.content.bukkit.hooks.mythicmobs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Drop lines of a MythicMobs pack written for ItemsAdder (DragonesEpicos), as MythicMobs hands them over. */
class NamespacedDropLineTest {

    @Test
    void theNamespaceMythicMobsAsksForAndTheLineGiveTheItem() {
        assertEquals("dragones_epicos:umbraxis_botas",
                NamespacedDropLine.itemId("dragones_epicos", "dragones_epicos:umbraxis_botas 1 0.05"));
        assertEquals("dragones_epicos:escama_tronagar",
                NamespacedDropLine.itemId("dragones_epicos", "  dragones_epicos:escama_tronagar 5-10"));
        assertEquals("dragones_epicos:umbraxis_yelmo",
                NamespacedDropLine.itemId("DRAGONES_EPICOS", "Dragones_Epicos:Umbraxis_Yelmo HEAD"));
        assertEquals("demo:ruby", NamespacedDropLine.itemId("demo", "demo:ruby{amount=2} 1"));
    }

    @Test
    void anythingElseIsNotThisPlugins() {
        assertNull(NamespacedDropLine.itemId("exp", "exp 18000"));
        assertNull(NamespacedDropLine.itemId("arkcontent", "arkcontent{item=demo:ruby} 1"));
        assertNull(NamespacedDropLine.itemId("other", "dragones_epicos:umbraxis_botas 1"));
        assertNull(NamespacedDropLine.itemId("demo", "demo: 1"));
        assertNull(NamespacedDropLine.itemId("demo", "demo:a:b 1"));
        assertNull(NamespacedDropLine.itemId("demo", null));
    }
}
