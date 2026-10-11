package com.arkcronist.content.core.loader;

import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.furniture.Footprint;
import com.arkcronist.content.core.pack.PackCompiler;
import com.arkcronist.content.core.pack.PackSettings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 1.9.5: the 60 medieval props the plugin ships load, and compile into a pack, without a problem. */
class MedievalPropsTest {

    private static final Path SHIPPED = Path.of("src/main/resources/contents");

    @TempDir
    Path temp;

    private static Map<String, Placement.Furniture> props() throws IOException {
        LoadReport report = new ContentLoader().load(SHIPPED);
        assertEquals(List.of(), report.problems());
        return report.items().stream().filter(item -> item.namespace().equals("medieval_props"))
                .collect(Collectors.toMap(ItemDefinition::id, item -> (Placement.Furniture) item.placement()));
    }

    @Test
    void sixtyPiecesOfEveryKind() throws IOException {
        Map<String, Placement.Furniture> props = props();
        assertEquals(60, props.size());
        assertEquals(13, count(props, furniture -> furniture.seat() != null), "seats");
        assertEquals(15, count(props, furniture -> furniture.ambience() != null), "effects");
        assertEquals(10, count(props, furniture -> furniture.commands() != null), "command signs");
        assertEquals(6, count(props, furniture -> furniture.storage() != null), "storage");
        assertEquals(1, count(props, furniture -> furniture.guillotine() != null), "the guillotine");
        assertEquals(5, count(props, furniture -> furniture.support() == Placement.Support.LIGHT), "lights");

        assertEquals(new Footprint(2, 1), props.get("banco_largo").footprint());
        assertEquals(new Footprint(1, 2), props.get("trono_real").footprint());
        assertEquals(new Footprint(3, 2), props.get("puesto_mercado").footprint());
        assertEquals(15, props.get("farol_hierro").light());
        assertEquals(54, props.get("tonel_vino").storage().slots());
    }

    @Test
    void drinksHandOutTheirEffectsAndFiresOnlyBurn() throws IOException {
        Map<String, Placement.Furniture> props = props();
        Placement.Ambience ale = props.get("jarra_cerveza").ambience();
        assertEquals(List.of(new Placement.Effect("minecraft:speed", 600, 0)), ale.potions());
        assertEquals("minecraft:entity.generic.drink", ale.sound());
        assertTrue(props.get("jarra_cerveza").interactive());
        Placement.Ambience brazier = props.get("brasero").ambience();
        assertFalse(brazier.clickable(), "a brazier is not drunk");
        assertFalse(props.get("brasero").interactive());
        for (Map.Entry<String, Placement.Furniture> prop : props.entrySet()) {
            Placement.Ambience ambience = prop.getValue().ambience();
            if (ambience != null) {
                assertFalse(ambience.particles().isEmpty(), prop.getKey() + " gives off particles");
            }
        }
    }

    /** The signs only greet: from the console, with the player's name filled in, and namespaced commands. */
    @Test
    void theSignsRunHarmlessNamespacedCommands() throws IOException {
        Map<String, Placement.Furniture> props = props();
        for (String sign : List.of("cartel_banco", "cartel_taberna", "cartel_herreria", "cartel_mercado",
                "cartel_establo", "cartel_alquimista", "cartel_posada", "mapa_mural")) {
            Placement.Commands commands = props.get(sign).commands();
            assertNotNull(commands, sign);
            assertFalse(commands.asPlayer(), sign);
            assertTrue(commands.run().get(0).startsWith("minecraft:tellraw %player% "), sign);
            JsonParser.parseString(commands.run().get(0).substring("minecraft:tellraw %player% ".length()));
        }
        assertTrue(props.get("buzon").commands().asPlayer(), "the mailbox reads the player's own mail");
        assertTrue(props.get("campana_plaza").commands().run().get(0).startsWith("minecraft:execute in %dimension% "));
    }

