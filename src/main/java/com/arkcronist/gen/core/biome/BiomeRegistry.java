package com.arkcronist.gen.core.biome;

import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.block.Palette;
import com.arkcronist.gen.core.prefab.TreeKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.arkcronist.gen.core.biome.StructureTag.*;

/**
 * The full biome table.
 *
 * <p>Each entry carries its own identity across every axis the generator can express: relief gates
 * (where it may appear on the height/mountain axes), climate target, surface stack, stone, trees,
 * decoration densities, ore bias and structure eligibility.</p>
 */
public final class BiomeRegistry {

    private final List<ArkBiome> biomes = new ArrayList<>();
    private final Map<String, ArkBiome> byName = new HashMap<>();

    public BiomeRegistry() {
        registerOceans();
        registerCoasts();
        registerWater();
        registerLowlands();
        registerWarmLands();
        registerColdLands();
        registerHighlands();
        registerExotic();
    }

    private ArkBiome register(ArkBiome.Builder builder) {
        ArkBiome biome = builder.build(biomes.size());
        biomes.add(biome);
        byName.put(biome.name, biome);
        return biome;
    }

    public List<ArkBiome> all() {
        return biomes;
    }

    public ArkBiome byId(int id) {
        return biomes.get(id);
    }

    public ArkBiome byName(String name) {
        return byName.get(name);
    }

    public int size() {
        return biomes.size();
    }

    private static DecorationProfile deco(Consumer<DecorationProfile> setup) {
        DecorationProfile profile = new DecorationProfile();
        setup.accept(profile);
        return profile;
    }

    // ------------------------------------------------------------------ oceans

    private void registerOceans() {
        register(ArkBiome.builder("warm_shelf")
                .vanilla("minecraft:warm_ocean").category(BiomeCategory.OCEAN_SHELF)
                .climate(0.65, 0.45).land(0.0, 0.45).ocean(0.0, 0.32)
                .surface(Palette.of(Blocks.SAND, 8.0, Blocks.GRAVEL, 1.0))
                .subsurface(Palette.single(Blocks.SAND))
                .underwater(Palette.of(Blocks.SAND, 6.0, Blocks.CLAY, 1.0))
                .surfaceDepth(4)
                .decoration(deco(d -> {
                    d.seagrass = 0.30;
                    d.coral = 0.16;
                    d.seaPickles = 0.05;
                    d.kelp = 0.04;
                    d.sponges = 0.01;
                }))
                .structures(UNDERWATER, RUINS, SHIPWRECK, SHIP));

        register(ArkBiome.builder("temperate_shelf")
                .vanilla("minecraft:ocean").category(BiomeCategory.OCEAN_SHELF)
                .climate(0.05, 0.35).land(0.0, 0.45).ocean(0.0, 0.34)
                .surface(Palette.of(Blocks.SAND, 5.0, Blocks.GRAVEL, 3.0, Blocks.CLAY, 1.0))
                .subsurface(Palette.of(Blocks.SAND, 3.0, Blocks.GRAVEL, 2.0))
                .decoration(deco(d -> {
                    d.seagrass = 0.26;
                    d.kelp = 0.14;
                }))
                .structures(UNDERWATER, RUINS, SHIPWRECK, SHIP));

        register(ArkBiome.builder("kelp_forest")
                .vanilla("minecraft:cold_ocean").category(BiomeCategory.OCEAN)
                .climate(-0.15, 0.7).land(0.0, 0.4).ocean(0.18, 0.6)
                .weird(0.45, 0.55)
                .surface(Palette.of(Blocks.GRAVEL, 5.0, Blocks.SAND, 2.0, Blocks.CLAY, 1.0))
                .decoration(deco(d -> {
                    d.kelp = 0.45;
                    d.seagrass = 0.22;
                    d.sponges = 0.01;
                }))
                .structures(UNDERWATER, SHIPWRECK, SHIP));

        register(ArkBiome.builder("open_ocean")
                .vanilla("minecraft:deep_ocean").category(BiomeCategory.OCEAN)
                .climate(0.0, 0.3).land(0.0, 0.4).ocean(0.30, 0.72)
                .surface(Palette.of(Blocks.GRAVEL, 6.0, Blocks.SAND, 3.0, Blocks.CLAY, 1.0))
                .decoration(deco(d -> {
                    d.seagrass = 0.10;
                    d.kelp = 0.05;
                }))
                .structures(UNDERWATER, RUINS, SHIPWRECK, MONUMENT, SHIP));

        register(ArkBiome.builder("frozen_ocean")
                .vanilla("minecraft:frozen_ocean").category(BiomeCategory.OCEAN)
                .climate(-0.85, 0.25).land(0.0, 0.4).ocean(0.10, 0.75)
                .surface(Palette.of(Blocks.GRAVEL, 6.0, Blocks.PACKED_ICE, 1.0))
                .decoration(deco(d -> {
                    d.iceSheet = 0.85;
                    d.seagrass = 0.04;
                }))
                .structures(UNDERWATER, SHIPWRECK, SHIP));

        register(ArkBiome.builder("abyssal_plain")
                .vanilla("minecraft:deep_ocean").category(BiomeCategory.OCEAN_DEEP)
                .climate(-0.1, 0.2).land(0.0, 0.35).ocean(0.70, 1.0)
                .surface(Palette.of(Blocks.GRAVEL, 4.0, Blocks.CLAY, 3.0, Blocks.TUFF, 2.0))
                .subsurface(Palette.of(Blocks.CLAY, 3.0, Blocks.TUFF, 2.0))
                .stone(Palette.of(Blocks.DEEPSLATE, 4.0, Blocks.TUFF, 2.0, Blocks.STONE, 1.0))
                .decoration(deco(d -> {
                    d.glowLichen = 0.02;
                    d.magmaVents = 0.02;
                }))
                .ores(1.0, 1.2, 1.4, 1.0, 1.2, 1.0, 1.1, 1.0)
                .structures(UNDERWATER, RUINS, MONUMENT, SHIP));

        register(ArkBiome.builder("ocean_trench")
                .vanilla("minecraft:deep_ocean").category(BiomeCategory.OCEAN_TRENCH)
                .climate(-0.05, 0.1).land(0.0, 0.3).ocean(0.86, 1.0)
                .height(-512.0, 10.0)
                .surface(Palette.of(Blocks.BLACKSTONE, 3.0, Blocks.TUFF, 3.0, Blocks.BASALT, 2.0, Blocks.GRAVEL, 1.0))
                .subsurface(Palette.of(Blocks.TUFF, 3.0, Blocks.BLACKSTONE, 2.0))
                .stone(Palette.of(Blocks.DEEPSLATE, 6.0, Blocks.BLACKSTONE, 2.0, Blocks.TUFF, 2.0))
                .decoration(deco(d -> {
                    d.magmaVents = 0.14;
                    d.glowLichen = 0.05;
                    d.dripstone = 0.03;
                }))
                .ores(1.0, 1.3, 1.6, 1.4, 1.5, 1.3, 1.8, 1.2)
                .structures(UNDERWATER, RUINS, MONUMENT, SHIP));

        register(ArkBiome.builder("seamount_ridge")
                .vanilla("minecraft:deep_ocean").category(BiomeCategory.OCEAN_DEEP)
                .climate(0.1, 0.25).land(0.0, 0.4).ocean(0.45, 1.0).mountain(0.28, 1.0)
                .surface(Palette.of(Blocks.BASALT, 4.0, Blocks.TUFF, 3.0, Blocks.GRAVEL, 2.0))
                .stone(Palette.of(Blocks.BASALT, 3.0, Blocks.DEEPSLATE, 3.0, Blocks.STONE, 2.0))
                .decoration(deco(d -> {
                    d.magmaVents = 0.08;
                    d.seagrass = 0.06;
                    d.coral = 0.03;
                }))
                .ores(1.0, 1.4, 1.8, 1.5, 1.0, 1.0, 1.2, 1.4)
                .structures(UNDERWATER, RUINS, MONUMENT, SHIP));
    }

