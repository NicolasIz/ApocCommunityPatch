package com.arkcronist.content.bukkit.item;

import com.arkcronist.content.core.definition.ItemBehaviour;
import com.arkcronist.content.core.definition.ItemDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * A custom item the server knows: its definition, with the material confirmed to exist and the
 * text already turned into components.
 *
 * <p>Built off the main thread during a rebuild and never changed afterwards, so the same instance
 * is safe to read from any thread.</p>
 *
 * @param displayName the item's name, or null to keep the material's own
 */
public record CustomItem(ItemDefinition definition, Material material, @Nullable Component displayName,
                         List<Component> lore) {

    public CustomItem {
        lore = List.copyOf(lore);
    }

    /**
     * Checks a definition against this server.
     *
     * @return the item, or null after adding the reason to {@code problems}
     */
    public static @Nullable CustomItem resolve(ItemDefinition definition, List<String> problems) {
        Material material = Material.matchMaterial(definition.material());
        if (material == null || material.isAir() || !material.isItem()) {
            problems.add(definition.source().getFileName() + " > " + definition.fullId()
                    + ": '" + definition.material() + "' is not an item material on this server");
            return null;
        }

        MiniMessage text = MiniMessage.miniMessage();
        Component name = definition.displayName() == null ? null : text.deserialize(definition.displayName());
        // Lore is drawn in italics unless told otherwise, which no one writing a lore line expects.
        List<Component> lore = definition.lore().stream()
                .map(line -> text.deserialize(line).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE))
                .toList();
        return new CustomItem(definition, material, name, lore);
    }

    /** {@code namespace:id}. */
    public String id() {
        return definition.fullId();
    }

    /** The key written into the stack's {@code item_model} component. */
    public NamespacedKey itemModel() {
        return Objects.requireNonNull(NamespacedKey.fromString(definition.itemModel().toString()));
    }

    /** False for an item with no model or texture, which keeps its material's look. */
    public boolean hasModel() {
        return definition.assets().hasLook();
    }

    public ItemBehaviour behaviour() {
        return definition.behaviour();
    }
}
