package com.arkcronist.content.bukkit.hooks.mythicmobs;

import com.arkcronist.content.bukkit.hooks.ContentHook;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicDropLoadEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * This plugin's items inside MythicMobs mob files:
 *
 * <pre>
 * Equipment:
 * - arkcontent{item=demo:ruby_sword} HAND
 * Drops:
 * - arkcontent{item=demo:ruby} 1-3 0.5
 * - dragones_epicos:escama_umbraxis 6-12     # as packs written for ItemsAdder have them
 * </pre>
 *
 * <p>The bare {@code namespace:id} works when ItemsAdder is not installed: MythicMobs reads the
 * colon as the old {@code MATERIAL:data} form and asks for a drop type named after the namespace,
 * with the line attached ({@link NamespacedDropLine}); a namespace of this plugin's items is
 * answered with the item the line names. With ItemsAdder installed those lines are its own.</p>
 *
 * <p>MythicMobs reads its mobs as it enables, and a drop type it does not know by then is reported
 * as missing. This plugin cannot load before it: AuraSkills and BetonQuest load after MythicMobs
 * and before this plugin, and the three would make a loop Paper refuses to start with. So
 * MythicMobs loads first, and if any of its files uses {@code arkcontent} it is reloaded once, a
 * tick after the server has started - its own {@code /mm reload}, which reads the mobs again with
 * this plugin listening and its item suppliers registered.</p>
 */
public final class MythicMobsHook implements Listener, ContentHook {

    static final String DROP_NAME = "arkcontent";

    private final ItemRegistry items;
    private final ItemFactory factory;
    private final Logger logger;
    private final Set<String> suppliedNamespaces = new HashSet<>();
    private final boolean itemsAdderInstalled;

    public MythicMobsHook(ItemRegistry items, ItemFactory factory, Logger logger) {
        this.items = items;
        this.factory = factory;
        this.logger = logger;
        this.itemsAdderInstalled = Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;
    }

    @EventHandler
    public void onDropLoad(MythicDropLoadEvent event) {
        String line = event.getContainer().getLine();
        if (event.getDropName().equalsIgnoreCase(DROP_NAME)) {
            event.register(new ArkContentDrop(line, event.getConfig(), items, factory, logger));
            return;
        }
        String id = itemsAdderInstalled ? null : NamespacedDropLine.itemId(event.getDropName(), line);
        if (id != null && hasNamespace(id.substring(0, id.indexOf(':')))) {
            event.register(new ArkContentDrop(line, event.getConfig(), id, items, factory, logger));
        }
    }

    /** Whether any loaded item is in {@code namespace}. */
    private boolean hasNamespace(String namespace) {
        String prefix = namespace + ":";
        return items.ids().stream().anyMatch(id -> id.startsWith(prefix));
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("MythicMobs")) {
            registerSuppliers();
        }
    }

    /**
     * Reloads MythicMobs once the server has started, if it enabled before this plugin and its
     * files name {@code arkcontent} - drops or equipment it could not read then.
     */
    public void reloadIfReadEarly(Plugin plugin) {
        Plugin mythic = plugin.getServer().getPluginManager().getPlugin("MythicMobs");
        if (mythic == null || !mythic.isEnabled() || !namesThisPlugin(mythic.getDataFolder().toPath())) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            logger.info("Reloading MythicMobs so that its arkcontent drops and equipment are read again, now that"
                    + " they are known.");
            registerSuppliers();
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "mythicmobs reload");
        });
    }

    private static boolean namesThisPlugin(Path folder) {
        if (!Files.isDirectory(folder)) {
            return false;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            return files.filter(file -> file.toString().endsWith(".yml") && Files.isRegularFile(file))
                    .anyMatch(file -> {
                        try {
                            return Files.readString(file).contains(DROP_NAME + "{");
                        } catch (IOException | UncheckedIOException exception) {
                            return false;
                        }
                    });
        } catch (IOException | UncheckedIOException exception) {
            return false;
        }
    }

    /** Whether MythicMobs runs this entity; false while MythicMobs is not enabled. */
    public boolean isMythicMob(Entity entity) {
        MythicBukkit mythic = MythicBukkit.inst();
        return mythic != null && mythic.getAPIHelper().isMythicMob(entity);
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
