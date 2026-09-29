package com.arkcronist.content.bukkit.hooks.mythicmobs;

import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import io.lumine.mythic.api.items.ItemSupplier;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;

/**
 * One content namespace offered to MythicMobs as an item supplier: {@code getItem("ruby")} on the
 * {@code demo} supplier is {@code demo:ruby}.
 *
 * <p>This is MythicMobs' official interface for other plugins' items. In 5.13 nothing inside
 * MythicMobs itself looks suppliers up yet - it registers them and stops there - so equipment and
 * drops go through {@link ArkContentDrop}; the suppliers are there for whatever reads them.</p>
 */
final class NamespaceItemSupplier implements ItemSupplier {

    private final String namespace;
    private final ItemRegistry items;
    private final ItemFactory factory;

    NamespaceItemSupplier(String namespace, ItemRegistry items, ItemFactory factory) {
        this.namespace = namespace;
        this.items = items;
        this.factory = factory;
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public ItemStack getItem(String name) {
        return items.get(namespace + ":" + name).map(item -> factory.create(item, 1)).orElse(null);
    }

    @Override
    public boolean isSimilar(String name, ItemStack stack) {
        String id = namespace + ":" + name;
        return factory.identify(stack).map(item -> item.id().equals(id)).orElse(false);
    }

    @Override
    public Collection<String> getAvailableItemNames() {
        String prefix = namespace + ":";
        return items.ids().stream()
                .filter(id -> id.startsWith(prefix))
                .map(id -> id.substring(prefix.length()))
                .toList();
    }
}
