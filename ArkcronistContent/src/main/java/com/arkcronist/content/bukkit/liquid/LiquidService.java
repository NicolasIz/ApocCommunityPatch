package com.arkcronist.content.bukkit.liquid;

import com.arkcronist.content.bukkit.EngineSettings;
import com.arkcronist.content.bukkit.protection.Protection;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.liquid.FluidSolver;
import com.arkcronist.content.core.storage.BlockKey;
import com.arkcronist.content.core.storage.DatabaseManager;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Custom liquids in the world: their sources, the flow worked out from them, and what they do to
 * players standing in them.
 *
 * <p>Only sources are kept - in memory and in SQLite ({@code liquid_sources}). Where a liquid flows
 * follows from its sources and the terrain, so it is worked out again whenever either changes near
 * it, in three steps:</p>
 * <ol>
 *   <li><b>Main thread.</b> The chunks around the change are copied as {@link ChunkSnapshot}s,
 *       which are safe to read from any thread, with the sources that could reach them.</li>
 *   <li><b>{@code ArkContent-Fluids}.</b> {@link FluidSolver} works out where each liquid should
 *       be, compares that with the copy, and returns the blocks to fill and to drain - the whole of
 *       the search, none of it on the server thread.</li>
 *   <li><b>Main thread, a few at a time.</b> The changes are applied nearest the source first, a
 *       step every {@code tick-rate} ticks, so the liquid is seen to run; at most
 *       {@code liquids.changes-per-tick} each tick. Each one is checked against the world as it is
 *       then - the block still empty, the liquid still next to it - and against WorldGuard and
 *       GriefPrevention: liquid never runs into a claim or region from outside it, nor where the
 *       {@code arkcontent-liquid-flow} flag is denied.</li>
 * </ol>
 */
public final class LiquidService {

    /** A source as remembered: which liquid, and who poured it. */
    public record Source(String liquid, @Nullable UUID owner) {
    }

    /** A fill or a drain waiting for its tick. */
    private record Pending(long due, int depth, UUID world, long key, String liquid, boolean flow, long origin) {
    }

    /** One liquid's part of a solve, copied for the liquids' thread. */
    private record Job(String liquid, BlockData sourceData, BlockData flowData, int flowDistance, int maxFall,
                       int tickRate, List<Long> sources) {
    }

    /** What a solve returns: each liquid's changes, and the flowing blocks it found already there. */
    private record Solved(UUID world, Map<String, List<FluidSolver.Change>> changes, Map<String, Set<Long>> present) {
    }

    private final Plugin plugin;
    private final LiquidRegistry registry;
    private final Protection protection;
    private final DatabaseManager database;
    private final EngineSettings.Liquids settings;
    private final Logger logger;
    private final ExecutorService fluids;

    /** Sources, by world and position. Main thread. */
    private final Map<UUID, Map<Long, Source>> sources = new HashMap<>();
    /**
     * Every flowing block placed or found, by world and position: which liquid it is. With the
     * sources, the state each liquid block should be in - to put back one vanilla reconnected.
     */
    private final Map<UUID, Map<Long, String>> flows = new HashMap<>();
    /** How many sources each chunk holds, by world: to know quickly whether a change is near one. */
    private final Map<UUID, Map<Long, Integer>> sourceChunks = new HashMap<>();
    /** Blocks whose surroundings changed, waiting to be solved, by world. */
    private final Map<UUID, Set<Long>> dirty = new HashMap<>();
    /** Worlds with a solve under way: one at a time per world, so their changes never cross. */
    private final Set<UUID> solving = new HashSet<>();
    private final PriorityQueue<Pending> pending = new PriorityQueue<>((a, b) -> a.due() != b.due()
            ? Long.compare(a.due(), b.due()) : Integer.compare(a.depth(), b.depth()));
    private final Set<String> warned = new HashSet<>();
    /** When each player was last hurt by a liquid, so each liquid keeps its own pace. */
    private final Map<UUID, Long> lastTouch = new HashMap<>();
    private BukkitTask task;
    private long tick;

