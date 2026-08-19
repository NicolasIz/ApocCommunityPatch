package com.arkcronist.gen.core.biome;

import com.arkcronist.gen.core.block.Palette;

import java.util.ArrayList;
import java.util.List;

/**
 * A generator biome: climate target, gates, materials, vegetation and decoration in one place.
 *
 * <p>A biome here is not "a vanilla biome with a different colour". It owns its surface stack, its
 * stone, its tree species, its decoration densities, its ore bias and which structures may spawn in
 * it, so two biomes with the same vanilla key can still look completely different on the ground.</p>
 */
public final class ArkBiome {

    public final int id;
    public final String name;
    public final String vanillaKey;
    public final BiomeCategory category;

    // Climate target: biomes compete for a column by distance to these values.
    public final double temperature;
    public final double humidity;
    public final double weirdness;
    public final double weirdnessWeight;

    // Gates: hard-ish constraints that keep deserts out of the ocean and kelp off the peaks.
    public final double minLand;
    public final double maxLand;
    public final double minOceanT;
    public final double maxOceanT;
    public final double minMountain;
    public final double maxMountain;
    public final double minHeight;
    public final double maxHeight;
    public final boolean river;

    // Materials
    public final Palette surface;
    public final Palette subsurface;
    public final Palette underwater;
    public final Palette stone;
    public final int surfaceDepth;
    public final double surfaceRoughness;

    // Vegetation
    /** Species tags handed to the prefab registry; see {@link com.arkcronist.gen.core.prefab.TreeKind}. */
    public final String[] trees;
    public final double[] treeWeights;
    public final double treeDensity;
    /** How often this biome reaches for the largest prefab it can find. */
    public final double giantTreeChance;
    /** How often this biome asks for a bare, leafless tree instead of its usual species. */
    public final double deadTreeChance;

    public final DecorationProfile decoration;

    // Ore bias, multiplied on top of the global ore rates.
    public final double coalBonus;
    public final double ironBonus;
    public final double copperBonus;
    public final double goldBonus;
    public final double redstoneBonus;
    public final double lapisBonus;
    public final double diamondBonus;
    public final double emeraldBonus;

    public final java.util.EnumSet<StructureTag> structures;

    private ArkBiome(Builder builder, int id) {
        this.id = id;
        this.name = builder.name;
        this.vanillaKey = builder.vanillaKey;
        this.category = builder.category;
        this.temperature = builder.temperature;
        this.humidity = builder.humidity;
        this.weirdness = builder.weirdness;
        this.weirdnessWeight = builder.weirdnessWeight;
        this.minLand = builder.minLand;
        this.maxLand = builder.maxLand;
        this.minOceanT = builder.minOceanT;
        this.maxOceanT = builder.maxOceanT;
        this.minMountain = builder.minMountain;
        this.maxMountain = builder.maxMountain;
        this.minHeight = builder.minHeight;
        this.maxHeight = builder.maxHeight;
        this.river = builder.river;
        this.surface = builder.surface;
        this.subsurface = builder.subsurface;
        this.underwater = builder.underwater;
        this.stone = builder.stone;
        this.surfaceDepth = builder.surfaceDepth;
        this.surfaceRoughness = builder.surfaceRoughness;
        this.trees = builder.trees.toArray(new String[0]);
        this.treeWeights = new double[builder.treeWeights.size()];
        for (int i = 0; i < treeWeights.length; i++) {
            this.treeWeights[i] = builder.treeWeights.get(i);
        }
        this.treeDensity = builder.treeDensity;
        this.giantTreeChance = builder.giantTreeChance;
        this.deadTreeChance = builder.deadTreeChance;
        this.decoration = builder.decoration;
        this.coalBonus = builder.coalBonus;
        this.ironBonus = builder.ironBonus;
        this.copperBonus = builder.copperBonus;
        this.goldBonus = builder.goldBonus;
        this.redstoneBonus = builder.redstoneBonus;
        this.lapisBonus = builder.lapisBonus;
        this.diamondBonus = builder.diamondBonus;
        this.emeraldBonus = builder.emeraldBonus;
        this.structures = builder.structures;
    }

    public boolean oceanic() {
        return category.oceanic();
    }

