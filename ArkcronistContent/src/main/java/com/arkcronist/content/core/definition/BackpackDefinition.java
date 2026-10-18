package com.arkcronist.content.core.definition;

import org.jetbrains.annotations.Nullable;

/**
 * 3.4: an item that holds others - a backpack. Opened with a right click while sneaking, or with
 * {@code /backpack} (also {@code /mochila}) for the one worn on the back or held. What it holds
 * belongs to that one backpack: kept in the plugin's database under an id the item carries, so it
 * travels with the item, worn or not.
 *
 * @param slots how many slots it has: 9 to 54, in whole rows
 * @param title MiniMessage title of its window; null for the item's name
 */
public record BackpackDefinition(int slots, @Nullable String title) {
}
