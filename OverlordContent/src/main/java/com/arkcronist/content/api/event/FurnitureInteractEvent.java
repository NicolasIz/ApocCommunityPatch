package com.arkcronist.content.api.event;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 5.4: a player right-clicks a piece of furniture with the main hand - before it seats them, opens,
 * plays or does whatever it does; also for furniture that does nothing of its own, so a plugin can
 * give it something to do. The block is the one of its blocks that was clicked.
 */
public final class FurnitureInteractEvent extends BlockEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String id;
    private boolean cancelled;

    public FurnitureInteractEvent(Block block, Player player, String id) {
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

    /** Cancelled, the piece does nothing, and nothing else gets the click. */
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
