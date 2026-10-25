package com.arkcronist.content.core.definition;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 3.4: an item drunk from in sips, and filled again - a canteen.
 *
 * <p>It is drunk as a potion is, holding the use key for {@code seconds}; each sip adds {@code hud}
 * to the HUD values this plugin keeps - thirst - and gives {@code potions}. How many sips are left
 * is the item's damage bar. Empty, it cannot be drunk until it is filled at one of {@code refill}:
 * {@code water} (a water source, clicked), {@code cauldron} (a cauldron with water, which loses a
 * level), or a piece of furniture's id - a trough, a well.</p>
 *
 * @param sips    how many drinks it holds when full
 * @param seconds how long one drink takes
 * @param hud     what one drink adds, by HUD id ({@code thirst})
 * @param potions potion effects one drink gives
 * @param refill  what fills it again
 * @param sound   played as a drink is taken; null for the game's own
 * @param filled  MiniMessage shown above the hotbar when it is filled; null for the plugin's
 * @param empty   MiniMessage shown when it is empty and drunk from; null for the plugin's
 */
public record DrinkDefinition(int sips, double seconds, Map<String, Double> hud, List<Placement.Effect> potions,
                              List<String> refill, @Nullable String sound, @Nullable String filled,
                              @Nullable String empty) {

    /** What {@code refill} can name besides furniture. */
    public static final List<String> SOURCES = List.of("water", "cauldron");

    public DrinkDefinition {
        hud = Map.copyOf(hud);
        potions = List.copyOf(potions);
        refill = List.copyOf(refill);
    }

    /** Whether it fills at a water source, clicked. */
    public boolean fillsAtWater() {
        return refill.contains("water");
    }

    /** Whether it fills at a cauldron with water in it. */
    public boolean fillsAtCauldron() {
        return refill.contains("cauldron");
    }

    /** Whether it fills at this piece of furniture. */
    public boolean fillsAt(String furnitureId) {
        return refill.contains(furnitureId);
    }
}
