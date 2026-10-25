package com.arkcronist.content.core.definition;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * How an item is worn: Minecraft's {@code equippable} component (1.21.4+), and what draws it on
 * the player.
 *
 * <pre>
 * equipment:
 *   slot: CHEST
 *   asset: ruby_armor          # assets/&lt;ns&gt;/equipment/ruby_armor.json, written by the compiler
 * </pre>
 *
 * <p>An <b>asset</b> is a set of armour layers. The compiler writes its descriptor from the
 * textures it finds under {@code textures/entity/equipment/<layer>/<asset>.png} - {@code humanoid}
 * for helmets, chestplates and boots, {@code humanoid_leggings} for leggings - and the client draws
 * them over the player, the way it draws vanilla armour.</p>
 *
 * <p>A <b>worn model</b> is a 3D model drawn on the head instead: horns, crests, anything an
 * armour layer cannot do. The client draws a head item's own model only when its {@code equippable}
 * names no asset, so a worn model and an asset do not go together; the item keeps its usual model
 * everywhere else - in an inventory, in a hand.</p>
 *
 * @param slot       where it is worn
 * @param asset      the layer set the client draws, or null for none
 * @param layers     the layers found for {@code asset}, e.g. {@code humanoid}; empty without one
 * @param worn       the model drawn on the head, or null
 * @param sourceRoot the content folder the asset's textures are read from
 */
public record Equipment(Slot slot, @Nullable ResourceLocation asset, List<String> layers, @Nullable ModelSource worn,
                        Path sourceRoot) {

    /** The layers an asset can have, as the client names them. */
    public static final List<String> LAYERS = List.of("humanoid", "humanoid_leggings", "wings");

    public Equipment {
        layers = List.copyOf(layers);
    }

    /** The four armour slots, by the names Bukkit's {@code EquipmentSlot} uses. */
    public enum Slot {
        HEAD("humanoid"),
        CHEST("humanoid"),
        LEGS("humanoid_leggings"),
        FEET("humanoid");

        /** The layer that draws an asset worn here. */
        public final String layer;

        Slot(String layer) {
            this.layer = layer;
        }

        public static @Nullable Slot parse(String raw) {
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
    }

    /** Where a layer texture of {@code asset} is read from, and written to: {@code entity/equipment/<layer>/<name>}. */
    public static ResourceLocation layerTexture(ResourceLocation asset, String layer) {
        return new ResourceLocation(asset.namespace(), "entity/equipment/" + layer + "/" + asset.path());
    }
}
