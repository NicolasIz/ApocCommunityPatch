package com.arkcronist.content.bukkit.hooks.mythicmobs;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import io.lumine.mythic.api.adapters.AbstractItemStack;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.drops.DropMetadata;
import io.lumine.mythic.api.drops.IItemDrop;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.core.drops.droppables.ItemDrop;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * {@code arkcontent{item=demo:ruby}} - one of this plugin's items, as a MythicMobs drop.
 *
 * <p>MythicMobs reads a mob's {@code Equipment} through the same drop parser as its {@code Drops},
 * so this one class serves both. It is the pattern MythicMobs' own MMOItems support uses: a drop
 * type registered through {@code MythicDropLoadEvent}.</p>
 *
 * <p>The item is looked up when the drop happens, not when MythicMobs reads its config. MythicMobs
 * may read its mobs before this plugin's first build has finished, and a {@code /arkcontent reload}
 * must reach mobs that are already loaded.</p>
 */
final class ArkContentDrop extends ItemDrop implements IItemDrop {

    /** Ids already reported missing, so a drop rolled a thousand times warns once. */
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private final String itemId;
    private final ItemRegistry items;
    private final ItemFactory factory;
    private final Logger logger;

    ArkContentDrop(String line, MythicLineConfig config, ItemRegistry items, ItemFactory factory, Logger logger) {
        super(line, config);
        this.itemId = config.getString(new String[] {"item", "i", "id"}, "").trim();
        this.items = items;
        this.factory = factory;
        this.logger = logger;
    }

    @Override
    public AbstractItemStack getDrop(DropMetadata metadata, double amount) {
        Optional<CustomItem> item = items.find(itemId);
        if (item.isEmpty()) {
            if (REPORTED.add(itemId)) {
                logger.warning("A MythicMobs drop or equipment line asks for custom item '" + itemId
                        + "', which is not loaded - it drops nothing. Write it as arkcontent{item=namespace:id}.");
            }
            return BukkitAdapter.adapt(new ItemStack(Material.AIR));
        }
        int stackSize = item.get().material().getMaxStackSize();
        int count = (int) Math.max(1, Math.min(stackSize, Math.round(amount)));
        return BukkitAdapter.adapt(factory.create(item.get(), count));
    }

    /**
     * MythicMobs compares drops by class alone. Two lines for different custom items would count as
     * the same drop and could be merged into one, so the item id takes part.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof ArkContentDrop drop && drop.itemId.equals(itemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ArkContentDrop.class.getName(), itemId);
    }
}
