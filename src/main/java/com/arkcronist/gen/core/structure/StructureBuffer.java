package com.arkcronist.gen.core.structure;

import com.arkcronist.gen.core.block.Blocks;

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

    // Markers are tracked separately from blocks. A garrison mob standing in a doorway, or a chest
    // recorded a block outside the wall, can fall in a chunk the structure places nothing in - and a
    // chunk that a structure does not write to never asks for its markers. Widening the reach used
    // to decide "does this structure concern chunk (x,z)" is what stops those being lost silently.
    private int markerMinX = Integer.MAX_VALUE;
    private int markerMinZ = Integer.MAX_VALUE;
    private int markerMaxX = Integer.MIN_VALUE;
    private int markerMaxZ = Integer.MIN_VALUE;

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
        noteMarker(spawn.x(), spawn.z());
    }

    private void noteMarker(int x, int z) {
        if (x < markerMinX) {
            markerMinX = x;
        }
        if (z < markerMinZ) {
            markerMinZ = z;
        }
        if (x > markerMaxX) {
            markerMaxX = x;
        }
        if (z > markerMaxZ) {
            markerMaxZ = z;
        }
    }

    public void addLoot(LootMarker marker) {
        loot.add(marker);
        noteMarker(marker.x(), marker.z());
    }

    public List<MobSpawn> spawns() {
        return spawns;
    }

    /** How far up or down a mob may be moved to find a floor of the structure's own. */
    private static final int SETTLE_REACH = 6;

    /**
     * Moves every queued mob onto a block this structure actually laid.
     *
     * <p>Run once, after the structure has finished building, and it is worth explaining why it has
     * to be here rather than inside each structure. A structure decides where its garrison goes as
     * it goes along, and then keeps building: it lays furniture, cuts a stair, hangs a lantern,
     * writes a wall. By the time it is finished, a position that was an empty room when it was
     * chosen may have a bookshelf in it. Measured across the whole catalogue, that and jittered
     * positions put nearly half of all garrison mobs inside a block - which is the "they come out
     * underground and die" this was reported as, and it was never one structure's bug.</p>
     *
     * <p>What it will not do is guess. Where the buffer holds nothing at all the structure has no
     * opinion about that column - the mob is standing on ordinary terrain - and the position is left
     * exactly as it was for the server side to check against the real world, which is the only place
     * that knows what the terrain there is. Nothing is ever dropped here either: this pass moves
     * mobs, and only the world can say that there is nowhere for one to go.</p>
     */
    public void settleSpawns() {
        for (int i = 0; i < spawns.size(); i++) {
            MobSpawn spawn = spawns.get(i);
            if (standable(spawn, spawn.y())) {
                continue;
            }
            for (int step = 1; step <= SETTLE_REACH; step++) {
                if (standable(spawn, spawn.y() - step)) {
                    spawns.set(i, spawn.atHeight(spawn.y() - step));
                    break;
                }
                if (standable(spawn, spawn.y() + step)) {
                    spawns.set(i, spawn.atHeight(spawn.y() + step));
                    break;
                }
            }
        }
    }

    /**
     * Whether this buffer would let the mob stand here.
     *
     * <p>An unwritten cell counts as clear rather than as blocked, because it means the structure
     * put nothing there - not that nothing is there. The same reading applies under the mob's feet:
     * an unwritten cell below is terrain, which is usually exactly what a mob outside the walls is
     * meant to be standing on.</p>
     */
    private boolean standable(MobSpawn spawn, int y) {
        boolean floats = !spawn.needsFloor();
        int needed = spawn.height();
        for (int offset = 0; offset < needed; offset++) {
            int block = get(spawn.x(), y + offset, spawn.z());
            if (block < 0 || Blocks.isAir(block)) {
                continue;
            }
            // Water is not in the way of something that swims in it. Without this line a monument's
            // guardians all read as walled in, and the pass below would helpfully move every one of
            // them out of the sea.
            if (floats && Blocks.isLiquid(block)) {
                continue;
            }
            return false;
        }
        if (floats) {
            return true;
        }
        int floor = get(spawn.x(), y - 1, spawn.z());
        return floor < 0 || (!Blocks.isAir(floor) && !Blocks.isLiquid(floor));
    }

    public List<LootMarker> loot() {
        return loot;
    }

    public void addSpawner(SpawnerMarker marker) {
        spawners.add(marker);
        noteMarker(marker.x(), marker.z());
    }

    public List<SpawnerMarker> spawners() {
        return spawners;
    }

    public int blockCount() {
        return blockCount;
    }

    public boolean isEmpty() {
        return blockCount == 0 && spawns.isEmpty() && loot.isEmpty() && spawners.isEmpty();
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

    /** True when this structure has any block <em>or marker</em> inside the given chunk. */
    public boolean touchesChunk(int chunkX, int chunkZ) {
        if (isEmpty()) {
            return false;
        }
        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;
        return overlaps(Math.min(minX, markerMinX), Math.max(maxX, markerMaxX),
                Math.min(minZ, markerMinZ), Math.max(maxZ, markerMaxZ), x0, z0);
    }

    /** True when this structure writes blocks into the given chunk. */
    public boolean writesToChunk(int chunkX, int chunkZ) {
        if (blockCount == 0) {
            return false;
        }
        return overlaps(minX, maxX, minZ, maxZ, chunkX << 4, chunkZ << 4);
    }

    private static boolean overlaps(int lowX, int highX, int lowZ, int highZ, int x0, int z0) {
        return highX >= x0 && lowX <= x0 + 15 && highZ >= z0 && lowZ <= z0 + 15;
    }

    /** Writes the part of this structure that belongs to one chunk. */
    public void blitChunk(int chunkX, int chunkZ, RegionWriter writer) {
        if (!writesToChunk(chunkX, chunkZ)) {
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
