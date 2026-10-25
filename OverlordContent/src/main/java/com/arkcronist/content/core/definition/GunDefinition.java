package com.arkcronist.content.core.definition;

import com.arkcronist.content.core.ballistics.DamageFalloff;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * An item that shoots: a right click traces its pellets along where the player looks, and hurts
 * what they meet.
 *
 * @param damage             one pellet's damage at close range, in half hearts
 * @param headshotMultiplier what a pellet entering through the head multiplies its damage by
 * @param falloff            how damage fades with distance, and how far a shot reaches
 * @param pellets            pellets per shot: 1 for a rifle, 6 to 12 for a shotgun
 * @param spread             how far, in degrees, a pellet may stray from where the player looks
 * @param sneakSpread        the same while sneaking - steadier aim
 * @param pierce             how many targets a pellet goes on through after the first
 * @param magazine           shots before reloading
 * @param ammo               the item a reload uses up, one per shot loaded: a custom item's id
 *                           (bare ids are looked for in the gun's own namespace first) or a vanilla
 *                           material; null for no ammunition at all, as with a magic wand
 * @param reloadTicks        how long a reload takes
 * @param fireDelayTicks     the least time between two shots
 * @param recoil             how the shooter's view jumps with each shot
 * @param damageType         the vanilla damage type the hit is dealt as, e.g. {@code minecraft:arrow}
 * @param targets            what can be hit
 * @param particle           drawn along each pellet's path, or null for none
 * @param sounds             played where they happen, heard by everyone nearby
 */
public record GunDefinition(double damage, double headshotMultiplier, DamageFalloff falloff, int pellets,
                            double spread, double sneakSpread, int pierce, int magazine, @Nullable String ammo,
                            int reloadTicks, int fireDelayTicks, Recoil recoil, String damageType,
                            Set<TargetKind> targets, @Nullable String particle, Sounds sounds) {

    public GunDefinition {
        targets = Set.copyOf(targets);
    }

    public double range() {
        return falloff.range();
    }

    /** How many rounds a reload loads: what the magazine lacks, as far as {@code available} goes. */
    public int reloadAmount(int loaded, int available) {
        return Math.max(0, Math.min(magazine - Math.max(0, loaded), Math.max(0, available)));
    }

    /**
     * How the view kicks: up by {@code pitch} degrees, and sideways by up to {@code yaw} degrees
     * either way.
     */
    public record Recoil(double pitch, double yaw) {

        public static final Recoil NONE = new Recoil(0, 0);

        /** One shot's kick: degrees to add to the yaw, and to the pitch (negative is up). */
        public double[] kick(RandomGenerator random) {
            double sideways = yaw == 0 ? 0 : (random.nextDouble() * 2 - 1) * yaw;
            return new double[]{sideways, -pitch};
        }
    }

    /** What a gun can hit. */
    public enum TargetKind {
        PLAYERS, MYTHIC_MOBS, MOBS;

        public static Optional<TargetKind> parse(String raw) {
            String name = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if (name.equals("MYTHICMOBS")) {
                name = "MYTHIC_MOBS";
            }
            for (TargetKind kind : values()) {
                if (kind.name().equals(name)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * A sound, by its key - vanilla's ({@code entity.generic.explode}) or a pack's own.
     */
    public record Sound(String key, float volume, float pitch) {
    }

    /** The sounds of a gun; any may be null for silence. */
    public record Sounds(@Nullable Sound shoot, @Nullable Sound empty, @Nullable Sound reload,
                         @Nullable Sound reloaded, @Nullable Sound hit, @Nullable Sound headshot) {

        public static final Sounds DEFAULT = new Sounds(
                new Sound("minecraft:entity.firework_rocket.blast", 1.6f, 1.4f),
                new Sound("minecraft:block.dispenser.fail", 0.8f, 1.6f),
                new Sound("minecraft:item.crossbow.loading_start", 1f, 1f),
                new Sound("minecraft:item.crossbow.loading_end", 1f, 1.2f),
                new Sound("minecraft:entity.arrow.hit_player", 0.6f, 1f),
                new Sound("minecraft:block.anvil.land", 0.4f, 2f));

        /** The sound names a content file can set, in the order of the components. */
        public static final List<String> KEYS = List.of("shoot", "empty", "reload", "reloaded", "hit", "headshot");
    }
}
