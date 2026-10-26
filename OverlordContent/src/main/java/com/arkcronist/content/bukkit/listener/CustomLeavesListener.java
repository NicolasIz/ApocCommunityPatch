package com.arkcronist.content.bukkit.listener;

import com.arkcronist.content.bukkit.block.CustomLeaves;
import com.arkcronist.content.bukkit.block.LeafDrops;
import com.arkcronist.content.bukkit.block.LeavesRegistry;
import com.arkcronist.content.bukkit.hooks.HookManager;
import com.arkcronist.content.bukkit.hooks.SkillXpHook;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.protection.Protection;
import com.arkcronist.content.core.block.LeavesState;
import com.arkcronist.content.core.definition.Placement;
import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 5.5: keeps custom leaves what they are.
 *
 * <p>Custom leaves are persistent states of vanilla leaves blocks at distances 1 to 6 (see
 * {@link LeavesState}). Vanilla works a leaf's distance out again a tick after a block next to it
 * changes; {@link StateGuardListener} sets it back. Here, a persistent leaf of a block in use that
 * changes all the same tells nothing round it - custom ones and vanilla's own placed ones, which are
 * held at distance 7 from the moment they are placed. Leaves a tree grew, of vanilla's kind, are not
 * persistent and left to vanilla.</p>
 *
 * <p>Breaking custom leaves drops what theirs do - themselves to shears or silk touch, else their
 * {@code drops:} - and explosions, ravagers and anything else that destroys them the same, each
 * explosion asked of the protection plugins first, as for other custom blocks.</p>
 */
public final class CustomLeavesListener implements Listener {

    private final LeavesRegistry leaves;
    private final ItemFactory items;
    private final LeafDrops drops;
    private final Protection protection;
    private final HookManager hooks;
    /** Told of leaves taken away, for the leaves round them that may now fall; none until set. */
    private Consumer<Block> gone = block -> { };

    public CustomLeavesListener(LeavesRegistry leaves, ItemFactory items, LeafDrops drops, Protection protection,
                                HookManager hooks) {
        this.leaves = leaves;
        this.items = items;
        this.drops = drops;
        this.protection = protection;
        this.hooks = hooks;
    }

    public void whenGone(Consumer<Block> gone) {
        this.gone = gone;
    }

    /** A persistent leaf of a block in use that changed tells nothing round it: no change spreads from it. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (!leaves.holds(block.getType())) {
            return;
        }
        if (block.getBlockData() instanceof Leaves leaf && leaf.isPersistent()) {
            event.setCancelled(true);
        }
    }

    /**
     * A stack of custom leaves made while they were drawn through another vanilla leaves block is
     * made of the right one, and the place refused once: the next click places them.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void remake(BlockPlaceEvent event) {
        if (leaves.isEmpty()) {
            return;
        }
        ItemStack hand = event.getItemInHand();
        Optional<CustomItem> held = items.identify(hand);
        if (held.isEmpty() || !(held.get().placement() instanceof Placement.Block block) || block.leaves() == null) {
            return;
        }
        if (hand.getType() != held.get().material() && leaves.get(held.get().id()).isPresent()) {
            event.setCancelled(true);
            ItemStack remade = hand.withType(held.get().material());
            items.repair(remade);
            event.getPlayer().getInventory().setItem(event.getHand(), remade);
        }
    }

    /**
     * Custom leaves placed take their placed state, which stays; vanilla leaves of a block in use are
     * held at distance 7, never drawn as custom ones.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!leaves.holds(block.getType()) || !(block.getBlockData() instanceof Leaves placed)) {
            return;
        }
        Optional<CustomLeaves> custom = items.identify(event.getItemInHand()).flatMap(item -> leaves.get(item.id()));
        if (custom.isPresent()) {
            block.setBlockData(leaves.state(custom.get(), false, placed.isWaterlogged()), false);
        } else if (placed.isPersistent() && placed.getDistance() != LeavesState.VANILLA_DISTANCE) {
            pin(block, placed);
        }
    }

    /** Vanilla's own persistent leaf, held at distance 7. */
    public static void pin(Block block, Leaves leaf) {
        Leaves held = (Leaves) leaf.clone();
        held.setDistance(LeavesState.VANILLA_DISTANCE);
        block.setBlockData(held, false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void noVanillaDrop(BlockBreakEvent event) {
        if (leaves.identify(event.getBlock()).isPresent()) {
            event.setDropItems(false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        leaves.identify(block).ifPresent(hit -> {
            if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
                drops.drop(block, hit.leaves(), event.getPlayer().getInventory().getItemInMainHand());
                hooks.skillXp(event.getPlayer(), SkillXpHook.Source.BLOCK, hit.leaves().id(),
                        hit.leaves().placement().skillXp());
                hooks.jobRewards(event.getPlayer(), block, hit.leaves().id(), hit.leaves().placement().jobs());
            }
            gone.accept(block);
        });
    }

    /** A ravager walking through them, {@code /setblock ... destroy}: they drop as broken by hand. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDestroy(BlockDestroyEvent event) {
        if (!event.willDrop()) {
            return;
        }
        leaves.identify(event.getBlock()).ifPresent(hit -> {
            event.setWillDrop(false);
            drops.drop(event.getBlock(), hit.leaves(), null);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        explode(event.blockList(), event.getLocation(), event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        explode(event.blockList(), event.getBlock().getLocation(), null);
    }

    /** Taken out of the explosion and broken here, dropping their own drops - each if protection allows. */
    private void explode(List<Block> destroyed, Location origin, @Nullable Entity source) {
        if (leaves.isEmpty()) {
            return;
        }
        for (Iterator<Block> it = destroyed.iterator(); it.hasNext(); ) {
            Block block = it.next();
            Optional<LeavesRegistry.Hit> hit = leaves.identify(block);
            if (hit.isEmpty()) {
                continue;
            }
            it.remove();
            if (protection.allowsExplosion(block, origin, source)) {
                BlockData data = block.getBlockData();
                block.setType(data instanceof Leaves wet && wet.isWaterlogged() ? Material.WATER : Material.AIR, false);
                drops.drop(block, hit.get().leaves(), null);
                gone.accept(block);
            }
        }
    }
}
