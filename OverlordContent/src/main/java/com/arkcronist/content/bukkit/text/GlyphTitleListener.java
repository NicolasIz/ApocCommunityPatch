package com.arkcronist.content.bukkit.text;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;

/**
 * Menu titles written for ItemsAdder - {@code :offset_-20::skills_menu_book:},
 * {@code %img_skills_menu_book%} - drawn as they were meant to be, whichever plugin opens the menu:
 * the title is rewritten as the menu opens ({@link InventoryOpenEvent#titleOverride}), before the
 * client is sent it. Last of all, so a plugin that sets a title in the same event is read too.
 */
public final class GlyphTitleListener implements Listener {

    private final FontImageRegistry images;

    public GlyphTitleListener(FontImageRegistry images) {
        this.images = images;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        Component override = event.titleOverride();
        Component title = override != null ? override : event.getView().title();
        Component replaced = GlyphComponents.replace(title, images.current());
        if (replaced != title) {
            event.titleOverride(replaced);
        }
    }
}
