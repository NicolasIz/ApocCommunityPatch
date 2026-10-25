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
        // The pack's furniture; since 3.0 it also has a backpack, an item worn rather than placed.
        return report.items().stream().filter(item -> item.namespace().equals("medieval_props"))
                .filter(item -> item.placement() instanceof Placement.Furniture)
                .collect(Collectors.toMap(ItemDefinition::id, item -> (Placement.Furniture) item.placement()));
    }

    /** 3.0: the backpack is worn on the back, its model and texture shipped with the pack. */
    @Test
    void theBackpackIsACosmeticWornOnTheBack() throws IOException {
        LoadReport report = new ContentLoader().load(SHIPPED);
        ItemDefinition backpack = report.items().stream().filter(item -> item.id().equals("mochila_viaje"))
                .findFirst().orElseThrow();
        assertEquals(com.arkcronist.content.core.cosmetic.CosmeticDefinition.Slot.BACK, backpack.cosmetic().slot());
        assertEquals(28, backpack.cosmetic().physics().maxAngle());
        assertEquals("PAPER", backpack.material());
        assertTrue(java.nio.file.Files.isRegularFile(SHIPPED.resolve("medieval_props/models/cosmetic/mochila_viaje.json")));
        assertTrue(java.nio.file.Files.isRegularFile(SHIPPED.resolve("medieval_props/textures/cosmetic/mochila_viaje.png")));
    }

    /** 3.0: the demo's rare ore and fishing rod play the HUD minigame. */
    @Test
    void theDemoOreAndRodPlayTheMinigame() throws IOException {
        LoadReport report = new ContentLoader().load(SHIPPED);
        ItemDefinition ore = report.items().stream().filter(item -> item.fullId().equals("demo:star_ore"))
                .findFirst().orElseThrow();
        ItemDefinition rod = report.items().stream().filter(item -> item.fullId().equals("demo:master_rod"))
                .findFirst().orElseThrow();
        assertEquals(22, ore.minigame().zone());
        assertEquals(com.arkcronist.content.core.minigame.MinigameDefinition.OnMiss.KEEP, ore.minigame().onMiss());
        assertEquals(2, ore.minigame().criticalDrops().size());
        assertEquals(com.arkcronist.content.core.minigame.MinigameDefinition.OnMiss.LOSE, rod.minigame().onMiss());
        assertEquals("FISHING_ROD", rod.material());
    }

    @Test
    void sixtyPiecesOfEveryKind() throws IOException {
        Map<String, Placement.Furniture> props = props();
        assertEquals(60, props.size());
        assertEquals(14, count(props, furniture -> furniture.seat() != null), "seats");
        assertEquals(25, count(props, furniture -> furniture.ambience() != null), "effects");
        assertEquals(10, count(props, furniture -> furniture.commands() != null), "command signs");
        assertEquals(10, count(props, furniture -> furniture.storage() != null), "storage");
        // 3.1: every one of the sixty does something on a right click.
        props.forEach((id, furniture) -> assertTrue(furniture.interactive(), id + " does something on a click"));
        assertEquals(1, count(props, furniture -> furniture.guillotine() != null), "the guillotine");
        assertEquals(5, count(props, furniture -> furniture.support() == Placement.Support.LIGHT), "lights");

        assertEquals(new Footprint(2, 1), props.get("banco_largo").footprint());
        // 3.2: the throne on its dais, three by three, with one seat in its middle; the bread oven and
        // the map board of the spawn, big too.
        assertEquals(new Footprint(3, 3, 2), props.get("trono_real").footprint());
        assertEquals(new Placement.Seat(1.0f, true, 0f, -0.2f), props.get("trono_real").seat());
        assertEquals(new Footprint(3, 2, 2), props.get("horno_pan").footprint());
        assertEquals(new Footprint(6, 4, 1), props.get("mapa_mural").footprint());
        assertEquals(new Footprint(3, 2), props.get("puesto_mercado").footprint());
        // 3.1: the big three - the forge, the well of a square and the woodpile.
        assertEquals(new Footprint(3, 3, 2), props.get("forja").footprint());
        assertEquals(new Footprint(6, 2, 6), props.get("pozo_deseos").footprint());
        assertEquals(new Footprint(3, 2, 2), props.get("pila_lena").footprint());
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
        assertTrue(brazier.potions().isEmpty(), "a brazier is not drunk");
        assertEquals("campfire", brazier.cook(), "3.1: it roasts what is held out to it");
        for (Map.Entry<String, Placement.Furniture> prop : props.entrySet()) {
            Placement.Ambience ambience = prop.getValue().ambience();
            if (ambience != null) {
                assertTrue(!ambience.particles().isEmpty() || ambience.clickable(), prop.getKey() + " does something");
            }
        }
    }

    /** 3.1: workstations, a forge that melts ore and opens a smithing table, fires that cook, lamps that go out. */
    @Test
    void theWorkingPiecesWorkAndTheLampsGoOut() throws IOException {
        Map<String, Placement.Furniture> props = props();
        for (String table : List.of("mesa_taberna", "mesa_redonda", "mesa_banquete")) {
            assertEquals("crafting", props.get(table).ambience().station(), table);
        }
        assertEquals("anvil", props.get("yunque_herrero").ambience().station());
        assertEquals("grindstone", props.get("piedra_afilar").ambience().station());
        Placement.Furniture forge = props.get("forja");
        assertEquals("blast", forge.ambience().cook());
        assertEquals("smithing", forge.ambience().station());
        assertEquals(15, forge.light(), "a forge on barriers lights the square from above");
        assertTrue(forge.ambience().particles().stream().noneMatch(p -> p.particle().equals("large_smoke")),
                "no black smoke squares over the forge");
        assertEquals("campfire", props.get("fogata_asador").ambience().cook());
        assertEquals("smoker", props.get("horno_pan").ambience().cook());
        for (String lamp : List.of("farol_hierro", "lampara_aceite", "candelabro_mesa")) {
            assertTrue(props.get(lamp).ambience().toggleLight(), lamp);
            assertTrue(props.get(lamp).light() > 0, lamp);
        }
        assertFalse(props.get("abrevadero").ambience().clickParticles().isEmpty(), "the trough splashes");
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
                // 3.2: the map board's picture is a second texture, of its own.
                if (model.getAsJsonObject("textures").has("1")) {
                    String picture = model.getAsJsonObject("textures").get("1").getAsString();
                    assertEquals("medieval_props:furniture/mapa_mural_mapa", picture, file.toString());
                    assertTrue(Files.isRegularFile(pack.resolve("assets/medieval_props/textures/furniture/mapa_mural_mapa.png")));
                    assertTrue(atlas.contains("\"" + picture + "\""), picture + " on the atlas");
                }
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
