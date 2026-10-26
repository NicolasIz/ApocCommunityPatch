package com.arkcronist.content.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * 5.4: the content has been (re)loaded and is live - items, blocks, furniture and the resource pack.
 * The time to look up ids kept in another plugin's configuration. Main thread.
 */
public final class OverlordContentLoadedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean first;
    private final int items;
    private final int blocks;
    private final boolean packChanged;

    public OverlordContentLoadedEvent(boolean first, int items, int blocks, boolean packChanged) {
        this.first = first;
        this.items = items;
        this.blocks = blocks;
        this.packChanged = packChanged;
    }

    /** Whether this is the first load since the server started. */
    public boolean isFirst() {
        return first;
    }

    public int getItems() {
        return items;
    }

    public int getBlocks() {
        return blocks;
    }

    /** Whether the resource pack changed, and players were sent it again. */
    public boolean isPackChanged() {
        return packChanged;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
