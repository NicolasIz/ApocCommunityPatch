package com.arkcronist.gen.core.prefab;

/**
 * The species names biomes ask for when they want a tree.
 *
 * <p>These are plain strings on purpose. A biome asking for {@code "cherry"} is asking the prefab
 * registry to find something cherry-like among whatever schematics the server actually has; it is
 * not naming a class that has to exist. That is what lets a server owner add
 * {@code prefabs/trees/giant_cherry_01.schem} and see it appear in cherry groves without anybody
 * recompiling the plugin.</p>
 */
public final class TreeKind {

    public static final String OAK = "oak";
    public static final String BIG_OAK = "big_oak";
    public static final String BIRCH = "birch";
    public static final String SPRUCE = "spruce";
    public static final String GIANT_SPRUCE = "giant_spruce";
    public static final String JUNGLE = "jungle";
    public static final String GIANT_JUNGLE = "giant_jungle";
    public static final String ACACIA = "acacia";
    public static final String DARK_OAK = "dark_oak";
    public static final String PALE_OAK = "pale_oak";
    public static final String MANGROVE = "mangrove";
    public static final String CHERRY = "cherry";
    /** The scarlet forest: red-canopied giants, and only where that biome asks for them. */
    public static final String SCARLET = "scarlet";
    public static final String AZALEA = "azalea";
    public static final String DEAD = "dead";
    /** Fantasy families that only turn up where a preset asks for something strange. */
    public static final String CRYSTAL = "crystal";
    public static final String AUTUMN = "autumn";

    private TreeKind() {
    }

    /** True for the species that should reach for the largest prefab available. */
    public static boolean prefersGiant(String species) {
        return BIG_OAK.equals(species) || GIANT_SPRUCE.equals(species) || GIANT_JUNGLE.equals(species);
    }
}
