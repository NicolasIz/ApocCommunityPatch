package com.arkcronist.content.api.event;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 5.4: a player places a custom block - after protection plugins let them, before it is recorded.
 * The block is where it goes.
 */
public final class CustomBlockPlaceEvent extends BlockEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String id;
    private boolean cancelled;

    public CustomBlockPlaceEvent(Block block, Player player, String id) {
        super(block);
        this.player = player;
        this.id = id;
    }

    public Player getPlayer() {
        return player;
    }

    /** The custom block's id, {@code namespace:id}. */
    public String getId() {
        return id;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /** Cancelled, the block is not placed, and the item stays in the hand. */
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