    // ------------------------------------------------------------------ coasts

    private void registerCoasts() {
        register(ArkBiome.builder("beach")
                .vanilla("minecraft:beach").category(BiomeCategory.BEACH)
                .climate(0.35, 0.35).land(0.35, 0.72).height(-8.0, 12.0)
                .surface(Palette.of(Blocks.SAND, 9.0, Blocks.GRAVEL, 1.0))
                .subsurface(Palette.single(Blocks.SAND))
                .surfaceDepth(5)
                .decoration(deco(d -> {
                    d.grass = 0.02;
                    d.sugarCane = 0.03;
                    d.seagrass = 0.05;
                }))
                .structures(CAMP, RUINS, SHIPWRECK, TREASURE, RUINED_PORTAL, SHIP));

        register(ArkBiome.builder("snowy_beach")
                .vanilla("minecraft:snowy_beach").category(BiomeCategory.BEACH)
                .climate(-0.75, 0.35).land(0.35, 0.72).height(-8.0, 12.0)
                .surface(Palette.of(Blocks.SAND, 6.0, Blocks.SNOW_BLOCK, 3.0, Blocks.PACKED_ICE, 1.0))
                .subsurface(Palette.single(Blocks.SAND))
                .decoration(deco(d -> {
                    d.snowLayer = 0.85;
                    d.iceSheet = 0.25;
                }))
                .structures(CAMP, IGLOO, SHIPWRECK, TREASURE, SHIP));

        register(ArkBiome.builder("rocky_shore")
                .vanilla("minecraft:stony_shore").category(BiomeCategory.BEACH)
                .climate(0.0, 0.2).land(0.35, 0.75).mountain(0.18, 1.0).height(-6.0, 34.0)
                .surface(Palette.of(Blocks.STONE, 5.0, Blocks.GRAVEL, 3.0, Blocks.ANDESITE, 2.0))
                .subsurface(Palette.of(Blocks.STONE, 4.0, Blocks.GRAVEL, 1.0))
                .decoration(deco(d -> {
                    d.boulders = 0.02;
                    d.grass = 0.03;
                    d.glowLichen = 0.01;
                }))
                .structures(TOWER, RUINS, SHIPWRECK, RUINED_PORTAL, SHIP));

        register(ArkBiome.builder("volcanic_shore")
                .vanilla("minecraft:stony_shore").category(BiomeCategory.BEACH)
                .climate(0.5, 0.15).land(0.35, 0.72).height(-8.0, 16.0)
                .weird(-0.65, 0.6)
                .surface(Palette.of(Blocks.BLACKSTONE, 4.0, Blocks.BASALT, 3.0, Blocks.GRAVEL, 2.0))
                .subsurface(Palette.of(Blocks.BASALT, 3.0, Blocks.BLACKSTONE, 2.0))
                .stone(Palette.of(Blocks.BASALT, 4.0, Blocks.BLACKSTONE, 2.0, Blocks.STONE, 2.0))
                .decoration(deco(d -> {
                    d.magmaVents = 0.05;
                    d.deadBush = 0.02;
                }))
                .structures(RUINS, CAMP, RUINED_PORTAL));

        register(ArkBiome.builder("mangrove_coast")
                .vanilla("minecraft:mangrove_swamp").category(BiomeCategory.SWAMP)
                .climate(0.6, 0.85).land(0.4, 0.8).height(-4.0, 10.0)
                .surface(Palette.of(Blocks.MUD, 6.0, Blocks.GRASS_BLOCK, 2.0, Blocks.CLAY, 2.0))
                .subsurface(Palette.of(Blocks.MUD, 4.0, Blocks.CLAY, 3.0))
                .tree(TreeKind.MANGROVE, 1.0)
                .treeDensity(0.012)
                .treeVariants(0.02, 0.02)
                .decoration(deco(d -> {
                    d.grass = 0.18;
                    d.lilyPads = 0.06;
                    d.vines = 0.10;
                    d.mushrooms = 0.02;
                    d.sugarCane = 0.04;
                }))
                .structures(CAMP, RUINS, BRIDGE, WITCH_HUT));
    }

