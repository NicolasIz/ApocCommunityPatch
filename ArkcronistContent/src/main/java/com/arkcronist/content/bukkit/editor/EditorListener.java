package com.arkcronist.content.bukkit.editor;

import com.arkcronist.content.core.furniture.EditorLayout;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * The furniture editor's menu: nothing in it moves - every click and drag is cancelled first and
 * kept cancelled last, as in the content browser - and a click on a button is handed to
 * {@link FurnitureEditor}. Closing the menu saves.
 */
public final class EditorListener implements Listener {

    private final FurnitureEditor editor;

    public EditorListener(FurnitureEditor editor) {
        this.editor = editor;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        EditorSession session = FurnitureEditor.of(event.getView());
        if (session == null) {
            return;
        }
        deny(event);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= EditorLayout.SIZE
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        editor.click(player, session, event.getRawSlot(), event.getClick());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void keepClickCancelled(InventoryClickEvent event) {
        if (FurnitureEditor.of(event.getView()) != null) {
            deny(event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (FurnitureEditor.of(event.getView()) != null) {
            event.setResult(Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void keepDragCancelled(InventoryDragEvent event) {
        onDrag(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof EditorSession session) {
            editor.closed(session);
        }
    }

    private static void deny(InventoryClickEvent event) {
        event.setResult(Event.Result.DENY);
        event.setCancelled(true);
    }
}
