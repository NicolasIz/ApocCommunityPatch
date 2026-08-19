package com.arkcronist.gen.core.structure;

import com.arkcronist.gen.core.biome.ArkBiome;
import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.math.Hashing;
import com.arkcronist.gen.core.math.MathUtil;
import com.arkcronist.gen.core.structure.types.*;
import com.arkcronist.gen.core.terrain.TerrainEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Decides where structures exist and hands their blocks to the chunk being generated.
 *
 * <p>Placement uses a deterministic grid: the world is divided into cells, each cell rolls once - from
 * a hash of (seed, cell) - for whether it holds a structure, which one, and exactly where inside the
 * cell. Any chunk can therefore work out the structures around it without knowing anything about the
 * chunks generated before it.</p>
 *
 * <p>Two grids run in parallel: a coarse one for cities, castles and villages, and a finer one for
 * towers, camps, ruins and vaults, so the world has both landmarks and small finds.</p>
 *
 * <p>Each structure is built once into a cached {@link StructureBuffer} and then blitted per chunk,
 * which is what allows a 128 block wide city to span dozens of chunks without ever being rebuilt or
 * cut in half.</p>
 */
public final class StructurePlacer {

    private static final int MAX_RADIUS = 64;
    private static final long LARGE_SALT = 0x1A26E_5A17L;
    private static final long SMALL_SALT = 0x5A11_5A17L;
    private static final long DEEP_SALT = 0xDEEB_5A17L;
    private static final int CACHE_LIMIT = 32;

    private final TerrainEngine engine;
    private final List<Structure> large = new ArrayList<>();
    private final List<Structure> small = new ArrayList<>();
    private final List<Structure> underground = new ArrayList<>();
    private final ConcurrentHashMap<Long, StructureBuffer> cache = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Long> cacheOrder = new ConcurrentLinkedQueue<>();

    public StructurePlacer(TerrainEngine engine) {
        this.engine = engine;
        // Settlements and landmarks
        register(new VillageStructure());
        register(new CityStructure());
        register(new CastleStructure());
        register(new FortressStructure());
        register(new WoodlandMansionStructure());
        register(new PillagerOutpostStructure());
        // Temples and towers
        register(new TempleStructure());
        register(new DesertPyramidStructure());
        register(new JungleTempleStructure());
        register(new TowerStructure());
        register(new BattleTowerStructure());
        // Small finds
        register(new CampStructure());
        register(new RuinsStructure());
        register(new TrailRuinsStructure());
        register(new RuinedPortalStructure());
        register(new IglooStructure());
        register(new WitchHutStructure());
        register(new BridgeStructure());
        // Water
        register(new UnderwaterStructure());
        register(new OceanMonumentStructure());
        register(new ShipwreckStructure());
        register(new BuriedTreasureStructure());
        // Sky
        register(new SkyStructure());
        // Underground
        register(new DungeonStructure());
        register(new UndergroundStructure());
        register(new MineshaftStructure());
        register(new StrongholdStructure());
        register(new AncientCityStructure());
        register(new TrialChamberStructure());
        register(new GeodeStructure());
        register(new FossilStructure());
    }

    private void register(Structure structure) {
        switch (structure.placement()) {
            case UNDERGROUND -> underground.add(structure);
            case SURFACE_LARGE -> large.add(structure);
            case SURFACE_SMALL -> small.add(structure);
        }
    }

    public List<Structure> structures() {
        List<Structure> all = new ArrayList<>(large);
        all.addAll(small);
        all.addAll(underground);
        return all;
    }

