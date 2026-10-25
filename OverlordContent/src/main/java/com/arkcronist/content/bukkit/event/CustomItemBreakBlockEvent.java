package com.arkcronist.content.bukkit.event;

import com.arkcronist.content.bukkit.item.CustomItem;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * A player broke a block while holding a custom item in the main hand.
 *
 * <p>The first of the world interceptions: the hook a custom tool hangs off. Cancelling it leaves
 * the block in place.</p>
 */
public final class CustomItemBreakBlockEvent extends CustomItemEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Block block;
    private boolean cancelled;

    public CustomItemBreakBlockEvent(Player player, CustomItem item, ItemStack stack, Block block) {
        super(player, item, stack);
        this.block = block;
    }

    public Block getBlock() {
        return block;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