    @Test
    void theGuillotineDropsItsBladeOnWhoeverKneels() throws IOException {
        Placement.Furniture furniture = props().get("guillotina_plaza");
        Placement.Guillotine guillotine = furniture.guillotine();
        assertEquals(new Footprint(2, 3), furniture.footprint());
        assertEquals(Placement.Support.BARRIER, furniture.support());
        assertEquals(63, guillotine.delayMillis());
        assertEquals(250, guillotine.impactMillis());
        assertEquals(60, guillotine.resetTicks());
        assertEquals(1, guillotine.leverAcross());
        assertEquals(0, guillotine.leverUp());
        assertFalse(guillotine.killUser());
        assertTrue(guillotine.respectPvp());
        assertEquals(List.of("crit", "electric_spark", "enchanted_hit"),
                guillotine.impact().stream().map(Placement.Particles::particle).toList());
        var model = furniture.animated().model();
        assertTrue(model.clips().keySet().containsAll(List.of("drop_blade", "reset_blade")));
        assertEquals(List.of("frame", "blade", "lever"), model.bones().stream().map(bone -> bone.name()).toList());
        // Held at the bottom once it falls; wound back up and let go by the reset.
        assertEquals("HOLD", model.clips().get("drop_blade").loop().name());
        assertEquals("ONCE", model.clips().get("reset_blade").loop().name());
    }

    /** 1.9.6: every piece is drawn from a texture of its own, painted for it, and it all goes into the pack. */
    @Test
    void theyCompileEachWithItsOwnTexture() throws IOException {
        LoadReport report = new ContentLoader().load(SHIPPED);
        List<ItemDefinition> props = report.items().stream()
                .filter(item -> item.namespace().equals("medieval_props")).toList();
        Path pack = temp.resolve("pack");
        PackCompiler.Result result = new PackCompiler(new PackSettings(new JsonPrimitive("medieval"), 46, 46, 99))
                .compile(pack, props, Map.of());
        assertEquals(List.of(), result.problems());
        String atlas = Files.readString(pack.resolve("assets/minecraft/atlases/blocks.json"));
        for (String bone : List.of("frame", "blade", "lever")) {
            assertTrue(Files.isRegularFile(pack.resolve("assets/medieval_props/models/guillotina_plaza/bone_" + bone
                    + ".json")), bone);
        }
        try (var models = Files.list(pack.resolve("assets/medieval_props/models/furniture"))) {
            List<Path> files = models.toList();
            assertEquals(60, files.size(), files.toString());
            for (Path file : files) {
                JsonObject model = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                String id = file.getFileName().toString().replace(".json", "").replace("_icon", "");
                String texture = model.getAsJsonObject("textures").get("0").getAsString();
                assertEquals("medieval_props:furniture/" + id, texture, file.toString());
                assertTrue(Files.isRegularFile(pack.resolve("assets/medieval_props/textures/furniture/" + id + ".png")), id);
                assertTrue(atlas.contains("\"" + texture + "\""), texture + " on the atlas");
                for (JsonElement element : model.getAsJsonArray("elements")) {
                    JsonObject cube = element.getAsJsonObject();
                    for (String corner : List.of("from", "to")) {
                        JsonArray at = cube.getAsJsonArray(corner);
                        for (JsonElement value : at) {
                            double v = value.getAsDouble();
                            assertTrue(v >= -16 && v <= 32, file.getFileName() + " " + corner + " " + at);
                        }
                    }
                    if (cube.has("rotation")) {
                        double angle = cube.getAsJsonObject("rotation").get("angle").getAsDouble();
                        assertTrue(List.of(-45.0, -22.5, 0.0, 22.5, 45.0).contains(angle), file + " rotates " + angle);
                    }
                    for (var face : cube.getAsJsonObject("faces").entrySet()) {
                        for (JsonElement uv : face.getValue().getAsJsonObject().getAsJsonArray("uv")) {
                            assertTrue(uv.getAsDouble() >= 0 && uv.getAsDouble() <= 16, file + " uv " + uv);
                        }
                    }
                }
            }
        }
    }

    private static long count(Map<String, Placement.Furniture> props, Predicate<Placement.Furniture> test) {
        return props.values().stream().filter(test).count();
    }
}
