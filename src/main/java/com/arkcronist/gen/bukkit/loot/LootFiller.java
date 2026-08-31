package com.arkcronist.gen.bukkit.loot;

import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.math.Hashing;
import com.arkcronist.gen.core.structure.LootMarker;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Location;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;

import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

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

    /** Counted across the whole world so a silent failure cannot stay silent. */
    private static final AtomicLong SEEN = new AtomicLong();
    private static final AtomicLong FILLED = new AtomicLong();
    private static final AtomicLong REPAIRED = new AtomicLong();
    private static final AtomicLong FROM_TABLE = new AtomicLong();
    private static final AtomicLong LEFT_EMPTY = new AtomicLong();

    public static void fill(LimitedRegion region, List<LootMarker> markers, long worldSeed) {
        fill(region, markers, worldSeed, LootRules.none());
    }

    /**
     * The same, with whatever {@code config.yml} says about these themes.
     *
     * <p>A theme the config does not mention falls through to the built-in rules below, so a server
     * that never opens the {@code loot} section gets exactly what it got before.</p>
     */
    public static void fill(LimitedRegion region, List<LootMarker> markers, long worldSeed,
                            LootRules rules) {
        if (!rules.enabled()) {
            return;
        }
        for (LootMarker marker : markers) {
            int x = marker.x();
            int y = marker.y();
            int z = marker.z();
            if (!region.isInRegion(x, y, z)) {
                continue;
            }
            SEEN.incrementAndGet();

            BlockState state = region.getBlockState(x, y, z);
            if (!(state instanceof Container)) {
                // The marker says a container was stamped here, so if the block does not read back
                // as one, put the chest in ourselves and try again. A prefab's chest arriving as
                // something the server will not hand back as a Container is the one failure mode
                // that leaves every chest in the world empty while everything else looks correct.
                BlockData chest = org.bukkit.Bukkit.createBlockData(Material.CHEST);
                region.setBlockData(x, y, z, chest);
                state = region.getBlockState(x, y, z);
                if (!(state instanceof Container)) {
                    continue;
                }
                REPAIRED.incrementAndGet();
            }
            Container container = (Container) state;

            FastRandom random = new FastRandom(Hashing.hash3(worldSeed ^ 0x100D_5EEDL, x, y, z));
            LootRules.Theme configured = rules.theme(marker.theme());

            // Rolled from the position like everything else, so which chests are worth opening is
            // fixed for a seed rather than decided by who walked in first.
            if (configured.fillChance() < 1.0 && !random.chance(configured.fillChance())) {
                // Counted apart from the ones that were filled, and that is not bookkeeping: the
                // whole reason these counters exist is to answer "why are my chests empty", and a
                // chest deliberately left empty reported as filled is the one answer that would send
                // somebody hunting for a bug that is not there.
                LEFT_EMPTY.incrementAndGet();
                continue;
            }

            Inventory inventory = container.getInventory();

            // The vanilla table is ROLLED HERE and the items put in the chest, rather than left on
            // the block for the game to roll when a player first opens it. Setting the table is the
            // tidier mechanism and it is what the game's own chests carry, but it only pays off if
            // that NBT survives being written back through the region - and on this server it did
            // not: the filler reported every container filled while every chest opened empty.
            // Real items in the inventory cannot fail that way.
            boolean rolled = false;
            LootTables tables = configured.tables().isEmpty()
                    ? tableFor(marker.theme(), marker.tier(), random)
                    : configured.tables().get(random.nextInt(configured.tables().size()));
            if (tables != null) {
                try {
                    LootTable table = tables.getLootTable();
                    Location where = new Location(region.getWorld(), x, y, z);
                    Collection<ItemStack> items = table.populateLoot(
                            new Random(Hashing.hash3(worldSeed, x, y, z)),
                            new LootContext.Builder(where).build());
                    if (items != null && !items.isEmpty()) {
                        for (ItemStack item : items) {
                            if (item != null && !item.getType().isAir()) {
                                inventory.addItem(item);
                            }
                        }
                        rolled = true;
                        FROM_TABLE.incrementAndGet();
                    }
                } catch (Throwable ignored) {
                    // A table missing from this version, or a context the server will not roll:
                    // fall through to the hand rolled items rather than leaving the chest empty.
                }
            }
            // The theme's own items go in on top of whatever the table gave, not instead of it.
            // Somebody who lists their server's own currency in a castle wants it in the castle
            // chests, not a castle that stops holding anything else.
            int own = Math.min(rules.maxStacks(), configured.rolls());
            for (int i = 0; i < own; i++) {
                ItemStack item = configured.pick(random);
                if (item == null) {
                    break;
                }
                if (item.getType().getMaxDurability() > 0 && enchantedIn(configured, item)) {
                    enchant(item, random, Math.max(1, marker.tier()));
                }
                // addItem and not setItem at a random slot: the vanilla table has already put its
                // items in, and a random slot would sometimes land on one and delete it. Scattering
                // looks better and is not worth losing a table's drop over.
                inventory.addItem(item);
                rolled = true;
            }

            if (!rolled) {
                int stacks = Math.min(rules.maxStacks(), 3 + marker.tier() * 2 + random.nextInt(0, 3));
                for (int i = 0; i < stacks; i++) {
                    ItemStack item = roll(random, marker.tier(), marker.theme());
                    if (item != null) {
                        inventory.setItem(random.nextInt(inventory.getSize()), item);
                    }
                }
            }
            region.setBlockState(x, y, z, container);
            FILLED.incrementAndGet();
        }
    }

    /** Whether the theme asked for this material to come out enchanted. */
    private static boolean enchantedIn(LootRules.Theme theme, ItemStack item) {
        for (LootRules.Entry entry : theme.items()) {
            if (entry.material() == item.getType()) {
                return entry.enchanted();
            }
        }
        return false;
    }

    /** Containers seen, filled, repaired, rolled from a vanilla table, and left empty on purpose. */
    public static long[] counters() {
        return new long[]{SEEN.get(), FILLED.get(), REPAIRED.get(), FROM_TABLE.get(), LEFT_EMPTY.get()};
    }

    /**
     * A vanilla loot table suited to the theme, or null to fall back to hand rolled items.
     *
     * <p>Tier picks how rich the table is where the theme offers a choice, so a landmark building
     * is worth more than an outbuilding of the same kind.</p>
     */
    private static LootTables tableFor(String theme, int tier, FastRandom random) {
        return switch (theme) {
            case "mineshaft" -> LootTables.ABANDONED_MINESHAFT;
            case "stronghold" -> tier >= 3 ? LootTables.STRONGHOLD_LIBRARY
                    : random.chance(0.5) ? LootTables.STRONGHOLD_CORRIDOR : LootTables.STRONGHOLD_CROSSING;
            case "ancient_city" -> LootTables.ANCIENT_CITY;
            case "dungeon" -> LootTables.SIMPLE_DUNGEON;
            case "shipwreck", "ship" -> tier >= 3 ? LootTables.SHIPWRECK_TREASURE
                    : random.chance(0.5) ? LootTables.SHIPWRECK_SUPPLY : LootTables.SHIPWRECK_MAP;
            case "underwater" -> tier >= 2 ? LootTables.UNDERWATER_RUIN_BIG : LootTables.UNDERWATER_RUIN_SMALL;
            case "treasure" -> LootTables.BURIED_TREASURE;
            case "temple" -> random.chance(0.5) ? LootTables.DESERT_PYRAMID : LootTables.JUNGLE_TEMPLE;
            case "igloo" -> LootTables.IGLOO_CHEST;
            case "portal" -> LootTables.RUINED_PORTAL;
            case "trial" -> tier >= 3 ? LootTables.TRIAL_CHAMBERS_REWARD_RARE : LootTables.TRIAL_CHAMBERS_SUPPLY;
            case "mansion" -> LootTables.WOODLAND_MANSION;
            case "camp", "battle" -> LootTables.PILLAGER_OUTPOST;
            case "vault" -> tier >= 3 ? LootTables.END_CITY_TREASURE : LootTables.WOODLAND_MANSION;
            // Prefab folders. A village house is a village house whoever built the schematic.
            case "village", "houses" -> switch (random.nextInt(6)) {
                case 0 -> LootTables.VILLAGE_PLAINS_HOUSE;
                case 1 -> LootTables.VILLAGE_SNOWY_HOUSE;
                case 2 -> LootTables.VILLAGE_TAIGA_HOUSE;
                case 3 -> LootTables.VILLAGE_SAVANNA_HOUSE;
                case 4 -> LootTables.VILLAGE_DESERT_HOUSE;
                default -> LootTables.VILLAGE_TANNERY;
            };
            case "castles", "castle", "fortress", "towers" -> tier >= 2
                    ? LootTables.WOODLAND_MANSION : LootTables.PILLAGER_OUTPOST;
            case "ruins", "ruin" -> random.chance(0.5) ? LootTables.UNDERWATER_RUIN_BIG : LootTables.SIMPLE_DUNGEON;
            case "temples" -> LootTables.JUNGLE_TEMPLE;
            case "ships" -> tier >= 2 ? LootTables.SHIPWRECK_TREASURE : LootTables.SHIPWRECK_SUPPLY;
            // sky, trail and anything a user invents in their own folder: hand rolled, because
            // there is no vanilla table that means the same thing.
            default -> null;
        };
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