    /** Picks a species tag for one tree from the biome's weighted list. */
    public String pickTree(double random01) {
        if (trees.length == 0) {
            return null;
        }
        double total = 0.0;
        for (double weight : treeWeights) {
            total += weight;
        }
        double target = random01 * total;
        double running = 0.0;
        for (int i = 0; i < trees.length; i++) {
            running += treeWeights[i];
            if (target < running) {
                return trees[i];
            }
        }
        return trees[trees.length - 1];
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    /** Fluent builder; every field has a usable default so definitions stay short and readable. */
    public static final class Builder {

        private final String name;
        private String vanillaKey = "minecraft:plains";
        private BiomeCategory category = BiomeCategory.PLAINS;
        private double temperature = 0.0;
        private double humidity = 0.0;
        private double weirdness = 0.0;
        private double weirdnessWeight = 0.35;
        private double minLand = 0.55;
        private double maxLand = 1.0;
        private double minOceanT = 0.0;
        private double maxOceanT = 1.0;
        private double minMountain = 0.0;
        private double maxMountain = 1.0;
        private double minHeight = -256.0;
        private double maxHeight = 512.0;
        private boolean river = false;
        private Palette surface = Palette.single(com.arkcronist.gen.core.block.Blocks.GRASS_BLOCK);
        private Palette subsurface = Palette.single(com.arkcronist.gen.core.block.Blocks.DIRT);
        private Palette underwater = Palette.single(com.arkcronist.gen.core.block.Blocks.GRAVEL);
        private Palette stone = Palette.single(com.arkcronist.gen.core.block.Blocks.STONE);
        private int surfaceDepth = 4;
        private double surfaceRoughness = 1.0;
        private final List<String> trees = new ArrayList<>();
        private final List<Double> treeWeights = new ArrayList<>();
        private double treeDensity = 0.0;
        private double giantTreeChance = 0.02;
        private double deadTreeChance = 0.03;
        private DecorationProfile decoration = new DecorationProfile();
        private double coalBonus = 1.0;
        private double ironBonus = 1.0;
        private double copperBonus = 1.0;
        private double goldBonus = 1.0;
        private double redstoneBonus = 1.0;
        private double lapisBonus = 1.0;
        private double diamondBonus = 1.0;
        private double emeraldBonus = 1.0;
        private java.util.EnumSet<StructureTag> structures = java.util.EnumSet.noneOf(StructureTag.class);

        private Builder(String name) {
            this.name = name;
        }

        public Builder vanilla(String key) {
            this.vanillaKey = key;
            return this;
        }

        public Builder category(BiomeCategory category) {
            this.category = category;
            return this;
        }

        public Builder climate(double temperature, double humidity) {
            this.temperature = temperature;
            this.humidity = humidity;
            return this;
        }

        public Builder weird(double weirdness, double weight) {
            this.weirdness = weirdness;
            this.weirdnessWeight = weight;
            return this;
        }

        public Builder land(double min, double max) {
            this.minLand = min;
            this.maxLand = max;
            return this;
        }

        public Builder ocean(double minOceanT, double maxOceanT) {
            this.minOceanT = minOceanT;
            this.maxOceanT = maxOceanT;
            return this;
        }

        public Builder mountain(double min, double max) {
            this.minMountain = min;
            this.maxMountain = max;
            return this;
        }

        public Builder height(double min, double max) {
            this.minHeight = min;
            this.maxHeight = max;
            return this;
        }

        public Builder river() {
            this.river = true;
            return this;
        }

        public Builder surface(Palette palette) {
            this.surface = palette;
            return this;
        }

        public Builder subsurface(Palette palette) {
            this.subsurface = palette;
            return this;
        }

        public Builder underwater(Palette palette) {
            this.underwater = palette;
            return this;
        }

        public Builder stone(Palette palette) {
            this.stone = palette;
            return this;
        }

        public Builder surfaceDepth(int depth) {
            this.surfaceDepth = depth;
            return this;
        }

        public Builder roughness(double roughness) {
            this.surfaceRoughness = roughness;
            return this;
        }

        public Builder tree(String species, double weight) {
            this.trees.add(species);
            this.treeWeights.add(weight);
            return this;
        }

        public Builder treeDensity(double density) {
            this.treeDensity = density;
            return this;
        }

        /**
         * @param giant chance a tree here reaches for the biggest prefab available
         * @param dead  chance a tree here is a bare, leafless one instead of the usual species
         */
        public Builder treeVariants(double giant, double dead) {
            this.giantTreeChance = giant;
            this.deadTreeChance = dead;
            return this;
        }

        public Builder decoration(DecorationProfile profile) {
            this.decoration = profile;
            return this;
        }

        public Builder ores(double coal, double iron, double copper, double gold, double redstone,
                            double lapis, double diamond, double emerald) {
            this.coalBonus = coal;
            this.ironBonus = iron;
            this.copperBonus = copper;
            this.goldBonus = gold;
            this.redstoneBonus = redstone;
            this.lapisBonus = lapis;
            this.diamondBonus = diamond;
            this.emeraldBonus = emerald;
            return this;
        }

        public Builder structures(StructureTag... tags) {
            this.structures = tags.length == 0
                    ? java.util.EnumSet.noneOf(StructureTag.class)
                    : java.util.EnumSet.of(tags[0], tags);
            return this;
        }

        public ArkBiome build(int id) {
            return new ArkBiome(this, id);
        }
    }
}