    // ------------------------------------------------------------------ rivers and lakes

    private void registerWater() {
        register(ArkBiome.builder("river")
                .vanilla("minecraft:river").category(BiomeCategory.RIVER)
                .climate(0.2, 0.6).land(0.35, 1.0).river()
                .surface(Palette.of(Blocks.GRAVEL, 4.0, Blocks.SAND, 4.0, Blocks.CLAY, 2.0))
                .subsurface(Palette.of(Blocks.GRAVEL, 3.0, Blocks.DIRT, 2.0))
                .decoration(deco(d -> {
                    d.seagrass = 0.10;
                    d.sugarCane = 0.06;
                    d.grass = 0.08;
                }))
                .structures(BRIDGE, CAMP, RUINS));

        register(ArkBiome.builder("frozen_river")
                .vanilla("minecraft:frozen_river").category(BiomeCategory.RIVER)
                .climate(-0.8, 0.5).land(0.35, 1.0).river()
                .surface(Palette.of(Blocks.GRAVEL, 4.0, Blocks.PACKED_ICE, 2.0, Blocks.SAND, 2.0))
                .decoration(deco(d -> {
                    d.iceSheet = 0.9;
                    d.snowLayer = 0.7;
                }))
                .structures(BRIDGE, IGLOO));
    }

    // ------------------------------------------------------------------ temperate lowlands

