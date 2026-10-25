package com.arkcronist.content.core.advancement;

import com.arkcronist.content.core.definition.AdvancementDefinition;
import com.arkcronist.content.core.definition.AdvancementDefinition.Frame;
import com.arkcronist.content.core.definition.AdvancementDefinition.Trigger;
import com.arkcronist.content.core.definition.Equipment;
import com.arkcronist.content.core.definition.ItemBehaviour;
import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.ResourceLocation;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancementCompilerTest {

    private static final Path ROOT = Path.of("contents/demo");

    private static final Map<String, ItemDefinition> ITEMS = Map.of(
            "demo:ruby", item("ruby", "EMERALD", null),
            "demo:ruby_helmet", item("ruby_helmet", "NETHERITE_HELMET", Equipment.Slot.HEAD),
            "demo:ruby_chestplate", item("ruby_chestplate", "NETHERITE_CHESTPLATE", Equipment.Slot.CHEST),
            "demo:ruby_crown", item("ruby_crown", "GOLDEN_HELMET", Equipment.Slot.HEAD));

    @Test
    void writesVanillasFormatParentsFirst() {
        AdvancementCompiler.Result result = AdvancementCompiler.compile(List.of(
                advancement("knight", "demo:root", Frame.CHALLENGE,
                        new Trigger.Wear(List.of(loc("demo:ruby_helmet"), loc("demo:ruby_chestplate"))), "<player> did it"),
                advancement("root", null, Frame.TASK, new Trigger.Join(), null),
                advancement("first_ruby", "demo:root", Frame.TASK, new Trigger.Obtain(loc("demo:ruby")), null)),
                ITEMS, JsonPrimitive::new);

        assertEquals(List.of(), result.problems());
        assertEquals(List.of("demo:root", "demo:knight", "demo:first_ruby"),
                result.advancements().stream().map(advancement -> advancement.key().toString()).toList(),
                "a parent is registered before its children");

        JsonObject root = json(result, 0);
        assertFalse(root.has("parent"));
        assertEquals("minecraft:tick", root.getAsJsonObject("criteria").getAsJsonObject("joined").get("trigger").getAsString());
        JsonObject rootDisplay = root.getAsJsonObject("display");
        assertEquals("minecraft:gui/advancements/backgrounds/stone", rootDisplay.get("background").getAsString(),
                "a tab gets vanilla's stone background when it names none");
        assertFalse(rootDisplay.get("announce_to_chat").getAsBoolean(), "nobody hears about a tab everyone gets");

        JsonObject knight = json(result, 1);
        assertEquals("demo:root", knight.get("parent").getAsString());
        assertEquals("minecraft:impossible",
                knight.getAsJsonObject("criteria").getAsJsonObject("worn").get("trigger").getAsString(),
                "vanilla has no trigger for wearing a set: the plugin awards it");
        assertEquals("[[\"worn\"]]", knight.get("requirements").toString());
        JsonObject display = knight.getAsJsonObject("display");
        assertEquals("challenge", display.get("frame").getAsString());
        assertFalse(display.has("background"), "only a tab's root has one");
        assertFalse(display.get("announce_to_chat").getAsBoolean(), "the plugin's own line replaces vanilla's");
        JsonObject icon = display.getAsJsonObject("icon");
        assertEquals("minecraft:netherite_helmet", icon.get("id").getAsString());
        assertEquals("demo:ruby_helmet", icon.getAsJsonObject("components").get("minecraft:item_model").getAsString(),
                "the icon is drawn with the item's own model");

        JsonObject obtain = json(result, 2).getAsJsonObject("criteria").getAsJsonObject("obtained");
        assertEquals("minecraft:inventory_changed", obtain.get("trigger").getAsString());
        JsonObject predicate = obtain.getAsJsonObject("conditions").getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals("minecraft:emerald", predicate.get("items").getAsString());
        assertEquals("demo:ruby", predicate.getAsJsonObject("components").get("minecraft:item_model").getAsString(),
                "a plain emerald does not count, only the ruby");
        assertTrue(json(result, 2).getAsJsonObject("display").get("announce_to_chat").getAsBoolean(),
                "without a line of its own, vanilla announces it");
    }

    @Test
    void dropsWhatCouldNeverBeShownOrEarned() {
        AdvancementCompiler.Result result = AdvancementCompiler.compile(List.of(
                advancement("root", null, Frame.TASK, new Trigger.Join(), null),
                advancement("orphan", "demo:nowhere", Frame.TASK, new Trigger.Manual(), null),
                advancement("loop_a", "demo:loop_b", Frame.TASK, new Trigger.Manual(), null),
                advancement("loop_b", "demo:loop_a", Frame.TASK, new Trigger.Manual(), null),
                advancement("child_of_orphan", "demo:orphan", Frame.TASK, new Trigger.Manual(), null),
                advancement("two_hats", "demo:root", Frame.GOAL,
                        new Trigger.Wear(List.of(loc("demo:ruby_helmet"), loc("demo:ruby_crown"))), null),
                advancement("not_armour", "demo:root", Frame.GOAL, new Trigger.Wear(List.of(loc("demo:ruby"))), null),
                advancement("ghost_item", "demo:root", Frame.GOAL, new Trigger.Obtain(loc("demo:ghost")), null),
                advancement("vanilla_tab", "minecraft:story/root", Frame.TASK, new Trigger.Obtain(loc("minecraft:diamond")),
                        null)),
                ITEMS, JsonPrimitive::new);

        assertEquals(List.of("demo:root", "demo:vanilla_tab"),
                result.advancements().stream().map(advancement -> advancement.key().toString()).toList());
        String problems = String.join("\n", result.problems());
        assertTrue(problems.contains("demo:orphan: parent demo:nowhere is not defined"), problems);
        assertTrue(problems.contains("demo:loop_a: its parents loop back to it"), problems);
        assertTrue(problems.contains("demo:child_of_orphan"), problems);
        assertTrue(problems.contains("two of the items to wear go on HEAD"), problems);
        assertTrue(problems.contains("demo:ruby has no 'equipment' section"), problems);
        assertTrue(problems.contains("'trigger.obtain' names demo:ghost"), problems);
    }

    private static JsonObject json(AdvancementCompiler.Result result, int index) {
        return JsonParser.parseString(result.advancements().get(index).json()).getAsJsonObject();
    }

    private static AdvancementDefinition advancement(String id, String parent, Frame frame, Trigger trigger,
                                                     String announce) {
        return new AdvancementDefinition("demo", id, "Title " + id, "Description", loc(
                trigger instanceof Trigger.Wear wear ? wear.items().get(0).toString() : "demo:ruby"),
                frame, parent == null ? null : loc(parent), null, true, false, announce, true, 0, trigger, ROOT,
                ROOT.resolve("advancements.yml"));
    }

    private static ItemDefinition item(String id, String material, Equipment.Slot slot) {
        ItemDefinition item = new ItemDefinition("demo", id, material, null, List.of(), null, ItemBehaviour.DEFAULT,
                null, ROOT.resolve("items.yml"));
        return slot == null ? item : item.withEquipment(new Equipment(slot, null, List.of(), null, ROOT));
    }

    private static ResourceLocation loc(String text) {
        return ResourceLocation.parse(text, "demo");
    }
}
