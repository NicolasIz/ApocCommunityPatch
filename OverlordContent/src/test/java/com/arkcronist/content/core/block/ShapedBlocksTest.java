package com.arkcronist.content.core.block;

import com.arkcronist.content.core.definition.ModelSource;
import com.arkcronist.content.core.definition.ResourceLocation;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapedBlocksTest {

    @TempDir
    Path temp;

    @Test
    void shapesAreReadByNameAndKnowTheirCopper() {
        assertEquals(Optional.of(BlockShape.SLAB), BlockShape.parse("slab"));
        assertEquals(Optional.of(BlockShape.STAIRS), BlockShape.parse("Stair"));
        assertEquals(Optional.of(BlockShape.TRANSPARENT), BlockShape.parse("grate"));
        assertEquals(Optional.of(BlockShape.TRAPDOOR), BlockShape.parse("TRAPDOOR"));
        assertEquals(Optional.empty(), BlockShape.parse("fence"));

        assertFalse(BlockShape.CUBE.copper());
        assertEquals(Optional.of(BlockShape.DOOR), BlockShape.ofBase("minecraft:waxed_weathered_copper_door"));
        assertEquals(Optional.empty(), BlockShape.ofBase("copper_door"));
        // The trial chambers' copper is last, and not handed out without leave.
        assertEquals(List.of("waxed_exposed_cut_copper_slab", "waxed_weathered_cut_copper_slab", "petrified_oak_slab"),
                BlockShape.SLAB.outsideStructures());
        assertEquals(3, BlockShape.TRAPDOOR.outsideStructures().size());
        assertFalse(BlockShape.TRAPDOOR.outsideStructures().contains("waxed_oxidized_copper_trapdoor"));
    }

    @Test
    void basesAreHandedOutInIdOrderAndKept() {
        Map<String, BlockShape> blocks = new LinkedHashMap<>();
        blocks.put("demo:oak_slab", BlockShape.SLAB);
        blocks.put("demo:ash_slab", BlockShape.SLAB);
        blocks.put("demo:glass", BlockShape.TRANSPARENT);
        blocks.put("demo:ruby_block", BlockShape.CUBE);

        CopperBaseAllocator.Allocation first = CopperBaseAllocator.allocate(Map.of(), blocks, Set.of(), false);
        assertEquals(List.of(), first.problems());
        assertTrue(first.changed());
        assertEquals("waxed_exposed_cut_copper_slab", first.active().get("demo:ash_slab"));
        assertEquals("waxed_weathered_cut_copper_slab", first.active().get("demo:oak_slab"));
        assertEquals("waxed_exposed_copper_grate", first.active().get("demo:glass"));
        assertFalse(first.active().containsKey("demo:ruby_block"), "a cube is a note block");

        // New slabs sorted before the others take nothing from them: the next free - then none.
        blocks.put("demo:aab_slab", BlockShape.SLAB);
        blocks.put("demo:aaa_slab", BlockShape.SLAB);
        CopperBaseAllocator.Allocation second = CopperBaseAllocator.allocate(first.assignments(), blocks, Set.of(), false);
        assertEquals(first.active().get("demo:ash_slab"), second.active().get("demo:ash_slab"));
        assertEquals(first.active().get("demo:oak_slab"), second.active().get("demo:oak_slab"));
        assertEquals("petrified_oak_slab", second.active().get("demo:aaa_slab"));
        assertNull(second.active().get("demo:aab_slab"));
        assertEquals(1, second.problems().size());
        assertTrue(second.problems().get(0).startsWith("demo:aab_slab: no slab block left"), second.problems().get(0));
        assertTrue(second.problems().get(0).contains("blocks.trial-chamber-copper"), second.problems().get(0));

        // With leave, the trial chambers' copper.
        CopperBaseAllocator.Allocation third = CopperBaseAllocator.allocate(first.assignments(), blocks, Set.of(), true);
        assertEquals("waxed_cut_copper_slab", third.active().get("demo:aab_slab"));
        assertEquals(List.of(), third.problems());
    }

    @Test
    void retiredReservedAndChangedShapes() {
        Map<String, String> previous = new LinkedHashMap<>();
        previous.put("demo:gone_slab", "waxed_exposed_cut_copper_slab");
        previous.put("demo:door", "waxed_exposed_cut_copper_stairs");
        previous.put("demo:typo", "waxed_gold_slab");
        Map<String, BlockShape> blocks = new LinkedHashMap<>();
        blocks.put("demo:new_slab", BlockShape.SLAB);
        blocks.put("demo:door", BlockShape.DOOR);

        CopperBaseAllocator.Allocation allocation = CopperBaseAllocator.allocate(previous, blocks,
                Set.of("waxed_exposed_copper_door"), false);
        // A slab no longer defined keeps its copper; the next one takes the one after.
        assertEquals("waxed_exposed_cut_copper_slab", allocation.assignments().get("demo:gone_slab"));
        assertEquals("waxed_weathered_cut_copper_slab", allocation.active().get("demo:new_slab"));
        // Stairs that became a door: a door's copper, not the one another pack draws.
        assertEquals("waxed_weathered_copper_door", allocation.active().get("demo:door"));
        assertFalse(allocation.assignments().containsKey("demo:typo"));
        assertEquals(2, allocation.problems().size(), allocation.problems().toString());
        assertFalse(allocation.active().containsKey("demo:gone_slab"));
    }

    @Test
    void theFileIsIdToBlockAndReadBack() throws IOException {
        Path file = temp.resolve("data/copper_block_bases.json");
        assertEquals(Map.of(), CopperBaseAllocator.read(file));
        CopperBaseAllocator.write(file, Map.of("demo:b", "waxed_exposed_copper_grate", "demo:a", "waxed_copper_trapdoor"));
        String text = Files.readString(file);
        assertTrue(text.indexOf("demo:a") < text.indexOf("demo:b"), text);
        assertEquals(Map.of("demo:a", "waxed_copper_trapdoor", "demo:b", "waxed_exposed_copper_grate"),
                CopperBaseAllocator.read(file));
    }

    @Test
    void blockStatesAreVanillasForEachShape() {
        JsonObject stairs = ShapedModels.blockState(BlockShape.STAIRS, suffix -> "demo:block/oak_stairs" + suffix)
                .getAsJsonObject("variants");
        assertEquals(40, stairs.size());
        assertVariant(stairs, "facing=east,half=bottom,shape=straight", "demo:block/oak_stairs", 0, 0, false);
        assertVariant(stairs, "facing=east,half=bottom,shape=inner_left", "demo:block/oak_stairs_inner", 0, 270, true);
        assertVariant(stairs, "facing=north,half=top,shape=outer_right", "demo:block/oak_stairs_outer", 180, 0, true);
        assertVariant(stairs, "facing=south,half=bottom,shape=inner_left", "demo:block/oak_stairs_inner", 0, 0, false);

        JsonObject door = ShapedModels.blockState(BlockShape.DOOR, suffix -> "d" + suffix).getAsJsonObject("variants");
        assertEquals(32, door.size());
        assertVariant(door, "facing=north,half=lower,hinge=right,open=true", "d_bottom_right_open", 0, 180, false);
        assertVariant(door, "facing=east,half=upper,hinge=left,open=true", "d_top_left_open", 0, 90, false);

        JsonObject trapdoor = ShapedModels.blockState(BlockShape.TRAPDOOR, suffix -> "t" + suffix)
                .getAsJsonObject("variants");
        assertEquals(16, trapdoor.size());
        assertVariant(trapdoor, "facing=west,half=top,open=true", "t_open", 0, 270, false);
        assertVariant(trapdoor, "facing=south,half=top,open=false", "t_top", 0, 0, false);

        JsonObject slab = ShapedModels.blockState(BlockShape.SLAB, suffix -> "s" + suffix).getAsJsonObject("variants");
        assertEquals(Set.of("type=bottom", "type=top", "type=double"), slab.keySet());
        assertEquals(Set.of(""), ShapedModels.blockState(BlockShape.TRANSPARENT, suffix -> "g")
                .getAsJsonObject("variants").keySet());
    }

    @Test
    void texturesFillEachShapesModels() {
        Path root = temp;
        List<String> problems = new ArrayList<>();
        ModelSource.Generated given = generated(root, "block/oak_slab", Map.of("side", "demo:block/oak_side",
                "top", "demo:block/oak_top"));
        ModelSource.Generated slab = (ModelSource.Generated) ShapedModels.itemModel("demo", "oak_slab", BlockShape.SLAB,
                given, "", problems);
        assertEquals(List.of(), problems);
        assertEquals(loc("minecraft:block/slab"), slab.parent());
        assertEquals(loc("demo:block/oak_top"), slab.textures().get("bottom"), "bottom falls back to top");
        List<ModelSource.Generated> extra = ShapedModels.extraModels("demo", "oak_slab", BlockShape.SLAB, slab);
        assertEquals(List.of(loc("demo:block/oak_slab_top"), loc("demo:block/oak_slab_double")),
                extra.stream().map(ModelSource.Generated::location).toList());
        assertEquals(loc("minecraft:block/cube_bottom_top"), extra.get(1).parent());

        ModelSource.Generated door = (ModelSource.Generated) ShapedModels.itemModel("demo", "gate", BlockShape.DOOR,
                generated(root, "block/gate", Map.of("top", "demo:block/gate_top", "bottom", "demo:block/gate_bottom")),
                "", problems);
        assertEquals(loc("demo:item/gate"), door.location());
        assertEquals(loc("demo:block/gate_bottom"), door.textures().get("layer0"), "no icon: the lower half");
        List<ModelSource.Generated> halves = ShapedModels.extraModels("demo", "gate", BlockShape.DOOR, door);
        assertEquals(8, halves.size());
        assertFalse(halves.get(0).textures().containsKey("layer0"));

        assertNull(ShapedModels.itemModel("demo", "gate", BlockShape.DOOR,
                generated(root, "block/gate", Map.of("top", "demo:block/gate_top")), "gate: ", problems));
        assertNull(ShapedModels.itemModel("demo", "s", BlockShape.STAIRS,
                new ModelSource.Provided(root, loc("demo:block/s")), "s: ", problems));
        assertEquals(2, problems.size(), problems.toString());

        ModelSource own = new ModelSource.Provided(root, loc("demo:block/lattice"));
        assertEquals(own, ShapedModels.itemModel("demo", "lattice", BlockShape.TRANSPARENT, own, "", problems));
    }

    private static void assertVariant(JsonObject variants, String key, String model, int x, int y, boolean uvlock) {
        JsonObject variant = variants.getAsJsonObject(key);
        assertEquals(model, variant.get("model").getAsString(), key);
        assertEquals(x, variant.has("x") ? variant.get("x").getAsInt() : 0, key + " x");
        assertEquals(y, variant.has("y") ? variant.get("y").getAsInt() : 0, key + " y");
        assertEquals(uvlock, variant.has("uvlock") && variant.get("uvlock").getAsBoolean(), key + " uvlock");
    }

    private static ModelSource.Generated generated(Path root, String path, Map<String, String> textures) {
        Map<String, ResourceLocation> variables = new LinkedHashMap<>();
        textures.forEach((key, value) -> variables.put(key, loc(value)));
        return new ModelSource.Generated(root, new ResourceLocation("demo", path), ModelSource.CUBE_ALL, variables);
    }

    private static ResourceLocation loc(String raw) {
        return ResourceLocation.parse(raw, "minecraft");
    }
}
