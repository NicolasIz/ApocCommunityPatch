package com.arkcronist.gen.core.block;

import java.util.HashMap;
import java.util.Map;

/**
 * Parametric block states: stairs, slabs, walls, fences, doors, trapdoors and wall mounted lights.
 *
 * <p>Every combination is registered eagerly during class initialisation so the Bukkit layer can
 * resolve the whole table once at startup. Nothing is ever registered while a chunk is generating,
 * which keeps generation free of block-state parsing and of any synchronisation on the registry.</p>
 *
 * <p>Structures need these shapes to stop looking like boxes: stair trim, wall caps, window frames,
 * railings and sconces are what separate "a cube of stone bricks" from a building.</p>
 */
public final class BlockShapes {

    public static final String[] FACINGS = {"north", "east", "south", "west"};

    private static final Map<String, int[]> STAIRS = new HashMap<>();
    private static final Map<String, int[]> SLABS = new HashMap<>();
    private static final Map<String, Integer> WALLS = new HashMap<>();
    private static final Map<String, Integer> FENCES = new HashMap<>();
    private static final Map<String, int[]> DOORS = new HashMap<>();
    private static final Map<String, int[]> TRAPDOORS = new HashMap<>();
    private static final int[] WALL_TORCHES = new int[4];
    private static final int[] SOUL_WALL_TORCHES = new int[4];
    private static final int[] LADDERS = new int[4];
    private static final int[] WALL_BANNERS = new int[4];
    private static final int[] BEDS = new int[8];
    private static final int[] CHESTS = new int[4];
    private static final int[] FURNACES = new int[4];

    /** Materials that have a full stair/slab/wall family. */
    private static final String[] STONE_FAMILIES = {
            "stone_brick", "mossy_stone_brick", "cobblestone", "mossy_cobblestone", "stone",
            "smooth_stone", "andesite", "polished_andesite", "granite", "polished_granite",
            "diorite", "polished_diorite", "deepslate_brick", "deepslate_tile", "polished_deepslate",
            "cobbled_deepslate", "tuff", "polished_tuff", "tuff_brick", "sandstone", "smooth_sandstone",
            "cut_sandstone", "red_sandstone", "smooth_red_sandstone", "brick", "mud_brick",
            "prismarine", "prismarine_brick", "dark_prismarine", "blackstone", "polished_blackstone",
            "polished_blackstone_brick", "quartz", "smooth_quartz", "purpur", "end_stone_brick",
            "cut_copper", "exposed_cut_copper", "weathered_cut_copper", "oxidized_cut_copper"
    };

    private static final String[] WOOD_FAMILIES = {
            "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry",
            "pale_oak", "bamboo", "crimson", "warped"
    };

    static {
        for (String family : STONE_FAMILIES) {
            registerStairs(family);
            registerSlab(family);
            registerWall(family);
        }
        for (String family : WOOD_FAMILIES) {
            registerStairs(family);
            registerSlab(family);
            registerFence(family);
            registerDoor(family);
            registerTrapdoor(family);
        }
        registerDoor("iron");
        registerTrapdoor("iron");

        for (int i = 0; i < 4; i++) {
            WALL_TORCHES[i] = Blocks.REGISTRY.id("minecraft:wall_torch[facing=" + FACINGS[i] + "]");
            SOUL_WALL_TORCHES[i] = Blocks.REGISTRY.id("minecraft:soul_wall_torch[facing=" + FACINGS[i] + "]");
            LADDERS[i] = Blocks.REGISTRY.id("minecraft:ladder[facing=" + FACINGS[i] + "]");
            WALL_BANNERS[i] = Blocks.REGISTRY.id("minecraft:red_wall_banner[facing=" + FACINGS[i] + "]");
            CHESTS[i] = Blocks.REGISTRY.id("minecraft:chest[facing=" + FACINGS[i] + ",type=single]");
            FURNACES[i] = Blocks.REGISTRY.id("minecraft:furnace[facing=" + FACINGS[i] + ",lit=false]");
        }
        for (int i = 0; i < 4; i++) {
            BEDS[i] = Blocks.REGISTRY.id("minecraft:red_bed[facing=" + FACINGS[i] + ",part=foot]");
            BEDS[4 + i] = Blocks.REGISTRY.id("minecraft:red_bed[facing=" + FACINGS[i] + ",part=head]");
        }
    }

    private BlockShapes() {
    }

    private static void registerStairs(String family) {
        int[] table = new int[8];
        for (int f = 0; f < 4; f++) {
            table[f] = Blocks.REGISTRY.id("minecraft:" + family + "_stairs[facing=" + FACINGS[f]
                    + ",half=bottom,shape=straight]");
            table[4 + f] = Blocks.REGISTRY.id("minecraft:" + family + "_stairs[facing=" + FACINGS[f]
                    + ",half=top,shape=straight]");
        }
        STAIRS.put(family, table);
    }

