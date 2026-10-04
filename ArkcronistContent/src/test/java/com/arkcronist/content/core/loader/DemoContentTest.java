package com.arkcronist.content.core.loader;

import com.arkcronist.content.core.definition.Equipment;
import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.ModelSource;
import com.arkcronist.content.core.definition.ResourceLocation;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.pack.PackCompiler;
import com.arkcronist.content.core.pack.PackSettings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The demo content the plugin ships loads and compiles without a single problem. */
class DemoContentTest {

    private static final Path SHIPPED = Path.of("src/main/resources/contents");

    @TempDir
    Path temp;

    @Test
    void theShippedDemoLoadsAndCompilesCleanly() throws IOException {
        LoadReport report = new ContentLoader().load(SHIPPED);
        assertEquals(java.util.List.of(), report.problems());

        Map<String, ItemDefinition> items = report.items().stream()
                .collect(Collectors.toMap(ItemDefinition::fullId, Function.identity()));

        Placement.Furniture chest = (Placement.Furniture) items.get("demo:ruby_chest").placement();
        assertEquals(Placement.Support.CHEST, chest.support());
        assertNotNull(chest.storage(), "a chest is storage");
        assertNotNull(chest.animated());
        assertTrue(chest.animated().model().clips().keySet().containsAll(java.util.List.of("open", "close", "shake")));
        assertNotNull(chest.animated().model().icon(), "its inventory icon is the model at rest");
        // The lid rides on the body: shaking the chest shakes its lid with it.
        var bones = chest.animated().model().bones();
        int body = bones.stream().map(bone -> bone.name()).toList().indexOf("body");
        assertEquals(body, bones.stream().filter(bone -> bone.name().equals("lid")).findFirst().orElseThrow().parent());
        assertEquals("CHEST", items.get("demo:ruby_chest").material());

        Placement.Furniture bed = (Placement.Furniture) items.get("demo:ruby_bed").placement();
        assertEquals(Placement.Support.BED, bed.support());
        assertEquals("RED_BED", items.get("demo:ruby_bed").material());

        Path pack = temp.resolve("pack");
        PackCompiler.Result result = new PackCompiler(new PackSettings(new JsonPrimitive("demo"), 46, 46, 99))
                .compile(pack, report.items(), Map.of());
        assertEquals(java.util.List.of(), result.problems());
        for (String bone : java.util.List.of("body", "lid")) {
            assertTrue(Files.isRegularFile(pack.resolve("assets/demo/items/ruby_chest/bone_" + bone + ".json")), bone);
            assertTrue(Files.isRegularFile(pack.resolve("assets/demo/models/ruby_chest/bone_" + bone + ".json")), bone);
        }
        assertTrue(Files.isRegularFile(pack.resolve("assets/demo/models/furniture/ruby_bed.json")));

        // What the client drew magenta and black: furniture, crop and Blockbench textures are not
        // in block/ or item/, so they are on the atlas only by name.
        String atlas = Files.readString(pack.resolve("assets/minecraft/atlases/blocks.json"));
        for (String sprite : java.util.List.of("demo:furniture/pedestal_stone", "demo:furniture/ruby_crate",
                "demo:furniture/ruby_bed", "demo:ruby_chest/tex_0", "demo:crop/ruby_stage_0", "demo:crop/ruby_stage_3")) {
            assertTrue(atlas.contains("\"" + sprite + "\""), sprite + " in " + atlas);
        }
    }

