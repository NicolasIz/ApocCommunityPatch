package com.arkcronist.enchants.listener;

import com.arkcronist.enchants.ArkEnchants;
import com.arkcronist.enchants.item.LootEnchanter;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;

/** Gear found in generated chests (dungeons, structures, datapack loot) may come enchanted, or cursed. */
public final class LootListener implements Listener {

    private final ArkEnchants plugin;

    public LootListener(ArkEnchants plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLoot(LootGenerateEvent e) {
        var s = plugin.settings();
        if (!s.lootEnabled || !s.lootWorlds.isEmpty() && !s.lootWorlds.contains(e.getWorld().getName())) {
            return;
        }
        for (ItemStack item : e.getLoot()) {
            LootEnchanter.roll(item, plugin.registry(), s, ThreadLocalRandom.current(), false);
        }
    }
}