    private static void registerSlab(String family) {
        SLABS.put(family, new int[]{
                Blocks.REGISTRY.id("minecraft:" + family + "_slab[type=bottom]"),
                Blocks.REGISTRY.id("minecraft:" + family + "_slab[type=top]"),
                Blocks.REGISTRY.id("minecraft:" + family + "_slab[type=double]")
        });
    }

    private static void registerWall(String family) {
        WALLS.put(family, Blocks.REGISTRY.id("minecraft:" + family + "_wall"));
    }

    private static void registerFence(String family) {
        FENCES.put(family, Blocks.REGISTRY.id("minecraft:" + family + "_fence"));
    }

    private static void registerDoor(String family) {
        int[] table = new int[16];
        for (int f = 0; f < 4; f++) {
            table[f] = Blocks.REGISTRY.id("minecraft:" + family + "_door[facing=" + FACINGS[f]
                    + ",half=lower,hinge=left,open=false]");
            table[4 + f] = Blocks.REGISTRY.id("minecraft:" + family + "_door[facing=" + FACINGS[f]
                    + ",half=upper,hinge=left,open=false]");
            table[8 + f] = Blocks.REGISTRY.id("minecraft:" + family + "_door[facing=" + FACINGS[f]
                    + ",half=lower,hinge=right,open=false]");
            table[12 + f] = Blocks.REGISTRY.id("minecraft:" + family + "_door[facing=" + FACINGS[f]
                    + ",half=upper,hinge=right,open=false]");
        }
        DOORS.put(family, table);
    }

    private static void registerTrapdoor(String family) {
        int[] table = new int[8];
        for (int f = 0; f < 4; f++) {
            table[f] = Blocks.REGISTRY.id("minecraft:" + family + "_trapdoor[facing=" + FACINGS[f]
                    + ",half=bottom,open=false]");
            table[4 + f] = Blocks.REGISTRY.id("minecraft:" + family + "_trapdoor[facing=" + FACINGS[f]
                    + ",half=top,open=false]");
        }
        TRAPDOORS.put(family, table);
    }

    /** Facing index for a horizontal direction, 0=north 1=east 2=south 3=west. */
    public static int facingFrom(int dx, int dz) {
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? 1 : 3;
        }
        return dz > 0 ? 2 : 0;
    }

    public static int opposite(int facing) {
        return (facing + 2) & 3;
    }

    public static int stairs(String family, int facing, boolean upsideDown) {
        int[] table = STAIRS.get(family);
        if (table == null) {
            return Blocks.STONE_BRICKS;
        }
        return table[(upsideDown ? 4 : 0) + (facing & 3)];
    }

    public static int slab(String family, boolean top) {
        int[] table = SLABS.get(family);
        return table == null ? Blocks.STONE_BRICKS : table[top ? 1 : 0];
    }

    public static int doubleSlab(String family) {
        int[] table = SLABS.get(family);
        return table == null ? Blocks.STONE_BRICKS : table[2];
    }

    public static int wall(String family) {
        Integer id = WALLS.get(family);
        return id == null ? Blocks.COBBLESTONE : id;
    }

    public static int fence(String family) {
        Integer id = FENCES.get(family);
        return id == null ? Blocks.OAK_FENCE : id;
    }

    public static int door(String family, int facing, boolean upper, boolean rightHinge) {
        int[] table = DOORS.get(family);
        if (table == null) {
            return Blocks.AIR;
        }
        return table[(rightHinge ? 8 : 0) + (upper ? 4 : 0) + (facing & 3)];
    }

    public static int trapdoor(String family, int facing, boolean top) {
        int[] table = TRAPDOORS.get(family);
        return table == null ? Blocks.AIR : table[(top ? 4 : 0) + (facing & 3)];
    }

    public static int wallTorch(int facing) {
        return WALL_TORCHES[facing & 3];
    }

    public static int soulWallTorch(int facing) {
        return SOUL_WALL_TORCHES[facing & 3];
    }

    public static int ladder(int facing) {
        return LADDERS[facing & 3];
    }

    public static int wallBanner(int facing) {
        return WALL_BANNERS[facing & 3];
    }

    public static int chest(int facing) {
        return CHESTS[facing & 3];
    }

    public static int furnace(int facing) {
        return FURNACES[facing & 3];
    }

    public static int bed(int facing, boolean head) {
        return BEDS[(head ? 4 : 0) + (facing & 3)];
    }

    /** Forces the whole table to be registered before the Bukkit layer resolves block data. */
    public static int registeredShapes() {
        return STAIRS.size() * 8 + SLABS.size() * 3 + WALLS.size() + FENCES.size()
                + DOORS.size() * 16 + TRAPDOORS.size() * 8 + 24;
    }
}
