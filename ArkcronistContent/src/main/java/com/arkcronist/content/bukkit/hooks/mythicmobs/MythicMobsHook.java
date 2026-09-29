package com.arkcronist.content.bukkit.hooks.mythicmobs;

import com.arkcronist.content.bukkit.hooks.ContentHook;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicDropLoadEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * This plugin's items inside MythicMobs mob files:
 *
 * <pre>
 * Equipment:
 * - arkcontent{item=demo:ruby_sword} HAND
 * Drops:
 * - arkcontent{item=demo:ruby} 1-3 0.5
 * </pre>
 *
 * <p>The id goes inside the braces. A bare {@code demo:ruby} cannot work: MythicMobs reads a colon in
 * a drop name as the old {@code MATERIAL:data} form before any plugin is asked.</p>
 *
 * <p>MythicMobs reads its mobs as it enables, and a drop type it does not know by then is reported
 * as missing. So this plugin loads before MythicMobs ({@code load: AFTER} in paper-plugin.yml) and
 * listens from its own onEnable; the item suppliers, which need MythicMobs running, are registered
 * once it is.</p>
 */
public final class MythicMobsHook implements Listener, ContentHook {

    static final String DROP_NAME = "arkcontent";

    private final ItemRegistry items;
    private final ItemFactory factory;
    private final Logger logger;
    private final Set<String> suppliedNamespaces = new HashSet<>();

    public MythicMobsHook(ItemRegistry items, ItemFactory factory, Logger logger) {
        this.items = items;
        this.factory = factory;
        this.logger = logger;
    }

    @EventHandler
    public void onDropLoad(MythicDropLoadEvent event) {
        if (event.getDropName().equalsIgnoreCase(DROP_NAME)) {
            event.register(new ArkContentDrop(event.getContainer().getLine(), event.getConfig(), items, factory, logger));
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("MythicMobs")) {
            registerSuppliers();
        }
    }

    /** New namespaces may have appeared; each gets its supplier. Main thread. */
    @Override
    public void contentReloaded() {
        registerSuppliers();
    }

    private void registerSuppliers() {
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            return;
        }
        for (CustomItem item : items.all()) {
            String namespace = item.definition().namespace();
            if (suppliedNamespaces.add(namespace)) {
                MythicBukkit.inst().getItemManager().registerItemSupplier(new NamespaceItemSupplier(namespace, items, factory));
                logger.info("MythicMobs: items of namespace '" + namespace + "' registered as an item supplier.");
            }
        }
    }
}
