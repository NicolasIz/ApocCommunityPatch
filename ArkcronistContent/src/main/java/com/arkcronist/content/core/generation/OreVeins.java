package com.arkcronist.content.core.generation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * 5.0: where the veins of one ore go in one chunk.
 *
 * <p>Worked out from the world's seed, the chunk and the ore's id alone - not from what the chunk
 * holds - so the same world always gets the same veins, whatever order its chunks are generated in,
 * and each ore has a stream of its own: adding an ore never moves another's veins. What the blocks
 * there are, and their biomes, is the caller's to check, at each position.</p>
 *
 * <p>A vein starts at a random block of the chunk within the ore's heights, and grows from a block
 * already in it into one of its six neighbours, until it is {@code vein-size} blocks big. It never
 * leaves the chunk: during generation, the chunk's own blocks are the ones that can be written.</p>
 */
public final class OreVeins {

    private OreVeins() {
    }

    /**
     * The blocks the veins of {@code ore} would take in chunk ({@code chunkX}, {@code chunkZ}), as
     * {x, y, z} in world coordinates, each once.
     *
     * @param worldMinY the world's lowest y, inclusive
     * @param worldMaxY the world's height limit, exclusive
     */
    public static List<int[]> plan(OreGeneration ore, String oreId, long worldSeed, int chunkX, int chunkZ,
                                   int worldMinY, int worldMaxY) {
        int low = Math.max(ore.minY(), worldMinY);
        int high = Math.min(ore.maxY(), worldMaxY - 1);
        if (low > high || ore.veinsPerChunk() == 0 || ore.chance() == 0) {
            return List.of();
        }
        SplittableRandom random = new SplittableRandom(seed(worldSeed, chunkX, chunkZ, oreId));
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        Set<Long> taken = new HashSet<>();
        List<int[]> blocks = new ArrayList<>();
        for (int vein = 0; vein < ore.veinsPerChunk(); vein++) {
            // Drawn whether or not the vein is made, so one chance does not shift the next try.
            boolean made = random.nextDouble() < ore.chance();
            int x = baseX + random.nextInt(16);
            int y = low + random.nextInt(high - low + 1);
            int z = baseZ + random.nextInt(16);
            if (!made) {
                continue;
            }
            List<int[]> grown = new ArrayList<>(ore.veinSize());
            Set<Long> inVein = new HashSet<>();
            grown.add(new int[]{x, y, z});
            inVein.add(pack(x, y, z));
            for (int tries = ore.veinSize() * 8; grown.size() < ore.veinSize() && tries > 0; tries--) {
                int[] from = grown.get(random.nextInt(grown.size()));
                int[] next = neighbour(from, random.nextInt(6));
                if (next[0] < baseX || next[0] > baseX + 15 || next[2] < baseZ || next[2] > baseZ + 15
                        || next[1] < low || next[1] > high || !inVein.add(pack(next[0], next[1], next[2]))) {
                    continue;
                }
                grown.add(next);
            }
            for (int[] block : grown) {
                if (taken.add(pack(block[0], block[1], block[2]))) {
                    blocks.add(block);
                }
            }
        }
        return blocks;
    }

    private static int[] neighbour(int[] block, int face) {
        return switch (face) {
            case 0 -> new int[]{block[0] + 1, block[1], block[2]};
            case 1 -> new int[]{block[0] - 1, block[1], block[2]};
            case 2 -> new int[]{block[0], block[1] + 1, block[2]};
            case 3 -> new int[]{block[0], block[1] - 1, block[2]};
            case 4 -> new int[]{block[0], block[1], block[2] + 1};
            default -> new int[]{block[0], block[1], block[2] - 1};
        };
    }

    /** One seed per world, chunk and ore, its bits well mixed. */
    static long seed(long worldSeed, int chunkX, int chunkZ, String oreId) {
        long seed = worldSeed;
        seed = mix(seed ^ chunkX * 0x9E3779B97F4A7C15L);
        seed = mix(seed ^ chunkZ * 0xC2B2AE3D27D4EB4FL);
        return mix(seed ^ oreId.hashCode() * 0x165667B19E3779F9L);
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
    }
}
