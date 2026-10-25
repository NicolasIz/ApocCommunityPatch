package com.arkcronist.content.bukkit.event;

import com.arkcronist.content.bukkit.item.CustomItem;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A player clicked with a custom item, in the air or on a block.
 *
 * <p>Cancelling it cancels what the base material would have done - eating, throwing, placing,
 * drawing a bow. It does not stop the click reaching the block: a chest still opens. It starts out
 * cancelled when the item's {@code behaviour.cancel-vanilla-use} is true, so a listener can let the
 * vanilla use through case by case with {@code setCancelled(false)}.</p>
 */
public final class CustomItemUseEvent extends CustomItemEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Action action;
    private final EquipmentSlot hand;
    private final Block clickedBlock;
    private boolean cancelled;

    public CustomItemUseEvent(Player player, CustomItem item, ItemStack stack, Action action,
                              EquipmentSlot hand, @Nullable Block clickedBlock) {
        super(player, item, stack);
        this.action = action;
        this.hand = hand;
        this.clickedBlock = clickedBlock;
        this.cancelled = item.behaviour().cancelVanillaUse();
    }

    public Action getAction() {
        return action;
    }

    public EquipmentSlot getHand() {
        return hand;
    }

    /** The block clicked, or null for a click in the air. */
    public @Nullable Block getClickedBlock() {
        return clickedBlock;
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