    private void registerLowlands() {
        register(ArkBiome.builder("plains")
                .vanilla("minecraft:plains").category(BiomeCategory.PLAINS)
                .climate(0.25, 0.15).land(0.7, 1.0).mountain(0.0, 0.30).height(2.0, 90.0)
                .surface(Palette.of(Blocks.GRASS_BLOCK, 1.0))
                .subsurface(Palette.of(Blocks.DIRT, 6.0, Blocks.COARSE_DIRT, 1.0))
                .tree(TreeKind.OAK, 6.0).tree(TreeKind.BIG_OAK, 1.0)
                .treeDensity(0.0018)
                .decoration(deco(d -> {
                    d.grass = 0.45;
                    d.tallGrass = 0.06;
                    d.flowers = 0.05;
                    d.pumpkinPatch = 0.002;
                    d.boulders = 0.002;
                }))
                .structures(VILLAGE, CITY, CAMP, TOWER, RUINS, BATTLE_TOWER, OUTPOST, TRAIL_RUINS, RUINED_PORTAL, PREFAB_RUIN));

        register(ArkBiome.builder("flower_meadow")
                .vanilla("minecraft:meadow").category(BiomeCategory.PLAINS)
                .climate(0.3, 0.45).land(0.7, 1.0).mountain(0.0, 0.35).height(8.0, 110.0)
                .weird(0.55, 0.5)
                .surface(Palette.single(Blocks.GRASS_BLOCK))
                .tree(TreeKind.OAK, 1.0).tree(TreeKind.CHERRY, 2.0)
                .treeDensity(0.0008)
                .decoration(deco(d -> {
                    d.grass = 0.55;
                    d.flowers = 0.35;
                    d.tallGrass = 0.08;
                }))
                .structures(VILLAGE, CAMP, TOWER, TRAIL_RUINS));

        register(ArkBiome.builder("temperate_forest")
                .vanilla("minecraft:forest").category(BiomeCategory.FOREST)
                .climate(0.2, 0.55).land(0.7, 1.0).mountain(0.0, 0.40).height(2.0, 105.0)
                .surface(Palette.of(Blocks.GRASS_BLOCK, 12.0, Blocks.COARSE_DIRT, 1.0))
                .subsurface(Palette.of(Blocks.DIRT, 8.0, Blocks.ROOTED_DIRT, 1.0))
                .tree(TreeKind.OAK, 5.0).tree(TreeKind.BIRCH, 3.0).tree(TreeKind.BIG_OAK, 2.0)
                .treeDensity(0.016)
                .treeVariants(0.05, 0.04)
                .decoration(deco(d -> {
                    d.grass = 0.35;
                    d.tallGrass = 0.08;
                    d.flowers = 0.06;
                    d.mushrooms = 0.02;
                    d.deadLogs = 0.006;
                    d.berryBush = 0.006;
                }))
                .structures(VILLAGE, CAMP, TOWER, RUINS, BATTLE_TOWER, CASTLE, OUTPOST, RUINED_PORTAL));

        register(ArkBiome.builder("birch_woods")
                .vanilla("minecraft:birch_forest").category(BiomeCategory.FOREST)
                .climate(0.1, 0.6).land(0.7, 1.0).mountain(0.0, 0.35).height(2.0, 100.0)
                .weird(0.4, 0.5)
                .tree(TreeKind.BIRCH, 9.0).tree(TreeKind.OAK, 1.0)
                .treeDensity(0.017)
                .decoration(deco(d -> {
                    d.grass = 0.40;
                    d.flowers = 0.08;
                    d.mushrooms = 0.02;
                    d.deadLogs = 0.004;
                }))
                .structures(VILLAGE, CAMP, RUINS, TOWER, TRAIL_RUINS));

        register(ArkBiome.builder("dark_forest")
                .vanilla("minecraft:dark_forest").category(BiomeCategory.FOREST)
                .climate(0.05, 0.75).land(0.75, 1.0).mountain(0.0, 0.35).height(2.0, 95.0)
                .weird(-0.5, 0.5)
                .surface(Palette.of(Blocks.GRASS_BLOCK, 8.0, Blocks.PODZOL, 3.0, Blocks.COARSE_DIRT, 1.0))
                .tree(TreeKind.DARK_OAK, 7.0).tree(TreeKind.OAK, 2.0).tree(TreeKind.BIG_OAK, 1.0)
                .treeDensity(0.022)
                .treeVariants(0.06, 0.05)
                .decoration(deco(d -> {
                    d.grass = 0.22;
                    d.mushrooms = 0.09;
                    d.deadLogs = 0.010;
                    d.mossPatches = 0.05;
                }))
                .structures(CASTLE, RUINS, BATTLE_TOWER, TOWER, MANSION, PREFAB_RUIN));

        register(ArkBiome.builder("pale_grove")
                .vanilla("minecraft:pale_garden").category(BiomeCategory.FOREST)
                .climate(-0.05, 0.65).land(0.78, 1.0).mountain(0.0, 0.3).height(4.0, 90.0)
                .weird(-0.85, 0.75)
                .surface(Palette.of(Blocks.GRASS_BLOCK, 6.0, Blocks.PODZOL, 2.0))
                .tree(TreeKind.PALE_OAK, 8.0).tree(TreeKind.OAK, 1.0)
                .treeDensity(0.020)
                .decoration(deco(d -> {
                    d.grass = 0.18;
                    d.mushrooms = 0.06;
                    d.mossPatches = 0.08;
                    d.deadLogs = 0.012;
                }))
                .structures(RUINS, TOWER, MANSION));

        register(ArkBiome.builder("swamp")
                .vanilla("minecraft:swamp").category(BiomeCategory.SWAMP)
                .climate(0.35, 0.9).land(0.65, 1.0).mountain(0.0, 0.18).height(-2.0, 22.0)
                .surface(Palette.of(Blocks.GRASS_BLOCK, 5.0, Blocks.MUD, 4.0, Blocks.CLAY, 1.0))
                .subsurface(Palette.of(Blocks.MUD, 4.0, Blocks.CLAY, 3.0, Blocks.DIRT, 2.0))
                .tree(TreeKind.OAK, 6.0).tree(TreeKind.MANGROVE, 2.0)
                .treeDensity(0.011)
                .treeVariants(0.03, 0.10)
                .decoration(deco(d -> {
                    d.grass = 0.30;
                    d.lilyPads = 0.10;
                    d.mushrooms = 0.06;
                    d.vines = 0.12;
                    d.sugarCane = 0.05;
                    d.deadLogs = 0.010;
                }))
                .structures(WITCH_HUT, RUINS, CAMP, TOWER));

        register(ArkBiome.builder("moorland")
                .vanilla("minecraft:windswept_hills").category(BiomeCategory.PLAINS)
                .climate(-0.2, 0.4).land(0.7, 1.0).mountain(0.05, 0.45).height(10.0, 120.0)
                .weird(-0.35, 0.45)
                .surface(Palette.of(Blocks.GRASS_BLOCK, 6.0, Blocks.COARSE_DIRT, 3.0, Blocks.PODZOL, 1.0))
                .subsurface(Palette.of(Blocks.DIRT, 5.0, Blocks.GRAVEL, 2.0))
                .tree(TreeKind.OAK, 2.0).tree(TreeKind.SPRUCE, 1.0).tree(TreeKind.DEAD, 1.0)
                .treeDensity(0.003)
                .treeVariants(0.02, 0.25)
                .decoration(deco(d -> {
                    d.grass = 0.30;
                    d.fern = 0.10;
                    d.boulders = 0.008;
                    d.deadBush = 0.03;
                }))
                .structures(RUINS, CAMP, TOWER, FORTRESS, OUTPOST, PREFAB_RUIN));
    }

    // ------------------------------------------------------------------ warm lands

