package com.arkcronist.content.core.definition;

import java.nio.file.Path;
import java.util.List;

/**
 * One content entry exactly as a content file describes it, before anything has been checked
 * against the server.
 *
 * <p>Nothing here touches Bukkit: the material is still the text that was written and the name is
 * still MiniMessage source. That keeps the loader and the pack compiler testable without a running
 * server; the plugin layer turns a definition into a {@code CustomItem} once it has confirmed the
 * material exists.</p>
 *
 * <p>Custom blocks and furniture are definitions too. They are items until they are placed, and
 * {@link #placement()} says what placing them does.</p>
 *
 * @param namespace   the namespace the item and its assets live in
 * @param id          unique within the namespace, across every type
 * @param material    the base material as written, e.g. {@code DIAMOND_SWORD}; implied by the
 *                    type for blocks ({@code NOTE_BLOCK}) and furniture (the support block)
 * @param displayName MiniMessage source, or null to keep the material's own name
 * @param lore        MiniMessage source, one entry per line
 * @param model       how it is drawn, or null to keep the material's own look
 * @param placement   what placing it does, or null for a plain item
 * @param source      the content file it came from, for messages
 * @param price       what one costs in the content shop ({@code /arkcontent shop}, with Vault);
 *                    0 when it is not for sale
 */
public record ItemDefinition(String namespace, String id, String material, String displayName,
                             List<String> lore, ModelSource model, ItemBehaviour behaviour,
                             Placement placement, Path source, double price) {

    public ItemDefinition {
        lore = List.copyOf(lore);
    }

    /** An item that is not for sale. */
    public ItemDefinition(String namespace, String id, String material, String displayName, List<String> lore,
                          ModelSource model, ItemBehaviour behaviour, Placement placement, Path source) {
        this(namespace, id, material, displayName, lore, model, behaviour, placement, source, 0);
    }

    /** The same item with a price: what one costs in the content shop, through Vault. 0 is not for sale. */
    public ItemDefinition withPrice(double price) {
        return new ItemDefinition(namespace, id, material, displayName, lore, model, behaviour, placement, source, price);
    }

    /** {@code namespace:id}, the key the item is registered, given and recognised by. */
    public String fullId() {
        return namespace + ":" + id;
    }

    public ContentType type() {
        return placement == null ? ContentType.ITEM : placement.type();
    }

    /**
     * The item definition the client is pointed at: {@code assets/<namespace>/items/<id>.json}.
     *
     * <p>Since 1.21.4 an item stack's {@code item_model} component names one of these files, not a
     * model. The indirection is what lets a stack already sitting in a chest pick up a new model: the
     * stack only ever stores this key, and the pack decides what it draws.</p>
     */
    public ResourceLocation itemModel() {
        return new ResourceLocation(namespace, id);
    }
}
