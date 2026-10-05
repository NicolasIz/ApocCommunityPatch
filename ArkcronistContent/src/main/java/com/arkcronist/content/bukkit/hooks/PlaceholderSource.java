package com.arkcronist.content.bukkit.hooks;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Placeholders a part of this plugin answers - guns their ammunition, HUDs their bars - asked by
 * the PlaceholderAPI expansion after its own. A plain interface of this plugin's: the parts that
 * answer never name PlaceholderAPI.
 *
 * <p>PlaceholderAPI is asked from whatever thread wants a value - TAB asks from its own - so an
 * answer is read from values kept for it, never worked out from the world.</p>
 */
@FunctionalInterface
public interface PlaceholderSource {

    /**
     * @param params what follows {@code %arkcontent_}, without the closing {@code %}
     * @return the value, or null when this source does not know {@code params}
     */
    @Nullable String resolve(@Nullable OfflinePlayer player, String params);
}
