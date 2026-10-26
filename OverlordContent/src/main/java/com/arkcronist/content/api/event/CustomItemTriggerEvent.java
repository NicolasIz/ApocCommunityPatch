package com.arkcronist.content.api.event;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 5.4: an item's {@code events:} are about to react to one of their triggers - {@code right-click},
 * {@code hit-entity}, {@code break-block} and the rest, as written in the YAML. Cancelled, nothing in
 * them is done (cooldowns are not started).
 */
public final class CustomItemTriggerEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String itemId;
    private final String trigger;
    private final @Nullable Entity target;
    private final @Nullable org.bukkit.block.Block block;
    private boolean cancelled;

    public CustomItemTriggerEvent(Player player, String itemId, String trigger, @Nullable Entity target,
                                  @Nullable org.bukkit.block.Block block) {
        super(player);
        this.itemId = itemId;
        this.trigger = trigger;
        this.target = target;
        this.block = block;
    }

    /** The item's id, {@code namespace:id}. */
    public String getItemId() {
        return itemId;
    }

    /** The trigger as written under {@code events:}: {@code right-click}, {@code kill}... */
    public String getTrigger() {
        return trigger;
    }

    /** The entity hit, killed, looked at or hurting the player; null when none. */
    public @Nullable Entity getTarget() {
        return target;
    }

    /** The block broken or looked at; null when none. */
    public @Nullable org.bukkit.block.Block getBlock() {
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
