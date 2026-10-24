package com.arkcronist.content.bukkit.vehicle;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * 4.0: a vehicle's item put down with a right click on a block; a right click on a vehicle gets in,
 * a sneaking one picks it up; vehicles come and go with their chunks.
 */
public final class VehicleListener implements Listener {

    private final VehicleService vehicles;
    private final ItemFactory factory;

    public VehicleListener(VehicleService vehicles, ItemFactory factory) {
        this.vehicles = vehicles;
        this.factory = factory;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null) {
            return;
        }
        ItemStack hand = event.getItem();
        Optional<CustomItem> item = factory.identify(hand);
        if (item.isEmpty() || item.get().definition().vehicle() == null) {
            return;
        }
        // A vehicle's item does nothing else when used on a block.
        event.setCancelled(true);
        if (vehicles.place(event.getPlayer(), item.get(), event.getClickedBlock(), event.getBlockFace())
                && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            hand.subtract(1);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            if (vehicles.isPart(event.getRightClicked())) {
                event.setCancelled(true);
            }
            return;
        }
        if (vehicles.clicked(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        vehicles.chunkLoaded(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        vehicles.chunkUnloading(event.getChunk());
    }
}
