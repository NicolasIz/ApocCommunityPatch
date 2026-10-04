package com.arkcronist.content.bukkit.hooks.zauctionhouse;

import com.arkcronist.content.bukkit.item.ItemFactory;
import fr.maxlego08.zauctionhouse.api.event.events.remove.RemoveEvent;
import fr.maxlego08.zauctionhouse.api.event.events.sell.AuctionPreSellEvent;
import fr.maxlego08.zauctionhouse.api.item.Item;
import fr.maxlego08.zauctionhouse.api.item.items.AuctionItem;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

/**
 * zAuctionHouse (v4): listed and bought items keep this plugin's look.
 *
 * <p>zAuctionHouse stores an item whole - on 1.20.5 and later, as the item's full data, components
 * and tags included - so a ruby listed is a ruby bought, drawn with its {@code item_model} in the
 * auction menus too. This hook is the guard for the case where something on the way rebuilt the
 * stack and kept only the plugin's id tag: as an item is listed, and again as it leaves the auction
 * house - bought, taken back, expired - its look is put back from the item's definition.</p>
 */
public final class ZAuctionHouseHook implements Listener {

    private final ItemFactory items;

    public ZAuctionHouseHook(ItemFactory items) {
        this.items = items;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSell(AuctionPreSellEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack != null && items.identify(stack).isPresent()) {
            ItemStack listed = stack.clone();
            if (items.repair(listed)) {
                event.setItemStack(listed);
            }
        }
    }

    /** Bought, taken back by its seller, or collected after it expired. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeave(RemoveEvent event) {
        Item item = event.getItem();
        if (item instanceof AuctionItem auction) {
            for (ItemStack stack : auction.getItemStacks()) {
                items.repair(stack);
            }
        }
    }
}
