package com.arkcronist.content.bukkit.tree;

import com.arkcronist.content.bukkit.block.BlockRegistry;
import com.arkcronist.content.bukkit.block.CustomBlock;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.tree.TreeSpec;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 5.4: the blocks a tree is made of, as the world takes them: a custom block's state - a note block
 * held in its own, or the waxed copper block a shaped one is drawn through - or a vanilla block.
 *
 * <p>Vanilla leaves need their distance from the trunk set, or they rot away on their first random
 * tick: a tree is set without physics, so nothing works it out for them. They decay as vanilla's do
 * when the trunk is a vanilla log; on a custom trunk, which vanilla leaves do not know for wood,
 * they are made persistent.</p>
 *
 * @param log          the trunk's block
 * @param leaves       the crown's block, before its distance is set
 * @param logBlock     the custom cube the trunk is, to be recorded; null for anything else
 * @param leavesBlock  the custom cube the crown is, likewise
 * @param decays       whether vanilla leaves keep vanilla's decay
 */
public record TreeBlocks(BlockData log, BlockData leaves, @Nullable CustomBlock logBlock,
                         @Nullable CustomBlock leavesBlock, boolean decays) {

    /** What a tree is made of, or empty - with why - when a block it names does not exist. */
    public static Optional<TreeBlocks> resolve(TreeSpec tree, ItemRegistry items, BlockRegistry cubes,
                                               List<String> problems) {
        Optional<Resolved> log = block(tree.log(), items, cubes, problems);
        Optional<Resolved> leaves = block(tree.leaves(), items, cubes, problems);
        if (log.isEmpty() || leaves.isEmpty()) {
            return Optional.empty();
        }
        boolean decays = leaves.get().data() instanceof Leaves && log.get().custom() == null
                && Tag.LOGS.isTagged(log.get().data().getMaterial());
        return Optional.of(new TreeBlocks(log.get().data(), leaves.get().data(), log.get().custom(),
                leaves.get().custom(), decays));
    }

    private record Resolved(BlockData data, @Nullable CustomBlock custom) {
    }

    private static Optional<Resolved> block(String id, ItemRegistry items, BlockRegistry cubes, List<String> problems) {
        Optional<CustomItem> item = items.get(id);
        if (item.isPresent()) {
            if (!(item.get().placement() instanceof Placement.Block block)) {
                problems.add(id + " is not a block a tree can be made of");
                return Optional.empty();
            }
            if (block.noteBlock()) {
                Optional<CustomBlock> cube = cubes.get(id);
                if (cube.isEmpty()) {
                    problems.add(id + " got no note block state, so no tree can be made of it");
                    return Optional.empty();
                }
                return Optional.of(new Resolved(cube.get().data(), cube.get()));
            }
            // A shaped block: the copper block it is drawn through, which is what it is in the world.
            return Optional.of(new Resolved(item.get().material().createBlockData(), null));
        }
        // Not a custom block: the vanilla block of that name - a bare name in the YAML came with the pack's namespace.
        Material material = Material.matchMaterial(id.substring(id.indexOf(':') + 1));
        if (material == null || !material.isBlock() || material.isAir()) {
            problems.add("no custom block or vanilla block called " + id);
            return Optional.empty();
        }
        return Optional.of(new Resolved(material.createBlockData(), null));
    }

    /**
     * The crown's block for a leaf this far from the trunk: vanilla leaves get the distance vanilla
     * would give them (1 to 7) and stay unless they may decay; anything else is as it is.
     */
    public BlockData leafAt(int distance) {
        if (!(leaves instanceof Leaves leaf)) {
            return leaves;
        }
        Leaves placed = (Leaves) leaf.clone();
        placed.setPersistent(!decays);
        placed.setDistance(Math.max(1, Math.min(7, distance)));
        return placed;
    }

    /** A leaf's distance from the trunk as vanilla counts it, near enough for a crown that touches it. */
    public static int distance(TreeSpec.Part leaf, List<TreeSpec.Part> logs) {
        int best = Integer.MAX_VALUE;
        for (TreeSpec.Part log : logs) {
            if (log.log()) {
                best = Math.min(best, Math.abs(log.dx() - leaf.dx()) + Math.abs(log.dy() - leaf.dy())
                        + Math.abs(log.dz() - leaf.dz()));
            }
        }
        return best;
    }
}
