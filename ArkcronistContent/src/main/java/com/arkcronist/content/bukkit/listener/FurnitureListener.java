package com.arkcronist.content.bukkit.listener;

import com.arkcronist.content.bukkit.furniture.FurnitureService;
import com.arkcronist.content.bukkit.furniture.SeatService;
import com.arkcronist.content.bukkit.furniture.StorageService;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.protection.Interaction;
import com.arkcronist.content.bukkit.protection.Protection;
import com.arkcronist.content.core.definition.Placement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Placing, breaking, using and tidying furniture.
 *
 * <p>A right click sits on a seat or opens a storage furniture's inventory, once every installed
 * protection plugin agrees - see {@link Protection}; a click on an invisible block reaches none of
 * them on its own.</p>
 *
 * <p>Breaking has two ways in. In creative a barrier breaks like any block and fires a
 * {@link BlockBreakEvent}; in survival neither support can be mined - a barrier is unbreakable and a
 * light block cannot even be targeted - so a punch at the furniture is turned into a
 * {@code BlockBreakEvent} here. Either way the event is fired for real, so protection plugins decide
 * as they would for any block, and the removal itself happens in one place, {@link #onBreak}.</p>
 */
public final class FurnitureListener implements Listener {

    private final Plugin plugin;
    private final FurnitureService furniture;
    private final SeatService seats;
    private final StorageService storage;
    private final Protection protection;
    private final ItemFactory items;

    public FurnitureListener(Plugin plugin, FurnitureService furniture, SeatService seats, StorageService storage,
                             Protection protection, ItemFactory items) {
        this.plugin = plugin;
        this.furniture = furniture;
        this.seats = seats;
        this.storage = storage;
        this.protection = protection;
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
            if (spec.storage() != null) {
                storage.placed(block);
            }
        }
    }

    /**
     * A right click on a seat sits on it, and on storage furniture opens it; sneaking, the click is
     * left to vanilla, to place a block against the furniture. Any other furniture does not react as
     * its support block would: holding a light item, a click on a light block steps its light level.
     *
     * <p>A light-block support cannot be clicked at all, so a click that lands on the floor behind
     * it, or in the air, is followed along the line of sight to find it - the same way a punch is.</p>
     */
    // Not ignoreCancelled: a click at the air arrives already cancelled.
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }
        Block clicked = event.getClickedBlock();
        boolean clickedFurniture = clicked != null && furniture.identify(clicked).isPresent();
        Optional<Block> support = clickedFurniture ? Optional.of(clicked) : furniture.target(event.getPlayer());
        Optional<CustomItem> item = support.flatMap(furniture::item);
        Placement.Furniture definition = item.map(CustomItem::placement).filter(Placement.Furniture.class::isInstance)
                .map(Placement.Furniture.class::cast).orElse(null);
        boolean interactive = definition != null && (definition.seat() != null || definition.storage() != null);
        if (interactive && !event.getPlayer().isSneaking()) {
            // Both hands' clicks are taken, so the off hand does not place a block against the furniture.
            event.setCancelled(true);
            Player player = event.getPlayer();
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            if (definition.seat() != null && protection.check(player, support.get(), Interaction.SIT)) {
                float yaw = furniture.facing(support.get()).orElse(player.getLocation().getYaw());
                seats.sit(player, support.get(), definition.seat(), yaw);
            } else if (definition.storage() != null && protection.check(player, support.get(), Interaction.CONTAINER)) {
                storage.open(player, support.get(), item.get().id(), definition.storage(),
                        title(item.get(), definition.storage()));
            }
            return;
        }
        if (action == Action.RIGHT_CLICK_BLOCK && clickedFurniture) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    private static Component title(CustomItem item, Placement.Storage spec) {
        if (spec.title() != null) {
            return MiniMessage.miniMessage().deserialize(spec.title());
        }
        return item.displayName() != null ? item.displayName() : Component.translatable("container.chest");
    }

    // ---------------------------------------------------------------- storage inventories

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStorageClick(InventoryClickEvent event) {
        storage.changed(event.getView().getTopInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStorageDrag(InventoryDragEvent event) {
        storage.changed(event.getView().getTopInventory());
    }

    /** Fires on logout and death too, so every way of leaving a storage inventory saves it. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onStorageClose(InventoryCloseEvent event) {
        storage.closed(event.getPlayer(), event.getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        storage.worldUnloaded(event.getWorld());
    }

    // ---------------------------------------------------------------- seats

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        seats.dismounted(event.getEntity(), event.getDismounted());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Entity vehicle = event.getPlayer().getVehicle();
        if (vehicle != null && seats.isSeat(vehicle)) {
            vehicle.eject();
            vehicle.remove();
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
        if (!protection.check(event.getPlayer(), block, Interaction.BREAK)) {
            return;
        }
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
        furniture.identify(block).ifPresent(id -> {
            seats.release(block);
            storage.broken(block);
            furniture.remove(block, id, player.getGameMode() != GameMode.CREATIVE);
        });
    }

    /**
     * Displays load with their chunk, a moment after its blocks. One whose support block is gone was
     * left behind while it was not loaded, and is removed now rather than floating forever.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        List<Entity> displays = new ArrayList<>();
        for (Entity entity : event.getEntities()) {
            if (furniture.isOrphan(entity)) {
                entity.remove();
            } else if (entity instanceof ItemDisplay) {
                displays.add(entity);
            }
        }
        if (displays.isEmpty()) {
            return;
        }
        // A tick later, so ModelEngine gets to restore its own saved models first.
        plugin.getServer().getScheduler().runTask(plugin, () -> displays.stream()
                .filter(Entity::isValid)
                .forEach(furniture::restoreModel));
    }
}
