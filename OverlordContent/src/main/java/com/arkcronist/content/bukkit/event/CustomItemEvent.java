package com.arkcronist.content.bukkit.event;

import com.arkcronist.content.bukkit.item.CustomItem;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Something a player did with a custom item.
 *
 * <p>This is the extension point for item behaviour. The listeners in this plugin only recognise
 * the item and fire one of these; what the item actually does - a staff casting on right click, a
 * hammer breaking a 3x3 - belongs in a listener for the event, here or in another plugin, keyed on
 * {@link #getItem()}. A new kind of interaction gets a new subclass and one more handler in a
 * listener.</p>
 */
public abstract class CustomItemEvent extends PlayerEvent {

    private final CustomItem item;
    private final ItemStack stack;

    protected CustomItemEvent(Player player, CustomItem item, ItemStack stack) {
        super(player);
        this.item = item;
        this.stack = stack;
    }

    /** Which custom item it is. Compare {@code getItem().id()} to act on one in particular. */
    public CustomItem getItem() {
        return item;
    }

    /** The stack itself, live: changes to it land in the player's inventory. */
    public ItemStack getStack() {
        return stack;
    }
}
