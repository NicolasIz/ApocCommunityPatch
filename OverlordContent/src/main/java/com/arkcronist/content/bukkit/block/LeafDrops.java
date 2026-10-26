package com.arkcronist.content.bukkit.block;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.core.definition.Placement;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 5.5: what custom leaves drop - as vanilla's leaves: themselves to shears or silk touch, else what
 * their {@code drops:} roll, a sapling now and then. Main thread.
 */
public final class LeafDrops {

    private final ItemRegistry registry;
    private final ItemFactory items;

    public LeafDrops(ItemRegistry registry, ItemFactory items) {
        this.registry = registry;
        this.items = items;
    }

    /** Whether {@code tool} takes leaves whole: shears, or anything with silk touch. */
    public static boolean takesWhole(@Nullable ItemStack tool) {
        return tool != null && (tool.getType() == Material.SHEARS || tool.containsEnchantment(Enchantment.SILK_TOUCH));
    }

    /** Drops at {@code block} what breaking these leaves with {@code tool} gives; null for no tool. */
    public void drop(Block block, CustomLeaves leaves, @Nullable ItemStack tool) {
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        if (takesWhole(tool)) {
            if (leaves.placement().dropSelf()) {
                at.getWorld().dropItemNaturally(at, items.create(leaves.item(), 1));
            }
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Placement.Drop drop : leaves.leaves().drops()) {
            if (random.nextDouble() >= drop.chance()) {
                continue;
            }
            int amount = drop.min() == drop.max() ? drop.min() : random.nextInt(drop.min(), drop.max() + 1);
            Optional<CustomItem> custom = registry.get(drop.item());
            Material material = custom.map(CustomItem::material).orElseGet(() -> Material.matchMaterial(drop.item()));
            if (material == null || material.isAir() || !material.isItem()) {
                continue;
            }
            for (int left = amount; left > 0; left -= material.getMaxStackSize()) {
                int count = Math.min(material.getMaxStackSize(), left);
                at.getWorld().dropItemNaturally(at, custom.isPresent() ? items.create(custom.get(), count)
                        : ItemStack.of(material, count));
            }
        }
    }
}
