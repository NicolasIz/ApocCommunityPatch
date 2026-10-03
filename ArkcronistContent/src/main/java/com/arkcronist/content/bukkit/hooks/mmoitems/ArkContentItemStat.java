package com.arkcronist.content.bukkit.hooks.mmoitems;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import net.Indyuce.mmoitems.api.item.build.ItemStackBuilder;
import net.Indyuce.mmoitems.gui.edition.EditionInventory;
import net.Indyuce.mmoitems.stat.data.StringData;
import net.Indyuce.mmoitems.stat.type.StringStat;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * An MMOItems item stat, {@code ARKCONTENT_ITEM}, that draws an MMOItems item with one of this
 * plugin's items: its model and textures, from the pack this plugin builds and sends.
 *
 * <pre>
 * CUTLASS:
 *   base:
 *     material: IRON_SWORD
 *     name: '&amp;cRuby Cutlass'
 *     arkcontent-item: demo:ruby_sword
 * </pre>
 *
 * <p>MMOItems keeps every RPG stat - damage, abilities, gem sockets, tiers - and this stat only
 * sets the {@code item_model} component, so the item looks like the custom one while staying an
 * MMOItems item in every other way. It is also stored in the item's NBT, as every MMOItems stat
 * is, so it survives MMOItems' item updates and can be edited in its GUI ({@code /mi edit}), where
 * an id that is not a loaded custom item is refused.</p>
 *
 * <p>The model is looked up when MMOItems builds the item, not when it loads its configs - MMOItems
 * reads those before this plugin's first build has finished. An item built before then still
 * gets its look: an item model is the item's id by construction.</p>
 */
public final class ArkContentItemStat extends StringStat {

    public static final String ID = "ARKCONTENT_ITEM";

    private final Supplier<ItemRegistry> registry;
    private final Logger logger;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    public ArkContentItemStat(Supplier<ItemRegistry> registry, Logger logger) {
        super(ID, Material.PAINTING, "ArkcronistContent Item", new String[]{
                "Draws this item with an ArkcronistContent",
                "item's model and textures; every MMOItems",
                "stat still applies.",
                "",
                "Format: namespace:id, e.g. demo:ruby_sword"}, new String[0]);
        this.registry = registry;
        this.logger = logger;
    }

    @Override
    public void whenApplied(ItemStackBuilder item, StringData data) {
        // Stored like any MMOItems stat, so MMOItems reads it back when it updates or edits the item.
        // No lore line: a look is not something the tooltip should list.
        item.addItemTag(getAppliedNBT(data));
        NamespacedKey model = model(data.getString());
        if (model != null) {
            item.getMeta().setItemModel(model);
        }
    }

    /** The GUI editor: an id that is not a loaded custom item is refused with a message, not saved. */
    @Override
    public void whenInput(EditionInventory inventory, String message, Object... info) {
        String id = message.trim();
        ItemRegistry items = registry.get();
        if (items != null && items.size() > 0) {
            Optional<CustomItem> item = items.get(id);
            if (item.isEmpty()) {
                throw new IllegalArgumentException("'" + id + "' is not an ArkcronistContent item; /arkcontent menu lists them.");
            }
            if (!item.get().hasModel()) {
                throw new IllegalArgumentException("'" + id + "' has no model of its own to lend.");
            }
        }
        super.whenInput(inventory, id, info);
    }

    /** The item model to draw {@code id} with, or null - logged once per id - when there is none. */
    private @Nullable NamespacedKey model(String id) {
        String trimmed = id == null ? "" : id.trim();
        ItemRegistry items = registry.get();
        if (items != null && items.size() > 0) {
            Optional<CustomItem> item = items.get(trimmed);
            if (item.isPresent() && item.get().hasModel()) {
                return item.get().itemModel();
            }
            if (warned.add(trimmed)) {
                logger.warning("MMOItems: arkcontent-item '" + trimmed + "' is "
                        + (item.isEmpty() ? "not a loaded custom item" : "an item without a model")
                        + "; items with it keep their material's look.");
            }
            return null;
        }
        // Before the first build: the item model of namespace:id is namespace:id.
        return NamespacedKey.fromString(trimmed);
    }
}
