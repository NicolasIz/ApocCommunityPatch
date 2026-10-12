package com.arkcronist.content.bukkit.item;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

/**
 * Brings the stacks players carry up to date with their items: a stack keeps the look it was
 * made with, so a piece made before its item was worn as an equipment asset - before 3.0.2 matched
 * imported armour with MythicArmor's 3D pieces - would stay plain netherite on the player for good.
 * Only what {@link ItemFactory#repair} restores changes: the look, never names, lore or enchantments.
 *
 * <p>Checked as a player joins, once each rebuild is live for everyone online, and for a piece
 * put on - one that waited in a chest through those.</p>
 */
public final class StackRepairListener implements Listener {

    private final Plugin plugin;
    private final ItemFactory items;

    public StackRepairListener(Plugin plugin, ItemFactory items) {
        this.plugin = plugin;
        this.items = items;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        repair(event.getPlayer());
    }

    /** Fires once the slot holds the piece; it is changed a tick later, outside the equipment update. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmour(PlayerArmorChangeEvent event) {
        if (items.identify(event.getNewItem()).isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                repair(player);
            }
        });
    }

    /** Every online player's stacks, once a rebuild changed what the items look like. Main thread. */
    public void repairOnline() {
        plugin.getServer().getOnlinePlayers().forEach(this::repair);
    }

    private void repair(Player player) {
        PlayerInventory inventory = player.getInventory();
        // Armour and the off hand included.
        ItemStack[] contents = inventory.getContents();
        boolean changed = false;
        for (ItemStack stack : contents) {
            changed |= stack != null && items.repair(stack);
        }
        if (changed) {
            inventory.setContents(contents);
        }
    }
}
