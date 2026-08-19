package com.arkcronist.gen.core.block;

/**
 * Every block the generator can place, as dense ids.
 *
 * <p>Ids are assigned in class initialisation order and stay stable for the lifetime of the JVM,
 * which is all the core needs; nothing is persisted.</p>
 */
public final class Blocks {

    public static final BlockRegistry REGISTRY = new BlockRegistry();

    public static final int AIR = id("minecraft:air");
    public static final int CAVE_AIR = id("minecraft:cave_air");
    public static final int WATER = id("minecraft:water");
    public static final int LAVA = id("minecraft:lava");
    public static final int BEDROCK = id("minecraft:bedrock");

    // Stone family / strata
    public static final int STONE = id("minecraft:stone");
    public static final int DEEPSLATE = id("minecraft:deepslate");
    public static final int TUFF = id("minecraft:tuff");
    public static final int GRANITE = id("minecraft:granite");
    public static final int DIORITE = id("minecraft:diorite");
    public static final int ANDESITE = id("minecraft:andesite");
    public static final int CALCITE = id("minecraft:calcite");
    public static final int DRIPSTONE = id("minecraft:dripstone_block");
    public static final int BASALT = id("minecraft:basalt");
    public static final int SMOOTH_BASALT = id("minecraft:smooth_basalt");
    public static final int BLACKSTONE = id("minecraft:blackstone");
    public static final int SANDSTONE = id("minecraft:sandstone");
    public static final int RED_SANDSTONE = id("minecraft:red_sandstone");
    public static final int TERRACOTTA = id("minecraft:terracotta");
    public static final int WHITE_TERRACOTTA = id("minecraft:white_terracotta");
    public static final int ORANGE_TERRACOTTA = id("minecraft:orange_terracotta");
    public static final int YELLOW_TERRACOTTA = id("minecraft:yellow_terracotta");
    public static final int BROWN_TERRACOTTA = id("minecraft:brown_terracotta");
    public static final int RED_TERRACOTTA = id("minecraft:red_terracotta");
    public static final int LIGHT_GRAY_TERRACOTTA = id("minecraft:light_gray_terracotta");

    // Soils and surfaces
    public static final int GRASS_BLOCK = id("minecraft:grass_block");
    public static final int DIRT = id("minecraft:dirt");
    public static final int COARSE_DIRT = id("minecraft:coarse_dirt");
    public static final int DIRT_PATH = id("minecraft:dirt_path");
    public static final int ROOTED_DIRT = id("minecraft:rooted_dirt");
    public static final int PODZOL = id("minecraft:podzol");
    public static final int MYCELIUM = id("minecraft:mycelium");
    public static final int MUD = id("minecraft:mud");
    public static final int CLAY = id("minecraft:clay");
    public static final int GRAVEL = id("minecraft:gravel");
    public static final int SAND = id("minecraft:sand");
    public static final int RED_SAND = id("minecraft:red_sand");
    public static final int SNOW_BLOCK = id("minecraft:snow_block");
    public static final int POWDER_SNOW = id("minecraft:powder_snow");
    public static final int PACKED_ICE = id("minecraft:packed_ice");
    public static final int BLUE_ICE = id("minecraft:blue_ice");
    public static final int ICE = id("minecraft:ice");
    public static final int MOSS_BLOCK = id("minecraft:moss_block");
    public static final int SNOW_LAYER = id("minecraft:snow[layers=1]");

