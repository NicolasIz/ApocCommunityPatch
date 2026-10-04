package com.arkcronist.content.bukkit.listener;

import com.arkcronist.content.bukkit.block.CustomBlock;
import com.arkcronist.content.bukkit.block.CustomBlockService;
import com.arkcronist.content.bukkit.hooks.HookManager;
import com.arkcronist.content.bukkit.hooks.SkillXpHook;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.protection.Protection;
import com.arkcronist.content.core.definition.Placement;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Keeps custom blocks what they are for as long as they stand.
 *
 * <p>A custom block is a note block state, and vanilla changes note block states all the time:
 * the instrument follows the block underneath, a right click tunes it, redstone powers it. Every one
 * of those would turn it into a different custom block, so while any custom block is loaded, all of
 * them are stopped - for vanilla note blocks too, which are held in the one reserved state. The
 * cost is that vanilla note blocks stop being instruments: they keep their look and play their
 * note when hit, and nothing else. With no custom blocks defined, this listener does nothing.</p>
 *
 * <p>Changes that are final - setting the state, recording the block, dropping the item - happen at
 * MONITOR, once no other plugin can still cancel the event; the decisions that must precede them
 * (no vanilla drop) happen at HIGH.</p>
 *
 * <p>Placing and breaking go through vanilla's own events, which protection plugins already judge.
 * The one change this listener makes itself - breaking custom blocks an explosion reached - is put
 * to them again, block by block, through {@link Protection}.</p>
 */
public final class CustomBlockListener implements Listener {

    private final CustomBlockService blocks;
    private final ItemFactory items;
    private final Protection protection;
    private final HookManager hooks;

    public CustomBlockListener(CustomBlockService blocks, ItemFactory items, Protection protection, HookManager hooks) {
        this.blocks = blocks;
        this.items = items;
        this.protection = protection;
        this.hooks = hooks;
    }

    /** A custom block item whose block got no state (all 799 taken) cannot be placed at all. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void refuseStatelessBlocks(BlockPlaceEvent event) {
        Optional<CustomItem> item = items.identify(event.getItemInHand());
        if (item.isPresent() && item.get().placement() instanceof Placement.Block
                && blocks.forItem(item.get()).isEmpty()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() != Material.NOTE_BLOCK || !blocks.active()) {
            return;
        }
        Optional<CustomBlock> custom = items.identify(event.getItemInHand()).flatMap(blocks::forItem);
        if (custom.isPresent()) {
            blocks.place(block, custom.get());
        } else {
            blocks.pinVanilla(block);
        }
    }

    /** No tuning: a right click would move the block to the next note - another custom block. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || !blocks.active()) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked != null && clicked.getType() == Material.NOTE_BLOCK) {
            // Only the block's own reaction is denied; placing a block against it still works.
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    /** A custom block is silent: hitting it must not play a harp note. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNote(NotePlayEvent event) {
        if (blocks.identify(event.getBlock()).isPresent()) {
            event.setCancelled(true);
        }
    }

    /**
     * Neighbour and shape updates are what re-derive the instrument and the powered flag, so for
     * note blocks they are refused outright. When the change came from directly above or below -
     * the case a client predicts on its own - the real state is sent back to undo the prediction.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.NOTE_BLOCK || !blocks.active()) {
            return;
        }
        event.setCancelled(true);
        Block source = event.getSourceBlock();
        if (source.getX() == block.getX() && source.getZ() == block.getZ() && source.getY() != block.getY()) {
            blocks.resync(block);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void noVanillaDrop(BlockBreakEvent event) {
        if (blocks.identify(event.getBlock()).isPresent()) {
            event.setDropItems(false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        boolean survival = player.getGameMode() != GameMode.CREATIVE;
        blocks.identify(event.getBlock()).ifPresent(custom -> {
            blocks.forget(event.getBlock(), custom, survival);
            if (survival) {
                hooks.skillXp(player, SkillXpHook.Source.BLOCK, custom.id(), custom.placement().skillXp());
                hooks.jobRewards(player, event.getBlock(), custom.id(), custom.placement().jobs());
            }
        });
    }

    /** Note blocks are wood: fire would take custom blocks with no drop and no record removed. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (blocks.identify(event.getBlock()).isPresent()) {
            event.setCancelled(true);
        }
    }

    // HIGHEST: this breaks blocks itself, so it runs as late as it can while the list may still be
    // changed, leaving other plugins every chance to cancel the explosion first.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        explode(event.blockList(), event.getLocation(), event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        explode(event.blockList(), event.getBlock().getLocation(), null);
    }

    /**
     * An explosion would drop a plain note block. Custom blocks are taken out of its list and broken
     * here instead, dropping their own item and leaving the record - each only if every protection
     * plugin lets this explosion destroy it; otherwise it is taken out of the list and left standing.
     */
    private void explode(List<Block> destroyed, Location origin, @Nullable Entity source) {
        if (!blocks.active()) {
            return;
        }
        for (Iterator<Block> it = destroyed.iterator(); it.hasNext(); ) {
            Block block = it.next();
            Optional<CustomBlock> custom = blocks.identify(block);
            if (custom.isPresent()) {
                it.remove();
                if (protection.allowsExplosion(block, origin, source)) {
                    blocks.forget(block, custom.get(), true);
                    block.setType(Material.AIR, false);
                }
            }
        }
    }
}
