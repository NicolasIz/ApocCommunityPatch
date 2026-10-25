package com.arkcronist.content.bukkit.hooks.mmoitems;

import com.arkcronist.content.bukkit.item.ItemRegistry;
import net.Indyuce.mmoitems.MMOItems;

import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Registers {@link ArkContentItemStat} with MMOItems.
 *
 * <p>Called from this plugin's {@code onLoad}: MMOItems loads first, and reads its item configs -
 * which may use the stat - only as it enables, so a stat registered while plugins are still loading
 * is known by then. Registered any later, items using it would fail to load.</p>
 */
public final class MMOItemsHook {

    private MMOItemsHook() {
    }

    public static void register(Supplier<ItemRegistry> registry, Logger logger) {
        MMOItems mmoItems = MMOItems.plugin;
        if (mmoItems == null) {
            throw new IllegalStateException("MMOItems has not loaded yet");
        }
        if (!mmoItems.getStats().has(ArkContentItemStat.ID)) {
            mmoItems.getStats().register(new ArkContentItemStat(registry, logger));
        }
    }
}