    // Ores
    public static final int COAL_ORE = id("minecraft:coal_ore");
    public static final int DEEPSLATE_COAL_ORE = id("minecraft:deepslate_coal_ore");
    public static final int IRON_ORE = id("minecraft:iron_ore");
    public static final int DEEPSLATE_IRON_ORE = id("minecraft:deepslate_iron_ore");
    public static final int COPPER_ORE = id("minecraft:copper_ore");
    public static final int DEEPSLATE_COPPER_ORE = id("minecraft:deepslate_copper_ore");
    public static final int GOLD_ORE = id("minecraft:gold_ore");
    public static final int DEEPSLATE_GOLD_ORE = id("minecraft:deepslate_gold_ore");
    public static final int REDSTONE_ORE = id("minecraft:redstone_ore");
    public static final int DEEPSLATE_REDSTONE_ORE = id("minecraft:deepslate_redstone_ore");
    public static final int LAPIS_ORE = id("minecraft:lapis_ore");
    public static final int DEEPSLATE_LAPIS_ORE = id("minecraft:deepslate_lapis_ore");
    public static final int DIAMOND_ORE = id("minecraft:diamond_ore");
    public static final int DEEPSLATE_DIAMOND_ORE = id("minecraft:deepslate_diamond_ore");
    public static final int EMERALD_ORE = id("minecraft:emerald_ore");
    public static final int DEEPSLATE_EMERALD_ORE = id("minecraft:deepslate_emerald_ore");
    public static final int ANCIENT_DEBRIS = id("minecraft:ancient_debris");
    public static final int AMETHYST_BLOCK = id("minecraft:amethyst_block");
    public static final int BUDDING_AMETHYST = id("minecraft:budding_amethyst");

    // Wood - logs
    public static final int OAK_LOG = id("minecraft:oak_log[axis=y]");
    public static final int OAK_LOG_X = id("minecraft:oak_log[axis=x]");
    public static final int OAK_LOG_Z = id("minecraft:oak_log[axis=z]");
    public static final int SPRUCE_LOG = id("minecraft:spruce_log[axis=y]");
    public static final int SPRUCE_LOG_X = id("minecraft:spruce_log[axis=x]");
    public static final int SPRUCE_LOG_Z = id("minecraft:spruce_log[axis=z]");
    public static final int BIRCH_LOG = id("minecraft:birch_log[axis=y]");
    public static final int BIRCH_LOG_X = id("minecraft:birch_log[axis=x]");
    public static final int BIRCH_LOG_Z = id("minecraft:birch_log[axis=z]");
    public static final int JUNGLE_LOG = id("minecraft:jungle_log[axis=y]");
    public static final int JUNGLE_LOG_X = id("minecraft:jungle_log[axis=x]");
    public static final int JUNGLE_LOG_Z = id("minecraft:jungle_log[axis=z]");
    public static final int ACACIA_LOG = id("minecraft:acacia_log[axis=y]");
    public static final int ACACIA_LOG_X = id("minecraft:acacia_log[axis=x]");
    public static final int ACACIA_LOG_Z = id("minecraft:acacia_log[axis=z]");
    public static final int DARK_OAK_LOG = id("minecraft:dark_oak_log[axis=y]");
    public static final int DARK_OAK_LOG_X = id("minecraft:dark_oak_log[axis=x]");
    public static final int DARK_OAK_LOG_Z = id("minecraft:dark_oak_log[axis=z]");
    public static final int MANGROVE_LOG = id("minecraft:mangrove_log[axis=y]");
    public static final int MANGROVE_LOG_X = id("minecraft:mangrove_log[axis=x]");
    public static final int MANGROVE_LOG_Z = id("minecraft:mangrove_log[axis=z]");
    public static final int CHERRY_LOG = id("minecraft:cherry_log[axis=y]");
    public static final int CHERRY_LOG_X = id("minecraft:cherry_log[axis=x]");
    public static final int CHERRY_LOG_Z = id("minecraft:cherry_log[axis=z]");
    public static final int PALE_OAK_LOG = id("minecraft:pale_oak_log[axis=y]");
    public static final int PALE_OAK_LOG_X = id("minecraft:pale_oak_log[axis=x]");
    public static final int PALE_OAK_LOG_Z = id("minecraft:pale_oak_log[axis=z]");
    public static final int STRIPPED_OAK_LOG = id("minecraft:stripped_oak_log[axis=y]");
    public static final int STRIPPED_SPRUCE_LOG = id("minecraft:stripped_spruce_log[axis=y]");

