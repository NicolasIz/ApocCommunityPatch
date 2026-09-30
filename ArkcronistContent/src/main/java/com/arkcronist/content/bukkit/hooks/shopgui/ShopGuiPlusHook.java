package com.arkcronist.content.bukkit.hooks.shopgui;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.event.ShopGUIPlusPostEnableEvent;
import net.brcdev.shopgui.provider.item.ItemProvider;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * This plugin's items in ShopGUI+ shops, drawn with their own models:
 *
 * <pre>
 * items:
 *   ruby:
 *     type: item
 *     item:
 *       arkcontent: demo:ruby
 *     buyPrice: 100
 *     sellPrice: 20
 * </pre>
 *
 * <p>The shop shows - and a buyer receives - the same stack {@code /customgive} makes, item model
 * and all; selling recognises it by this plugin's own tag, not by its name or lore. Registered on
 * ShopGUI+'s post-enable event, which is the moment its API asks for: started, shops not loaded.</p>
 */
public final class ShopGuiPlusHook implements Listener {

    /** The key under a shop item's {@code item:} section. */
    static final String KEY = "arkcontent";

    private final ItemRegistry items;
    private final ItemFactory factory;
    private final Logger logger;

    public ShopGuiPlusHook(ItemRegistry items, ItemFactory factory, Logger logger) {
        this.items = items;
        this.factory = factory;
        this.logger = logger;
    }

    @EventHandler
    public void onShopGuiPlusEnabled(ShopGUIPlusPostEnableEvent event) {
        ShopGuiPlusApi.registerItemProvider(new Provider());
        logger.info("ShopGUI+: shop items can now be 'arkcontent: <namespace:id>'.");
    }

    private final class Provider extends ItemProvider {

        Provider() {
            super("ArkcronistContent");
        }

        @Override
        public boolean isValidItem(ItemStack stack) {
            return factory.identify(stack).isPresent();
        }

        @Override
        public ItemStack loadItem(ConfigurationSection section) {
            String id = section.getString(KEY);
            if (id == null) {
                return null;
            }
            Optional<CustomItem> item = items.find(id);
            if (item.isEmpty()) {
                logger.warning("ShopGUI+ asks for custom item '" + id + "', which is not loaded.");
                return null;
            }
            return factory.create(item.get(), 1);
        }

        @Override
        public boolean compare(ItemStack first, ItemStack second) {
            Optional<CustomItem> a = factory.identify(first);
            return a.isPresent() && Objects.equals(a, factory.identify(second));
        }
    }
}
