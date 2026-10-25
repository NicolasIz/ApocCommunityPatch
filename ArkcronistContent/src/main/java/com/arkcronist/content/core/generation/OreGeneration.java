package com.arkcronist.content.core.generation;

import java.util.Locale;
import java.util.Set;

/**
 * 5.0: where a custom block is laid into new chunks as an ore - one entry of {@code block:
 * generation:} in a content file. Nothing here touches Bukkit: names are as written, in lower case,
 * keys with their namespace.
 *
 * @param worlds        world names it is laid in; empty: every world of {@code environments}
 * @param environments  {@code normal}, {@code nether}, {@code the_end}, {@code custom}; empty: every
 *                      environment when worlds are named, else {@code normal}
 * @param biomes        biome keys, e.g. {@code minecraft:plains}; empty: every biome
 * @param minY          the lowest y a vein reaches, inclusive
 * @param maxY          the highest y a vein reaches, inclusive
 * @param veinsPerChunk tries at a vein in each chunk
 * @param veinSize      blocks a vein is grown to, at most; fewer when it runs into anything not in
 *                      {@code replace}
 * @param chance        the chance each try makes a vein, 0 to 1
 * @param replace       block keys a vein may take the place of, e.g. {@code minecraft:stone}
 */
public record OreGeneration(Set<String> worlds, Set<String> environments, Set<String> biomes, int minY, int maxY,
                            int veinsPerChunk, int veinSize, double chance, Set<String> replace) {

    /** What a vein takes the place of when {@code replace} is not given: the stones ores sit in. */
    public static final Set<String> STONES = Set.of("minecraft:stone", "minecraft:deepslate", "minecraft:granite",
            "minecraft:diorite", "minecraft:andesite", "minecraft:tuff", "minecraft:netherrack",
            "minecraft:end_stone");
    public static final Set<String> ENVIRONMENTS = Set.of("normal", "nether", "the_end", "custom");

    public OreGeneration {
        worlds = Set.copyOf(worlds);
        environments = Set.copyOf(environments);
        biomes = Set.copyOf(biomes);
        replace = Set.copyOf(replace);
        if (minY > maxY || veinsPerChunk < 0 || veinSize < 1 || !(chance >= 0 && chance <= 1)) {
            throw new IllegalArgumentException("y " + minY + ".." + maxY + ", " + veinsPerChunk + " vein(s) of "
                    + veinSize + ", chance " + chance);
        }
    }

    /**
     * Whether it is laid in the world called {@code world}, of {@code environment} (a Bukkit
     * environment's name, in any case).
     */
    public boolean appliesTo(String world, String environment) {
        String env = environment.toLowerCase(Locale.ROOT);
        if (!worlds.isEmpty() && !worlds.contains(world.toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (environments.isEmpty()) {
            return !worlds.isEmpty() || env.equals("normal");
        }
        return environments.contains(env);
    }

    /** Whether a vein grows into a block of this key; an empty biome list allows every biome. */
    public boolean inBiome(String biome) {
        return biomes.isEmpty() || biomes.contains(biome);
    }

    /**
     * A block or biome key as it is compared: lower case, with {@code minecraft:} when it has no
     * namespace. {@code STONE} and {@code minecraft:stone} are the same.
     */
    public static String key(String raw) {
        String key = raw.trim().toLowerCase(Locale.ROOT);
        return key.indexOf(':') < 0 ? "minecraft:" + key : key;
    }
}
