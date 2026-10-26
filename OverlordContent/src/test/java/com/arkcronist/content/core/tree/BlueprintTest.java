package com.arkcronist.content.core.tree;

import com.arkcronist.content.core.block.BlockAxis;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintTest {

    /** A trunk of 3, a branch east of it along x, a ring of leaves on top and a vine hanging off. */
    private static Map<String, Object> drawing() {
        Map<String, Object> legend = new LinkedHashMap<>();
        legend.put("#", "log");
        legend.put("-", "log-x");
        legend.put("@", "leaves");
        legend.put("%", Map.of("block", "leaves", "chance", 0.5));
        legend.put("~", "minecraft:vine[east=true]");
        Map<String, Object> blueprint = new LinkedHashMap<>();
        blueprint.put("legend", legend);
        blueprint.put("layers", List.of(
                "...\n.#.\n...\n",
                List.of("...", ".#--", "...~"),
                List.of("@@@", "@#@", "@%@")));
        return blueprint;
    }

    @Test
    void layersAreReadFromTheBottomUpAroundTheSapling() {
        List<String> problems = new ArrayList<>();
        List<TreeBlueprint> read = BlueprintReader.read(List.of(drawing()), "woodland", "tree: ", problems);
        assertEquals(List.of(), problems);
        TreeBlueprint blueprint = read.get(0);
        assertEquals(3, blueprint.height());
        assertEquals(2, blueprint.reach(), "the branch's end, two east of the trunk");
        Map<List<Integer>, TreeBlueprint.Cell> at = new LinkedHashMap<>();
        blueprint.cells().forEach(cell -> at.put(List.of(cell.dx(), cell.dy(), cell.dz()), cell));
        assertEquals(TreeSpec.LOG, at.get(List.of(0, 0, 0)).block(), "the sapling's block, the centre-most log");
        assertEquals(BlockAxis.X, at.get(List.of(2, 1, 0)).axis());
        assertEquals("minecraft:vine[east=true]", at.get(List.of(2, 1, 1)).block());
        assertEquals(0.5, at.get(List.of(0, 2, 1)).chance());
        assertEquals(TreeSpec.LEAVES, at.get(List.of(-1, 2, -1)).block());
    }

    @Test
    void aTreeIsTurnedAndMirroredWithItsLogsLyingTheTurnedWay() {
        TreeBlueprint blueprint = BlueprintReader.read(List.of(drawing()), "woodland", "", new ArrayList<>()).get(0);
        TreeSpec tree = TreeSpec.drawn("woodland:log", "woodland:leaves", List.of(blueprint), List.of(), 2);
        Set<Integer> turns = new HashSet<>();
        for (long seed = 0; seed < 64; seed++) {
            List<TreeSpec.Part> parts = tree.plan(seed);
            TreeSpec.Part first = parts.get(0);
            assertEquals(List.of(0, 0, 0, TreeSpec.LOG), List.of(first.dx(), first.dy(), first.dz(), first.block()),
                    "the sapling's block first");
            TreeSpec.Part branchEnd = parts.stream().filter(part -> part.log() && part.dy() == 1
                    && Math.abs(part.dx()) + Math.abs(part.dz()) == 2).findFirst().orElseThrow();
            turns.add(branchEnd.turn());
            // The branch's end moves with the turn, and lies along the line it now runs on.
            int x = branchEnd.mirrored() ? -2 : 2;
            int z = 0;
            for (int i = 0; i < branchEnd.quarterTurns(); i++) {
                int swap = x;
                x = -z;
                z = swap;
            }
            assertEquals(List.of(x, z), List.of(branchEnd.dx(), branchEnd.dz()));
            assertEquals(z == 0 ? BlockAxis.X : BlockAxis.Z, branchEnd.axis());
            long chanceLeaves = parts.stream().filter(part -> part.dy() == 2 && !part.log()).count();
            assertTrue(chanceLeaves == 7 || chanceLeaves == 8, "the leaf of chance 0.5 there or not");
        }
        assertEquals(8, turns.size(), "every quarter turn, mirrored or not");
        assertEquals(tree.plan(5), tree.plan(5), "the same seed, the same tree");
        assertEquals(TreeSpec.Shape.BLUEPRINT, tree.shape());
        assertEquals(3, tree.maxHeight());
    }

    @Test
    void leavesAreCountedFromTheWoodAsVanillaCountsThem() {
        TreeBlueprint blueprint = BlueprintReader.read(List.of(drawing()), "woodland", "", new ArrayList<>()).get(0);
        Map<String, Integer> distances = blueprint.leafDistances(block -> block.equals(TreeSpec.LEAVES));
        assertEquals(Map.of(TreeSpec.LEAVES, 2), distances, "the ring's corners, two steps from the trunk's top");
    }

    @Test
    void whatCannotBeReadIsSaidAndLeftOut() {
        List<String> problems = new ArrayList<>();
        Map<String, Object> noLog = new LinkedHashMap<>();
        noLog.put("legend", Map.of("@", "leaves"));
        noLog.put("layers", List.of("@@\n@@"));
        Map<String, Object> wide = new LinkedHashMap<>();
        wide.put("legend", Map.of("#", "log"));
        wide.put("layers", List.of("#" + ".".repeat(30) + "#"));
        Map<String, Object> stray = new LinkedHashMap<>(drawing());
        stray.put("layers", List.of(".#.\n.X."));
        List<TreeBlueprint> read = BlueprintReader.read(List.of(noLog, wide, stray), "woodland", "tree: ", problems);
        assertEquals(1, read.size(), "only the one with a stray character, which is left out of it");
        assertTrue(problems.stream().anyMatch(p -> p.contains("#1") && p.contains("log")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("#2") && p.contains("reaches")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("#3") && p.contains("[X]")), problems.toString());
    }
}
