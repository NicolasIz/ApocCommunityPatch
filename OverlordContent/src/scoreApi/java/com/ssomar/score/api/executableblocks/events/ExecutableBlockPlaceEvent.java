package com.ssomar.score.api.executableblocks.events;

import com.ssomar.score.api.executableblocks.config.placed.ExecutableBlockPlacedInterface;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Signatures copied from SCore 5.25 - see build.gradle. Never packaged. */
public class ExecutableBlockPlaceEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    public ExecutableBlockPlaceEvent(Entity placer, ExecutableBlockPlacedInterface placed) {
        throw new UnsupportedOperationException("signature only");
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public final @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    @Override
    public boolean isCancelled() {
        throw new UnsupportedOperationException("signature only");
    }

    @Override
    public void setCancelled(boolean cancelled) {
        throw new UnsupportedOperationException("signature only");
    }

    public Entity getPlacer() {
        throw new UnsupportedOperationException("signature only");
    }

    public ExecutableBlockPlacedInterface getExecutableBlockPlaced() {
        throw new UnsupportedOperationException("signature only");
    }
}