    // Wood - wood blocks used for thick trunks and roots
    public static final int OAK_WOOD = id("minecraft:oak_wood[axis=y]");
    public static final int SPRUCE_WOOD = id("minecraft:spruce_wood[axis=y]");
    public static final int JUNGLE_WOOD = id("minecraft:jungle_wood[axis=y]");
    public static final int DARK_OAK_WOOD = id("minecraft:dark_oak_wood[axis=y]");

    // Leaves
    public static final int OAK_LEAVES = id("minecraft:oak_leaves[persistent=false,distance=7]");
    public static final int SPRUCE_LEAVES = id("minecraft:spruce_leaves[persistent=false,distance=7]");
    public static final int BIRCH_LEAVES = id("minecraft:birch_leaves[persistent=false,distance=7]");
    public static final int JUNGLE_LEAVES = id("minecraft:jungle_leaves[persistent=false,distance=7]");
    public static final int ACACIA_LEAVES = id("minecraft:acacia_leaves[persistent=false,distance=7]");
    public static final int DARK_OAK_LEAVES = id("minecraft:dark_oak_leaves[persistent=false,distance=7]");
    public static final int MANGROVE_LEAVES = id("minecraft:mangrove_leaves[persistent=false,distance=7]");
    public static final int CHERRY_LEAVES = id("minecraft:cherry_leaves[persistent=false,distance=7]");
    public static final int PALE_OAK_LEAVES = id("minecraft:pale_oak_leaves[persistent=false,distance=7]");
    public static final int AZALEA_LEAVES = id("minecraft:azalea_leaves[persistent=false,distance=7]");
    public static final int FLOWERING_AZALEA_LEAVES = id("minecraft:flowering_azalea_leaves[persistent=false,distance=7]");

    // Plants and decoration
    public static final int SHORT_GRASS = id("minecraft:short_grass");
    public static final int TALL_GRASS_LOWER = id("minecraft:tall_grass[half=lower]");
    public static final int TALL_GRASS_UPPER = id("minecraft:tall_grass[half=upper]");
    public static final int FERN = id("minecraft:fern");
    public static final int LARGE_FERN_LOWER = id("minecraft:large_fern[half=lower]");
    public static final int LARGE_FERN_UPPER = id("minecraft:large_fern[half=upper]");
    public static final int DEAD_BUSH = id("minecraft:dead_bush");
    public static final int POPPY = id("minecraft:poppy");
    public static final int DANDELION = id("minecraft:dandelion");
    public static final int CORNFLOWER = id("minecraft:cornflower");
    public static final int AZURE_BLUET = id("minecraft:azure_bluet");
    public static final int OXEYE_DAISY = id("minecraft:oxeye_daisy");
    public static final int ALLIUM = id("minecraft:allium");
    public static final int BLUE_ORCHID = id("minecraft:blue_orchid");
    public static final int LILY_OF_THE_VALLEY = id("minecraft:lily_of_the_valley");
    public static final int SWEET_BERRY_BUSH = id("minecraft:sweet_berry_bush[age=3]");
    public static final int BROWN_MUSHROOM = id("minecraft:brown_mushroom");
    public static final int RED_MUSHROOM = id("minecraft:red_mushroom");
    public static final int CACTUS = id("minecraft:cactus");
    public static final int BAMBOO = id("minecraft:bamboo[age=1,leaves=none,stage=0]");
    public static final int SUGAR_CANE = id("minecraft:sugar_cane");
    public static final int VINE_NORTH = id("minecraft:vine[north=true]");
    public static final int VINE_SOUTH = id("minecraft:vine[south=true]");
    public static final int VINE_EAST = id("minecraft:vine[east=true]");
    public static final int VINE_WEST = id("minecraft:vine[west=true]");
    public static final int GLOW_LICHEN = id("minecraft:glow_lichen[down=true]");
    public static final int MOSS_CARPET = id("minecraft:moss_carpet");
    public static final int LILY_PAD = id("minecraft:lily_pad");
    public static final int SEAGRASS = id("minecraft:seagrass");
    public static final int TALL_SEAGRASS_LOWER = id("minecraft:tall_seagrass[half=lower]");
    public static final int TALL_SEAGRASS_UPPER = id("minecraft:tall_seagrass[half=upper]");
    public static final int KELP = id("minecraft:kelp[age=12]");
    public static final int KELP_PLANT = id("minecraft:kelp_plant");
    public static final int SEA_PICKLE = id("minecraft:sea_pickle[pickles=2,waterlogged=true]");
    public static final int TUBE_CORAL_BLOCK = id("minecraft:tube_coral_block");
    public static final int BRAIN_CORAL_BLOCK = id("minecraft:brain_coral_block");
    public static final int BUBBLE_CORAL_BLOCK = id("minecraft:bubble_coral_block");
    public static final int FIRE_CORAL_BLOCK = id("minecraft:fire_coral_block");
    public static final int HORN_CORAL_BLOCK = id("minecraft:horn_coral_block");
    public static final int TUBE_CORAL_FAN = id("minecraft:tube_coral_fan[waterlogged=true]");
    public static final int BRAIN_CORAL_FAN = id("minecraft:brain_coral_fan[waterlogged=true]");
    public static final int FIRE_CORAL_FAN = id("minecraft:fire_coral_fan[waterlogged=true]");
    public static final int SPONGE = id("minecraft:sponge");
    public static final int WET_SPONGE = id("minecraft:wet_sponge");
    public static final int MAGMA_BLOCK = id("minecraft:magma_block");
    public static final int PRISMARINE = id("minecraft:prismarine");
    public static final int PRISMARINE_BRICKS = id("minecraft:prismarine_bricks");
    public static final int DARK_PRISMARINE = id("minecraft:dark_prismarine");
    public static final int SEA_LANTERN = id("minecraft:sea_lantern");

