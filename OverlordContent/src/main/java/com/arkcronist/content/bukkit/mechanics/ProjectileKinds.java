package com.arkcronist.content.bukkit.mechanics;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LlamaSpit;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.Trident;
import org.bukkit.entity.WindCharge;
import org.bukkit.entity.WitherSkull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/** 5.4: the projectiles a {@code projectile:} action shoots, by name. */
final class ProjectileKinds {

    private static final Map<String, Class<? extends Projectile>> KINDS = Map.ofEntries(
            Map.entry("arrow", Arrow.class),
            Map.entry("spectral_arrow", SpectralArrow.class),
            Map.entry("snowball", Snowball.class),
            Map.entry("egg", Egg.class),
            Map.entry("ender_pearl", EnderPearl.class),
            Map.entry("fireball", LargeFireball.class),
            Map.entry("small_fireball", SmallFireball.class),
            Map.entry("dragon_fireball", DragonFireball.class),
            Map.entry("wither_skull", WitherSkull.class),
            Map.entry("trident", Trident.class),
            Map.entry("wind_charge", WindCharge.class),
            Map.entry("llama_spit", LlamaSpit.class));

    static final String NAMES = String.join(", ", new java.util.TreeSet<>(KINDS.keySet()));

    private ProjectileKinds() {
    }

    static @Nullable Class<? extends Projectile> of(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (key.startsWith("minecraft:")) {
            key = key.substring("minecraft:".length());
        }
        return KINDS.get(key);
    }
}
