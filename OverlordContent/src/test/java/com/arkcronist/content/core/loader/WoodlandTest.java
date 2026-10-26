package com.arkcronist.content.core.loader;

import com.arkcronist.content.core.block.BlockShape;
import com.arkcronist.content.core.block.LeavesAllocator;
import com.arkcronist.content.core.block.LeavesState;
import com.arkcronist.content.core.block.NoteBlockAllocator;
import com.arkcronist.content.core.block.NoteBlockState;
import com.arkcronist.content.core.block.BlockAxis;
import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.pack.PackCompiler;
import com.arkcronist.content.core.pack.PackSettings;
import com.arkcronist.content.core.tree.TreeBlueprint;
import com.arkcronist.content.core.tree.TreeSpec;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 5.5: the woodland pack - thirteen trees of their own wood, leaves and shape - loads and compiles. */
class WoodlandTest {

    private static final Path SHIPPED = Path.of("src/main/resources/contents");
    static final List<String> SPECIES = List.of("weeping_willow", "baobab", "palm", "red_maple", "frostpine", "wisteria",
            "emberwood", "cloudpuff", "spiralwood", "sunshade", "toadstool", "gnarlwood", "moonbloom");

    @TempDir
    Path temp;

    private static Map<String, ItemDefinition> woodland() throws IOException {
        LoadReport report = new ContentLoader().load(SHIPPED);
        assertEquals(List.of(), report.problems());
        return report.items().stream().filter(item -> item.namespace().equals("woodland"))
                .collect(Collectors.toMap(ItemDefinition::id, item -> item));
    }

    @Test
    void thirteenTreesEachDrawnInBlueprintsOfItsOwnWood() throws IOException {
        Map<String, ItemDefinition> items = woodland();
        for (String species : SPECIES) {
            Placement.Crop sapling = (Placement.Crop) items.get(species + "_sapling").placement();
            assertTrue(sapling.sapling(), species);
            TreeSpec tree = sapling.tree();
            assertEquals(TreeSpec.Shape.BLUEPRINT, tree.shape(), species);
            assertEquals(3, tree.blueprints().size(), species + ": three variants");
            assertEquals("woodland:" + species + "_log", tree.log());
            assertEquals("woodland:" + species + "_leaves", tree.leaves());
            assertTrue(tree.growthSeconds() > 0, species + " comes up over some seconds");
            assertEquals(1, tree.generation().size(), species + " grows into new chunks somewhere");
            Placement.Block log = (Placement.Block) items.get(species + "_log").placement();
            assertTrue(log.axis(), species + "'s log lies on its side too");
            Placement.Block leaves = (Placement.Block) items.get(species + "_leaves").placement();
            assertEquals(BlockShape.LEAVES, leaves.shape(), species);
            assertTrue(leaves.leaves().drops().stream().anyMatch(drop -> drop.item().equals("woodland:" + species + "_sapling")),
                    species + "'s leaves drop its sapling now and then");
            assertNotNull(items.get(species + "_planks"), species);
            for (String block : tree.blocks()) {
                String id = block.equals(TreeSpec.LOG) || block.equals(TreeSpec.LEAVES) ? null : block;
                if (id != null) {
                    assertTrue(items.containsKey(id.substring("woodland:".length())), species + " names " + id);
                }
            }
            for (TreeBlueprint blueprint : tree.blueprints()) {
                assertTrue(blueprint.reach() <= 10, species + " reaches " + blueprint.reach());
                assertTrue(blueprint.height() <= 20, species + " is " + blueprint.height() + " tall");
                assertTrue(blueprint.cells().size() > 15, species);
            }
        }
        assertEquals(SPECIES.size() * 4 + 5, items.size(), "sapling, log, planks and leaves, and five hanging extras");
    }

    /** Every custom leaves block gets a slot and every log three note block states, and the pack builds. */
    @Test
    void theLeavesFitAndThePackCompiles() throws IOException {
        LoadReport report = new ContentLoader().load(SHIPPED);
        List<String> leaves = new ArrayList<>();
        List<String> blocks = new ArrayList<>();
        for (ItemDefinition item : report.items()) {
            if (item.placement() instanceof Placement.Block block && block.leaves() != null) {
                leaves.add(item.fullId());
            } else if (item.placement() instanceof Placement.Block block && block.noteBlock()) {
                block.axes().forEach(axis -> blocks.add(axis.key(item.fullId())));
            }
        }
        assertEquals(18, leaves.size(), "thirteen crowns and five hanging extras");
        LeavesAllocator.Allocation slots = LeavesAllocator.allocate(Map.of(), leaves, Set.of());
        assertEquals(List.of(), slots.problems());
        assertTrue(slots.materials().size() <= 6, "six vanilla leaves blocks at most: " + slots.materials());
        assertTrue(!slots.materials().contains("oak_leaves") && !slots.materials().contains("spruce_leaves"),
                "the leaves builds use most are left alone");
        assertTrue(!slots.materials().contains("cherry_leaves"), "no pink petals falling from the thirteen trees");
        NoteBlockAllocator.Allocation states = NoteBlockAllocator.allocate(Map.of(), blocks);
        assertEquals(List.of(), states.problems());
        Map<String, NoteBlockState> noteBlocks = states.active();
        assertNotNull(noteBlocks.get(BlockAxis.X.key("woodland:palm_log")));

        PackCompiler.Result result = new PackCompiler(new PackSettings(new JsonPrimitive("test"), 46, 46, 99))
                .compile(temp.resolve("pack"), new PackCompiler.Input(report.items(), noteBlocks, Map.of(), List.of(),
                        Map.of(), Map.of(), List.of(), false, List.of(), SHIPPED, List.of(), slots.active()));
        assertEquals(List.of(), result.problems().stream().filter(p -> p.contains("woodland")).toList());
        for (String material : slots.materials()) {
            assertTrue(Files.isRegularFile(temp.resolve("pack/assets/minecraft/blockstates/" + material + ".json")), material);
        }
        String ember = Files.readString(temp.resolve("pack/assets/woodland/models/block/emberwood_leaves.json"));
        assertTrue(ember.contains("light_emission"), "ember leaves glow");
        for (String species : SPECIES) {
            for (String texture : List.of("block/" + species + "_log", "block/" + species + "_log_top",
                    "block/" + species + "_planks", "block/" + species + "_leaves", "crop/" + species + "_sapling_0",
                    "crop/" + species + "_sapling_1")) {
                assertTrue(Files.isRegularFile(temp.resolve("pack/assets/woodland/textures/" + texture + ".png")), texture);
            }
        }
    }
}