    // Cave decoration
    public static final int POINTED_DRIPSTONE_UP = id("minecraft:pointed_dripstone[vertical_direction=up,thickness=tip]");
    public static final int POINTED_DRIPSTONE_DOWN = id("minecraft:pointed_dripstone[vertical_direction=down,thickness=tip]");
    public static final int GLOWSTONE = id("minecraft:glowstone");
    public static final int SHROOMLIGHT = id("minecraft:shroomlight");

    // Building blocks used by structures
    public static final int COBBLESTONE = id("minecraft:cobblestone");
    public static final int MOSSY_COBBLESTONE = id("minecraft:mossy_cobblestone");
    public static final int STONE_BRICKS = id("minecraft:stone_bricks");
    public static final int MOSSY_STONE_BRICKS = id("minecraft:mossy_stone_bricks");
    public static final int CRACKED_STONE_BRICKS = id("minecraft:cracked_stone_bricks");
    public static final int CHISELED_STONE_BRICKS = id("minecraft:chiseled_stone_bricks");
    public static final int POLISHED_ANDESITE = id("minecraft:polished_andesite");
    public static final int POLISHED_DEEPSLATE = id("minecraft:polished_deepslate");
    public static final int DEEPSLATE_BRICKS = id("minecraft:deepslate_bricks");
    public static final int DEEPSLATE_TILES = id("minecraft:deepslate_tiles");
    public static final int BRICKS = id("minecraft:bricks");
    public static final int OAK_PLANKS = id("minecraft:oak_planks");
    public static final int SPRUCE_PLANKS = id("minecraft:spruce_planks");
    public static final int BIRCH_PLANKS = id("minecraft:birch_planks");
    public static final int DARK_OAK_PLANKS = id("minecraft:dark_oak_planks");
    public static final int OAK_FENCE = id("minecraft:oak_fence");
    public static final int SPRUCE_FENCE = id("minecraft:spruce_fence");
    public static final int DARK_OAK_FENCE = id("minecraft:dark_oak_fence");
    public static final int OAK_DOOR_LOWER = id("minecraft:oak_door[half=lower,facing=north]");
    public static final int OAK_DOOR_UPPER = id("minecraft:oak_door[half=upper,facing=north]");
    public static final int GLASS = id("minecraft:glass");
    public static final int GLASS_PANE = id("minecraft:glass_pane");
    public static final int TORCH = id("minecraft:torch");
    public static final int LANTERN = id("minecraft:lantern");
    public static final int HANGING_LANTERN = id("minecraft:lantern[hanging=true]");
    public static final int CAMPFIRE = id("minecraft:campfire[lit=true]");
    public static final int CHEST = id("minecraft:chest[facing=north]");
    public static final int BARREL = id("minecraft:barrel");
    public static final int SPAWNER = id("minecraft:spawner");
    public static final int LADDER_NORTH = id("minecraft:ladder[facing=north]");
    public static final int LADDER_SOUTH = id("minecraft:ladder[facing=south]");
    public static final int LADDER_EAST = id("minecraft:ladder[facing=east]");
    public static final int LADDER_WEST = id("minecraft:ladder[facing=west]");
    public static final int OAK_STAIRS_NORTH = id("minecraft:oak_stairs[facing=north]");
    public static final int STONE_BRICK_STAIRS_NORTH = id("minecraft:stone_brick_stairs[facing=north]");
    public static final int STONE_BRICK_SLAB = id("minecraft:stone_brick_slab[type=bottom]");
    public static final int OAK_SLAB = id("minecraft:oak_slab[type=bottom]");
    public static final int IRON_BARS = id("minecraft:iron_bars");
    public static final int COBWEB = id("minecraft:cobweb");
    public static final int HAY_BLOCK = id("minecraft:hay_block[axis=y]");
    public static final int BOOKSHELF = id("minecraft:bookshelf");
    public static final int CRAFTING_TABLE = id("minecraft:crafting_table");
    public static final int FURNACE = id("minecraft:furnace[facing=north]");
    public static final int ANVIL = id("minecraft:anvil[facing=north]");
    public static final int NETHERRACK = id("minecraft:netherrack");
    public static final int OBSIDIAN = id("minecraft:obsidian");
    public static final int CRYING_OBSIDIAN = id("minecraft:crying_obsidian");
    public static final int END_STONE = id("minecraft:end_stone");
    public static final int PURPUR_BLOCK = id("minecraft:purpur_block");
    public static final int QUARTZ_BLOCK = id("minecraft:quartz_block");
    public static final int COPPER_BLOCK = id("minecraft:copper_block");
    public static final int OXIDIZED_COPPER = id("minecraft:oxidized_copper");
    public static final int CUT_COPPER = id("minecraft:cut_copper");
    public static final int WAXED_WEATHERED_COPPER = id("minecraft:waxed_weathered_copper");
    public static final int LODESTONE = id("minecraft:lodestone");
    public static final int CHAIN = id("minecraft:chain[axis=y]");
    public static final int SCAFFOLDING = id("minecraft:scaffolding");
    public static final int MUD_BRICKS = id("minecraft:mud_bricks");
    public static final int PACKED_MUD = id("minecraft:packed_mud");
    public static final int SMOOTH_SANDSTONE = id("minecraft:smooth_sandstone");
    public static final int CUT_SANDSTONE = id("minecraft:cut_sandstone");
    public static final int CHISELED_SANDSTONE = id("minecraft:chiseled_sandstone");


