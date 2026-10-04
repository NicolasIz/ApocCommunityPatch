package com.arkcronist.content.bukkit.item;

import com.arkcronist.content.core.definition.Equipment;
import com.arkcronist.content.core.definition.ResourceLocation;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.EquipmentSlot;
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
        Equipment equipment = item.definition().equipment();
        if (equipment != null) {
            stack.setData(DataComponentTypes.EQUIPPABLE, equippable(stack, equipment));
        }
        return stack;
    }

    /**
     * The {@code equippable} component of a worn item. Worn where its base material is - a
     * netherite chestplate as a chestplate - it is the material's own, keeping its equip sound and
     * that it can be dispensed and swapped, with only the asset changed; anywhere else, a plain one.
     * A worn model has no asset at all: the client draws a head item's model only when there is none.
     */
    static Equippable equippable(ItemStack stack, Equipment equipment) {
        EquipmentSlot slot = EquipmentSlot.valueOf(equipment.slot().name());
        Equippable base = stack.getData(DataComponentTypes.EQUIPPABLE);
        Equippable.Builder builder = base != null && base.slot() == slot ? base.toBuilder() : Equippable.equippable(slot);
        ResourceLocation asset = equipment.asset();
        builder.assetId(asset == null ? null : Key.key(asset.namespace(), asset.path()));
        return builder.build();
    }

    /**
     * Puts back what makes a stack look like its item, when something took it off: another plugin
     * that rebuilt the stack from its material, name, lore and tags - an auction house, a crate,
     * a mail plugin - keeps the plugin's id tag but can lose the {@code item_model} and the
     * {@code equippable}. Only the look is restored; name, lore, enchantments and amount stay as
     * they are.
     *
     * @return whether anything was put back
     */
    public boolean repair(@Nullable ItemStack stack) {
        Optional<CustomItem> found = identify(stack);
        if (found.isEmpty()) {
            return false;
        }
        CustomItem item = found.get();
        boolean changed = false;
        if (item.hasModel() && !item.itemModel().equals(stack.getData(DataComponentTypes.ITEM_MODEL))) {
            stack.setData(DataComponentTypes.ITEM_MODEL, item.itemModel());
            changed = true;
        }
        Equipment equipment = item.definition().equipment();
        if (equipment != null) {
            Equippable wanted = equippable(stack, equipment);
            Equippable current = stack.getData(DataComponentTypes.EQUIPPABLE);
            if (current == null || current.slot() != wanted.slot()
                    || !java.util.Objects.equals(current.assetId(), wanted.assetId())) {
                stack.setData(DataComponentTypes.EQUIPPABLE, wanted);
                changed = true;
            }
        }
        return changed;
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
