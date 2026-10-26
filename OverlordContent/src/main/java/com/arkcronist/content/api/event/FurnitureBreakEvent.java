package com.arkcronist.content.api.event;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 5.4: a player breaks a piece of furniture - after protection plugins let them. The block is the
 * one of its blocks that was hit.
 */
public final class FurnitureBreakEvent extends BlockEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String id;
    private boolean cancelled;

    public FurnitureBreakEvent(Block block, Player player, String id) {
        super(block);
        this.player = player;
        this.id = id;
    }

    public Player getPlayer() {
        return player;
    }

    /** The furniture's id, {@code namespace:id}. */
    public String getId() {
        return id;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /** Cancelled, the piece stays. */
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