    // ---------------------------------------------------------------- cave life and cave rock
    public static final int CAVE_VINES_BERRIES = id("minecraft:cave_vines[age=20,berries=true]");
    public static final int CAVE_VINES_PLANT_BERRIES = id("minecraft:cave_vines_plant[berries=true]");
    public static final int CAVE_VINES_PLANT = id("minecraft:cave_vines_plant[berries=false]");
    public static final int SPORE_BLOSSOM = id("minecraft:spore_blossom");
    public static final int BIG_DRIPLEAF = id("minecraft:big_dripleaf[facing=north,tilt=none]");
    public static final int SMALL_DRIPLEAF_LOWER = id("minecraft:small_dripleaf[half=lower,facing=north]");
    public static final int SMALL_DRIPLEAF_UPPER = id("minecraft:small_dripleaf[half=upper,facing=north]");
    public static final int HANGING_ROOTS = id("minecraft:hanging_roots");
    public static final int AZALEA = id("minecraft:azalea");
    public static final int FLOWERING_AZALEA = id("minecraft:flowering_azalea");
    public static final int GLOW_LICHEN_DOWN = id("minecraft:glow_lichen[down=true]");
    public static final int GLOW_LICHEN_UP = id("minecraft:glow_lichen[up=true]");
    public static final int GLOW_LICHEN_NORTH = id("minecraft:glow_lichen[north=true]");
    public static final int GLOW_LICHEN_SOUTH = id("minecraft:glow_lichen[south=true]");
    public static final int GLOW_LICHEN_EAST = id("minecraft:glow_lichen[east=true]");
    public static final int GLOW_LICHEN_WEST = id("minecraft:glow_lichen[west=true]");
    public static final int DRIPSTONE_TIP_DOWN = id("minecraft:pointed_dripstone[vertical_direction=down,thickness=tip]");
    public static final int DRIPSTONE_FRUSTUM_DOWN = id("minecraft:pointed_dripstone[vertical_direction=down,thickness=frustum]");
    public static final int DRIPSTONE_MIDDLE_DOWN = id("minecraft:pointed_dripstone[vertical_direction=down,thickness=middle]");
    public static final int DRIPSTONE_BASE_DOWN = id("minecraft:pointed_dripstone[vertical_direction=down,thickness=base]");
    public static final int DRIPSTONE_TIP_UP = id("minecraft:pointed_dripstone[vertical_direction=up,thickness=tip]");
    public static final int DRIPSTONE_FRUSTUM_UP = id("minecraft:pointed_dripstone[vertical_direction=up,thickness=frustum]");
    public static final int DRIPSTONE_MIDDLE_UP = id("minecraft:pointed_dripstone[vertical_direction=up,thickness=middle]");
    public static final int DRIPSTONE_BASE_UP = id("minecraft:pointed_dripstone[vertical_direction=up,thickness=base]");
    public static final int AMETHYST_CLUSTER_UP = id("minecraft:amethyst_cluster[facing=up]");
    public static final int AMETHYST_CLUSTER_DOWN = id("minecraft:amethyst_cluster[facing=down]");
    public static final int LARGE_AMETHYST_BUD_UP = id("minecraft:large_amethyst_bud[facing=up]");
    public static final int MEDIUM_AMETHYST_BUD_UP = id("minecraft:medium_amethyst_bud[facing=up]");
    public static final int SMALL_AMETHYST_BUD_UP = id("minecraft:small_amethyst_bud[facing=up]");
    public static final int SCULK = id("minecraft:sculk");
    public static final int SCULK_VEIN_DOWN = id("minecraft:sculk_vein[down=true]");
    public static final int SCULK_VEIN_UP = id("minecraft:sculk_vein[up=true]");
    public static final int SCULK_SENSOR = id("minecraft:sculk_sensor");
    public static final int SCULK_SHRIEKER = id("minecraft:sculk_shrieker[can_summon=true,shrieking=false]");
    public static final int SCULK_CATALYST = id("minecraft:sculk_catalyst");
    public static final int REINFORCED_DEEPSLATE = id("minecraft:reinforced_deepslate");
    public static final int COBBLED_DEEPSLATE = id("minecraft:cobbled_deepslate");
    public static final int CRACKED_DEEPSLATE_BRICKS = id("minecraft:cracked_deepslate_bricks");
    public static final int CRACKED_DEEPSLATE_TILES = id("minecraft:cracked_deepslate_tiles");
    public static final int CHISELED_DEEPSLATE = id("minecraft:chiseled_deepslate");
    public static final int POLISHED_TUFF = id("minecraft:polished_tuff");
    public static final int TUFF_BRICKS = id("minecraft:tuff_bricks");
    public static final int CHISELED_TUFF = id("minecraft:chiseled_tuff");
    public static final int SOUL_SAND = id("minecraft:soul_sand");
    public static final int SOUL_SOIL = id("minecraft:soul_soil");
    public static final int SOUL_LANTERN = id("minecraft:soul_lantern");
    public static final int SOUL_HANGING_LANTERN = id("minecraft:soul_lantern[hanging=true]");
    public static final int SOUL_TORCH = id("minecraft:soul_torch");
    public static final int SOUL_FIRE = id("minecraft:soul_fire");
    public static final int SNOW_LAYER_DEEP = id("minecraft:snow[layers=5]");
    public static final int BROWN_MUSHROOM_BLOCK = id("minecraft:brown_mushroom_block");
    public static final int RED_MUSHROOM_BLOCK = id("minecraft:red_mushroom_block");
    public static final int MUSHROOM_STEM = id("minecraft:mushroom_stem");
    public static final int WATER_CAULDRON = id("minecraft:water_cauldron[level=3]");
    public static final int TINTED_GLASS = id("minecraft:tinted_glass");
    public static final int WATER_LOGGED_AIR = id("minecraft:water");

