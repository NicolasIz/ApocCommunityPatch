package com.arkcronist.content.core.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelDataDispatchTest {

    @Test
    void plainMaterialsFallBackToTheirVanillaModel() {
        assertEquals("item/paper", ModelDataDispatch.vanillaModel("PAPER"));
        assertEquals("item/diamond_sword", ModelDataDispatch.vanillaModel("diamond_sword"));
        // A block item whose inventory model is not the block's own.
        assertEquals("block/oak_fence_inventory", ModelDataDispatch.vanillaModel("OAK_FENCE"));
        assertTrue(ModelDataDispatch.dispatchable("STICK"));
    }

    @Test
    void materialsWithTheirOwnDefinitionAreLeftAlone() {
        // Drawing back, trims, tints, a live clock face: one model would lose them.
        for (String material : List.of("BOW", "NETHERITE_HELMET", "LEATHER_HORSE_ARMOR", "CLOCK", "RED_BANNER")) {
            assertFalse(ModelDataDispatch.dispatchable(material), material);
            assertNull(ModelDataDispatch.vanillaModel(material), material);
        }
        assertThrows(IllegalArgumentException.class, () -> ModelDataDispatch.definition("BOW", new TreeMap<>()));
    }

    @Test
    void exactlyOurNumbersDrawOurItems() {
        SortedMap<Integer, JsonObject> models = new TreeMap<>();
        models.put(10000, model("demo:item/ruby"));
        models.put(10001, model("demo:item/sapphire"));
        models.put(10005, model("demo:item/coin"));

        JsonObject dispatch = ModelDataDispatch.definition("PAPER", models).getAsJsonObject("model");
        assertEquals("minecraft:range_dispatch", dispatch.get("type").getAsString());
        assertEquals("minecraft:custom_model_data", dispatch.get("property").getAsString());
        assertEquals(0, dispatch.get("index").getAsInt());
        assertEquals("minecraft:item/paper", dispatch.getAsJsonObject("fallback").get("model").getAsString());

        // 10002 and 10006 go back to paper: a stack numbered 10003 is not drawn as the sapphire.
        List<String> entries = new ArrayList<>();
        JsonArray array = dispatch.getAsJsonArray("entries");
        array.forEach(entry -> entries.add(entry.getAsJsonObject().get("threshold").getAsInt() + "="
                + entry.getAsJsonObject().getAsJsonObject("model").get("model").getAsString()));
        assertEquals(List.of("10000=demo:item/ruby", "10001=demo:item/sapphire", "10002=minecraft:item/paper",
                "10005=demo:item/coin", "10006=minecraft:item/paper"), entries);
    }

    private static JsonObject model(String id) {
        JsonObject model = new JsonObject();
        model.addProperty("type", "minecraft:model");
        model.addProperty("model", id);
        return model;
    }
}
