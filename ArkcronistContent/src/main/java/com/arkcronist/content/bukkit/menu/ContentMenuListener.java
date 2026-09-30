package com.arkcronist.content.bukkit.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.Plugin;

/**
 * Nothing moves while a content browser is open.
 *
 * <p>Every click and drag in the view is cancelled - in the menu and in the player's own inventory
 * below it alike, because shift-clicks, number keys, double-click collecting and off-hand swaps all
 * reach across. It is cancelled first ({@link EventPriority#LOWEST}), so other plugins see it
 * cancelled, and again last ({@link EventPriority#HIGHEST}), in case one of them un-cancelled it.
 * What the click was for - a page, a tab, taking a copy - is then carried out by the menu, which
 * hands out fresh stacks and never the one in the slot.</p>
 */
public final class ContentMenuListener implements Listener {

    private final Plugin plugin;

    public ContentMenuListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        ContentMenu menu = ContentMenus.of(event.getView());
        if (menu == null) {
            return;
        }
        deny(event);
        // Raw slots below the menu's size are the menu; the rest are the player's own inventory.
        if (event.getRawSlot() < 0 || event.getRawSlot() >= ContentMenu.SIZE
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (menu.click(player, event.getRawSlot(), event.getClick()) == ContentMenu.Outcome.CLOSE) {
            // Closing the view from inside its own click event is not safe; next tick is.
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (ContentMenus.of(player.getOpenInventory()) == menu) {
                    player.closeInventory();
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void keepClickCancelled(InventoryClickEvent event) {
        if (ContentMenus.of(event.getView()) != null) {
            deny(event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (ContentMenus.of(event.getView()) != null) {
            event.setResult(Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void keepDragCancelled(InventoryDragEvent event) {
        onDrag(event);
    }

    private static void deny(InventoryClickEvent event) {
        event.setResult(Event.Result.DENY);
        event.setCancelled(true);
    }
}