    // ---------------------------------------------------------------- mineshafts, rails, redstone
    public static final int RAIL_NS = id("minecraft:rail[shape=north_south]");
    public static final int RAIL_EW = id("minecraft:rail[shape=east_west]");
    public static final int POWERED_RAIL_NS = id("minecraft:powered_rail[shape=north_south,powered=false]");
    public static final int REDSTONE_TORCH = id("minecraft:redstone_torch");
    public static final int LEVER_FLOOR = id("minecraft:lever[face=floor,facing=north,powered=false]");
    public static final int TRIPWIRE_HOOK_NORTH = id("minecraft:tripwire_hook[facing=north]");
    public static final int STONE_PRESSURE_PLATE = id("minecraft:stone_pressure_plate");
    public static final int TNT = id("minecraft:tnt");
    public static final int INFESTED_STONE_BRICKS = id("minecraft:infested_stone_bricks");
    public static final int SUSPICIOUS_SAND = id("minecraft:suspicious_sand");
    public static final int SUSPICIOUS_GRAVEL = id("minecraft:suspicious_gravel");
    public static final int DECORATED_POT = id("minecraft:decorated_pot");
    public static final int TRIAL_SPAWNER = id("minecraft:trial_spawner");
    public static final int VAULT_BLOCK = id("minecraft:vault");
    public static final int END_PORTAL_FRAME_NORTH = id("minecraft:end_portal_frame[facing=north,eye=false]");
    public static final int END_PORTAL_FRAME_EAST = id("minecraft:end_portal_frame[facing=east,eye=false]");
    public static final int END_PORTAL_FRAME_SOUTH = id("minecraft:end_portal_frame[facing=south,eye=false]");
    public static final int END_PORTAL_FRAME_WEST = id("minecraft:end_portal_frame[facing=west,eye=false]");
    public static final int SILVERFISH_STONE = id("minecraft:infested_cobblestone");

