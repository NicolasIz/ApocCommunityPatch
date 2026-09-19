package com.arkcronist.gen.bukkit.mobs;

import com.arkcronist.gen.bukkit.config.ArkConfig;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.math.Hashing;
import com.arkcronist.gen.core.structure.MobSpawn;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;

/**
 * Turns a {@link MobSpawn} request into a real entity.
 *
 * <p>Ordinary garrison mobs get a modest tier bump so a fortress feels defended. Minibosses get the
 * full treatment: multiplied health and damage, armour toughness, gear, permanent effects, a custom
 * name, persistence (they wait for you rather than despawning) and a marker in their persistent data
 * so other plugins - and our own loot rules - can recognise them later.</p>
 */
public final class MinibossFactory {

    public static final String MINIBOSS_KEY = "arkcronist_miniboss";
    public static final String TIER_KEY = "arkcronist_tier";

    private MinibossFactory() {
    }

    /**
     * @param table the swap table for this world, configured entries and discovered ones already
     *              merged - passed in rather than read from the config so a garrison uses exactly
     *              the same table as a natural spawn in the same world, auto-discovery included
     */
    public static Entity spawn(World world, MobSpawn request, ArkConfig config,
                               java.util.Map<String, java.util.List<String>> table,
                               NamespacedKey bossKey, NamespacedKey tierKey) {
        EntityType type;
        try {
            type = EntityType.valueOf(request.entityType().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }

        // Where the structure asked for is a wish, not a fact: it was chosen during generation from
        // the structure's own buffer, and several structures scatter their guards sideways while
        // keeping the anchor's height, which walls them into the hillside wherever the ground rises.
        // The world is finished and loaded by now, so the column is checked and the mob is put where
        // it can actually stand - or not put anywhere at all.
        Location location = SpawnSpot.resolve(world, request, type);
        if (location == null) {
            return null;
        }

        // A garrison of the custom mobs, where one is configured for this entity type. Returned as
        // it comes: a MythicMobs mob carries its own health, damage and behaviour from its own
        // configuration, and layering this generator's tier scaling on top of that would give a mob
        // with several times the health its author set. The tier is passed to MythicMobs as a level
        // and what it does with it is its business.
        Entity custom = customFor(request, location, config, table);
        if (custom != null) {
            return custom;
        }

        Entity entity = world.spawnEntity(location, type);
        if (!(entity instanceof LivingEntity living)) {
            return entity;
        }

        FastRandom random = new FastRandom(Hashing.hash3(world.getSeed(), request.x(), request.y(), request.z()));
        int tier = Math.max(0, request.tier());
        living.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, tier);

        if (request.miniboss()) {
            applyBoss(living, request, config, random, tier);
            living.getPersistentDataContainer().set(bossKey, PersistentDataType.BYTE, (byte) 1);
        } else if (tier > 0) {
            applyGarrison(living, random, tier);
        }

        if (living instanceof Mob mob) {
            mob.setRemoveWhenFarAway(!request.miniboss());
        }
        living.setPersistent(true);
        return living;
    }

    private static void applyGarrison(LivingEntity living, FastRandom random, int tier) {
        scale(living, Attribute.MAX_HEALTH, 1.0 + tier * 0.35);
        scale(living, Attribute.ATTACK_DAMAGE, 1.0 + tier * 0.20);
        living.setHealth(maxHealth(living));
        EntityEquipment equipment = living.getEquipment();
        if (equipment != null && tier >= 2 && random.chance(0.5)) {
            equipment.setItemInMainHand(new ItemStack(tier >= 3 ? Material.IRON_SWORD : Material.STONE_SWORD));
            equipment.setItemInMainHandDropChance(0.05f);
            if (random.chance(0.4)) {
                equipment.setHelmet(new ItemStack(Material.LEATHER_HELMET));
                equipment.setHelmetDropChance(0.05f);
            }
        }
    }

    /**
     * The MythicMobs stand-in for a structure's mob, or null to use the vanilla one.
     *
     * <p>Same table the spawn listener uses, so a castle's guards are the same goblins a player
     * meets in the open rather than a second, separate idea of what lives in this world.</p>
     */
    private static Entity customFor(MobSpawn request, Location location, ArkConfig config,
                                    java.util.Map<String, java.util.List<String>> table) {
        if (!config.hostileMobsEnabled() || !config.replaceStructureMobs() || !MythicBridge.available()) {
            return null;
        }
        // A named boss gets its own table first. Without that, the only key available is the
        // vanilla entity it is built on, which a castle lord shares with every evoker standing in a
        // courtyard - and mapping EVOKER to a thousand-health boss turns all of them into one.
        java.util.List<String> candidates = null;
        if (request.miniboss() && request.name() != null) {
            candidates = config.bossMobTable().get(request.name().toUpperCase(Locale.ROOT));
        }
        if (candidates == null || candidates.isEmpty()) {
            candidates = table.get(request.entityType().toUpperCase(Locale.ROOT));
        }
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        // Part of the garrison stays vanilla where the config asks for a mix - and decided from the
        // position, not from a coin flip, because the same castle has to hold the same guards every
        // time its chunk is generated. A named boss is exempt: a castle lord that is a plain evoker
        // one time in three is not a mix, it is a broken structure.
        if (!request.miniboss()
                && !MobMix.replacesAt(config.replaceChance(), request.x(), request.y(), request.z())) {
            return null;
        }
        // Chosen from the position, not from a shared random: the same site must produce the same
        // garrison every time it is generated, exactly like everything else here.
        long roll = Hashing.hash3(0x60B1_1A5L, request.x(), request.y(), request.z());
        String chosen = candidates.get((int) Math.floorMod(roll, candidates.size()));
        return MythicBridge.spawn(chosen, location, Math.max(1, request.tier()));
    }

