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
    void aStepBackToTheFallbackGivesWayToARealEntryAndTheirSettingsStay() {
        // Ours: an item at 10, and paper again from 11. Theirs: a real item at 11, oversized in the GUI.
        String ours = "{\"model\":{\"type\":\"minecraft:range_dispatch\",\"property\":\"minecraft:custom_model_data\","
                + "\"entries\":[{\"threshold\":10,\"model\":{\"type\":\"minecraft:model\",\"model\":\"demo:item/a\"}},"
                + "{\"threshold\":11,\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/paper\"}}],"
                + "\"fallback\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/paper\"}}}";
        String theirs = "{\"oversized_in_gui\":true,\"model\":{\"type\":\"minecraft:range_dispatch\","
                + "\"property\":\"minecraft:custom_model_data\",\"entries\":[{\"threshold\":11,\"model\":"
                + "{\"type\":\"model\",\"model\":\"ia:b\",\"tints\":[{\"type\":\"dye\",\"default\":-6265536}]}}],"
                + "\"fallback\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/paper\"}}}";
        JsonMerge.Result result = merge("assets/minecraft/items/paper.json", ours, theirs);

        assertEquals(List.of(), result.collisions());
        JsonObject merged = json(result);
        assertTrue(merged.get("oversized_in_gui").getAsBoolean());
        JsonArray entries = merged.getAsJsonObject("model").getAsJsonArray("entries");
        assertEquals(2, entries.size());
        assertEquals(JsonParser.parseString(theirs).getAsJsonObject().getAsJsonObject("model")
                .getAsJsonArray("entries").get(0), entries.get(1), "theirs, tints and all");
    }

    @Test
    void noteBlockStatesAreSharedStateByStateWithAPackThatNamesThemPartly() {
        // Ours: every state, one of them a block. Theirs, as ItemsAdder writes it: harp left vanilla, two blocks.
        JsonObject ours = new JsonObject();
        JsonObject variants = new JsonObject();
        for (String instrument : List.of("harp", "basedrum")) {
            for (int note = 0; note < 2; note++) {
                for (boolean powered : new boolean[]{false, true}) {
                    JsonObject variant = new JsonObject();
                    variant.addProperty("model", instrument.equals("harp") && note == 0 && powered
                            ? "demo:block/ruby_ore" : "minecraft:block/note_block");
                    variants.add("instrument=" + instrument + ",note=" + note + ",powered=" + powered, variant);
                }
            }
        }
        ours.add("variants", variants);
        String theirs = "{\"variants\":{\"instrument=harp\":{\"model\":\"block/original/note_block\"},"
                + "\"instrument=basedrum,note=0,powered=false\":{\"model\":\"ia:brick\"},"
                + "\"instrument=basedrum,note=1,powered=false\":{\"model\":\"ia:stone\"},"
                + "\"instrument=harp,note=1,powered=true\":{\"model\":\"ia:clash\"}}}";
        String path = "assets/minecraft/blockstates/note_block.json";

        JsonMerge.Result result = merge(path, ours.toString(), theirs);
        JsonObject merged = json(result).getAsJsonObject("variants");

        assertEquals(8, merged.size());
        assertEquals("demo:block/ruby_ore", model(merged, "instrument=harp,note=0,powered=true"), "our block stays");
        assertEquals("ia:brick", model(merged, "instrument=basedrum,note=0,powered=false"), "theirs over vanilla");
        assertEquals("ia:stone", model(merged, "instrument=basedrum,note=1,powered=false"));
        assertEquals("ia:clash", model(merged, "instrument=harp,note=1,powered=true"));
        assertEquals("minecraft:block/note_block", model(merged, "instrument=basedrum,note=0,powered=true"));
        assertEquals(List.of(), result.collisions());

        // Two blocks on one state: ours, or theirs inside their own overlay.
        String clash = "{\"variants\":{\"instrument=harp,note=0,powered=true\":{\"model\":\"ia:other\"}}}";
        assertEquals("demo:block/ruby_ore", model(json(merge(path, ours.toString(), clash)).getAsJsonObject("variants"),
                "instrument=harp,note=0,powered=true"));
        JsonMerge.Result intoTheirs = JsonMerge.mergeIntoTheirs(path, ours.toString().getBytes(StandardCharsets.UTF_8),
                clash.getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of(path + ": 1 state(s), e.g. instrument=harp,note=0,powered=true, are a block in both packs"
                + " - keeping the pack's own; give one of the two blocks another state"), intoTheirs.collisions());
        JsonObject inOverlay = json(intoTheirs).getAsJsonObject("variants");
        assertEquals(8, inOverlay.size(), "every state of ours is still drawn");
        assertEquals("ia:other", model(inOverlay, "instrument=harp,note=0,powered=true"));
    }

    @Test
    void aPlainLookForAnItemBecomesWhatOurNumbersFallBackTo() {
        String ours = "{\"model\":{\"type\":\"minecraft:range_dispatch\",\"property\":\"minecraft:custom_model_data\","
                + "\"entries\":[{\"threshold\":7,\"model\":{\"type\":\"minecraft:model\",\"model\":\"demo:item/table\"}},"
                + "{\"threshold\":8,\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/barrier\"}}],"
                + "\"fallback\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/barrier\"}}}";
        String theirs = "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"shop:item/btn_1\"},\"mcmodels\":\"f4\"}";

        JsonObject merged = json(merge("assets/minecraft/items/barrier.json", ours, theirs));

        JsonObject dispatch = merged.getAsJsonObject("model");
        assertEquals("demo:item/table", dispatch.getAsJsonArray("entries").get(0).getAsJsonObject()
                .getAsJsonObject("model").get("model").getAsString());
        assertEquals("shop:item/btn_1", dispatch.getAsJsonArray("entries").get(1).getAsJsonObject()
                .getAsJsonObject("model").get("model").getAsString(), "the step back is to their look");
        assertEquals("shop:item/btn_1", dispatch.getAsJsonObject("fallback").get("model").getAsString());
        assertEquals("f4", merged.get("mcmodels").getAsString());
    }

    private static String model(JsonObject variants, String key) {
        return variants.getAsJsonObject(key).get("model").getAsString();
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
