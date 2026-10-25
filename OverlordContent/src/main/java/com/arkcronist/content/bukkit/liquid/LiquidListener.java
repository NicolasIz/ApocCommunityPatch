package com.arkcronist.content.bukkit.liquid;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.protection.Interaction;
import com.arkcronist.content.bukkit.protection.Protection;
import com.arkcronist.content.core.ballistics.Ray;
import com.arkcronist.content.core.ballistics.Vec3;
import com.arkcronist.content.core.ballistics.VoxelTraversal;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Tripwire;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
import java.util.Optional;

/**
 * How liquids meet the world: poured from their bucket, scooped up with an empty one, and kept as
 * they are by everything else.
 *
 * <p>A liquid is a tripwire state, and vanilla would otherwise change it: neighbour updates join
 * it to string next to it, stepping on it presses it, a piston or an explosion breaks it and drops
 * string, water washes it away. Each of those is stopped here for the liquids' states - and only
 * for those: string placed by players is left entirely to vanilla.</p>
 */
public final class LiquidListener implements Listener {

    private static final double DEFAULT_REACH = 4.5;

    private final LiquidService liquids;
    private final LiquidRegistry registry;
    private final ItemFactory factory;
    private final Protection protection;

    public LiquidListener(LiquidService liquids, LiquidRegistry registry, ItemFactory factory, Protection protection) {
        this.liquids = liquids;
        this.registry = registry;
        this.factory = factory;
        this.protection = protection;
    }

    private boolean isLiquid(Block block) {
        return block.getType() == Material.TRIPWIRE && registry.state(block.getBlockData()) != null;
    }

    // ------------------------------------------------------------------ buckets