    private static void applyBoss(LivingEntity living, MobSpawn request, ArkConfig config,
                                  FastRandom random, int tier) {
        double healthFactor = config.minibossHealthMultiplier() * (1.0 + tier * 0.35);
        double damageFactor = config.minibossDamageMultiplier() * (1.0 + tier * 0.20);

        scale(living, Attribute.MAX_HEALTH, healthFactor);
        scale(living, Attribute.ATTACK_DAMAGE, damageFactor);
        scale(living, Attribute.ARMOR, 1.0 + tier * 0.6);
        scale(living, Attribute.ARMOR_TOUGHNESS, 1.0 + tier * 0.5);
        scale(living, Attribute.KNOCKBACK_RESISTANCE, 1.0 + tier * 0.4);
        scale(living, Attribute.FOLLOW_RANGE, 1.6);
        scale(living, Attribute.MOVEMENT_SPEED, 1.08);
        living.setHealth(maxHealth(living));

        String name = config.minibossName(request.name() == null ? "miniboss" : request.name());
        living.setCustomName(name);
        living.setCustomNameVisible(true);
        living.setGlowing(config.minibossGlowing());
        living.setRemoveWhenFarAway(false);

        living.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE,
                Math.min(2, tier / 2), true, false));
        if (tier >= 2) {
            living.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0, true, false));
        }
        if (tier >= 3) {
            living.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, true, false));
            living.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 0, true, false));
        }
        if (tier >= 4) {
            living.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, true, false));
        }

        equip(living, random, tier);
    }

    private static void equip(LivingEntity living, FastRandom random, int tier) {
        EntityEquipment equipment = living.getEquipment();
        if (equipment == null) {
            return;
        }
        Material weapon = tier >= 4 ? Material.NETHERITE_SWORD
                : tier >= 3 ? Material.DIAMOND_SWORD
                : tier >= 2 ? Material.IRON_SWORD : Material.STONE_SWORD;
        ItemStack blade = new ItemStack(weapon);
        ItemMeta meta = blade.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.SHARPNESS, Math.min(5, tier + 1), true);
            if (tier >= 3) {
                meta.addEnchant(Enchantment.FIRE_ASPECT, 1, true);
            }
            blade.setItemMeta(meta);
        }
        equipment.setItemInMainHand(blade);
        equipment.setItemInMainHandDropChance(tier >= 3 ? 0.25f : 0.10f);

        Material armourTier = tier >= 4 ? Material.NETHERITE_CHESTPLATE
                : tier >= 3 ? Material.DIAMOND_CHESTPLATE
                : tier >= 2 ? Material.IRON_CHESTPLATE : Material.CHAINMAIL_CHESTPLATE;
        equipment.setChestplate(protectedPiece(armourTier, tier));
        equipment.setHelmet(protectedPiece(helmetFor(tier), tier));
        equipment.setChestplateDropChance(tier >= 3 ? 0.20f : 0.08f);
        equipment.setHelmetDropChance(0.08f);
    }

    private static Material helmetFor(int tier) {
        return tier >= 4 ? Material.NETHERITE_HELMET
                : tier >= 3 ? Material.DIAMOND_HELMET
                : tier >= 2 ? Material.IRON_HELMET : Material.CHAINMAIL_HELMET;
    }

    private static ItemStack protectedPiece(Material material, int tier) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.PROTECTION, Math.min(4, tier), true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * The highest health the game will accept on an attribute.
     *
     * <p>Past this, {@code setBaseValue} throws. The multipliers here are config, and the config can
     * say anything: at a health multiplier of thirty a tier four boss asks for 1440, the call throws,
     * the spawn is abandoned and the server gets a warning for every miniboss in the world. Clamping
     * gives a very tough boss instead of no boss and a log full of stack traces.</p>
     */
    private static final double HEALTH_CEILING = 1024.0;

    private static void scale(LivingEntity living, Attribute attribute, double factor) {
        AttributeInstance instance = living.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        double base = instance.getBaseValue();
        if (base <= 0.0 && attribute == Attribute.KNOCKBACK_RESISTANCE) {
            instance.setBaseValue(Math.min(1.0, factor - 1.0));
            return;
        }
        if (base <= 0.0) {
            base = attribute == Attribute.ARMOR || attribute == Attribute.ARMOR_TOUGHNESS ? 2.0 : base;
        }
        double scaled = base * factor;
        if (attribute == Attribute.MAX_HEALTH) {
            scaled = Math.min(scaled, HEALTH_CEILING);
        }
        instance.setBaseValue(scaled);
    }

    private static double maxHealth(LivingEntity living) {
        AttributeInstance instance = living.getAttribute(Attribute.MAX_HEALTH);
        return instance == null ? living.getHealth() : instance.getValue();
    }
}
