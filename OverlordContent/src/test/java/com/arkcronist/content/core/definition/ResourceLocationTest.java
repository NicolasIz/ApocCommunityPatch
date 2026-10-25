package com.arkcronist.content.core.definition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceLocationTest {

    @Test
    void bareLocationTakesTheCallersNamespace() {
        assertEquals(new ResourceLocation("demo", "item/ruby"), ResourceLocation.parse("item/ruby", "demo"));
        assertEquals(new ResourceLocation("minecraft", "item/generated"),
                ResourceLocation.parse("item/generated", ResourceLocation.MINECRAFT));
    }

    @Test
    void explicitNamespaceWins() {
        ResourceLocation location = ResourceLocation.parse("other:block/stone", "demo");
        assertEquals("other", location.namespace());
        assertEquals("block/stone", location.path());
        assertEquals("other:block/stone", location.toString());
    }

    @Test
    void assetPathFollowsTheVanillaLayout() {
        assertEquals("assets/demo/models/item/ruby.json",
                new ResourceLocation("demo", "item/ruby").assetPath("models", ".json"));
    }

    /** These paths become file paths while the pack is compiled, and the pack is served publicly. */
    @Test
    void refusesPathsThatClimbOutOfTheirFolder() {
        assertFalse(ResourceLocation.isValidPath("../../server.properties"));
        assertFalse(ResourceLocation.isValidPath("item/../../secret"));
        assertFalse(ResourceLocation.isValidPath("./item"));
        assertFalse(ResourceLocation.isValidPath("/etc/passwd"));
        assertFalse(ResourceLocation.isValidPath("item//ruby"));
        assertThrows(IllegalArgumentException.class, () -> ResourceLocation.parse("item/../../x", "demo"));
    }

    @Test
    void usesTheClientsCharacterRules() {
        assertTrue(ResourceLocation.isValidPath("item/ruby_sword-2.v1"));
        assertFalse(ResourceLocation.isValidPath("item/Ruby"));
        assertFalse(ResourceLocation.isValidPath("item/ruby sword"));
        assertFalse(ResourceLocation.isValidNamespace("Demo"));
        assertFalse(ResourceLocation.isValidNamespace("de/mo"));
        assertThrows(IllegalArgumentException.class, () -> ResourceLocation.parse("Demo:item", "x"));
    }
}
