package com.arkcronist.content.bukkit.tree;

import com.arkcronist.content.bukkit.block.BlockRegistry;
import com.arkcronist.content.bukkit.block.CustomBlock;
import com.arkcronist.content.bukkit.block.CustomBlockService;
import com.arkcronist.content.bukkit.block.LeavesRegistry;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.core.tree.TreeSpec;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * 5.4: a sapling grown into its tree, in the world, on the main thread.
 *
 * <p>The tree needs room: every block of its wood free - air, something a block replaces, like
 * grass, or leaves, which a growing tree pushes through as vanilla's do - and the world tall enough
 * for it; the rest takes only free blocks too, and leaves alone whatever is in its way. Before
 * anything changes, a {@link StructureGrowEvent} carries every block of the tree, as vanilla's own
 * trees do, so protection plugins decide - and may take blocks out of it. Custom note blocks it is
 * made of are recorded like any placed one; custom leaves are known by their state.</p>
 *
 * <p>5.5: a tree with {@code growth-seconds} comes up over that time - its wood from the ground up,
 * then the rest from the trunk out - each block set only if its place is still free then.</p>
 */
public final class TreeGrower {

    private final Plugin plugin;
    private final ItemRegistry items;
    private final BlockRegistry cubes;
    private final LeavesRegistry leaves;
    private final CustomBlockService blocks;
    private final Logger logger;
    private final Set<String> warned = new HashSet<>();

    public TreeGrower(Plugin plugin, ItemRegistry items, BlockRegistry cubes, LeavesRegistry leaves,
                      CustomBlockService blocks, Logger logger) {
        this.plugin = plugin;
        this.items = items;
        this.cubes = cubes;
        this.leaves = leaves;
        this.blocks = blocks;
        this.logger = logger;
    }

    /** One block of the tree as it will be set. */
    private record Planned(TreeSpec.Part part, BlockState state, Material was) {
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
        Optional<TreeBlocks> made = TreeBlocks.resolve(tree, items, cubes, leaves, problems);
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
        Map<Long, Planned> planned = new HashMap<>();
        for (TreeSpec.Part part : parts) {
            int y = base.getY() + part.dy();
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                if (part.log()) {
                    return false;
                }
                continue;
            }
            Block at = base.getRelative(part.dx(), part.dy(), part.dz());
            if (planned.containsKey(at.getBlockKey())) {
                continue;
            }
            boolean free = at.equals(base) || free(at);
            if (!free) {
                if (part.log()) {
                    // Wood does not grow through anything.
                    return false;
                }
                continue;
            }
            BlockState state = at.getState();
            state.setBlockData(made.get().data(part, parts));
            states.add(state);
            planned.put(at.getBlockKey(), new Planned(part, state, at.getType()));
        }
        StructureGrowEvent event = new StructureGrowEvent(base.getLocation(), TreeType.TREE, boneMeal, player, states);
        if (!event.callEvent()) {
            return false;
        }
        List<BlockState> allowed = new ArrayList<>(event.getBlocks());
        // Wood from the ground up, then the rest from the trunk out: how it comes up when it takes its time.
        allowed.sort(Comparator.comparingInt((BlockState state) -> {
                    Planned plan = planned.get(state.getBlock().getBlockKey());
                    return plan == null || !plan.part().log() ? 1 : 0;
                })
                .thenComparingInt(state -> state.getY())
                .thenComparingInt(state -> Math.abs(state.getX() - base.getX()) + Math.abs(state.getZ() - base.getZ())));
        int ticks = (int) Math.round(tree.growthSeconds() * 20);
        world.playSound(base.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_AZALEA_LEAVES_PLACE, 1f, 0.8f);
        if (ticks <= 0 || allowed.size() < 2) {
            allowed.forEach(state -> set(state, planned, made.get(), true));
            world.spawnParticle(Particle.HAPPY_VILLAGER, base.getLocation().add(0.5, 1, 0.5), 20, 0.6, 1, 0.6);
            return true;
        }
        // The sapling's own block at once: the sapling is gone, its trunk begun.
        set(allowed.remove(0), planned, made.get(), true);
        int perTick = Math.max(1, (int) Math.ceil(allowed.size() / (double) ticks));
        new BukkitRunnable() {
            private int next;

            @Override
            public void run() {
                if (!base.getWorld().isChunkLoaded(base.getX() >> 4, base.getZ() >> 4)) {
                    // Unloaded on its way up: the rest is set at once, as a tree that grew while no one looked.
                    for (; next < allowed.size(); next++) {
                        set(allowed.get(next), planned, made.get(), false);
                    }
                    cancel();
                    return;
                }
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int i = 0; i < perTick && next < allowed.size(); i++, next++) {
                    BlockState state = allowed.get(next);
                    set(state, planned, made.get(), false);
                    if (random.nextInt(6) == 0) {
                        world.spawnParticle(Particle.HAPPY_VILLAGER, state.getLocation().add(0.5, 0.5, 0.5), 2,
                                0.3, 0.3, 0.3);
                    }
                }
                if (next >= allowed.size()) {
                    world.playSound(base.getLocation().add(0.5, 2, 0.5), Sound.BLOCK_AZALEA_LEAVES_PLACE, 1f, 1.2f);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    /** Sets one block of the tree - when it comes up later, only if its place is still as it was or free. */
    private void set(BlockState state, Map<Long, Planned> planned, TreeBlocks made, boolean now) {
        Block at = state.getBlock();
        Planned plan = planned.get(at.getBlockKey());
        if (!now && plan != null && at.getType() != plan.was() && !free(at)) {
            return;
        }
        CustomBlock custom = plan == null ? null : made.recorded(plan.part());
        if (custom != null && state.getBlockData().equals(custom.data())) {
            blocks.place(at, custom);
        } else {
            at.setBlockData(state.getBlockData(), false);
        }
    }

    /** Air, what a block replaces - grass, snow - or leaves; never a light block, which holds a crop or furniture's light. */
    private static boolean free(Block at) {
        Material type = at.getType();
        if (type.isAir()) {
            return true;
        }
        if (type == Material.LIGHT) {
            return false;
        }
        return at.isReplaceable() || Tag.REPLACEABLE_BY_TREES.isTagged(type);
    }
}
