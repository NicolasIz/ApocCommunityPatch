package com.arkcronist.content.bukkit.listener;

import com.arkcronist.content.bukkit.block.ShapedBlockRegistry;
import com.arkcronist.content.bukkit.hooks.HookManager;
import com.arkcronist.content.bukkit.hooks.SkillXpHook;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.protection.Protection;
import com.arkcronist.content.core.definition.Placement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * 5.3: keeps shaped custom blocks - slabs, stairs, transparent blocks, doors, trapdoors - what they
 * are, and keeps vanilla's waxed copper out of their way.
 *
 * <p>A shaped block is a waxed copper block the pack draws as something else, so most of what it
 * does is vanilla's: placing (a slab doubles, stairs turn their corners, a door takes two blocks),
 * opening a door or trapdoor, redstone, pistons pushing a slab. What this listener adds:</p>
 * <ul>
 *   <li>its drops are its own item - breaking a double slab gives two, as vanilla's rules decide,
 *       needing a pickaxe as copper does;</li>
 *   <li>the copper underneath never changes: an axe does not scrape the wax off (it would become
 *       copper that weathers, drawn as vanilla copper), and honeycomb does not wax copper into a
 *       block that draws a custom one;</li>
 *   <li>vanilla's own waxed copper of a block taken cannot be placed - it would be drawn as the
 *       custom block, and break into it - nor crafted ({@link com.arkcronist.content.bukkit.block.CopperRecipes});</li>
 *   <li>explosions break it as a player would, dropping its item - each asked of the protection
 *       plugins first, as for note block blocks - and so do a wither, a door losing the block under
 *       it and anything else vanilla destroys a block with.</li>
 * </ul>
 * <p>With no shaped block loaded, every handler returns at once.</p>
 */
public final class ShapedBlockListener implements Listener {

    private static final Component RESERVED = Component.text(
            "This copper block draws a custom block on this server.", NamedTextColor.GRAY);

    private final ShapedBlockRegistry blocks;
    private final ItemFactory items;
    private final Protection protection;
    private final HookManager hooks;

    public ShapedBlockListener(ShapedBlockRegistry blocks, ItemFactory items, Protection protection, HookManager hooks) {
        this.blocks = blocks;
        this.items = items;
        this.protection = protection;
        this.hooks = hooks;
    }

    // ---------------------------------------------------------------- placing

    /**
     * Only the custom block's own item places its copper block. A stack made while the block was
     * drawn through another copper block - or had none - is made of the right one and the place
     * refused, once: the next click places it.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (blocks.isEmpty()) {
            return;
        }
        ItemStack hand = event.getItemInHand();
        Optional<CustomItem> held = items.identify(hand);
        if (held.isPresent() && held.get().placement() instanceof Placement.Block block && block.shape().copper()
                && hand.getType() != held.get().material() && blocks.get(held.get().id()).isPresent()) {
            event.setCancelled(true);
            ItemStack remade = hand.withType(held.get().material());
            items.repair(remade);
            event.getPlayer().getInventory().setItem(event.getHand(), remade);
            return;
        }
        Optional<CustomItem> drawn = blocks.byBase(event.getBlockPlaced().getType());
        if (drawn.isEmpty() || held.map(item -> item.id().equals(drawn.get().id())).orElse(false)) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendActionBar(RESERVED);
    }

    // ---------------------------------------------------------------- the copper underneath

    /**
     * Axes scrape wax off, and honeycomb puts it on - both through this event on Paper. Neither may
     * touch a custom block's copper, nor make one.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChange(EntityChangeBlockEvent event) {
        if (blocks.isEmpty()) {
            return;
        }
        Block block = event.getBlock();
        Material from = block.getType();
        Material to = event.getBlockData().getMaterial();
        if (from == to) {
            return;
        }
        // Taken away (a wither) it drops its own item, through onDestroy; changed into anything else, never.
        if (blocks.claimed(from)) {
            if (!to.isAir()) {
                event.setCancelled(true);
            }
            return;
        }
        if (blocks.claimed(to)) {
            event.setCancelled(true);
        }
    }

    /**
     * The same refusal before the item is used, should a version reach the block without the event
     * above: an axe on a custom block's copper, honeycomb on copper that waxing would make one.
     * Only the item's use is denied - a door still opens, a block still goes against it.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || blocks.isEmpty() || event.getItem() == null) {
            return;
        }
        Block clicked = event.getClickedBlock();
        Material held = event.getItem().getType();
        if (clicked == null) {
            return;
        }
        if ((Tag.ITEMS_AXES.isTagged(held) && blocks.claimed(clicked.getType()))
                || (held == Material.HONEYCOMB && waxesIntoCustom(clicked.getType()))) {
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    /** A dispenser waxes the copper in front of it with honeycomb. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (blocks.isEmpty() || event.getItem().getType() != Material.HONEYCOMB
                || !(event.getBlock().getBlockData() instanceof Directional facing)) {
            return;
        }
        if (waxesIntoCustom(event.getBlock().getRelative(facing.getFacing()).getType())) {
            event.setCancelled(true);
        }
    }

    /** Whether honeycomb on {@code copper} would make the copper block of a custom block. */
    private boolean waxesIntoCustom(Material copper) {
        if (copper.name().startsWith("WAXED_")) {
            return false;
        }
        Material waxed = Material.matchMaterial("WAXED_" + copper.name());
        return waxed != null && blocks.claimed(waxed);
    }

