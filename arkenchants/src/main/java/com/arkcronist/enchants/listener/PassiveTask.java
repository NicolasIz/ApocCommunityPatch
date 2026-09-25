package com.arkcronist.enchants.listener;

import com.arkcronist.enchants.engine.Context;
import com.arkcronist.enchants.engine.Engine;
import com.arkcronist.enchants.item.Keys;
import com.arkcronist.enchants.model.Trigger;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * EFFECT_STATIC (worn armor / held item), HELD, REPEATING and ELYTRA_FLY. Potion effects are handed out a bit
 * longer than the interval, so they fade on their own when the item is taken off.
 */
public final class PassiveTask implements Runnable {

    private final Engine engine;
    private final int interval;

    public PassiveTask(Engine engine, int interval) {
        this.engine = engine;
        this.interval = interval;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead()) {
                continue;
            }
            Context c = new Context(Trigger.EFFECT_STATIC, p);
            c.victim = p;
            engine.fire(Trigger.EFFECT_STATIC, c, Gear.all(p));
            Context h = new Context(Trigger.HELD, p);
            h.victim = p;
            engine.fire(Trigger.HELD, h, Gear.hand(p));
            Context r = new Context(Trigger.REPEATING, p);
            r.victim = p;
            engine.fire(Trigger.REPEATING, r, Gear.all(p));
            if (p.isGliding()) {
                Context g = new Context(Trigger.ELYTRA_FLY, p);
                g.victim = p;
                engine.fire(Trigger.ELYTRA_FLY, g, List.of(p.getInventory().getChestplate() == null
                        ? new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR) : p.getInventory().getChestplate()));
            }
            // FLY only lasts while the item that grants it is worn
            Long granted = p.getPersistentDataContainer().get(Keys.GUARD, PersistentDataType.LONG);
            if (granted != null && now - granted > interval * 50L * 2 + 500) {
                p.getPersistentDataContainer().remove(Keys.GUARD);
                if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
                    p.setFlying(false);
                    p.setAllowFlight(false);
                }
            }
        }
    }
}
