package com.arkcronist.content.bukkit.gun;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * How players use guns: right click fires, the swap-hands key (F) reloads, and anything that
 * moves the gun - another hotbar slot, the inventory, dropping it, dying - writes its rounds back
 * onto it and calls off a reload.
 */
public final class GunListener implements Listener {

    private final Plugin plugin;
    private final GunService guns;

    public GunListener(Plugin plugin, GunService guns) {
        this.plugin = plugin;
        this.guns = guns;
    }

    // Not ignoreCancelled: a click in the air arrives "cancelled", having no block to use.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (event.getHand() != EquipmentSlot.HAND
                || action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (guns.gun(player.getInventory().getItemInMainHand()).isEmpty()) {
            return;
        }
        Block clicked = event.getClickedBlock();
        // Doors, chests and buttons still work - a sneaking player shoots at them instead.
        if (action == Action.RIGHT_CLICK_BLOCK && clicked != null && usable(clicked.getType())
                && !player.isSneaking() && event.useInteractedBlock() != Event.Result.DENY) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        guns.trigger(player);
    }

    /**
     * Whether a right click on this block does something of its own worth not shooting over.
     * Bukkit's list counts stairs and fences, which only do something to a leash or a seat plugin.
     */
    @SuppressWarnings("deprecation")
    private static boolean usable(Material type) {
        return type.isInteractable() && !Tag.STAIRS.isTagged(type) && !Tag.FENCES.isTagged(type);
    }

    /** Right clicking a mob or a player with a gun shoots it, rather than trading or mounting. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || guns.gun(event.getPlayer().getInventory().getItemInMainHand()).isEmpty()) {
            return;
        }
        event.setCancelled(true);
        guns.trigger(event.getPlayer());
    }

    /** F reloads the gun in hand instead of swapping it into the other hand. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (guns.gun(player.getInventory().getItemInMainHand()).isPresent()) {
            event.setCancelled(true);
            guns.reload(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        guns.cancelReload(player);
        guns.flush(player);
        // What the ammunition placeholders show follows the new slot, once the server has moved to it.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                guns.updateView(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            guns.cancelReload(player);
            guns.flush(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            guns.flush(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            guns.flush(player);
        }
    }

    /** The gun has already left the inventory when this fires: its rounds go onto the dropped stack. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (guns.flushInto(player, dropped)) {
            event.getItemDrop().setItemStack(dropped);
            guns.cancelReload(player);
        }
        guns.updateView(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        guns.cancelReload(player);
        for (ItemStack drop : event.getDrops()) {
            if (guns.flushInto(player, drop)) {
                return;
            }
        }
        guns.flush(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        guns.updateView(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        guns.forget(event.getPlayer());
    }

    @EventHandler
    public void onSave(WorldSaveEvent event) {
        guns.flushAll();
    }
}