    public LiquidService(Plugin plugin, LiquidRegistry registry, Protection protection, DatabaseManager database,
                         EngineSettings.Liquids settings) {
        this.plugin = plugin;
        this.registry = registry;
        this.protection = protection;
        this.database = database;
        this.settings = settings;
        this.logger = plugin.getLogger();
        this.fluids = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ArkContent-Fluids");
            thread.setDaemon(true);
            return thread;
        });
    }

    // ------------------------------------------------------------------ lifecycle

    /** Starts the tick that solves, applies and hurts, and reads every loaded world's sources. */
    public void start(Collection<World> worlds) {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
        worlds.forEach(this::worldLoaded);
    }

    /** Reads a world's sources on the database thread, then marks them all to be solved. */
    public void worldLoaded(World world) {
        UUID id = world.getUID();
        database.loadLiquids(id).thenAcceptAsync(rows -> {
            Map<Long, Source> loaded = sources.computeIfAbsent(id, ignored -> new HashMap<>());
            for (DatabaseManager.LiquidSource row : rows) {
                long key = BlockKey.pack(row.x(), row.y(), row.z());
                if (loaded.putIfAbsent(key, new Source(row.liquid(), row.owner())) == null) {
                    countChunk(id, key, 1);
                }
                markDirty(id, key);
            }
            if (!rows.isEmpty()) {
                logger.info(rows.size() + " liquid source(s) loaded in " + world.getName() + ".");
            }
        }, task -> plugin.getServer().getScheduler().runTask(plugin, task)).exceptionally(error -> {
            logger.warning("Could not read the liquid sources of " + world.getName() + ": " + error.getMessage());
            return null;
        });
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
        fluids.shutdownNow();
        try {
            fluids.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ sources

    /** The source at {@code block}, if one was poured there. */
    public Optional<Source> source(Block block) {
        Map<Long, Source> world = sources.get(block.getWorld().getUID());
        return world == null ? Optional.empty()
                : Optional.ofNullable(world.get(BlockKey.pack(block.getX(), block.getY(), block.getZ())));
    }

    /**
     * Pours a source of {@code liquid} at {@code block}, which must be empty or that liquid's flow.
     * Protection is the caller's to have checked.
     */
    public boolean pour(Block block, LiquidRegistry.Liquid liquid, @Nullable UUID owner) {
        LiquidRegistry.State there = registry.state(block.getBlockData());
        if (!block.getType().isAir() && (there == null || there.liquid() != liquid)) {
            return false;
        }
        block.setBlockData(liquid.sourceData(), false);
        UUID world = block.getWorld().getUID();
        long key = BlockKey.pack(block.getX(), block.getY(), block.getZ());
        Source source = new Source(liquid.id(), owner);
        if (sources.computeIfAbsent(world, ignored -> new HashMap<>()).put(key, source) == null) {
            countChunk(world, key, 1);
        }
        flows(world).remove(key);
        database.saveLiquid(world, new DatabaseManager.LiquidSource(block.getX(), block.getY(), block.getZ(),
                liquid.id(), owner)).exceptionally(error -> {
                    logger.warning("Could not save the liquid source at " + block.getX() + " " + block.getY() + " "
                            + block.getZ() + ": " + error.getMessage());
                    return null;
                });
        markDirty(world, key);
        return true;
    }

    /**
     * Takes away the source at {@code block}, leaving air; its flow drains after it.
     *
     * @return the liquid that was there, or null when there was no source
     */
    public @Nullable String removeSource(Block block) {
        UUID world = block.getWorld().getUID();
        long key = BlockKey.pack(block.getX(), block.getY(), block.getZ());
        Map<Long, Source> inWorld = sources.get(world);
        LiquidRegistry.State state = registry.state(block.getBlockData());
        Source removed = inWorld == null ? null : inWorld.remove(key);
        if (removed == null && (state == null || !state.source())) {
            return null;
        }
        if (state != null) {
            block.setType(Material.AIR, false);
        }
        flows(world).remove(key);
        if (removed != null) {
            countChunk(world, key, -1);
            database.deleteLiquid(world, block.getX(), block.getY(), block.getZ());
        }
        markDirty(world, key);
        return removed != null ? removed.liquid() : state.liquid().id();
    }

    /** Every source in a world, for commands. */
    public Map<Long, Source> sources(World world) {
        return Map.copyOf(sources.getOrDefault(world.getUID(), Map.of()));
    }

    /**
     * The state the liquid block at {@code block} should be in, or null when no liquid is meant to
     * be there. Main thread.
     */
    public @Nullable BlockData expected(Block block) {
        UUID world = block.getWorld().getUID();
        long key = BlockKey.pack(block.getX(), block.getY(), block.getZ());
        Map<Long, Source> inWorld = sources.get(world);
        Source source = inWorld == null ? null : inWorld.get(key);
        if (source != null) {
            return registry.get(source.liquid()).map(LiquidRegistry.Liquid::sourceData).orElse(null);
        }
        Map<Long, String> flowing = flows.get(world);
        String liquid = flowing == null ? null : flowing.get(key);
        return liquid == null ? null : registry.get(liquid).map(LiquidRegistry.Liquid::flowData).orElse(null);
    }

    private Map<Long, String> flows(UUID world) {
        return flows.computeIfAbsent(world, ignored -> new HashMap<>());
    }

    private void countChunk(UUID world, long key, int change) {
        long chunk = chunkKey(BlockKey.x(key) >> 4, BlockKey.z(key) >> 4);
        sourceChunks.computeIfAbsent(world, ignored -> new HashMap<>()).merge(chunk, change,
                (a, b) -> a + b == 0 ? null : a + b);
    }

    // ------------------------------------------------------------------ changes nearby

    /**
     * Something changed at {@code block} - built, broken, blown up: if a liquid could be affected,
     * its flow is worked out again.
     */
    public void changedNear(Block block) {
        UUID world = block.getWorld().getUID();
        if (nearSource(world, block.getX() >> 4, block.getZ() >> 4)) {
            markDirty(world, BlockKey.pack(block.getX(), block.getY(), block.getZ()));
        }
    }

    /** A chunk loaded: the sources in it and next to it flow again, filling in what is missing. */
    public void chunkLoaded(Chunk chunk) {
        UUID world = chunk.getWorld().getUID();
        Map<Long, Source> inWorld = sources.get(world);
        if (inWorld == null || !nearSource(world, chunk.getX(), chunk.getZ())) {
            return;
        }
        int reach = settings.chunkReach();
        for (long key : inWorld.keySet()) {
            if (Math.abs((BlockKey.x(key) >> 4) - chunk.getX()) <= reach
                    && Math.abs((BlockKey.z(key) >> 4) - chunk.getZ()) <= reach) {
                markDirty(world, key);
            }
        }
    }

    private boolean nearSource(UUID world, int chunkX, int chunkZ) {
        Map<Long, Integer> chunks = sourceChunks.get(world);
        if (chunks == null || chunks.isEmpty()) {
            return false;
        }
        int reach = settings.chunkReach() * 2;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (chunks.containsKey(chunkKey(chunkX + dx, chunkZ + dz))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void markDirty(UUID world, long key) {
        dirty.computeIfAbsent(world, ignored -> new HashSet<>()).add(key);
    }

    // ------------------------------------------------------------------ the tick

    private void tick() {
        tick++;
        // Solves start every few ticks, so a burst of changes - an explosion - is one solve.
        if (tick % 4 == 0) {
            for (UUID world : List.copyOf(dirty.keySet())) {
                if (!solving.contains(world)) {
                    startSolve(world);
                }
            }
        }
        apply();
        if (tick % settings.contactTicks() == 0) {
            contact();
        }
    }

    /** Step one: copies what the solve needs, and hands it to the liquids' thread. */
    private void startSolve(UUID worldId) {
        Set<Long> points = dirty.remove(worldId);
        World world = plugin.getServer().getWorld(worldId);
        if (points == null || points.isEmpty() || world == null || registry.isEmpty()) {
            return;
        }
        int reach = settings.chunkReach();
        // The chunks this solve speaks for: around every change, as far as a liquid could run.
        Set<Long> authority = new HashSet<>();
        for (long point : points) {
            around(BlockKey.x(point) >> 4, BlockKey.z(point) >> 4, reach, authority);
        }
        // Every source that could reach into them; and every chunk those sources could reach.
        Map<Long, Source> inWorld = sources.getOrDefault(worldId, Map.of());
        Map<String, List<Long>> bySource = new HashMap<>();
        Set<Long> copied = new HashSet<>(authority);
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (long point : points) {
            lowest = Math.min(lowest, BlockKey.y(point));
            highest = Math.max(highest, BlockKey.y(point));
        }
        for (Map.Entry<Long, Source> entry : inWorld.entrySet()) {
            long key = entry.getKey();
            int cx = BlockKey.x(key) >> 4;
            int cz = BlockKey.z(key) >> 4;
            boolean near = false;
            for (long chunk : authority) {
                if (Math.abs(chunkX(chunk) - cx) <= reach && Math.abs(chunkZ(chunk) - cz) <= reach) {
                    near = true;
                    break;
                }
            }
            if (!near) {
                continue;
            }
            bySource.computeIfAbsent(entry.getValue().liquid(), ignored -> new ArrayList<>()).add(key);
            around(cx, cz, reach, copied);
            lowest = Math.min(lowest, BlockKey.y(key));
            highest = Math.max(highest, BlockKey.y(key));
        }

        Map<Long, ChunkSnapshot> snapshots = new HashMap<>();
        for (long chunk : copied) {
            int cx = chunkX(chunk);
            int cz = chunkZ(chunk);
            // An unloaded chunk is not loaded for a liquid: it is a wall, until it loads.
            if (world.isChunkLoaded(cx, cz)) {
                snapshots.put(chunk, world.getChunkAt(cx, cz).getChunkSnapshot(false, false, false));
            }
        }
        List<Job> jobs = new ArrayList<>();
        int deepest = 0;
        for (LiquidRegistry.Liquid liquid : registry.all()) {
            Placement.Liquid definition = liquid.definition();
            deepest = Math.max(deepest, definition.maxFall());
            jobs.add(new Job(liquid.id(), liquid.sourceData(), liquid.flowData(), definition.flowDistance(),
                    definition.maxFall(), definition.tickRate(), bySource.getOrDefault(liquid.id(), List.of())));
        }
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        int scanFrom = Math.max(minY, lowest - deepest - 1);
        int scanTo = Math.min(maxY - 1, highest + 1);

        solving.add(worldId);
        CompletableFuture.supplyAsync(() -> solve(worldId, jobs, snapshots, authority, minY, maxY, scanFrom, scanTo),
                        fluids)
                .whenComplete((solved, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    solving.remove(worldId);
                    if (error != null) {
                        logger.log(Level.WARNING, "A liquid solve failed", error);
                    } else {
                        schedule(solved, jobs);
                    }
                }));
    }

    /** Step two, on {@code ArkContent-Fluids}: the solve, and the difference from what is there. */
    private static Solved solve(UUID world, List<Job> jobs, Map<Long, ChunkSnapshot> snapshots, Set<Long> authority,
                                int minY, int maxY, int scanFrom, int scanTo) {
        Map<String, List<FluidSolver.Change>> changes = new HashMap<>();
        Map<String, Set<Long>> found = new HashMap<>();
        for (Job job : jobs) {
            FluidSolver.Terrain terrain = (x, y, z) -> {
                if (y < minY || y >= maxY) {
                    return false;
                }
                ChunkSnapshot snapshot = snapshots.get(chunkKey(x >> 4, z >> 4));
                if (snapshot == null) {
                    return false;
                }
                Material type = snapshot.getBlockType(x & 15, y, z & 15);
                if (type.isAir()) {
                    return true;
                }
                if (type != Material.TRIPWIRE) {
                    return false;
                }
                BlockData data = snapshot.getBlockData(x & 15, y, z & 15);
                return data.equals(job.flowData()) || data.equals(job.sourceData());
            };
            Map<Long, FluidSolver.Cell> wanted = new FluidSolver(job.flowDistance(), job.maxFall(), minY, maxY)
                    .solve(job.sources(), terrain);
            // What is there: this liquid's flowing blocks in the chunks the solve speaks for.
            Set<Long> present = new HashSet<>();
            for (long chunk : authority) {
                ChunkSnapshot snapshot = snapshots.get(chunk);
                if (snapshot == null) {
                    continue;
                }
                int baseX = chunkX(chunk) << 4;
                int baseZ = chunkZ(chunk) << 4;
                for (int y = scanFrom; y <= scanTo; y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            if (snapshot.getBlockType(x, y, z) == Material.TRIPWIRE
                                    && snapshot.getBlockData(x, y, z).equals(job.flowData())) {
                                present.add(BlockKey.pack(baseX + x, y, baseZ + z));
                            }
                        }
                    }
                }
            }
            List<FluidSolver.Change> diff = FluidSolver.changes(present, wanted,
                    key -> authority.contains(chunkKey(BlockKey.x(key) >> 4, BlockKey.z(key) >> 4)));
            if (!diff.isEmpty()) {
                changes.put(job.liquid(), diff);
            }
            if (!present.isEmpty()) {
                found.put(job.liquid(), present);
            }
        }
        return new Solved(world, changes, found);
    }

    /** Step three begins: each change gets its tick, a step of the liquid's tick rate per block from the source. */
    private void schedule(Solved solved, List<Job> jobs) {
        Map<String, Integer> rates = new HashMap<>();
        jobs.forEach(job -> rates.put(job.liquid(), job.tickRate()));
        // Flowing blocks already in the world - from before a restart - are known from now on.
        Map<Long, String> flowing = flows(solved.world());
        solved.present().forEach((liquid, keys) -> keys.forEach(key -> flowing.putIfAbsent(key, liquid)));
        for (Map.Entry<String, List<FluidSolver.Change>> entry : solved.changes().entrySet()) {
            int rate = rates.getOrDefault(entry.getKey(), 5);
            for (FluidSolver.Change change : entry.getValue()) {
                boolean flow = change.kind() == FluidSolver.Change.Kind.FLOW;
                // Drains go a little quicker than fills, so a removed source empties visibly but soon.
                long due = tick + (long) Math.max(1, change.depth()) * (flow ? rate : Math.max(1, rate / 2));
                pending.add(new Pending(due, change.depth(), solved.world(), change.key(), entry.getKey(), flow,
                        change.origin()));
            }
        }
    }

    /** Step three: the changes that are due, each checked against the world as it is now. */
    private void apply() {
        int budget = settings.changesPerTick();
        while (budget > 0 && !pending.isEmpty() && pending.peek().due() <= tick) {
            Pending change = pending.poll();
            budget--;
            World world = plugin.getServer().getWorld(change.world());
            LiquidRegistry.Liquid liquid = registry.get(change.liquid()).orElse(null);
            int x = BlockKey.x(change.key());
            int y = BlockKey.y(change.key());
            int z = BlockKey.z(change.key());
            if (world == null || liquid == null || !world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            Block block = world.getBlockAt(x, y, z);
            Map<Long, String> flowing = flows(change.world());
            if (!change.flow()) {
                flowing.remove(change.key());
                if (block.getBlockData().equals(liquid.flowData())) {
                    block.setType(Material.AIR, false);
                }
                continue;
            }
            if (!block.getType().isAir() || !fed(block, liquid)) {
                continue;
            }
            Location from = new Location(world, BlockKey.x(change.origin()), BlockKey.y(change.origin()),
                    BlockKey.z(change.origin()));
            if (!protection.allowsLiquidFlow(block, from)) {
                continue;
            }
            block.setBlockData(liquid.flowData(), false);
            flowing.put(change.key(), liquid.id());
        }
    }

    /** Whether liquid could run into {@code block} now: the same liquid above it, or beside it. */
    private boolean fed(Block block, LiquidRegistry.Liquid liquid) {
        if (is(block.getRelative(0, 1, 0), liquid)) {
            return true;
        }
        return is(block.getRelative(1, 0, 0), liquid) || is(block.getRelative(-1, 0, 0), liquid)
                || is(block.getRelative(0, 0, 1), liquid) || is(block.getRelative(0, 0, -1), liquid);
    }

    private boolean is(Block block, LiquidRegistry.Liquid liquid) {
        LiquidRegistry.State state = registry.state(block.getBlockData());
        return state != null && state.liquid() == liquid;
    }

    // ------------------------------------------------------------------ contact

    /** Every player standing or swimming in a liquid that does something gets what it does. */
    private void contact() {
        if (registry.isEmpty()) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR || player.isDead()) {
                continue;
            }
            Block feet = player.getLocation().getBlock();
            LiquidRegistry.State state = registry.state(feet.getBlockData());
            Block in = feet;
            if (state == null) {
                in = player.getEyeLocation().getBlock();
                state = registry.state(in.getBlockData());
            }
            if (state == null) {
                continue;
            }
            Placement.Contact contact = state.liquid().definition().contact();
            Long last = lastTouch.get(player.getUniqueId());
            if (contact.harmless() || last != null && tick - last < contact.intervalTicks()) {
                continue;
            }
            Location source = nearestSource(in, state.liquid());
            if (source == null || !protection.allowsLiquidContact(player, in, source)) {
                continue;
            }
            lastTouch.put(player.getUniqueId(), tick);
            touch(player, in, contact);
        }
    }

    public void forget(Player player) {
        lastTouch.remove(player.getUniqueId());
    }

    private void touch(Player player, Block in, Placement.Contact contact) {
        if (contact.damage() > 0) {
            DamageType type = damageType(contact.damageType());
            player.damage(contact.damage(), DamageSource.builder(type).withDamageLocation(in.getLocation()).build());
        }
        if (contact.fireTicks() > 0) {
            player.setFireTicks(Math.max(player.getFireTicks(), contact.fireTicks()));
        }
        if (contact.freezeTicks() > 0) {
            player.setFreezeTicks(Math.max(player.getFreezeTicks(), contact.freezeTicks()));
        }
        for (Placement.Effect effect : contact.effects()) {
            PotionEffectType type = effectType(effect.type());
            if (type != null) {
                player.addPotionEffect(new PotionEffect(type, effect.durationTicks(), effect.amplifier()));
            }
        }
    }

    /** The source the liquid at {@code block} most likely came from; null for liquid with no source near. */
    private @Nullable Location nearestSource(Block block, LiquidRegistry.Liquid liquid) {
        Map<Long, Source> inWorld = sources.get(block.getWorld().getUID());
        if (inWorld == null) {
            return null;
        }
        int reach = (settings.chunkReach() + 1) * 16;
        long best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (Map.Entry<Long, Source> entry : inWorld.entrySet()) {
            if (!entry.getValue().liquid().equals(liquid.id())) {
                continue;
            }
            long key = entry.getKey();
            int dx = BlockKey.x(key) - block.getX();
            int dz = BlockKey.z(key) - block.getZ();
            int dy = BlockKey.y(key) - block.getY();
            if (Math.abs(dx) > reach || Math.abs(dz) > reach || dy < 0) {
                continue;
            }
            double distance = dx * dx + dz * dz + dy * (double) dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = key;
            }
        }
        return bestDistance == Double.MAX_VALUE ? null
                : new Location(block.getWorld(), BlockKey.x(best), BlockKey.y(best), BlockKey.z(best));
    }

    private DamageType damageType(String key) {
        NamespacedKey parsed = NamespacedKey.fromString(key);
        DamageType type = parsed == null ? null
                : RegistryAccess.registryAccess().getRegistry(RegistryKey.DAMAGE_TYPE).get(parsed);
        if (type == null) {
            if (warned.add("damage " + key)) {
                logger.warning("Liquids: '" + key + "' is not a damage type on this server; using minecraft:generic");
            }
            return DamageType.GENERIC;
        }
        return type;
    }

    private @Nullable PotionEffectType effectType(String key) {
        NamespacedKey parsed = NamespacedKey.fromString(key);
        PotionEffectType type = parsed == null ? null
                : RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT).get(parsed);
        if (type == null && warned.add("effect " + key)) {
            logger.warning("Liquids: '" + key + "' is not a potion effect on this server; it is left out");
        }
        return type;
    }

    // ------------------------------------------------------------------ chunk keys

    private static void around(int chunkX, int chunkZ, int reach, Set<Long> into) {
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                into.add(chunkKey(chunkX + dx, chunkZ + dz));
            }
        }
    }

    static long chunkKey(int chunkX, int chunkZ) {
        return (long) chunkX << 32 | chunkZ & 0xFFFFFFFFL;
    }

    static int chunkX(long key) {
        return (int) (key >> 32);
    }

    static int chunkZ(long key) {
        return (int) key;
    }
}
