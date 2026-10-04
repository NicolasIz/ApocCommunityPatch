package com.arkcronist.content.bukkit.advancement;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

/**
 * What moves the plugin's advancements along.
 *
 * <p>Armour is watched through Paper's {@link PlayerArmorChangeEvent}, which fires once a slot
 * really holds something new - whether the piece got there by a click in the inventory, a
 * shift-click, a right-click with it in hand, a dispenser or a command. An inventory click alone
 * would miss most of those, and fires before the piece lands.</p>
 */
public final class AdvancementListener implements Listener {

    private final Plugin plugin;
    private final AdvancementService advancements;

    public AdvancementListener(Plugin plugin, AdvancementService advancements) {
        this.plugin = plugin;
        this.advancements = advancements;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmour(PlayerArmorChangeEvent event) {
        if (advancements.isWorn(event.getNewItem())) {
            advancements.checkWorn(event.getPlayer());
        }
    }

    /** A set put on while the advancement did not exist yet, or worn since before a restart. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                advancements.checkWorn(event.getPlayer());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDone(PlayerAdvancementDoneEvent event) {
        advancements.completed(event.getPlayer(), event.getAdvancement());
    }

    /** {@code /minecraft:reload}, or the plugin's own: every advancement added at run time is gone. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDataReload(ServerResourcesReloadedEvent event) {
        advancements.reregister();
    }
}