    // ---------------------------------------------------------------- furnishing
    public static final int BOOKSHELF_2 = id("minecraft:bookshelf");
    public static final int CHISELED_BOOKSHELF = id("minecraft:chiseled_bookshelf");
    public static final int LECTERN = id("minecraft:lectern[facing=north]");
    public static final int BREWING_STAND = id("minecraft:brewing_stand");
    public static final int CAULDRON = id("minecraft:cauldron");
    public static final int COMPOSTER = id("minecraft:composter[level=0]");
    public static final int SMOKER = id("minecraft:smoker[facing=north,lit=false]");
    public static final int BLAST_FURNACE = id("minecraft:blast_furnace[facing=north,lit=false]");
    public static final int CARTOGRAPHY_TABLE = id("minecraft:cartography_table");
    public static final int FLETCHING_TABLE = id("minecraft:fletching_table");
    public static final int SMITHING_TABLE = id("minecraft:smithing_table");
    public static final int LOOM = id("minecraft:loom[facing=north]");
    public static final int STONECUTTER = id("minecraft:stonecutter[facing=north]");
    public static final int GRINDSTONE = id("minecraft:grindstone[face=floor,facing=north]");
    public static final int BARREL_UP = id("minecraft:barrel[facing=up,open=false]");
    public static final int BELL_CEILING = id("minecraft:bell[attachment=ceiling,facing=north]");
    public static final int FLOWER_POT = id("minecraft:flower_pot");
    public static final int CANDLE_LIT = id("minecraft:candle[candles=3,lit=true]");
    public static final int LIGHTNING_ROD = id("minecraft:lightning_rod[facing=up]");
    public static final int RED_CARPET = id("minecraft:red_carpet");
    public static final int BLUE_CARPET = id("minecraft:blue_carpet");
    public static final int WHITE_CARPET = id("minecraft:white_carpet");
    public static final int RED_BANNER = id("minecraft:red_banner[rotation=0]");
    public static final int ITEM_FRAME_PLACEHOLDER = id("minecraft:stone_button[face=wall,facing=north]");
    public static final int GOLD_BLOCK = id("minecraft:gold_block");
    public static final int IRON_BLOCK = id("minecraft:iron_block");
    public static final int DIAMOND_BLOCK = id("minecraft:diamond_block");
    public static final int EMERALD_BLOCK = id("minecraft:emerald_block");
    public static final int LAPIS_BLOCK = id("minecraft:lapis_block");
    public static final int BONE_BLOCK = id("minecraft:bone_block[axis=y]");
    public static final int BONE_BLOCK_X = id("minecraft:bone_block[axis=x]");
    public static final int BONE_BLOCK_Z = id("minecraft:bone_block[axis=z]");
    public static final int COPPER_BULB = id("minecraft:copper_bulb[lit=true,powered=false]");
    public static final int CHISELED_COPPER = id("minecraft:chiseled_copper");
    public static final int COPPER_GRATE = id("minecraft:copper_grate");
    public static final int EXPOSED_COPPER = id("minecraft:exposed_copper");
    public static final int WEATHERED_COPPER = id("minecraft:weathered_copper");
    public static final int OXIDIZED_CUT_COPPER = id("minecraft:oxidized_cut_copper");
    public static final int WAXED_COPPER_BLOCK = id("minecraft:waxed_copper_block");
    public static final int DARK_OAK_LOG_STRIPPED = id("minecraft:stripped_dark_oak_log[axis=y]");
    public static final int SPRUCE_TRAPDOOR_PLACEHOLDER = id("minecraft:spruce_trapdoor[facing=north,half=bottom,open=false]");
    public static final int MUD_BRICK_WALL = id("minecraft:mud_brick_wall");
    public static final int SANDSTONE_WALL = id("minecraft:sandstone_wall");
    public static final int OBSIDIAN_2 = id("minecraft:obsidian");
    public static final int SEA_LANTERN_2 = id("minecraft:sea_lantern");
    public static final int DARK_PRISMARINE_2 = id("minecraft:dark_prismarine");
    public static final int WET_SPONGE_2 = id("minecraft:wet_sponge");
    public static final int GILDED_BLACKSTONE = id("minecraft:gilded_blackstone");
    public static final int NETHER_BRICKS = id("minecraft:nether_bricks");
    public static final int MOSSY_STONE_BRICK_WALL = id("minecraft:mossy_stone_brick_wall");
    public static final int STONE_BRICK_WALL = id("minecraft:stone_brick_wall");
    public static final int COBBLESTONE_WALL = id("minecraft:cobblestone_wall");

    private Blocks() {
    }

    private static int id(String key) {
        return REGISTRY.id(key);
    }

    /** Blocks that terrain treats as replaceable when carving or placing decoration. */
    public static boolean isAir(int block) {
        return block == AIR || block == CAVE_AIR;
    }

    public static boolean isLiquid(int block) {
        return block == WATER || block == LAVA;
    }
}
