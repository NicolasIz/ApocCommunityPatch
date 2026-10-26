package com.arkcronist.content.core.block;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 5.5: the vanilla leaves states a custom leaves block is drawn through.
 *
 * <p>Leaves have a {@code distance} from the nearest log, 1 to 7, a {@code persistent} flag and a
 * {@code waterlogged} flag. Vanilla's own persistent leaves - placed by a player - are held at
 * distance 7 by this plugin once their leaves block draws custom ones, and their distance is never
 * worked out again, so the persistent states at distances 1 to 6 are free. Each custom leaves block
 * takes two of them, a <em>slot</em>: the odd distance for leaves a tree grew, which fall when no
 * log holds them, and the even one for leaves a player placed, which stay - waterlogged or not, as
 * vanilla's. Three slots per vanilla leaves block, {@link #POOL} in the order they are handed out:
 * first the leaves whose falling particles go with any tree - the client lets them fall from custom
 * leaves as from the vanilla ones they are, and no pack changes that - and few builds are made of;
 * pale oak's paler ones later, the leaves of most builds after, and cherry's last: its pink petals
 * fall ten times as often. Mangrove leaves are left out - bone meal grows a propagule under them.</p>
 *
 * @param material the vanilla leaves block, without namespace: {@code flowering_azalea_leaves}
 * @param slot     0 to {@link #SLOTS} - 1
 */
public record LeavesState(String material, int slot) {

    /** The vanilla leaves blocks custom ones are drawn through, in the order they are handed out. */
    public static final List<String> POOL = List.of("flowering_azalea_leaves", "azalea_leaves", "jungle_leaves",
            "acacia_leaves", "pale_oak_leaves", "dark_oak_leaves", "birch_leaves", "spruce_leaves", "oak_leaves",
            "cherry_leaves");
    public static final int SLOTS = 3;
    public static final int CAPACITY = POOL.size() * SLOTS;
    /** The distance vanilla's own persistent leaves of a block in use are held at. */
    public static final int VANILLA_DISTANCE = 7;

    public LeavesState {
        if (!POOL.contains(material) || slot < 0 || slot >= SLOTS) {
            throw new IllegalArgumentException(material + " slot " + slot);
        }
    }

    public static LeavesState fromIndex(int index) {
        if (index < 0 || index >= CAPACITY) {
            throw new IllegalArgumentException("leaves state " + index);
        }
        return new LeavesState(POOL.get(index / SLOTS), index % SLOTS);
    }

    public int index() {
        return POOL.indexOf(material) * SLOTS + slot;
    }

    /** The distance of leaves of this block a tree grew: they fall when no log is near. */
    public int naturalDistance() {
        return slot * 2 + 1;
    }

    /** The distance of leaves of this block a player placed: they stay. */
    public int placedDistance() {
        return slot * 2 + 2;
    }

    /** {@code minecraft:flowering_azalea_leaves[distance=1,persistent=true,waterlogged=false]}. */
    public String asBlockData(boolean natural, boolean waterlogged) {
        return "minecraft:" + material + "[distance=" + (natural ? naturalDistance() : placedDistance())
                + ",persistent=true,waterlogged=" + waterlogged + "]";
    }

    /** A blockstate file's variant key, in the order vanilla's own files use. */
    public static String variantKey(int distance, boolean persistent, boolean waterlogged) {
        return "distance=" + distance + ",persistent=" + persistent + ",waterlogged=" + waterlogged;
    }

    /** What a leaves block's state is, read as this plugin draws it. */
    public record Reading(LeavesState state, boolean natural) {
    }

    /**
     * The slot and kind of a leaves block in this state, or empty for one no custom leaves block is
     * drawn through - not persistent, at distance 7, or not a leaves block of the pool.
     *
     * @param material a block id with or without namespace, any case: {@code AZALEA_LEAVES}
     */
    public static Optional<Reading> read(String material, int distance, boolean persistent) {
        String id = material.toLowerCase(Locale.ROOT);
        if (id.startsWith("minecraft:")) {
            id = id.substring("minecraft:".length());
        }
        if (!persistent || distance < 1 || distance >= VANILLA_DISTANCE || !POOL.contains(id)) {
            return Optional.empty();
        }
        return Optional.of(new Reading(new LeavesState(id, (distance - 1) / 2), distance % 2 == 1));
    }

    /** As {@link #read}, from a block data string: {@code minecraft:azalea_leaves[distance=3,persistent=true,...]}. */
    public static Optional<Reading> parse(String blockData) {
        int open = blockData.indexOf('[');
        if (open < 0 || !blockData.endsWith("]")) {
            return Optional.empty();
        }
        int distance = -1;
        boolean persistent = false;
        for (String property : blockData.substring(open + 1, blockData.length() - 1).split(",")) {
            int equals = property.indexOf('=');
            if (equals < 0) {
                continue;
            }
            String key = property.substring(0, equals).trim();
            String value = property.substring(equals + 1).trim();
            if (key.equals("distance")) {
                try {
                    distance = Integer.parseInt(value);
                } catch (NumberFormatException exception) {
                    return Optional.empty();
                }
            } else if (key.equals("persistent")) {
                persistent = value.equals("true");
            }
        }
        return read(blockData.substring(0, open), distance, persistent);
    }
}
