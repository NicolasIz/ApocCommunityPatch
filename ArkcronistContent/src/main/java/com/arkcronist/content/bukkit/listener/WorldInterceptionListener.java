package com.arkcronist.content.bukkit.listener;

import com.arkcronist.content.bukkit.event.CustomItemBreakBlockEvent;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Where custom items meet the world's blocks.
 *
 * <p>The pattern for any new interception is the one used here: find the stack involved, ask
 * {@link ItemFactory#identify} whether it is custom, and either enforce an
 * {@code ItemBehaviour} flag or fire a {@code CustomItemEvent} subclass and honour its outcome.
 * Candidates that fit straight in: projectiles launched by custom items, custom items dropped or
 * picked up, and custom blocks placed from custom items (a note block or mushroom state per block,
 * restored when broken).</p>
 */
public final class WorldInterceptionListener implements Listener {

    private final ItemFactory items;

    public WorldInterceptionListener(ItemFactory items) {
        this.items = items;
    }

    /**
     * A custom item made from a block material would otherwise be placed as that plain vanilla
     * block, losing its identity for good. Refused unless the item says it is {@code placeable}.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Optional<CustomItem> item = items.identify(event.getItemInHand());
        if (item.isPresent() && !item.get().behaviour().placeable()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        Optional<CustomItem> item = items.identify(tool);
        if (item.isEmpty()) {
            return;
        }
        CustomItemBreakBlockEvent broken = new CustomItemBreakBlockEvent(event.getPlayer(), item.get(), tool,
                event.getBlock());
        if (!broken.callEvent()) {
            event.setCancelled(true);
        }
    }
}