    /**
     * Writes every structure block that belongs to this chunk, collecting the mob and loot markers
     * that also fall inside it.
     */
    public void placeInto(int chunkX, int chunkZ, RegionWriter writer,
                          List<MobSpawn> spawnsOut, List<LootMarker> lootOut, List<SpawnerMarker> spawnersOut) {
        if (!engine.settings().structures) {
            return;
        }
        int gridLarge = Math.max(128, engine.settings().structureGridSize);
        int gridSmall = Math.max(64, gridLarge / 3);
        int gridDeep = Math.max(80, gridLarge / 2);
        scan(chunkX, chunkZ, writer, spawnsOut, lootOut, spawnersOut, gridLarge, LARGE_SALT, large);
        scan(chunkX, chunkZ, writer, spawnsOut, lootOut, spawnersOut, gridSmall, SMALL_SALT, small);
        scan(chunkX, chunkZ, writer, spawnsOut, lootOut, spawnersOut, gridDeep, DEEP_SALT, underground);
    }

    private void scan(int chunkX, int chunkZ, RegionWriter writer, List<MobSpawn> spawnsOut,
                      List<LootMarker> lootOut, List<SpawnerMarker> spawnersOut,
                      int grid, long salt, List<Structure> pool) {
        int blockX = chunkX << 4;
        int blockZ = chunkZ << 4;
        int minCellX = Math.floorDiv(blockX - MAX_RADIUS, grid);
        int maxCellX = Math.floorDiv(blockX + 15 + MAX_RADIUS, grid);
        int minCellZ = Math.floorDiv(blockZ - MAX_RADIUS, grid);
        int maxCellZ = Math.floorDiv(blockZ + 15 + MAX_RADIUS, grid);

        for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                StructureBuffer buffer = structureAt(cellX, cellZ, grid, salt, pool);
                if (buffer == null || !buffer.touchesChunk(chunkX, chunkZ)) {
                    continue;
                }
                buffer.blitChunk(chunkX, chunkZ, writer);
                collect(buffer, chunkX, chunkZ, spawnsOut, lootOut, spawnersOut);
            }
        }
    }

    private void collect(StructureBuffer buffer, int chunkX, int chunkZ,
                         List<MobSpawn> spawnsOut, List<LootMarker> lootOut, List<SpawnerMarker> spawnersOut) {
        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;
        for (MobSpawn spawn : buffer.spawns()) {
            if (spawn.x() >= x0 && spawn.x() <= x0 + 15 && spawn.z() >= z0 && spawn.z() <= z0 + 15) {
                spawnsOut.add(spawn);
            }
        }
        for (LootMarker marker : buffer.loot()) {
            if (marker.x() >= x0 && marker.x() <= x0 + 15 && marker.z() >= z0 && marker.z() <= z0 + 15) {
                lootOut.add(marker);
            }
        }
        for (SpawnerMarker marker : buffer.spawners()) {
            if (marker.x() >= x0 && marker.x() <= x0 + 15 && marker.z() >= z0 && marker.z() <= z0 + 15) {
                spawnersOut.add(marker);
            }
        }
    }

    /** Resolves (and caches) the structure for one grid cell, or null when the cell is empty. */
    private StructureBuffer structureAt(int cellX, int cellZ, int grid, long salt, List<Structure> pool) {
        long key = Hashing.hash3(engine.seed() ^ salt, cellX, grid, cellZ);
        StructureBuffer cached = cache.get(key);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }

        StructureBuffer built = cache.computeIfAbsent(key, k -> {
            cacheOrder.add(k);
            return build(cellX, cellZ, grid, k, pool);
        });
        trimCache();
        return built.isEmpty() ? null : built;
    }

    private StructureBuffer build(int cellX, int cellZ, int grid, long key, List<Structure> pool) {
        StructureBuffer buffer = new StructureBuffer();
        FastRandom random = new FastRandom(key);

        double chance = MathUtil.clamp(0.55 * engine.settings().structureDensity, 0.0, 1.0);
        if (!random.chance(chance)) {
            return buffer;
        }

        int padding = Math.min(grid / 4, MAX_RADIUS + 8);
        int x = cellX * grid + random.nextInt(padding, grid - padding);
        int z = cellZ * grid + random.nextInt(padding, grid - padding);

        ArkBiome biome = engine.biomeAt(x, z);
        Structure chosen = pick(pool, biome, random);
        if (chosen == null) {
            return buffer;
        }

        StructureContext context = new StructureContext(engine, x, z, random.fork(0x5EEDL));
        if (!chosen.canPlace(context)) {
            return buffer;
        }
        chosen.build(context, buffer);
        return buffer;
    }

    /**
     * Whether a biome accepts a structure.
     *
     * <p>Two families are not biome gated: sky sanctuaries belong to the floating island band rather
     * than to any surface biome, and buried vaults sit far below whatever happens to be overhead.</p>
     */
    private boolean allows(ArkBiome biome, Structure structure) {
        if (structure.placement() == Structure.Placement.UNDERGROUND) {
            // Depth decides these, not the biome overhead.
            return true;
        }
        return switch (structure.tag()) {
            case SKY -> engine.density().floatingIslandsEnabled();
            case RUINED_PORTAL -> true;
            default -> biome.structures.contains(structure.tag());
        };
    }

    private Structure pick(List<Structure> pool, ArkBiome biome, FastRandom random) {
        double total = 0.0;
        for (Structure structure : pool) {
            if (allows(biome, structure)) {
                total += structure.weight();
            }
        }
        if (total <= 0.0) {
            return null;
        }
        double target = random.nextDouble() * total;
        double running = 0.0;
        for (Structure structure : pool) {
            if (!allows(biome, structure)) {
                continue;
            }
            running += structure.weight();
            if (target < running) {
                return structure;
            }
        }
        return null;
    }

    private void trimCache() {
        while (cache.size() > CACHE_LIMIT) {
            Long oldest = cacheOrder.poll();
            if (oldest == null) {
                return;
            }
            cache.remove(oldest);
        }
    }

    /** Finds the nearest structure of any type around a position; used by {@code /ag locate}. */
    public int[] locate(int startX, int startZ, StructureTag tag, int maxCellRadius) {
        int gridLarge = Math.max(128, engine.settings().structureGridSize);
        int gridSmall = Math.max(64, gridLarge / 3);
        int gridDeep = Math.max(80, gridLarge / 2);
        for (int ring = 0; ring <= maxCellRadius; ring++) {
            int[] found = searchRing(startX, startZ, tag, ring, gridLarge, LARGE_SALT, large);
            if (found != null) {
                return found;
            }
            found = searchRing(startX, startZ, tag, ring, gridSmall, SMALL_SALT, small);
            if (found != null) {
                return found;
            }
            found = searchRing(startX, startZ, tag, ring, gridDeep, DEEP_SALT, underground);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private int[] searchRing(int startX, int startZ, StructureTag tag, int ring, int grid, long salt,
                             List<Structure> pool) {
        int originCellX = Math.floorDiv(startX, grid);
        int originCellZ = Math.floorDiv(startZ, grid);
        for (int dx = -ring; dx <= ring; dx++) {
            for (int dz = -ring; dz <= ring; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                    continue;
                }
                int cellX = originCellX + dx;
                int cellZ = originCellZ + dz;
                long key = Hashing.hash3(engine.seed() ^ salt, cellX, grid, cellZ);
                FastRandom random = new FastRandom(key);
                double chance = MathUtil.clamp(0.55 * engine.settings().structureDensity, 0.0, 1.0);
                if (!random.chance(chance)) {
                    continue;
                }
                int padding = Math.min(grid / 4, MAX_RADIUS + 8);
                int x = cellX * grid + random.nextInt(padding, grid - padding);
                int z = cellZ * grid + random.nextInt(padding, grid - padding);
                ArkBiome biome = engine.biomeAt(x, z);
                Structure chosen = pick(pool, biome, random);
                if (chosen == null || (tag != null && chosen.tag() != tag)) {
                    continue;
                }
                StructureContext context = new StructureContext(engine, x, z, random.fork(0x5EEDL));
                if (!chosen.canPlace(context)) {
                    continue;
                }
                return new int[]{x, engine.surfaceHeight(x, z), z};
            }
        }
        return null;
    }
}
