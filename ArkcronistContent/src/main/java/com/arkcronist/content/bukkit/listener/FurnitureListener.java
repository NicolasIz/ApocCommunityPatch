package com.arkcronist.content.bukkit.listener;

import com.arkcronist.content.bukkit.furniture.FurnitureService;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.core.definition.Placement;
import org.bukkit.GameMode;
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
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;

/**
 * Placing, breaking and tidying furniture.
 *
 * <p>Breaking has two ways in. In creative a barrier breaks like any block and fires a
 * {@link BlockBreakEvent}; in survival neither support can be mined - a barrier is unbreakable and a
 * light block cannot even be targeted - so a punch at the furniture is turned into a
 * {@code BlockBreakEvent} here. Either way the event is fired for real, so protection plugins decide
 * as they would for any block, and the removal itself happens in one place, {@link #onBreak}.</p>
 */
public final class FurnitureListener implements Listener {

    private final FurnitureService furniture;
    private final ItemFactory items;

    public FurnitureListener(FurnitureService furniture, ItemFactory items) {
        this.furniture = furniture;
        this.items = items;
    }

    /** A light-block support is replaceable, like air: a block placed into it would erase it. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void protectLightSupports(BlockPlaceEvent event) {
        if (FurnitureService.isSupport(event.getBlockReplacedState().getType())
                && furniture.claims(event.getBlockPlaced())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Optional<CustomItem> item = items.identify(event.getItemInHand());
        if (item.isEmpty() || !(item.get().placement() instanceof Placement.Furniture spec)) {
            return;
        }
        Block block = event.getBlockPlaced();
        if (block.getType() == FurnitureService.material(spec.support())) {
            furniture.place(block, event.getPlayer(), item.get(), spec);
        }
    }

    /**
     * Furniture does not react to a right click as its support block would: holding a light item,
     * a click on a light block steps its light level. This is where sitting on a chair would go.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && clicked != null
                && furniture.identify(clicked).isPresent()) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    // Not ignoreCancelled: a punch at the air arrives already cancelled.
    @EventHandler(priority = EventPriority.HIGH)
    public void onPunch(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.LEFT_CLICK_AIR
                || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        // Adventure and spectator players cannot break blocks, and this is a block break.
        GameMode mode = event.getPlayer().getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.CREATIVE) {
            return;
        }
        Block clicked = event.getClickedBlock();
        Optional<Block> target = clicked != null && furniture.identify(clicked).isPresent()
                ? Optional.of(clicked)
                : furniture.target(event.getPlayer());
        if (target.isEmpty()) {
            return;
        }
        // Keeps vanilla from also breaking a clicked barrier in creative: there is one path, below.
        event.setCancelled(true);

        Block block = target.get();
        BlockBreakEvent breakEvent = new BlockBreakEvent(block, event.getPlayer());
        breakEvent.setDropItems(false);
        if (breakEvent.callEvent()) {
            // onBreak has run as part of the event; what is left is the block.
            block.setType(Material.AIR);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        furniture.identify(block).ifPresent(id -> furniture.remove(block, id, player.getGameMode() != GameMode.CREATIVE));
    }

    /**
     * Displays load with their chunk, a moment after its blocks. One whose support block is gone was
     * left behind while it was not loaded, and is removed now rather than floating forever.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (furniture.isOrphan(entity)) {
                entity.remove();
            }
        }
    }
}
