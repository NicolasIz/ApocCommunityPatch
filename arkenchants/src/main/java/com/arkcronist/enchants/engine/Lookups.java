package com.arkcronist.enchants.engine;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;

/** Names from old AdvancedEnchantments configs (SLOW, SMOKE_LARGE, PIG_ZOMBIE...) to 1.21 registry entries. */
public final class Lookups {

    private static final Map<String, String> POTIONS = Map.ofEntries(
            Map.entry("SLOW", "slowness"), Map.entry("FAST_DIGGING", "haste"), Map.entry("SLOW_DIGGING", "mining_fatigue"),
            Map.entry("INCREASE_DAMAGE", "strength"), Map.entry("HEAL", "instant_health"), Map.entry("HARM", "instant_damage"),
            Map.entry("JUMP", "jump_boost"), Map.entry("CONFUSION", "nausea"), Map.entry("DAMAGE_RESISTANCE", "resistance"),
            Map.entry("UNLUCK", "unluck"));
    private static final Map<String, String> PARTICLES = Map.ofEntries(
            Map.entry("SMOKE_LARGE", "LARGE_SMOKE"), Map.entry("SMOKE_NORMAL", "SMOKE"), Map.entry("VILLAGER_HAPPY", "HAPPY_VILLAGER"),
            Map.entry("VILLAGER_ANGRY", "ANGRY_VILLAGER"), Map.entry("SPELL_WITCH", "WITCH"), Map.entry("SPELL", "EFFECT"),
            Map.entry("SPELL_INSTANT", "INSTANT_EFFECT"), Map.entry("SPELL_MOB", "ENTITY_EFFECT"), Map.entry("FIREWORKS_SPARK", "FIREWORK"),
            Map.entry("EXPLOSION_LARGE", "EXPLOSION"), Map.entry("EXPLOSION_HUGE", "EXPLOSION_EMITTER"), Map.entry("EXPLOSION_NORMAL", "POOF"),
            Map.entry("REDSTONE", "DUST"), Map.entry("BLOCK_CRACK", "BLOCK"), Map.entry("BLOCK_DUST", "BLOCK"), Map.entry("ITEM_CRACK", "ITEM"),
            Map.entry("DRIP_LAVA", "DRIPPING_LAVA"), Map.entry("DRIP_WATER", "DRIPPING_WATER"), Map.entry("WATER_SPLASH", "SPLASH"),
            Map.entry("WATER_BUBBLE", "BUBBLE"), Map.entry("SUSPENDED", "UNDERWATER"), Map.entry("TOWN_AURA", "MYCELIUM"),
            Map.entry("ENCHANTMENT_TABLE", "ENCHANT"), Map.entry("SLIME", "ITEM_SLIME"), Map.entry("SNOWBALL", "ITEM_SNOWBALL"),
            Map.entry("TOTEM", "TOTEM_OF_UNDYING"), Map.entry("MOB_APPEARANCE", "ELDER_GUARDIAN"));
    private static final Map<String, String> ENTITIES = Map.ofEntries(
            Map.entry("PIG_ZOMBIE", "ZOMBIFIED_PIGLIN"), Map.entry("SNOWMAN", "SNOW_GOLEM"), Map.entry("MUSHROOM_COW", "MOOSHROOM"),
            Map.entry("ENDER_CRYSTAL", "END_CRYSTAL"), Map.entry("PRIMED_TNT", "TNT"), Map.entry("LIGHTNING", "LIGHTNING_BOLT"));
    private static Map<String, Sound> sounds;

    private Lookups() {
    }

    public static PotionEffectType potion(String name) {
        String n = name.trim().toUpperCase(Locale.ROOT);
        String key = POTIONS.getOrDefault(n, n.toLowerCase(Locale.ROOT));
        PotionEffectType t = Registry.EFFECT.get(NamespacedKey.minecraft(key));
        return t;
    }

    public static Particle particle(String name) {
        String n = name.trim().toUpperCase(Locale.ROOT);
        n = PARTICLES.getOrDefault(n, n);
        try {
            return Particle.valueOf(n);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static synchronized Sound sound(String name) {
        if (sounds == null) {
            sounds = new HashMap<>();
            for (Sound s : Registry.SOUNDS) {
                NamespacedKey k = Registry.SOUNDS.getKey(s);
                if (k != null) {
                    sounds.put(k.getKey().replace('.', '_').toUpperCase(Locale.ROOT), s);
                }
            }
        }
        String n = name.trim().toUpperCase(Locale.ROOT).replace('.', '_');
        return sounds.get(n);
    }

    public static EntityType entity(String name) {
        String n = name.trim().toUpperCase(Locale.ROOT);
        n = ENTITIES.getOrDefault(n, n);
        try {
            return EntityType.valueOf(n);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