    private void registerWarmLands() {
        // Deliberately given a wide berth in the climate map and a low bar on land: a desert
        // pyramid needs a desert big enough to hold one, and at the old window the whole biome was
        // 3% of the world.
        register(ArkBiome.builder("desert")
                .vanilla("minecraft:desert").category(BiomeCategory.DESERT)
                .climate(0.78, -0.75).land(0.60, 1.0).mountain(0.0, 0.45).height(2.0, 120.0)
                .weird(0.0, 0.25)
                .surface(Palette.of(Blocks.SAND, 12.0, Blocks.SANDSTONE, 1.0))
                .subsurface(Palette.of(Blocks.SAND, 6.0, Blocks.SANDSTONE, 4.0))
                .stone(Palette.of(Blocks.SANDSTONE, 3.0, Blocks.STONE, 5.0))
                .surfaceDepth(7)
                .roughness(1.4)
                .decoration(deco(d -> {
                    d.cactus = 0.02;
                    d.deadBush = 0.05;
                    d.boulders = 0.001;
                }))
                .ores(0.8, 1.0, 1.1, 1.5, 0.9, 1.0, 1.0, 0.6)
                .structures(PYRAMID, VILLAGE, RUINS, CAMP, TOWER, TRAIL_RUINS, RUINED_PORTAL, PREFAB_RUIN));

        register(ArkBiome.builder("red_desert")
                .vanilla("minecraft:desert").category(BiomeCategory.DESERT)
                .climate(0.95, -0.7).land(0.72, 1.0).mountain(0.0, 0.4).height(4.0, 130.0)
                .weird(0.6, 0.5)
                .surface(Palette.of(Blocks.RED_SAND, 10.0, Blocks.RED_SANDSTONE, 2.0))
                .subsurface(Palette.of(Blocks.RED_SAND, 5.0, Blocks.RED_SANDSTONE, 5.0))
                .stone(Palette.of(Blocks.RED_SANDSTONE, 3.0, Blocks.STONE, 4.0, Blocks.TERRACOTTA, 2.0))
                .surfaceDepth(7)
                .decoration(deco(d -> {
                    d.cactus = 0.015;
                    d.deadBush = 0.06;
                }))
                .structures(PYRAMID, RUINS, CAMP, TOWER, TRAIL_RUINS));

        register(ArkBiome.builder("savanna")
                .vanilla("minecraft:savanna").category(BiomeCategory.SAVANNA)
                .climate(0.75, -0.25).land(0.72, 1.0).mountain(0.0, 0.35).height(2.0, 110.0)
                .surface(Palette.of(Blocks.GRASS_BLOCK, 10.0, Blocks.COARSE_DIRT, 3.0))
                .tree(TreeKind.ACACIA, 9.0).tree(TreeKind.OAK, 1.0)
                .treeDensity(0.005)
                .treeVariants(0.04, 0.06)
                .decoration(deco(d -> {
                    d.grass = 0.42;
                    d.tallGrass = 0.05;
                    d.deadBush = 0.01;
                }))
                .structures(VILLAGE, CAMP, RUINS, TOWER, BATTLE_TOWER, OUTPOST, TRAIL_RUINS));

        register(ArkBiome.builder("badlands")
                .vanilla("minecraft:badlands").category(BiomeCategory.BADLANDS)
                .climate(0.85, -0.6).land(0.7, 1.0).mountain(0.15, 0.75).height(20.0, 190.0)
                .weird(-0.6, 0.55)
                .surface(Palette.of(Blocks.RED_SAND, 4.0, Blocks.TERRACOTTA, 4.0, Blocks.ORANGE_TERRACOTTA, 2.0))
                .subsurface(Palette.of(Blocks.TERRACOTTA, 4.0, Blocks.RED_TERRACOTTA, 2.0, Blocks.YELLOW_TERRACOTTA, 2.0))
                .stone(Palette.of(Blocks.TERRACOTTA, 3.0, Blocks.STONE, 4.0, Blocks.RED_SANDSTONE, 2.0))
                .surfaceDepth(6)
                .roughness(1.6)
                .tree(TreeKind.DEAD, 1.0)
                .treeDensity(0.0008)
                .decoration(deco(d -> {
                    d.deadBush = 0.07;
                    d.cactus = 0.004;
                    d.boulders = 0.003;
                }))
                .ores(1.0, 1.0, 1.2, 2.2, 1.0, 1.0, 1.0, 1.0)
                .structures(TEMPLE, PYRAMID, RUINS, CAMP, TOWER, TRAIL_RUINS));

        register(ArkBiome.builder("jungle")
                .vanilla("minecraft:jungle").category(BiomeCategory.JUNGLE)
                .climate(0.8, 0.9).land(0.72, 1.0).mountain(0.0, 0.45).height(2.0, 110.0)
                .surface(Palette.of(Blocks.GRASS_BLOCK, 10.0, Blocks.PODZOL, 2.0, Blocks.MOSS_BLOCK, 1.0))
                .tree(TreeKind.JUNGLE, 7.0).tree(TreeKind.GIANT_JUNGLE, 2.0).tree(TreeKind.OAK, 1.0)
                .treeDensity(0.026)
                .treeVariants(0.12, 0.03)
                .decoration(deco(d -> {
                    d.grass = 0.55;
                    d.tallGrass = 0.12;
                    d.vines = 0.30;
                    d.melon = 0.006;
                    d.mushrooms = 0.03;
                    d.mossPatches = 0.10;
                }))
                .structures(JUNGLE_TEMPLE, RUINS, BRIDGE, TOWER, TRAIL_RUINS, PREFAB_RUIN));

        register(ArkBiome.builder("bamboo_jungle")
                .vanilla("minecraft:bamboo_jungle").category(BiomeCategory.JUNGLE)
                .climate(0.7, 0.95).land(0.75, 1.0).mountain(0.0, 0.35).height(2.0, 95.0)
                .weird(0.7, 0.6)
                .surface(Palette.of(Blocks.GRASS_BLOCK, 8.0, Blocks.PODZOL, 3.0))
                .tree(TreeKind.JUNGLE, 3.0)
                .treeDensity(0.011)
                .decoration(deco(d -> {
                    d.bamboo = 0.35;
                    d.grass = 0.30;
                    d.vines = 0.12;
                    d.mossPatches = 0.08;
                }))
                .structures(JUNGLE_TEMPLE, RUINS, CAMP));
    }

    // ------------------------------------------------------------------ cold lands

