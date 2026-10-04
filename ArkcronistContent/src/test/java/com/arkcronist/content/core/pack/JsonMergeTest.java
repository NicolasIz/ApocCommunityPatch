package com.arkcronist.content.core.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonMergeTest {

    @Test
    void atlasSourcesAndFontProvidersAreJoined() {
        JsonMerge.Result atlas = merge("assets/minecraft/atlases/blocks.json",
                "{\"sources\":[{\"type\":\"minecraft:single\",\"resource\":\"demo:furniture/stone\"}]}",
                "{\"sources\":[{\"type\":\"directory\",\"source\":\"cosmetics\",\"prefix\":\"cosmetics/\"},"
                        + "{\"type\":\"minecraft:single\",\"resource\":\"demo:furniture/stone\"}]}");
        assertEquals(2, json(atlas).getAsJsonArray("sources").size(), "the same source once");
        assertEquals(List.of(), atlas.collisions());

        JsonMerge.Result font = merge("assets/minecraft/font/default.json",
                "{\"providers\":[{\"type\":\"bitmap\",\"file\":\"demo:emoji/ruby.png\",\"chars\":[\"\\ue000\"]}]}",
                "{\"providers\":[{\"type\":\"bitmap\",\"file\":\"hats:icons/hat.png\",\"chars\":[\"\\ue100\"]}]}");
        JsonArray providers = json(font).getAsJsonArray("providers");
        assertEquals(2, providers.size());
        assertEquals("demo:emoji/ruby.png", providers.get(0).getAsJsonObject().get("file").getAsString(),
                "ours first: on a character both draw, ours wins");
    }

    @Test
    void soundsAndLangKeepOursOnACollision() {
        JsonMerge.Result sounds = merge("assets/demo/sounds.json",
                "{\"chest.open\":{\"sounds\":[\"demo:chest/open\"]}}",
                "{\"chest.open\":{\"sounds\":[\"other:open\"]},\"hat.wear\":{\"sounds\":[\"other:wear\"]}}");
        JsonObject merged = json(sounds);
        assertEquals("demo:chest/open", merged.getAsJsonObject("chest.open").getAsJsonArray("sounds").get(0).getAsString());
        assertTrue(merged.has("hat.wear"));
        assertEquals(List.of("assets/demo/sounds.json: 'chest.open' is defined by both packs - keeping ours"),
                sounds.collisions());

        JsonMerge.Result lang = merge("assets/minecraft/lang/es_es.json", "{\"a\":\"uno\"}", "{\"a\":\"uno\",\"b\":\"dos\"}");
        assertEquals(2, json(lang).size());
        assertEquals(List.of(), lang.collisions(), "the same text twice is no collision");
    }

    @Test
    void legacyCustomModelDataOverridesAreJoinedSortedAndTheirCollisionsNamed() {
        JsonMerge.Result result = merge("assets/minecraft/models/item/paper.json",
                "{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\"item/paper\"},\"overrides\":["
                        + "{\"predicate\":{\"custom_model_data\":20},\"model\":\"demo:item/ruby\"},"
                        + "{\"predicate\":{\"custom_model_data\":5},\"model\":\"demo:item/sword\"}]}",
                "{\"parent\":\"item/generated\",\"overrides\":["
                        + "{\"predicate\":{\"custom_model_data\":10},\"model\":\"hats:crown\"},"
                        + "{\"predicate\":{\"custom_model_data\":20},\"model\":\"hats:cap\"}]}");
        JsonObject merged = json(result);
        assertEquals("item/paper", merged.getAsJsonObject("textures").get("layer0").getAsString(), "ours is the base");
        List<String> models = new ArrayList<>();
        merged.getAsJsonArray("overrides").forEach(o -> models.add(o.getAsJsonObject().get("model").getAsString()));
        assertEquals(List.of("demo:item/sword", "hats:crown", "demo:item/ruby"), models,
                "sorted by number, as the client reads them; 20 stays ours");
        assertEquals(1, result.collisions().size());
        assertTrue(result.collisions().get(0).contains("custom_model_data 20"), result.collisions().toString());
    }

    @Test
    void itemDefinitionsDispatchingOnCustomModelDataAreJoined() {
        String ours = "{\"model\":{\"type\":\"minecraft:range_dispatch\",\"property\":\"minecraft:custom_model_data\","
                + "\"entries\":[{\"threshold\":3,\"model\":{\"type\":\"minecraft:model\",\"model\":\"demo:item/a\"}}],"
                + "\"fallback\":{\"type\":\"minecraft:model\",\"model\":\"item/paper\"}}}";
        String theirs = "{\"model\":{\"type\":\"minecraft:range_dispatch\",\"property\":\"minecraft:custom_model_data\","
                + "\"entries\":[{\"threshold\":1,\"model\":{\"type\":\"minecraft:model\",\"model\":\"hats:b\"}},"
                + "{\"threshold\":3,\"model\":{\"type\":\"minecraft:model\",\"model\":\"hats:c\"}}]}}";
        JsonMerge.Result result = merge("assets/minecraft/items/paper.json", ours, theirs);
        JsonObject model = json(result).getAsJsonObject("model");
        List<Double> thresholds = new ArrayList<>();
        model.getAsJsonArray("entries").forEach(e -> thresholds.add(e.getAsJsonObject().get("threshold").getAsDouble()));
        assertEquals(List.of(1.0, 3.0), thresholds);
        assertEquals("demo:item/a", model.getAsJsonArray("entries").get(1).getAsJsonObject()
                .getAsJsonObject("model").get("model").getAsString());
        assertEquals("item/paper", model.getAsJsonObject("fallback").get("model").getAsString());
        assertEquals(1, result.collisions().size());
    }

    @Test
    void anythingElseIsNotMerged() {
        assertFalse(JsonMerge.mergeable("assets/demo/models/furniture/chest.json"));
        assertNull(merge("assets/demo/textures/item/a.png.mcmeta", "{}", "{}").merged());
        assertNull(merge("assets/minecraft/items/paper.json",
                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"item/paper\"}}",
                "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"hats:x\"}}").merged(), "nothing to join: first kept");
        assertNull(merge("assets/minecraft/font/default.json", "not json", "{\"providers\":[]}").merged());
    }

    private static JsonMerge.Result merge(String path, String ours, String theirs) {
        return JsonMerge.merge(path, ours.getBytes(StandardCharsets.UTF_8), theirs.getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject json(JsonMerge.Result result) {
        return JsonParser.parseString(new String(result.merged(), StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
