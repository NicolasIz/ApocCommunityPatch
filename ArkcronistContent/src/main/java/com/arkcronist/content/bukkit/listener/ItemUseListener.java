package com.arkcronist.content.bukkit.listener;

import com.arkcronist.content.bukkit.event.CustomItemUseEvent;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Optional;

/**
 * Clicks with a custom item become a {@link CustomItemUseEvent}, and its outcome decides whether
 * the base material's own use happens.
 */
public final class ItemUseListener implements Listener {

    private final ItemFactory items;

    public ItemUseListener(ItemFactory items) {
        this.items = items;
    }

    // Not ignoreCancelled: a click in the air arrives already "cancelled", because there is no block
    // to use, and would be skipped. What matters here is only whether the item use is still allowed.
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL || event.getHand() == null
                || event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        Optional<CustomItem> item = items.identify(event.getItem());
        if (item.isEmpty()) {
            return;
        }

        CustomItemUseEvent use = new CustomItemUseEvent(event.getPlayer(), item.get(), event.getItem(),
                event.getAction(), event.getHand(), event.getClickedBlock());
        if (!use.callEvent()) {
            event.setUseItemInHand(Event.Result.DENY);
        }
    }
}
