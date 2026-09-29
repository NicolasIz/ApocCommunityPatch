package com.arkcronist.content.bukkit.listener;

import com.arkcronist.content.bukkit.event.CustomItemHitEvent;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Melee hits dealt with a custom item become a {@link CustomItemHitEvent}.
 *
 * <p>Only direct and sweep attacks count. An arrow from a custom bow is a projectile, and the bow
 * may no longer be in hand when it lands; that case belongs to a future projectile interception
 * that tags the arrow as it is shot.</p>
 */
public final class ItemCombatListener implements Listener {

    private final ItemFactory items;

    public ItemCombatListener(ItemFactory items) {
        this.items = items;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        DamageCause cause = event.getCause();
        if (cause != DamageCause.ENTITY_ATTACK && cause != DamageCause.ENTITY_SWEEP_ATTACK) {
            return;
        }
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        Optional<CustomItem> item = items.identify(weapon);
        if (item.isEmpty()) {
            return;
        }

        CustomItemHitEvent hit = new CustomItemHitEvent(attacker, item.get(), weapon, event.getEntity(),
                event.getDamage());
        if (!hit.callEvent()) {
            event.setCancelled(true);
        } else if (hit.getDamage() != event.getDamage()) {
            event.setDamage(hit.getDamage());
        }
    }
}