    // Not ignoreCancelled: a click in the air arrives "cancelled", having no block to use.
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        EquipmentSlot hand = event.getHand();
        if (hand == null || action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR
                || registry.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItem(hand);
        Optional<CustomItem> custom = factory.identify(held);
        Optional<LiquidRegistry.Liquid> liquid = custom.flatMap(item -> registry.get(item.id()));
        if (liquid.isPresent()) {
            event.setUseItemInHand(Event.Result.DENY);
            event.setUseInteractedBlock(Event.Result.DENY);
            if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                pour(player, hand, event.getClickedBlock(), event.getBlockFace(), liquid.get());
            }
            return;
        }
        if (held.getType() == Material.BUCKET && custom.isEmpty()) {
            Block target = aimedLiquid(player);
            if (target == null) {
                return;
            }
            // A source is in the way of whatever vanilla would fill the bucket from.
            event.setUseItemInHand(Event.Result.DENY);
            event.setUseInteractedBlock(Event.Result.DENY);
            scoop(player, hand, target, registry.state(target.getBlockData()).liquid());
        }
    }

    private void pour(Player player, EquipmentSlot hand, Block clicked, BlockFace face, LiquidRegistry.Liquid liquid) {
        LiquidRegistry.State there = registry.state(clicked.getBlockData());
        Block target = there != null && there.liquid() == liquid && !there.source() ? clicked
                : clicked.getRelative(face);
        LiquidRegistry.State inTarget = registry.state(target.getBlockData());
        boolean free = target.getType().isAir() || inTarget != null && inTarget.liquid() == liquid && !inTarget.source();
        if (!free || !protection.check(player, target, Interaction.PLACE)) {
            return;
        }
        if (!liquids.pour(target, liquid, player.getUniqueId())) {
            return;
        }
        target.getWorld().playSound(target.getLocation().add(0.5, 0.5, 0.5), Sound.ITEM_BUCKET_EMPTY,
                SoundCategory.BLOCKS, 1, 1);
        if (player.getGameMode() != GameMode.CREATIVE) {
            player.getInventory().setItem(hand, new ItemStack(Material.BUCKET));
        }
    }

    private void scoop(Player player, EquipmentSlot hand, Block block, LiquidRegistry.Liquid liquid) {
        if (!protection.check(player, block, Interaction.BREAK)) {
            return;
        }
        String id = liquids.removeSource(block);
        if (id == null) {
            return;
        }
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.ITEM_BUCKET_FILL,
                SoundCategory.BLOCKS, 1, 1);
        ItemStack filled = factory.create(liquid.item(), 1);
        PlayerInventory inventory = player.getInventory();
        if (player.getGameMode() == GameMode.CREATIVE) {
            if (!inventory.containsAtLeast(filled, 1)) {
                inventory.addItem(filled);
            }
            return;
        }
        ItemStack empty = inventory.getItem(hand);
        if (empty.getAmount() <= 1) {
            inventory.setItem(hand, filled);
            return;
        }
        empty.setAmount(empty.getAmount() - 1);
        inventory.setItem(hand, empty);
        for (ItemStack left : inventory.addItem(filled).values()) {
            player.getWorld().dropItem(player.getLocation(), left);
        }
    }

    /**
     * The liquid source the player aims at within reach - walked block by block along their line of
     * sight, through air and through flowing liquid, as a vanilla bucket looks through flowing water
     * for a source; stopping at anything solid.
     */
    private Block aimedLiquid(Player player) {
        Location eye = player.getEyeLocation();
        World world = player.getWorld();
        AttributeInstance range = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        double reach = range != null ? range.getValue() : DEFAULT_REACH;
        Ray ray = new Ray(new Vec3(eye.getX(), eye.getY(), eye.getZ()),
                Vec3.fromRotation(eye.getYaw(), eye.getPitch()));
        Block[] found = new Block[1];
        VoxelTraversal.walk(ray, reach, voxel -> {
            if (voxel.y() < world.getMinHeight() || voxel.y() >= world.getMaxHeight()) {
                return false;
            }
            Block block = world.getBlockAt(voxel.x(), voxel.y(), voxel.z());
            LiquidRegistry.State state = block.getType() == Material.TRIPWIRE ? registry.state(block.getBlockData()) : null;
            if (state != null && state.source()) {
                found[0] = block;
                return true;
            }
            return state == null && !block.isPassable();
        });
        return found[0];
    }

    /** Vanilla water or lava poured into a liquid would wash it away. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (isLiquid(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ keeping the states

    /**
     * A disarmed tripwire is a liquid block - vanilla never leaves one disarmed. When one changes,
     * or a neighbour change has just reconnected it, the change goes no further - no shape updates
     * from one liquid block to the next - and the block is put back as it should be, within the
     * same tick, so no player ever sees it otherwise. Not ignoreCancelled: whoever cancelled it,
     * the block may already have changed.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.TRIPWIRE || !(block.getBlockData() instanceof Tripwire wire)
                || !wire.isDisarmed()) {
            return;
        }
        event.setCancelled(true);
        BlockData expected = liquids.expected(block);
        if (expected != null && !expected.equals(block.getBlockData())) {
            block.setBlockData(expected, false);
        }
    }

    /** Stepping on it: vanilla would press it, and send redstone to any hook. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onStep(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL && event.getClickedBlock() != null && isLiquid(event.getClickedBlock())) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityStep(EntityInteractEvent event) {
        if (isLiquid(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /** A liquid is not broken like a block: it is scooped up, or drains. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreakLiquid(BlockBreakEvent event) {
        if (isLiquid(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlowInto(BlockFromToEvent event) {
        if (isLiquid(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    /** A piston would break a liquid in its way and drop string. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        BlockFace direction = event.getDirection();
        if (isLiquid(event.getBlock().getRelative(direction))) {
            event.setCancelled(true);
            return;
        }
        for (Block moved : event.getBlocks()) {
            if (isLiquid(moved) || isLiquid(moved.getRelative(direction))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block moved : event.getBlocks()) {
            if (isLiquid(moved)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** Liquids are not blown up, as water is not. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isLiquid);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isLiquid);
    }

    // ------------------------------------------------------------------ terrain changes nearby

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        liquids.changedNear(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        liquids.changedNear(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExploded(EntityExplodeEvent event) {
        changed(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExploded(BlockExplodeEvent event) {
        changed(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        liquids.changedNear(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        liquids.changedNear(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent event) {
        liquids.changedNear(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonMoved(BlockPistonExtendEvent event) {
        changed(event.getBlocks());
        liquids.changedNear(event.getBlock().getRelative(event.getDirection()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonPulled(BlockPistonRetractEvent event) {
        changed(event.getBlocks());
        liquids.changedNear(event.getBlock().getRelative(event.getDirection()));
    }

    private void changed(List<Block> blocks) {
        if (!blocks.isEmpty()) {
            // One per change is plenty: a solve looks all round each.
            liquids.changedNear(blocks.get(0));
            liquids.changedNear(blocks.get(blocks.size() - 1));
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        liquids.chunkLoaded(event.getChunk());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        liquids.worldLoaded(event.getWorld());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        liquids.forget(event.getPlayer());
    }
}
