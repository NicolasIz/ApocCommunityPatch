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
    // Site resolution is far cheaper to keep than a whole built structure, and it is what the flat
    // ground search costs. Keeping it separately means evicting a buffer never repeats that search.
    private final ConcurrentHashMap<Long, Object> siteCache = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Long> siteOrder = new ConcurrentLinkedQueue<>();
    private static final Object NO_SITE = new Object();
    private static final int SITE_CACHE_LIMIT = 1024;

    private final java.util.Set<String> disabled;

    public StructurePlacer(TerrainEngine engine) {
        this(engine, java.util.Set.of());
    }

    /**
     * @param disabled structure ids that must not generate, by id. Used to step aside for the
     *                 server's own vanilla structures when those are enabled.
     */
    public StructurePlacer(TerrainEngine engine, java.util.Set<String> disabled) {
        this.disabled = disabled;
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
        if (disabled.contains(structure.id())) {
            return;
        }
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

    /**
     * Finds the flattest buildable spot near a position.
     *
     * <p>Sampled on a ring pattern around the original point; the winner is the one whose footprint
     * has the smallest height spread, with a bias towards staying near where the grid pointed.</p>
     */
    private int[] flattestSite(int x, int z, int radius, int searchRadius) {
        int bestX = x;
        int bestZ = z;
        double bestScore = Double.MAX_VALUE;
        int footprint = Math.max(6, Math.min(radius, 24));

        for (int ring = 0; ring <= 2; ring++) {
            int distance = ring * searchRadius / 2;
            int samples = ring == 0 ? 1 : 6;
            for (int i = 0; i < samples; i++) {
                double angle = i * Math.PI * 2.0 / samples;
                int px = x + (int) Math.round(Math.cos(angle) * distance);
                int pz = z + (int) Math.round(Math.sin(angle) * distance);
                double relief = footprintRelief(px, pz, footprint);
                // Prefer flat, but do not wander to the far side of the cell for a marginal gain.
                double score = relief + ring * 1.5;
                if (score < bestScore) {
                    bestScore = score;
                    bestX = px;
                    bestZ = pz;
                }
            }
        }
        return new int[]{bestX, bestZ};
    }

    /**
     * Height spread over a structure's footprint.
     *
     * <p>Deliberately measured on the heightmap rather than the exact solid surface: this runs for
     * every candidate site on every grid cell, and the heightmap answer needs only the cached 2D
     * chunk data instead of building the 3D fields.</p>
     */
    private double footprintRelief(int x, int z, int radius) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < 5; i++) {
            int ox = i == 1 ? -radius : i == 2 ? radius : 0;
            int oz = i == 3 ? -radius : i == 4 ? radius : 0;
            int height = engine.heightmapHeight(x + ox, z + oz);
            min = Math.min(min, height);
            max = Math.max(max, height);
        }
        return max - min;
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

    /** A resolved site: which structure a cell holds and exactly where it stands. */
    private record Site(Structure structure, int x, int z, FastRandom random) {
    }

    /**
     * Works out what a grid cell holds, if anything.
     *
     * <p>Building and {@code /ag locate} both go through here, so the coordinates the command reports
     * are the coordinates the structure is actually built at - including the flat-site search.</p>
     */
    private Site resolveSite(int cellX, int cellZ, int grid, long salt, List<Structure> pool) {
        long cacheKey = Hashing.hash3(engine.seed() ^ salt ^ 0x51E7L, cellX, grid, cellZ);
        Object cached = siteCache.get(cacheKey);
        if (cached != null) {
            return cached == NO_SITE ? null : (Site) cached;
        }
        Site resolved = computeSite(cellX, cellZ, grid, salt, pool);
        siteCache.put(cacheKey, resolved == null ? NO_SITE : resolved);
        siteOrder.add(cacheKey);
        while (siteCache.size() > SITE_CACHE_LIMIT) {
            Long oldest = siteOrder.poll();
            if (oldest == null) {
                break;
            }
            siteCache.remove(oldest);
        }
        return resolved;
    }

    private Site computeSite(int cellX, int cellZ, int grid, long salt, List<Structure> pool) {
        long key = Hashing.hash3(engine.seed() ^ salt, cellX, grid, cellZ);
        FastRandom random = new FastRandom(key);

        double chance = MathUtil.clamp(0.55 * engine.settings().structureDensity, 0.0, 1.0);
        if (!random.chance(chance)) {
            return null;
        }

        int padding = Math.min(grid / 4, MAX_RADIUS + 8);
        int x = cellX * grid + random.nextInt(padding, grid - padding);
        int z = cellZ * grid + random.nextInt(padding, grid - padding);

        ArkBiome biome = engine.biomeAt(x, z);
        Structure chosen = pick(pool, biome, random);
        if (chosen == null) {
            return null;
        }

        if (chosen.placement() != Structure.Placement.UNDERGROUND) {
            // Look around the cell for the flattest ground within reach. A castle half swallowed by a
            // hillside is worse than a castle fifty blocks from where the grid first pointed.
            int[] site = flattestSite(x, z, chosen.radius(), Math.min(grid / 4, 48));
            x = site[0];
            z = site[1];
            if (!allows(engine.biomeAt(x, z), chosen)) {
                return null;
            }
        }
        return new Site(chosen, x, z, random);
    }

    private StructureBuffer build(int cellX, int cellZ, int grid, long key, List<Structure> pool) {
        StructureBuffer buffer = new StructureBuffer();
        Site site = resolveSite(cellX, cellZ, grid, cellSalt(grid), pool);
        if (site == null) {
            return buffer;
        }
        StructureContext context = new StructureContext(engine, site.x(), site.z(),
                site.random().fork(0x5EEDL));
        if (!site.structure().canPlace(context)) {
            return buffer;
        }
        site.structure().build(context, buffer);
        return buffer;
    }

    /** Recovers the salt a grid was scanned with, so the cached key and the site agree. */
    private long cellSalt(int grid) {
        int gridLarge = Math.max(128, engine.settings().structureGridSize);
        if (grid == gridLarge) {
            return LARGE_SALT;
        }
        return grid == Math.max(80, gridLarge / 2) ? DEEP_SALT : SMALL_SALT;
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
                Site site = resolveSite(originCellX + dx, originCellZ + dz, grid, salt, pool);
                if (site == null || (tag != null && site.structure().tag() != tag)) {
                    continue;
                }
                StructureContext context = new StructureContext(engine, site.x(), site.z(),
                        site.random().fork(0x5EEDL));
                if (!site.structure().canPlace(context)) {
                    continue;
                }
                return new int[]{site.x(), engine.surfaceHeight(site.x(), site.z()), site.z()};
            }
        }
        return null;
    }
}
