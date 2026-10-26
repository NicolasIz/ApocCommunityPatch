package com.arkcronist.content.bukkit.api;

import com.arkcronist.content.api.event.CustomBlockBreakEvent;
import com.arkcronist.content.api.event.CustomBlockPlaceEvent;
import com.arkcronist.content.api.event.FurnitureBreakEvent;
import com.arkcronist.content.api.event.FurniturePlaceEvent;
import com.arkcronist.content.bukkit.ArkContentPlugin;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.core.definition.Placement;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Optional;

/**
 * 5.4: the API's place and break events, told from the vanilla ones: after protection plugins have
 * had their say (they decide at lower priorities), before this plugin records or drops anything
 * (it does so at MONITOR, for events not cancelled) - so cancelling one cancels the vanilla event.
 */
public final class ApiEventBridge implements Listener {

    private final ArkContentPlugin plugin;

    public ApiEventBridge(ArkContentPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Optional<CustomItem> item = plugin.itemFactory().identify(event.getItemInHand());
        if (item.isEmpty()) {
            return;
        }
        Block block = event.getBlockPlaced();
        boolean cancelled = switch (item.get().placement()) {
            case Placement.Block ignored -> !new CustomBlockPlaceEvent(block, event.getPlayer(), item.get().id()).callEvent();
            case Placement.Furniture ignored -> !new FurniturePlaceEvent(block, event.getPlayer(), item.get().id()).callEvent();
            case null, default -> false;
        };
        if (cancelled) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Optional<String> furniture = plugin.furniture().identify(block);
        if (furniture.isPresent()) {
            if (!new FurnitureBreakEvent(block, event.getPlayer(), furniture.get()).callEvent()) {
                event.setCancelled(true);
            }
            return;
        }
        Optional<String> cube = plugin.customBlocks() == null ? Optional.empty()
                : plugin.customBlocks().identify(block).map(custom -> custom.id());
        if (cube.isEmpty()) {
            cube = plugin.shapedBlocks().byBase(block.getType()).map(CustomItem::id);
        }
        if (cube.isPresent() && !new CustomBlockBreakEvent(block, event.getPlayer(), cube.get()).callEvent()) {
            event.setCancelled(true);
        }
    }
}