    private void registerColdLands() {
        register(ArkBiome.builder("taiga")
                .vanilla("minecraft:taiga").category(BiomeCategory.TAIGA)
                .climate(-0.45, 0.6).land(0.7, 1.0).mountain(0.0, 0.45).height(4.0, 130.0)
                .surface(Palette.of(Blocks.GRASS_BLOCK, 8.0, Blocks.PODZOL, 4.0, Blocks.COARSE_DIRT, 1.0))
                .tree(TreeKind.SPRUCE, 8.0).tree(TreeKind.GIANT_SPRUCE, 2.0)
                .treeDensity(0.018)
                .treeVariants(0.08, 0.06)
                .decoration(deco(d -> {
                    d.grass = 0.20;
                    d.fern = 0.22;
                    d.berryBush = 0.02;
                    d.mushrooms = 0.03;
                    d.deadLogs = 0.010;
                }))
                .structures(VILLAGE, CAMP, RUINS, FORTRESS, BATTLE_TOWER, OUTPOST));

        register(ArkBiome.builder("snowy_taiga")
                .vanilla("minecraft:snowy_taiga").category(BiomeCategory.TAIGA)
                .climate(-0.8, 0.55).land(0.7, 1.0).mountain(0.0, 0.5).height(6.0, 150.0)
                .surface(Palette.of(Blocks.GRASS_BLOCK, 5.0, Blocks.PODZOL, 3.0, Blocks.SNOW_BLOCK, 2.0))
                .tree(TreeKind.SPRUCE, 9.0).tree(TreeKind.GIANT_SPRUCE, 1.0)
                .treeDensity(0.015)
                .decoration(deco(d -> {
                    d.snowLayer = 0.95;
                    d.fern = 0.10;
                    d.grass = 0.05;
                    d.deadLogs = 0.008;
                }))
                .structures(VILLAGE, CAMP, FORTRESS, RUINS, IGLOO));

        register(ArkBiome.builder("tundra")
                .vanilla("minecraft:snowy_plains").category(BiomeCategory.TUNDRA)
                .climate(-0.9, -0.1).land(0.7, 1.0).mountain(0.0, 0.35).height(4.0, 120.0)
                .surface(Palette.of(Blocks.SNOW_BLOCK, 6.0, Blocks.GRASS_BLOCK, 3.0, Blocks.PACKED_ICE, 1.0))
                .subsurface(Palette.of(Blocks.DIRT, 5.0, Blocks.GRAVEL, 2.0))
                .tree(TreeKind.SPRUCE, 1.0).tree(TreeKind.DEAD, 1.0)
                .treeDensity(0.0015)
                .decoration(deco(d -> {
                    d.snowLayer = 1.0;
                    d.grass = 0.05;
                    d.boulders = 0.004;
                }))
                .structures(VILLAGE, CAMP, RUINS, FORTRESS, TOWER, IGLOO));

        register(ArkBiome.builder("glacier")
                .vanilla("minecraft:ice_spikes").category(BiomeCategory.TUNDRA)
                .climate(-1.0, 0.15).land(0.72, 1.0).mountain(0.0, 0.55).height(10.0, 200.0)
                .weird(0.8, 0.7)
                .surface(Palette.of(Blocks.SNOW_BLOCK, 5.0, Blocks.PACKED_ICE, 4.0, Blocks.BLUE_ICE, 1.0))
                .subsurface(Palette.of(Blocks.PACKED_ICE, 5.0, Blocks.SNOW_BLOCK, 3.0))
                .surfaceDepth(8)
                .decoration(deco(d -> {
                    d.snowLayer = 1.0;
                    d.iceSheet = 0.4;
                }))
                .structures(RUINS, TOWER, IGLOO));
    }

    // ------------------------------------------------------------------ highlands and peaks

