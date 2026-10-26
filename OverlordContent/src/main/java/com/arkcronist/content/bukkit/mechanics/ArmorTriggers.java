package com.arkcronist.content.bukkit.mechanics;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.arkcronist.content.core.mechanics.ItemMechanics.Trigger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * 5.4: {@code equip} and {@code unequip} - armour put on and taken off, by any means: a click, a
 * right click with it in hand, a dispenser. A listener of its own, as Paper's armour event is one a
 * later version may take away: then only these two triggers are missing.
 */
public final class ArmorTriggers implements Listener {

    private final MechanicsService mechanics;

    public ArmorTriggers(MechanicsService mechanics) {
        this.mechanics = mechanics;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmor(PlayerArmorChangeEvent event) {
        if (!mechanics.any()) {
            return;
        }
        var slot = event.getSlot();
        var before = mechanics.withId(event.getOldItem());
        var after = mechanics.withId(event.getNewItem());
        String beforeId = before.map(java.util.Map.Entry::getKey).orElse(null);
        String afterId = after.map(java.util.Map.Entry::getKey).orElse(null);
        if (java.util.Objects.equals(beforeId, afterId)) {
            return; // the same piece, worn down by a hit or mended
        }
        before.ifPresent(found -> mechanics.fire(event.getPlayer(), found.getKey(), found.getValue(), Trigger.UNEQUIP,
                new MechanicsService.Context(slot, null, null)));
        after.ifPresent(found -> mechanics.fire(event.getPlayer(), found.getKey(), found.getValue(), Trigger.EQUIP,
                new MechanicsService.Context(slot, null, null)));
    }
}
