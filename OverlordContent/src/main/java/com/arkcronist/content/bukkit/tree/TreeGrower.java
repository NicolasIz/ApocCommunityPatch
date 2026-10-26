package com.arkcronist.content.bukkit.tree;

import com.arkcronist.content.bukkit.block.BlockRegistry;
import com.arkcronist.content.bukkit.block.CustomBlockService;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.core.tree.TreeSpec;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.world.StructureGrowEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * 5.4: a sapling grown into its tree, in the world, on the main thread.
 *
 * <p>The tree needs room: every block of its trunk free - air, or something a block replaces, like
 * grass - and the world tall enough for it; its leaves take only free blocks too, and leave alone
 * whatever is in their way. Before anything changes, a {@link StructureGrowEvent} carries every block
 * of the tree, as vanilla's own trees do, so protection plugins decide - and may take blocks out of
 * it. Custom blocks it is made of are recorded like any placed one.</p>
 */
public final class TreeGrower {

    private final ItemRegistry items;
    private final BlockRegistry cubes;
    private final CustomBlockService blocks;
    private final Logger logger;
    private final Set<String> warned = new HashSet<>();

    public TreeGrower(ItemRegistry items, BlockRegistry cubes, CustomBlockService blocks, Logger logger) {
        this.items = items;
        this.cubes = cubes;
        this.blocks = blocks;
        this.logger = logger;
    }

    /**
     * Grows {@code tree} from {@code base} - the sapling's own block, which becomes the foot of its
     * trunk.
     *
     * @param player who bone-mealed it; null when it grew by itself
     * @return whether it grew; false when there is no room, a protection plugin said no, or a block
     *         it names does not exist (told once in the log)
     */
    public boolean grow(Block base, TreeSpec tree, @Nullable Player player, boolean boneMeal) {
        List<String> problems = new ArrayList<>();
        Optional<TreeBlocks> made = TreeBlocks.resolve(tree, items, cubes, problems);
        if (made.isEmpty()) {
            String reason = tree.log() + "/" + tree.leaves() + ": " + String.join("; ", problems);
            if (warned.add(reason)) {
                logger.warning("A sapling cannot grow its tree - " + reason);
            }
            return false;
        }
        List<TreeSpec.Part> parts = tree.plan(ThreadLocalRandom.current().nextLong());
        World world = base.getWorld();
        List<BlockState> states = new ArrayList<>();
        for (TreeSpec.Part part : parts) {
            int y = base.getY() + part.dy();
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                if (part.log()) {
                    return false;
                }
                continue;
            }
            Block at = base.getRelative(part.dx(), part.dy(), part.dz());
            // Light blocks hold other crops and furniture's lights: a tree leaves them be.
            boolean free = at.equals(base) || at.getType().isAir()
                    || (at.isReplaceable() && at.getType() != org.bukkit.Material.LIGHT);
            if (!free) {
                if (part.log()) {
                    // A trunk does not grow through anything.
                    return false;
                }
                continue;
            }
            if (at.isLiquid() && part.log()) {
                return false;
            }
            BlockState state = at.getState();
            state.setBlockData(part.log() ? made.get().log() : made.get().leafAt(TreeBlocks.distance(part, parts)));
            states.add(state);
        }
        StructureGrowEvent event = new StructureGrowEvent(base.getLocation(), TreeType.TREE, boneMeal, player, states);
        if (!event.callEvent()) {
            return false;
        }
        for (BlockState state : event.getBlocks()) {
            Block at = state.getBlock();
            if (made.get().logBlock() != null && state.getBlockData().equals(made.get().log())) {
                blocks.place(at, made.get().logBlock());
            } else if (made.get().leavesBlock() != null && state.getBlockData().equals(made.get().leaves())) {
                blocks.place(at, made.get().leavesBlock());
            } else {
                state.update(true, false);
            }
        }
        world.playSound(base.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_AZALEA_LEAVES_PLACE, 1f, 0.8f);
        world.spawnParticle(Particle.HAPPY_VILLAGER, base.getLocation().add(0.5, 1, 0.5), 20, 0.6, 1, 0.6);
        return true;
    }
}