    private void registerHighlands() {
        register(ArkBiome.builder("rolling_highlands")
                .vanilla("minecraft:meadow").category(BiomeCategory.HIGHLAND)
                .climate(0.05, 0.35).land(0.75, 1.0).mountain(0.22, 0.62).height(60.0, 165.0)
                .surface(Palette.of(Blocks.GRASS_BLOCK, 9.0, Blocks.COARSE_DIRT, 2.0, Blocks.STONE, 1.0))
                .tree(TreeKind.SPRUCE, 3.0).tree(TreeKind.OAK, 2.0)
                .treeDensity(0.007)
                .decoration(deco(d -> {
                    d.grass = 0.35;
                    d.flowers = 0.10;
                    d.boulders = 0.010;
                }))
                .structures(CASTLE, FORTRESS, TOWER, RUINS, CAMP, BRIDGE, BATTLE_TOWER, OUTPOST, PREFAB_RUIN));

        register(ArkBiome.builder("alpine_forest")
                .vanilla("minecraft:grove").category(BiomeCategory.HIGHLAND)
                .climate(-0.5, 0.55).land(0.75, 1.0).mountain(0.28, 0.7).height(80.0, 190.0)
                .surface(Palette.of(Blocks.GRASS_BLOCK, 5.0, Blocks.SNOW_BLOCK, 4.0, Blocks.PODZOL, 2.0))
                .tree(TreeKind.SPRUCE, 8.0).tree(TreeKind.GIANT_SPRUCE, 3.0)
                .treeDensity(0.014)
                .decoration(deco(d -> {
                    d.snowLayer = 0.7;
                    d.fern = 0.12;
                    d.grass = 0.10;
                    d.boulders = 0.010;
                }))
                .structures(FORTRESS, TOWER, CAMP, RUINS, BRIDGE, OUTPOST));

        register(ArkBiome.builder("rocky_mountains")
                .vanilla("minecraft:windswept_gravelly_hills").category(BiomeCategory.MOUNTAIN)
                .climate(-0.25, 0.2).land(0.75, 1.0).mountain(0.5, 0.9).height(100.0, 260.0)
                .surface(Palette.of(Blocks.STONE, 6.0, Blocks.GRAVEL, 3.0, Blocks.ANDESITE, 2.0, Blocks.COARSE_DIRT, 1.0))
                .subsurface(Palette.of(Blocks.STONE, 6.0, Blocks.GRAVEL, 2.0))
                .stone(Palette.of(Blocks.STONE, 6.0, Blocks.ANDESITE, 2.0, Blocks.GRANITE, 2.0, Blocks.TUFF, 1.0))
                .surfaceDepth(3)
                .roughness(1.5)
                .tree(TreeKind.SPRUCE, 1.0).tree(TreeKind.DEAD, 1.0)
                .treeDensity(0.003)
                .decoration(deco(d -> {
                    d.boulders = 0.020;
                    d.grass = 0.05;
                    d.glowLichen = 0.02;
                }))
                .ores(1.2, 1.5, 1.3, 1.1, 1.2, 1.1, 1.2, 2.5)
                .structures(FORTRESS, TOWER, RUINS, BRIDGE, BATTLE_TOWER, CASTLE, PREFAB_RUIN));

        register(ArkBiome.builder("snowy_peaks")
                .vanilla("minecraft:snowy_slopes").category(BiomeCategory.PEAK)
                .climate(-0.85, 0.3).land(0.75, 1.0).mountain(0.55, 1.0).height(140.0, 320.0)
                .surface(Palette.of(Blocks.SNOW_BLOCK, 8.0, Blocks.PACKED_ICE, 2.0, Blocks.STONE, 1.0))
                .subsurface(Palette.of(Blocks.SNOW_BLOCK, 4.0, Blocks.STONE, 4.0))
                .stone(Palette.of(Blocks.STONE, 6.0, Blocks.ANDESITE, 2.0, Blocks.CALCITE, 1.0))
                .decoration(deco(d -> {
                    d.snowLayer = 1.0;
                    d.boulders = 0.008;
                }))
                .ores(1.0, 1.3, 1.0, 1.0, 1.0, 1.0, 1.1, 2.8)
                .structures(TOWER, FORTRESS, RUINS, BRIDGE));

        register(ArkBiome.builder("jagged_peaks")
                .vanilla("minecraft:jagged_peaks").category(BiomeCategory.PEAK)
                .climate(-0.6, 0.05).land(0.75, 1.0).mountain(0.72, 1.0).height(165.0, 384.0)
                .weird(0.75, 0.6)
                .surface(Palette.of(Blocks.STONE, 6.0, Blocks.SNOW_BLOCK, 3.0, Blocks.CALCITE, 2.0, Blocks.PACKED_ICE, 1.0))
                .stone(Palette.of(Blocks.STONE, 5.0, Blocks.CALCITE, 2.0, Blocks.DEEPSLATE, 1.0))
                .surfaceDepth(2)
                .roughness(2.0)
                .decoration(deco(d -> {
                    d.snowLayer = 0.9;
                    d.boulders = 0.006;
                }))
                .ores(1.0, 1.4, 1.0, 1.0, 1.1, 1.0, 1.3, 3.0)
                .structures(TOWER, RUINS, BRIDGE, BATTLE_TOWER));

        register(ArkBiome.builder("volcanic_range")
                .vanilla("minecraft:stony_peaks").category(BiomeCategory.VOLCANIC)
                .climate(0.85, -0.35).land(0.75, 1.0).mountain(0.55, 1.0).height(110.0, 300.0)
                .weird(-0.8, 0.7)
                .surface(Palette.of(Blocks.BASALT, 5.0, Blocks.BLACKSTONE, 4.0, Blocks.MAGMA_BLOCK, 1.0))
                .subsurface(Palette.of(Blocks.BASALT, 5.0, Blocks.BLACKSTONE, 3.0))
                .stone(Palette.of(Blocks.BASALT, 4.0, Blocks.BLACKSTONE, 3.0, Blocks.SMOOTH_BASALT, 2.0, Blocks.DEEPSLATE, 1.0))
                .roughness(1.7)
                .decoration(deco(d -> {
                    d.magmaVents = 0.06;
                    d.boulders = 0.010;
                    d.deadBush = 0.01;
                }))
                .ores(1.4, 1.6, 1.5, 1.8, 1.4, 1.2, 1.6, 1.2)
                .structures(FORTRESS, RUINS, TOWER, BATTLE_TOWER, RUINED_PORTAL));

        register(ArkBiome.builder("canyon_lands")
                .vanilla("minecraft:eroded_badlands").category(BiomeCategory.BADLANDS)
                .climate(0.6, -0.45).land(0.72, 1.0).mountain(0.25, 0.85).height(40.0, 220.0)
                .weird(-0.95, 0.65)
                .surface(Palette.of(Blocks.ORANGE_TERRACOTTA, 3.0, Blocks.TERRACOTTA, 3.0, Blocks.RED_SAND, 2.0,
                        Blocks.BROWN_TERRACOTTA, 2.0))
                .subsurface(Palette.of(Blocks.TERRACOTTA, 4.0, Blocks.WHITE_TERRACOTTA, 2.0, Blocks.YELLOW_TERRACOTTA, 2.0))
                .stone(Palette.of(Blocks.TERRACOTTA, 3.0, Blocks.STONE, 3.0, Blocks.RED_SANDSTONE, 2.0))
                .surfaceDepth(6)
                .roughness(1.8)
                .decoration(deco(d -> {
                    d.deadBush = 0.06;
                    d.boulders = 0.008;
                    d.cactus = 0.003;
                }))
                .ores(1.1, 1.2, 1.3, 2.0, 1.1, 1.0, 1.2, 1.1)
                .structures(TEMPLE, PYRAMID, RUINS, BRIDGE, TOWER, BATTLE_TOWER, TRAIL_RUINS, PREFAB_RUIN));
    }

    // ------------------------------------------------------------------ exotic

