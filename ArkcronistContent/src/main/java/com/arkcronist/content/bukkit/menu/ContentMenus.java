package com.arkcronist.content.bukkit.menu;

import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.jetbrains.annotations.Nullable;

/**
 * Opens content browsers, and keeps the open ones honest: refreshed when the content is rebuilt,
 * closed when the plugin goes away. Main thread only.
 */
public final class ContentMenus {

    private final Server server;
    private final ItemRegistry registry;
    private final ItemFactory factory;

    public ContentMenus(Server server, ItemRegistry registry, ItemFactory factory) {
        this.server = server;
        this.registry = registry;
        this.factory = factory;
    }

    public void open(Player player) {
        ContentMenu menu = new ContentMenu(registry, factory);
        menu.render(player);
        player.openInventory(menu.getInventory());
    }

    /** After a rebuild: items may have come, gone or changed. */
    public void refreshOpen() {
        for (Player player : server.getOnlinePlayers()) {
            ContentMenu menu = of(player.getOpenInventory());
            if (menu != null) {
                menu.render(player);
            }
        }
    }

    /**
     * On disable. The listener that keeps items in the menu is about to be unregistered; a menu
     * left open after that would be an ordinary chest full of free custom items.
     */
    public void closeAll() {
        for (Player player : server.getOnlinePlayers()) {
            if (of(player.getOpenInventory()) != null) {
                player.closeInventory();
            }
        }
    }

    /** The content browser shown in {@code view}, if that is what it is. */
    static @Nullable ContentMenu of(InventoryView view) {
        return view.getTopInventory().getHolder(false) instanceof ContentMenu menu ? menu : null;
    }
}
