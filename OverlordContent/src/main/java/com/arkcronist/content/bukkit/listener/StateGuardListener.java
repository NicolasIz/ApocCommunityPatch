package com.arkcronist.content.bukkit.listener;

import com.arkcronist.content.bukkit.block.CustomBlockService;
import com.arkcronist.content.bukkit.block.LeavesRegistry;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 5.5: puts back the state of custom blocks a change next to them made vanilla work out again.
 *
 * <p>When a block changes, vanilla tells each block beside it to work its shape out again, and
 * Paper fires no event for that, only one for the change itself. A note block works out its
 * instrument from the blocks above and below it - stone set under a custom log would make it
 * another block - and leaves their distance from the nearest log, a tick later: custom leaves next
 * to a log set down would become other leaves. So on every change, the custom note blocks and held
 * persistent leaves beside it are noted as they are. Vanilla's change to a noted block fires an
 * event of its own, and the block is set back right there, without physics; two ticks after it was
 * noted - after the leaves' own tick - one changed some other way is set back all the same. One that was broken or
 * replaced meanwhile, by a player or anything else, is left as it is now. Held persistent leaves
 * set by a command are noted themselves: {@code /setblock} has a new leaf work out its distance a
 * tick later, from the leaves beside it.</p>
 */
public final class StateGuardListener implements Listener {

    private static final List<BlockFace> AROUND = List.of(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
            BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST);

    private record Spot(UUID world, long key) {
    }

    /** @param due the tick it is looked at again: two after it was noted, once the leaves' own tick is past */
    private record Guarded(Block block, BlockData data, int due) {
    }

    private final Plugin plugin;
    private final CustomBlockService cubes;
    private final LeavesRegistry leaves;
    private final Map<Spot, Guarded> noted = new LinkedHashMap<>();
    private @Nullable BukkitTask task;
    private long restored;

    public StateGuardListener(Plugin plugin, CustomBlockService cubes, LeavesRegistry leaves) {
        this.plugin = plugin;
        this.cubes = cubes;
        this.leaves = leaves;
    }

    /** Custom blocks set back since the server started. */
    public long restored() {
        return restored;
    }

    /**
     * A noted block changing - its instrument worked out again, its distance in its own tick - is
     * set back at once, in the same tick, before any player is sent the change; and tells nothing
     * round it.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void revertAtOnce(BlockPhysicsEvent event) {
        if (noted.isEmpty()) {
            return;
        }
        Block block = event.getBlock();
        if (!block.equals(event.getSourceBlock())) {
            return;
        }
        Spot spot = new Spot(block.getWorld().getUID(), block.getBlockKey());
        Guarded guarded = noted.get(spot);
        if (guarded == null) {
            return;
        }
        BlockData now = block.getBlockData();
        if (now.getMaterial() != guarded.data().getMaterial() || now.equals(guarded.data())) {
            return;
        }
        noted.remove(spot);
        block.setBlockData(guarded.data(), false);
        event.setCancelled(true);
        restored++;
    }

    /**
     * A change itself - the event Paper fires before telling the blocks around it - not a block
     * told of a neighbour's change; and not one refused, which tells nothing around it.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        boolean noteBlocks = cubes.active();
        boolean held = !leaves.held().isEmpty();
        if (!noteBlocks && !held) {
            return;
        }
        Block changed = event.getBlock();
        if (!changed.equals(event.getSourceBlock())) {
            return;
        }
        for (BlockFace face : AROUND) {
            Block next = changed.getRelative(face);
            Material type = next.getType();
            if (type == Material.NOTE_BLOCK ? noteBlocks : held && leaves.holds(type)) {
                BlockData data = next.getBlockData();
                if (type == Material.NOTE_BLOCK || (data instanceof Leaves leaf && leaf.isPersistent())) {
                    note(next, data);
                }
            }
        }
    }

    /**
     * Held persistent leaves set by a command or another plugin - {@code /setblock} and {@code /fill}
     * have the new leaf work its distance out again a tick later, from whatever leaves are beside it
     * - are noted as they were set, so that tick is set back as well. Refused or not: refusing it
     * keeps the blocks around it as they were, not the leaf itself.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeavesSet(BlockPhysicsEvent event) {
        if (leaves.held().isEmpty() || !leaves.holds(event.getChangedType())) {
            return;
        }
        Block block = event.getBlock();
        if (!block.equals(event.getSourceBlock())) {
            return;
        }
        BlockData data = block.getBlockData();
        if (data instanceof Leaves leaf && leaf.isPersistent()) {
            // As it is now: the latest it was set to, not what a neighbour's change found there before.
            noted.put(new Spot(block.getWorld().getUID(), block.getBlockKey()),
                    new Guarded(block, data, plugin.getServer().getCurrentTick() + 2));
            startRestoring();
        }
    }

    private void note(Block block, BlockData data) {
        noted.putIfAbsent(new Spot(block.getWorld().getUID(), block.getBlockKey()),
                new Guarded(block, data, plugin.getServer().getCurrentTick() + 2));
        startRestoring();
    }

    private void startRestoring() {
        if (task == null) {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::restore, 1L, 1L);
        }
    }

    /** A block a player breaks or places is theirs to change: never set back. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        release(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        release(event.getBlockPlaced());
    }

    private void release(Block block) {
        if (!noted.isEmpty()) {
            noted.remove(new Spot(block.getWorld().getUID(), block.getBlockKey()));
        }
    }

    /** Each tick while any is noted: those due are looked at, the rest wait their turn. */
    private void restore() {
        int now = plugin.getServer().getCurrentTick();
        for (Iterator<Guarded> it = noted.values().iterator(); it.hasNext(); ) {
            Guarded guarded = it.next();
            if (guarded.due() > now) {
                continue;
            }
            it.remove();
            Block block = guarded.block();
            if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                continue;
            }
            BlockData current = block.getBlockData();
            if (current.getMaterial() == guarded.data().getMaterial() && !current.equals(guarded.data())) {
                block.setBlockData(guarded.data(), false);
                restored++;
            }
        }
        if (noted.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        noted.clear();
    }
}