    @Test
    void theBloodRubySetIsNetheriteWornAsRubyArmour() throws IOException {
        LoadReport report = new ContentLoader().load(SHIPPED);
        assertEquals(java.util.List.of(), report.problems());
        Map<String, ItemDefinition> items = report.items().stream()
                .collect(Collectors.toMap(ItemDefinition::fullId, Function.identity()));

        ResourceLocation asset = new ResourceLocation("demo", "ruby_armor");
        Map<String, Equipment.Slot> pieces = Map.of("ruby_chestplate", Equipment.Slot.CHEST,
                "ruby_leggings", Equipment.Slot.LEGS, "ruby_boots", Equipment.Slot.FEET);
        pieces.forEach((id, slot) -> {
            ItemDefinition piece = items.get("demo:" + id);
            assertTrue(piece.material().startsWith("NETHERITE_"), id);
            assertEquals(slot, piece.equipment().slot(), id);
            assertEquals(asset, piece.equipment().asset(), id);
            assertEquals(java.util.List.of("humanoid", "humanoid_leggings"), piece.equipment().layers(), id);
            assertTrue(piece.displayName().contains("<gradient:"), id);
        });

        ItemDefinition helmet = items.get("demo:ruby_helmet");
        assertEquals("NETHERITE_HELMET", helmet.material());
        assertEquals(Equipment.Slot.HEAD, helmet.equipment().slot());
        assertNull(helmet.equipment().asset(), "an asset on the head hides the horned model");
        assertEquals(new ResourceLocation("demo", "item/ruby_helmet_worn"),
                ((ModelSource.Provided) helmet.equipment().worn()).location());

        Path pack = temp.resolve("pack");
        PackCompiler.Result result = new PackCompiler(new PackSettings(new JsonPrimitive("demo"), 46, 46, 99))
                .compile(pack, report.items(), Map.of());
        assertEquals(java.util.List.of(), result.problems());
        Path assets = pack.resolve("assets/demo");
        JsonObject layers = json(assets.resolve("equipment/ruby_armor.json")).getAsJsonObject("layers");
        assertEquals(java.util.Set.of("humanoid", "humanoid_leggings"), layers.keySet());
        assertTrue(Files.isRegularFile(assets.resolve("textures/entity/equipment/humanoid/ruby_armor.png")));
        assertTrue(Files.isRegularFile(assets.resolve("textures/entity/equipment/humanoid_leggings/ruby_armor.png")));
        assertEquals("minecraft:select", json(assets.resolve("items/ruby_helmet.json"))
                .getAsJsonObject("model").get("type").getAsString());

        // The worn model's horns: every texture it names is in the pack, and it reaches above the
        // helmet shell - the silhouette vanilla's helmet does not have.
        JsonObject worn = json(assets.resolve("models/item/ruby_helmet_worn.json"));
        for (var texture : worn.getAsJsonObject("textures").entrySet()) {
            String value = texture.getValue().getAsString();
            if (value.startsWith("#")) {
                continue; // another variable of the same model
            }
            String path = ResourceLocation.parse(value, "demo").path();
            assertTrue(Files.isRegularFile(assets.resolve("textures/" + path + ".png")), path);
        }
        double top = 0;
        for (var element : worn.getAsJsonArray("elements")) {
            top = Math.max(top, element.getAsJsonObject().getAsJsonArray("to").get(1).getAsDouble());
        }
        assertTrue(top > 16, "horns rise above the shell: " + top);
    }

    @Test
    void theDemoAdvancementsCompileIntoOneTab() throws IOException {
        LoadReport report = new ContentLoader().load(SHIPPED);
        assertEquals(java.util.List.of(), report.problems());
        Map<String, ItemDefinition> items = report.items().stream()
                .collect(Collectors.toMap(ItemDefinition::fullId, Function.identity()));
        var result = com.arkcronist.content.core.advancement.AdvancementCompiler.compile(report.advancements(), items,
                JsonPrimitive::new);
        assertEquals(java.util.List.of(), result.problems());
        assertEquals(java.util.List.of("demo:arkcronist", "demo:first_ruby", "demo:ruby_knight"),
                result.advancements().stream().map(advancement -> advancement.key().toString()).toList());
        JsonObject knight = JsonParser.parseString(result.advancements().get(2).json()).getAsJsonObject();
        assertEquals("challenge", knight.getAsJsonObject("display").get("frame").getAsString());
        var wear = (com.arkcronist.content.core.definition.AdvancementDefinition.Trigger.Wear)
                result.advancements().get(2).definition().trigger();
        assertEquals(4, wear.items().size(), "the whole Blood Ruby set");
    }

    private static JsonObject json(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }
}
