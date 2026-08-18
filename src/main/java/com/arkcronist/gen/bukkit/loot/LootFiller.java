package com.arkcronist.gen.bukkit.loot;

import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.math.Hashing;
import com.arkcronist.gen.core.structure.LootMarker;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Fills structure containers with tiered, themed loot.
 *
 * <p>Contents are drawn from a position seeded generator, so the same chest always holds the same
 * items on the same seed - no duplication exploit through regeneration, and no dependence on when
 * the chunk happened to be populated.</p>
 */
public final class LootFiller {

    private LootFiller() {
    }

    public static void fill(LimitedRegion region, List<LootMarker> markers, long worldSeed) {
        for (LootMarker marker : markers) {
            if (!region.isInRegion(marker.x(), marker.y(), marker.z())) {
                continue;
            }
            BlockState state = region.getBlockState(marker.x(), marker.y(), marker.z());
            if (!(state instanceof Container container)) {
                continue;
            }
            Inventory inventory = container.getInventory();
            FastRandom random = new FastRandom(
                    Hashing.hash3(worldSeed ^ 0x100D_5EEDL, marker.x(), marker.y(), marker.z()));
            int stacks = 3 + marker.tier() * 2 + random.nextInt(0, 3);
            for (int i = 0; i < stacks; i++) {
                ItemStack item = roll(random, marker.tier(), marker.theme());
                if (item != null) {
                    inventory.setItem(random.nextInt(inventory.getSize()), item);
                }
            }
            region.setBlockState(marker.x(), marker.y(), marker.z(), container);
        }
    }

    private static ItemStack roll(FastRandom random, int tier, String theme) {
        double roll = random.nextDouble();
        Material material;
        int amount = 1;

        if (roll < 0.34) {
            material = switch (random.nextInt(8)) {
                case 0 -> Material.BREAD;
                case 1 -> Material.COOKED_BEEF;
                case 2 -> Material.APPLE;
                case 3 -> Material.TORCH;
                case 4 -> Material.STICK;
                case 5 -> Material.STRING;
                case 6 -> Material.COAL;
                default -> Material.WHEAT;
            };
            amount = random.nextInt(2, 8 + tier * 3);
        } else if (roll < 0.62) {
            material = switch (random.nextInt(7)) {
                case 0 -> Material.IRON_INGOT;
                case 1 -> Material.GOLD_INGOT;
                case 2 -> Material.COPPER_INGOT;
                case 3 -> Material.LAPIS_LAZULI;
                case 4 -> Material.REDSTONE;
                case 5 -> tier >= 2 ? Material.DIAMOND : Material.IRON_NUGGET;
                default -> tier >= 3 ? Material.NETHERITE_SCRAP : Material.AMETHYST_SHARD;
            };
            amount = random.nextInt(1, 3 + tier * 2);
        } else if (roll < 0.82) {
            material = themeItem(random, theme, tier);
        } else {
            material = gear(random, tier);
        }

        if (material == null || material == Material.AIR) {
            return null;
        }
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(amount, material.getMaxStackSize())));
        if (tier >= 2 && material.getMaxDurability() > 0 && random.chance(0.35 + tier * 0.1)) {
            enchant(item, random, tier);
        }
        return item;
    }

    private static Material themeItem(FastRandom random, String theme, int tier) {
        return switch (theme) {
            case "underwater" -> random.chance(0.4) ? Material.PRISMARINE_SHARD
                    : random.chance(0.5) ? Material.NAUTILUS_SHELL : Material.HEART_OF_THE_SEA;
            case "temple", "vault" -> random.chance(0.5) ? Material.EMERALD
                    : random.chance(0.5) ? Material.GOLDEN_APPLE : Material.ENDER_PEARL;
            case "sky" -> random.chance(0.5) ? Material.PHANTOM_MEMBRANE
                    : random.chance(0.5) ? Material.ELYTRA : Material.AMETHYST_SHARD;
            case "village", "camp" -> random.chance(0.5) ? Material.EMERALD
                    : random.chance(0.5) ? Material.HAY_BLOCK : Material.LEATHER;
            case "castle", "fortress", "battle" -> random.chance(0.4) ? Material.GOLDEN_APPLE
                    : random.chance(0.5) ? Material.EXPERIENCE_BOTTLE : Material.SHIELD;
            case "dungeon" -> random.chance(0.5) ? Material.BONE
                    : random.chance(0.5) ? Material.SPIDER_EYE : Material.ENCHANTED_BOOK;
            default -> tier >= 2 ? Material.EXPERIENCE_BOTTLE : Material.BOOK;
        };
    }

    private static Material gear(FastRandom random, int tier) {
        if (tier >= 3 && random.chance(0.25)) {
            return switch (random.nextInt(5)) {
                case 0 -> Material.DIAMOND_SWORD;
                case 1 -> Material.DIAMOND_CHESTPLATE;
                case 2 -> Material.DIAMOND_HELMET;
                case 3 -> Material.DIAMOND_PICKAXE;
                default -> Material.ENCHANTED_GOLDEN_APPLE;
            };
        }
        if (tier >= 2) {
            return switch (random.nextInt(6)) {
                case 0 -> Material.IRON_SWORD;
                case 1 -> Material.IRON_CHESTPLATE;
                case 2 -> Material.IRON_LEGGINGS;
                case 3 -> Material.CROSSBOW;
                case 4 -> Material.BOW;
                default -> Material.IRON_AXE;
            };
        }
        return switch (random.nextInt(6)) {
            case 0 -> Material.LEATHER_CHESTPLATE;
            case 1 -> Material.LEATHER_BOOTS;
            case 2 -> Material.STONE_SWORD;
            case 3 -> Material.STONE_AXE;
            case 4 -> Material.ARROW;
            default -> Material.WOODEN_PICKAXE;
        };
    }

    private static void enchant(ItemStack item, FastRandom random, int tier) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        Enchantment[] pool = {
                Enchantment.SHARPNESS, Enchantment.PROTECTION, Enchantment.UNBREAKING,
                Enchantment.EFFICIENCY, Enchantment.POWER, Enchantment.FORTUNE
        };
        Enchantment enchantment = pool[random.nextInt(pool.length)];
        meta.addEnchant(enchantment, random.nextInt(1, Math.min(4, tier + 1)), true);
        item.setItemMeta(meta);
    }
}
