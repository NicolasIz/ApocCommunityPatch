package com.arkcronist.content.bukkit.event;

import com.arkcronist.content.bukkit.item.CustomItem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * A player struck an entity in melee while holding a custom item in the main hand.
 *
 * <p>The damage can be changed, and cancelling the event cancels the hit altogether.</p>
 */
public final class CustomItemHitEvent extends CustomItemEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Entity victim;
    private double damage;
    private boolean cancelled;

    public CustomItemHitEvent(Player player, CustomItem item, ItemStack stack, Entity victim, double damage) {
        super(player, item, stack);
        this.victim = victim;
        this.damage = damage;
    }

    public Entity getVictim() {
        return victim;
    }

    /** The damage the hit will deal, before armour. */
    public double getDamage() {
        return damage;
    }

    public void setDamage(double damage) {
        this.damage = Math.max(0, damage);
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
