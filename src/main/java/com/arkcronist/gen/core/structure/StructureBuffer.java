package com.arkcronist.gen.core.structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A complete structure held in memory before it is written into the world.
 *
 * <p>Blocks are stored in globally aligned 16x16x16 sections, which means "give me the part of this
 * castle that belongs to chunk (x,z)" is a direct lookup rather than a scan. A structure is therefore
 * built <em>once</em>, cached, and then blitted into each chunk it overlaps - the same mechanism that
 * lets a 100 block wide fortress cross nine chunks without a single seam or a single duplicated
 * build.</p>
 *
 * <p>Section slots store {@code blockId + 1} so that 0 can mean "this structure does not touch this
 * position", which is different from "this structure explicitly places air here" (used to clear the
 * inside of buildings out of the surrounding hill).</p>
 */
public final class StructureBuffer {

    private final Map<Long, short[]> sections = new HashMap<>();
    private final List<MobSpawn> spawns = new ArrayList<>();
    private final List<LootMarker> loot = new ArrayList<>();
    private final List<SpawnerMarker> spawners = new ArrayList<>();

    private int minX = Integer.MAX_VALUE;
    private int minY = Integer.MAX_VALUE;
    private int minZ = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE;
    private int maxY = Integer.MIN_VALUE;
    private int maxZ = Integer.MIN_VALUE;
    private int blockCount;

    private static long sectionKey(int sx, int sy, int sz) {
        return ((long) (sx & 0x3FFFFF) << 42) | ((long) (sy & 0xFFFFF) << 22) | (sz & 0x3FFFFFL);
    }

    public void set(int x, int y, int z, int blockId) {
        int sx = x >> 4;
        int sy = y >> 4;
        int sz = z >> 4;
        short[] section = sections.computeIfAbsent(sectionKey(sx, sy, sz), key -> new short[4096]);
        int localIndex = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
        if (section[localIndex] == 0) {
            blockCount++;
        }
        section[localIndex] = (short) (blockId + 1);

        if (x < minX) {
            minX = x;
        }
        if (y < minY) {
            minY = y;
        }
        if (z < minZ) {
            minZ = z;
        }
        if (x > maxX) {
            maxX = x;
        }
        if (y > maxY) {
            maxY = y;
        }
        if (z > maxZ) {
            maxZ = z;
        }
    }

    /**
     * Reads a position back.
     *
     * @return the block id written there, or -1 when this structure does not touch the position
     */
    public int get(int x, int y, int z) {
        short[] section = sections.get(sectionKey(x >> 4, y >> 4, z >> 4));
        if (section == null) {
            return -1;
        }
        short value = section[((y & 15) << 8) | ((z & 15) << 4) | (x & 15)];
        return value == 0 ? -1 : value - 1;
    }

    public void addSpawn(MobSpawn spawn) {
        spawns.add(spawn);
    }

    public void addLoot(LootMarker marker) {
        loot.add(marker);
    }

    public List<MobSpawn> spawns() {
        return spawns;
    }

    public List<LootMarker> loot() {
        return loot;
    }

    public void addSpawner(SpawnerMarker marker) {
        spawners.add(marker);
    }

    public List<SpawnerMarker> spawners() {
        return spawners;
    }

    public int blockCount() {
        return blockCount;
    }

    public boolean isEmpty() {
        return blockCount == 0;
    }

    public int minX() {
        return minX;
    }

    public int maxX() {
        return maxX;
    }

    public int minZ() {
        return minZ;
    }

    public int maxZ() {
        return maxZ;
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    /** True when this structure has any block inside the given chunk. */
    public boolean touchesChunk(int chunkX, int chunkZ) {
        if (isEmpty()) {
            return false;
        }
        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;
        return maxX >= x0 && minX <= x0 + 15 && maxZ >= z0 && minZ <= z0 + 15;
    }

    /** Writes the part of this structure that belongs to one chunk. */
    public void blitChunk(int chunkX, int chunkZ, RegionWriter writer) {
        if (!touchesChunk(chunkX, chunkZ)) {
            return;
        }
        int sectionMinY = minY >> 4;
        int sectionMaxY = maxY >> 4;
        for (int sy = sectionMinY; sy <= sectionMaxY; sy++) {
            short[] section = sections.get(sectionKey(chunkX, sy, chunkZ));
            if (section == null) {
                continue;
            }
            int baseY = sy << 4;
            int baseX = chunkX << 4;
            int baseZ = chunkZ << 4;
            for (int localY = 0; localY < 16; localY++) {
                int y = baseY + localY;
                if (y < writer.minY() || y >= writer.maxY()) {
                    continue;
                }
                int rowBase = localY << 8;
                for (int localZ = 0; localZ < 16; localZ++) {
                    int columnBase = rowBase | (localZ << 4);
                    for (int localX = 0; localX < 16; localX++) {
                        short value = section[columnBase | localX];
                        if (value != 0) {
                            writer.set(baseX + localX, y, baseZ + localZ, value - 1);
                        }
                    }
                }
            }
        }
    }
}
