package com.arkcronist.content.bukkit.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Makes custom item stacks, and recognises them again.
 *
 * <p>Two things are written into every stack, and they do different jobs:</p>
 * <ul>
 *   <li>the {@code item_model} data component - what the client draws. Since 1.21.4 an item names
 *       its model directly, and {@link org.bukkit.inventory.meta.ItemMeta#setItemModel} is the API
 *       for it; the old {@code CustomModelData} number, and the per-material override tables it
 *       needed in the pack, are not used at all. The key names {@code assets/<ns>/items/<id>.json},
 *       so the pack can change what that item looks like without touching stacks players already
 *       own;</li>
 *   <li>a persistent data tag with the item's id - what the server recognises. The item model is a
 *       visual and anything can copy it (another plugin, {@code /give} with components); the tag is
 *       what listeners trust.</li>
 * </ul>
 */
public final class ItemFactory {

    private final NamespacedKey idKey;
    private final ItemRegistry registry;

    public ItemFactory(Plugin plugin, ItemRegistry registry) {
        this.idKey = new NamespacedKey(plugin, "item");
        this.registry = registry;
    }

    /**
     * A new stack of {@code item}. {@code amount} must not exceed the material's stack size.
     */
    public ItemStack create(CustomItem item, int amount) {
        ItemStack stack = ItemStack.of(item.material(), amount);
        stack.editMeta(meta -> {
            if (item.hasModel()) {
                // Equivalent through Paper's data component API:
                // stack.setData(DataComponentTypes.ITEM_MODEL, item.itemModel());
                meta.setItemModel(item.itemModel());
            }
            if (item.displayName() != null) {
                // item_name rather than custom_name: not italic, and an anvil rename sits on top of it
                // instead of replacing it.
                meta.itemName(item.displayName());
            }
            if (!item.lore().isEmpty()) {
                meta.lore(item.lore());
            }
            meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, item.id());
        });
        return stack;
    }

    /**
     * The custom item a stack is, if it is one that is loaded right now.
     *
     * <p>Reads the tag through the stack's read-only view, without copying its meta, so it is cheap
     * enough for events that fire every click.</p>
     */
    public Optional<CustomItem> identify(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        String id = stack.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        return id == null ? Optional.empty() : registry.get(id);
    }
}
