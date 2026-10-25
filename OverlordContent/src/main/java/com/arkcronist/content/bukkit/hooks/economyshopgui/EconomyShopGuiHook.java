package com.arkcronist.content.bukkit.hooks.economyshopgui;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import me.gypopo.economyshopgui.api.events.ItemProviderPreLoadEvent;
import me.gypopo.economyshopgui.api.objects.ExternalItems;
import me.gypopo.economyshopgui.util.exceptions.ItemLoadException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * This plugin's items in EconomyShopGUI Premium shops, as an external item provider:
 *
 * <pre>
 * # EconomyShopGUI-Premium/shops/Gems.yml
 * pages:
 *   page1:
 *     items:
 *       '1':
 *         material: "arkcontent:demo:ruby"
 *         buy: 250
 *         sell: 50
 * </pre>
 *
 * <p>A buyer receives the same stack {@code /customgive} makes; selling recognises it by this
 * plugin's own tag. Registered when EconomyShopGUI asks for item providers, as it loads its shops -
 * by then the items are live, read before any plugin enabled after this one.</p>
 *
 * <p>The free EconomyShopGUI has no item providers, and cannot sell these items: its
 * {@code /editshop addhanditem} saves a held item's material, name and lore only, so what it sells
 * is a plain stack, without the item model or this plugin's tag.</p>
 */
public final class EconomyShopGuiHook implements Listener {

    /** What precedes an item id in a shop's {@code material}. */
    static final String PREFIX = "arkcontent";

    private final Plugin plugin;
    private final ItemRegistry items;
    private final ItemFactory factory;
    private final Logger logger;

    public EconomyShopGuiHook(Plugin plugin, ItemRegistry items, ItemFactory factory, Logger logger) {
        this.plugin = plugin;
        this.items = items;
        this.factory = factory;
        this.logger = logger;
    }

    @EventHandler
    public void onProviders(ItemProviderPreLoadEvent event) {
        event.registerExternal(new Provider());
        logger.info("EconomyShopGUI: shop items can now be material: \"" + PREFIX + ":<namespace:id>\".");
    }

    private final class Provider extends ExternalItems {

        Provider() {
            super(plugin, PREFIX);
        }

        @Override
        public ItemStack getItem(String id) throws ItemLoadException {
            Optional<CustomItem> item = items.find(id);
            if (item.isEmpty()) {
                throw new ItemLoadException("'" + id + "' is not a loaded custom item");
            }
            return factory.create(item.get(), 1);
        }

        @Override
        public String getItem(ItemStack stack) {
            return factory.identify(stack).map(CustomItem::id).orElse(null);
        }
    }
}