    private void registerExotic() {
        register(ArkBiome.builder("cherry_hills")
                .vanilla("minecraft:cherry_grove").category(BiomeCategory.FOREST)
                .climate(0.35, 0.5).land(0.75, 1.0).mountain(0.1, 0.5).height(40.0, 150.0)
                .weird(0.9, 0.75)
                .surface(Palette.of(Blocks.GRASS_BLOCK, 10.0, Blocks.MOSS_BLOCK, 1.0))
                .tree(TreeKind.CHERRY, 9.0).tree(TreeKind.OAK, 1.0)
                .treeDensity(0.015)
                .decoration(deco(d -> {
                    d.grass = 0.40;
                    d.flowers = 0.25;
                    d.mossPatches = 0.05;
                }))
                .structures(VILLAGE, TEMPLE, TOWER, CAMP, TRAIL_RUINS));

        // A second cherry biome, kept apart from cherry_hills on purpose: that one is a rolling
        // upland with a mix of trees, this one is a flat grove of nothing but the schematic cherry.
        // Pale ground, as asked - diorite and calcite under a thin skin - so the pink reads against
        // something light rather than against ordinary dirt.
        register(ArkBiome.builder("cherry_grove")
                .vanilla("minecraft:cherry_grove").category(BiomeCategory.FOREST)
                .climate(0.40, 0.40).land(0.70, 1.0).mountain(0.0, 0.22).height(6.0, 95.0)
                .weird(0.55, 0.85)
                .surface(Palette.of(Blocks.GRASS_BLOCK, 9.0, Blocks.MOSS_BLOCK, 1.0))
                .subsurface(Palette.of(Blocks.DIRT, 4.0, Blocks.DIORITE, 4.0, Blocks.CALCITE, 2.0))
                .stone(Palette.of(Blocks.DIORITE, 5.0, Blocks.CALCITE, 2.0, Blocks.STONE, 4.0))
                .surfaceDepth(3)
                .tree(TreeKind.CHERRY, 10.0)
                .treeDensity(0.020)
                .treeVariants(0.55, 0.0)
                .decoration(deco(d -> {
                    d.grass = 0.35;
                    d.flowers = 0.30;
                    d.mossPatches = 0.04;
                }))
                .structures(VILLAGE, CAMP, RUINS, TOWER, PREFAB_RUIN));

        // The scarlet forest. Its trees are red because they are built out of a block that is red -
        // see the note on the extractor - and its ground leans the same way. The grass itself is
        // only tinted where the colour datapack is installed; without it the ground blocks below
        // still carry the biome, which is why they are chosen the way they are.
        register(ArkBiome.builder("scarlet_forest")
                .vanilla("minecraft:dark_forest").category(BiomeCategory.FOREST)
                .climate(0.15, 0.62).land(0.72, 1.0).mountain(0.0, 0.30).height(8.0, 110.0)
                .weird(-0.85, 0.85)
                .surface(Palette.of(Blocks.GRASS_BLOCK, 5.0, Blocks.PODZOL, 4.0,
                        Blocks.COARSE_DIRT, 2.0, Blocks.RED_SAND, 1.0))
                .subsurface(Palette.of(Blocks.COARSE_DIRT, 5.0, Blocks.DIRT, 3.0,
                        Blocks.RED_SANDSTONE, 2.0))
                .stone(Palette.of(Blocks.STONE, 6.0, Blocks.RED_SANDSTONE, 2.0, Blocks.GRANITE, 2.0))
                .surfaceDepth(4)
                .tree(TreeKind.SCARLET, 10.0)
                .treeDensity(0.012)
                .treeVariants(0.85, 0.0)
                .decoration(deco(d -> {
                    d.grass = 0.30;
                    d.flowers = 0.18;
                    d.mushrooms = 0.04;
                }))
                .structures(RUINS, CAMP, TOWER, PREFAB_RUIN, BATTLE_TOWER));

        register(ArkBiome.builder("mushroom_isle")
                .vanilla("minecraft:mushroom_fields").category(BiomeCategory.MUSHROOM)
                .climate(0.3, 0.8).land(0.55, 0.95).mountain(0.0, 0.4).height(2.0, 90.0)
                .weird(-1.0, 0.9)
                .surface(Palette.of(Blocks.MYCELIUM, 10.0, Blocks.MOSS_BLOCK, 1.0))
                .subsurface(Palette.of(Blocks.DIRT, 8.0, Blocks.ROOTED_DIRT, 2.0))
                .decoration(deco(d -> {
                    d.mushrooms = 0.30;
                    d.mossPatches = 0.10;
                    d.glowLichen = 0.03;
                }))
                .structures(RUINS));

        register(ArkBiome.builder("lush_valley")
                .vanilla("minecraft:jungle").category(BiomeCategory.FOREST)
                .climate(0.5, 0.95).land(0.75, 1.0).mountain(0.0, 0.4).height(4.0, 100.0)
                .weird(0.25, 0.4)
                .surface(Palette.of(Blocks.GRASS_BLOCK, 8.0, Blocks.MOSS_BLOCK, 4.0))
                .tree(TreeKind.AZALEA, 4.0).tree(TreeKind.OAK, 3.0).tree(TreeKind.BIG_OAK, 2.0)
                .treeDensity(0.017)
                .decoration(deco(d -> {
                    d.grass = 0.50;
                    d.flowers = 0.15;
                    d.mossPatches = 0.20;
                    d.berryBush = 0.02;
                    d.vines = 0.08;
                }))
                .structures(RUINS, TEMPLE, CAMP, JUNGLE_TEMPLE));

        register(ArkBiome.builder("sky_isles")
                .vanilla("minecraft:meadow").category(BiomeCategory.FLOATING)
                .climate(0.1, 0.4).land(0.6, 1.0).height(150.0, 384.0)
                .weird(0.95, 0.9)
                .surface(Palette.of(Blocks.GRASS_BLOCK, 8.0, Blocks.MOSS_BLOCK, 2.0))
                .subsurface(Palette.of(Blocks.DIRT, 6.0, Blocks.ROOTED_DIRT, 2.0))
                .stone(Palette.of(Blocks.STONE, 5.0, Blocks.CALCITE, 2.0, Blocks.AMETHYST_BLOCK, 1.0))
                .tree(TreeKind.AZALEA, 2.0).tree(TreeKind.OAK, 1.0)
                .treeDensity(0.011)
                .decoration(deco(d -> {
                    d.grass = 0.35;
                    d.flowers = 0.18;
                    d.mossPatches = 0.12;
                    d.glowLichen = 0.05;
                }))
                .ores(1.0, 1.2, 1.2, 1.2, 1.2, 1.4, 1.5, 2.0)
                .structures(SKY, RUINS, TOWER));
    }
}
