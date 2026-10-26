package com.arkcronist.content.core.loader;

import com.arkcronist.content.core.block.BlockAxis;
import com.arkcronist.content.core.block.BlockShape;
import com.arkcronist.content.core.definition.ItemDefinition;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.tree.TreeSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 5.5: logs that lie on their side, leaves of a tree's own, and trees drawn in blueprints. */
class ContentLoaderTreesTest {

    @TempDir
    Path contents;

    @Test
    void logsLeavesAndBlueprintsAreRead() throws IOException {
        write("woodland/trees.yml", """
                namespace: woodland
                items:
                  willow_log:
                    type: custom_block
                    resource: {texture: block/willow_log}
                    block: {axis: true, tool: axe}
                  willow_leaves:
                    type: custom_block
                    resource: {texture: block/willow_leaves}
                    block:
                      shape: leaves
                      drops:
                        - {item: woodland:willow_sapling, chance: 0.05}
                        - {item: STICK, amount: 1-2, chance: 0.02}
                  stubborn_leaves:
                    type: custom_block
                    resource: {texture: block/stubborn_leaves}
                    block: {shape: leaves, decay: false}
                  slab_log:
                    type: custom_block
                    resource: {texture: block/slab_log}
                    block: {shape: slab, axis: true}
                  willow_sapling:
                    type: custom_crop
                    crop:
                      stages: [{texture: block/willow_sapling}]
                      tree:
                        log: willow_log
                        leaves: willow_leaves
                        shape: cone
                        growth-seconds: 3
                        blueprints:
                          - weight: 2
                            legend: {"#": log, "-": log-x, "@": leaves, "v": hanging_moss}
                            layers:
                              - ".#."
                              - [".#--", "...v"]
                              - ["@@@", "@#@", "@@@"]
                """);
        LoadReport report = new ContentLoader().load(contents);
        Map<String, ItemDefinition> items = report.items().stream()
                .collect(Collectors.toMap(ItemDefinition::id, item -> item));

        Placement.Block log = (Placement.Block) items.get("willow_log").placement();
        assertTrue(log.axis());
        assertEquals(List.of(BlockAxis.Y, BlockAxis.X, BlockAxis.Z), log.axes());
        assertTrue(log.noteBlock());
        assertEquals("NOTE_BLOCK", items.get("willow_log").material());

        Placement.Block leaves = (Placement.Block) items.get("willow_leaves").placement();
        assertEquals(BlockShape.LEAVES, leaves.shape());
        assertFalse(leaves.noteBlock(), "leaves are not a note block state");
        assertEquals(List.of(new Placement.Drop("woodland:willow_sapling", 1, 1, 0.05), new Placement.Drop("STICK", 1, 2, 0.02)),
                leaves.leaves().drops());
        assertTrue(leaves.leaves().decays());
        assertEquals("FLOWERING_AZALEA_LEAVES", items.get("willow_leaves").material(), "until the build gives it a block");
        assertFalse(((Placement.Block) items.get("stubborn_leaves").placement()).leaves().decays());

        Placement.Block slab = (Placement.Block) items.get("slab_log").placement();
        assertFalse(slab.axis(), "only a cube lies on its side");
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("slab_log") && p.contains("axis")), report.problems().toString());

        TreeSpec tree = ((Placement.Crop) items.get("willow_sapling").placement()).tree();
        assertEquals(TreeSpec.Shape.BLUEPRINT, tree.shape());
        assertEquals(3.0, tree.growthSeconds());
        assertEquals(1, tree.blueprints().size());
        assertEquals(2, tree.blueprints().get(0).weight());
        assertEquals(3, tree.maxHeight());
        assertTrue(tree.blocks().contains("woodland:hanging_moss"), "a bare name is the pack's block");
        assertTrue(report.problems().stream().anyMatch(p -> p.contains("willow_sapling") && p.contains("'shape' is ignored")),
                report.problems().toString());
        assertNull(((Placement.Block) items.get("willow_log").placement()).leaves());
    }

    private void write(String relative, String text) throws IOException {
        Path file = contents.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
}