    // ---------------------------------------------------------------- breaking

    /**
     * What vanilla drops for the copper block - none without a pickaxe, two for a double slab, one
     * for both halves of a door - becomes the custom block's item, so its rules hold. A door's drop
     * comes from its lower half even when the upper one is broken, and is gathered here with it.
     * Only copper of the broken block, or of a block it brought down, is turned: the contents of a
     * broken chest stay what they are.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent event) {
        if (blocks.isEmpty() || event.getBlockState() instanceof Container) {
            return;
        }
        for (Iterator<Item> it = event.getItems().iterator(); it.hasNext(); ) {
            Item dropped = it.next();
            ItemStack stack = dropped.getItemStack();
            Optional<CustomItem> drawn = blocks.byBase(stack.getType());
            if (drawn.isEmpty() || items.hasId(stack)) {
                continue;
            }
            if (((Placement.Block) drawn.get().placement()).dropSelf()) {
                dropped.setItemStack(items.create(drawn.get(), stack.getAmount()));
            } else {
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (blocks.isEmpty() || event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            return;
        }
        Block block = event.getBlock();
        blocks.byBase(block.getType()).ifPresent(custom -> {
            // A door's upper half is broken with the lower one: rewarded once, for the lower.
            if (block.getBlockData() instanceof Door door && door.getHalf() == Bisected.Half.TOP
                    && block.getRelative(BlockFace.DOWN).getType() == block.getType()) {
                return;
            }
            Placement.Block placement = (Placement.Block) custom.placement();
            hooks.skillXp(event.getPlayer(), SkillXpHook.Source.BLOCK, custom.id(), placement.skillXp());
            hooks.jobRewards(event.getPlayer(), block, custom.id(), placement.jobs());
        });
    }

    /**
     * Vanilla destroys a block that lost what held it - a door whose block underneath is gone, the
     * other half of a broken door, a block set with {@code /setblock ... destroy} or taken by a
     * wither - and drops its copper. It drops its own item instead: two for a double slab, one for a
     * door from its lower half.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDestroy(BlockDestroyEvent event) {
        if (blocks.isEmpty() || !event.willDrop()) {
            return;
        }
        Block block = event.getBlock();
        Optional<CustomItem> drawn = blocks.byBase(block.getType());
        if (drawn.isEmpty()) {
            return;
        }
        event.setWillDrop(false);
        int count = dropped(block.getBlockData());
        if (count > 0 && ((Placement.Block) drawn.get().placement()).dropSelf()) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), items.create(drawn.get(), count));
        }
    }

    /** What vanilla drops of a copper block of a custom one: two for a double slab, none for a door's upper half. */
    private static int dropped(BlockData data) {
        if (data instanceof Slab slab && slab.getType() == Slab.Type.DOUBLE) {
            return 2;
        }
        return data instanceof Door door && door.getHalf() == Bisected.Half.TOP ? 0 : 1;
    }

    /** A piston would break a custom door into vanilla's copper door item: it does not move. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!blocks.isEmpty() && movesCustomDoor(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!blocks.isEmpty() && movesCustomDoor(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    private boolean movesCustomDoor(List<Block> moved) {
        for (Block block : moved) {
            if (Tag.DOORS.isTagged(block.getType()) && blocks.claimed(block.getType())) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        explode(event.blockList(), event.getLocation(), event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        explode(event.blockList(), event.getBlock().getLocation(), null);
    }

    /**
     * An explosion would drop vanilla's copper. Custom blocks are taken out of its list and broken
     * here, dropping their own item - each only if the protection plugins let this explosion destroy
     * it; otherwise it stays. Like note block blocks, they always drop.
     */
    private void explode(List<Block> destroyed, Location origin, @Nullable Entity source) {
        if (blocks.isEmpty()) {
            return;
        }
        for (Iterator<Block> it = destroyed.iterator(); it.hasNext(); ) {
            Block block = it.next();
            Optional<CustomItem> drawn = blocks.byBase(block.getType());
            if (drawn.isPresent()) {
                it.remove();
                if (protection.allowsExplosion(block, origin, source)) {
                    breakAway(block, drawn.get(), true);
                }
            }
        }
    }

    /**
     * Takes a shaped block away as breaking it would: both halves of a door, water left where it
     * was waterlogged, and its item dropped - two for a double slab, one for a door.
     */
    private void breakAway(Block block, CustomItem custom, boolean drop) {
        BlockData data = block.getBlockData();
        if (data.getMaterial() != custom.material()) {
            return;
        }
        int count = Math.max(1, dropped(data));
        if (data instanceof Door door) {
            Block other = block.getRelative(door.getHalf() == Bisected.Half.TOP ? BlockFace.DOWN : BlockFace.UP);
            if (other.getType() == block.getType()) {
                other.setType(Material.AIR, false);
            }
        }
        block.setType(data instanceof Waterlogged wet && wet.isWaterlogged() ? Material.WATER : Material.AIR, false);
        if (drop && ((Placement.Block) custom.placement()).dropSelf()) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), items.create(custom, count));
        }
    }
}
