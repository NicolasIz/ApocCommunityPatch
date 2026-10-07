package com.arkcronist.content.bukkit.sanity;

import com.arkcronist.content.bukkit.ArkContentPlugin;
import com.arkcronist.content.bukkit.EngineSettings;
import com.arkcronist.content.bukkit.block.CustomBlock;
import com.arkcronist.content.bukkit.block.CustomBlockService;
import com.arkcronist.content.bukkit.furniture.FurnitureService;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.liquid.LiquidRegistry;
import com.arkcronist.content.bukkit.liquid.LiquidService;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.sanity.AuditPlan;
import com.arkcronist.content.core.sanity.SanityJudge;
import com.arkcronist.content.core.storage.BlockKey;
import com.arkcronist.content.core.storage.DatabaseManager;
import com.arkcronist.content.core.storage.FurnitureTransformStore;
import com.arkcronist.content.core.storage.PlacedContent;
import com.arkcronist.content.core.storage.PlacedContentStore;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The sanity checker: every {@code sanity.interval-seconds}, an audit of the loaded chunks for what
 * this plugin left inconsistent when something else changed the world behind its back - a
 * rollback, {@code /fill}, a world editor, a crash mid-save.
 *
 * <ul>
 *   <li>a furniture display whose support block is gone, or whose root is gone (a bone), is
 *       removed;</li>
 *   <li>a stored custom block whose note block is no longer that block's state, a furniture row
 *       whose support block is gone, and a liquid source whose tripwire is no longer the liquid's
 *       are purged - each row deleted in one transaction, and only if it still says what it said
 *       when it was found.</li>
 * </ul>
 *
 * <p>Threads: the cycle is driven from {@code ArkContent-DB}. It plans there - which rows of which
 * worlds, by chunk, from memory - and judges there: a finding is acted on only when two audits in a
 * row found it the same way ({@link SanityJudge}). The world itself can only be read on the server
 * thread, so the looking is done there, a slice of chunks per tick within
 * {@code sanity.max-millis-per-tick}, and never loads a chunk. What is confirmed is looked at once
 * more on the server thread before anything is removed; the row deletions then run on
 * {@code ArkContent-DB}. Content no longer defined is left alone: it may be back after the next
 * reload.</p>
 *
 * <p>Quiet: one line when something was cleaned, nothing otherwise; a failure is reported once and
 * the next audit runs as planned.</p>
 */
public final class SanityChecker {

    /** One of this plugin's displays found left over: an orphan, or a bone without its root. */
    record LeftOver(UUID entity, String furnitureId, int blockX, int blockY, int blockZ) {
    }

    /** A row whose block is not what it says, and what the block was found to be. */
    record StaleRow(PlacedContent row, String found) {
    }

    /** A liquid source whose block is not the liquid's source state. */
    record StaleLiquid(UUID world, int x, int y, int z, String liquid, @Nullable UUID owner, String found) {
    }

    private record Plan(Map<UUID, Map<Long, List<PlacedContent>>> rows) {
    }

    private record Findings(List<LeftOver> leftOvers, List<StaleRow> rows, List<StaleLiquid> liquids, int chunks,
                            int ticks, long nanos) {
    }

    /** The last audit, for {@code /arkcontent info}. */
    public record Status(long finishedAtMillis, int chunks, int ticks, double millis, int suspects,
                         int displaysRemoved, int rowsPurged, int liquidsPurged) {
    }

    private final ArkContentPlugin plugin;
    private final EngineSettings.Sanity settings;
    private final DatabaseManager database;
    private final PlacedContentStore placed;
    private final FurnitureTransformStore transforms;
    private final FurnitureService furniture;
    private final CustomBlockService blocks;
    private final LiquidService liquids;
    private final LiquidRegistry liquidRegistry;
    private final Logger logger;

    // Kept on ArkContent-DB: judged there, and nowhere else.
    private final SanityJudge<LeftOver> displayJudge = new SanityJudge<>(LeftOver::entity);
    private final SanityJudge<StaleRow> rowJudge = new SanityJudge<>(found -> found.row().world() + "/"
            + found.row().key());
    private final SanityJudge<StaleLiquid> liquidJudge = new SanityJudge<>(found -> found.world() + "/"
            + BlockKey.pack(found.x(), found.y(), found.z()));

    private final AtomicInteger displaysRemoved = new AtomicInteger();
    private final AtomicInteger rowsPurged = new AtomicInteger();
    private final AtomicInteger liquidsPurged = new AtomicInteger();
    private final Set<String> reported = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile boolean stopped;
    private volatile @Nullable Status last;

    public SanityChecker(ArkContentPlugin plugin, EngineSettings.Sanity settings, DatabaseManager database,
                         PlacedContentStore placed, FurnitureTransformStore transforms, FurnitureService furniture,
                         CustomBlockService blocks, LiquidService liquids, LiquidRegistry liquidRegistry) {
        this.plugin = plugin;
        this.settings = settings;
        this.database = database;
        this.placed = placed;
        this.transforms = transforms;
        this.furniture = furniture;
        this.blocks = blocks;
        this.liquids = liquids;
        this.liquidRegistry = liquidRegistry;
        this.logger = plugin.getLogger();
    }

    public void start() {
        if (settings.enabled()) {
            schedule();
        }
    }

    public void stop() {
        stopped = true;
    }

    public boolean enabled() {
        return settings.enabled();
    }

    public @Nullable Status last() {
        return last;
    }

    /** The next audit, an interval from now; started on ArkContent-DB. */
    private void schedule() {
        if (stopped) {
            return;
        }
        CompletableFuture.delayedExecutor(settings.intervalSeconds(), TimeUnit.SECONDS).execute(() -> {
            if (stopped) {
                return;
            }
            database.onThread(this::plan)
                    .thenCompose(this::observe)
                    .thenCompose(findings -> database.onThread(() -> judge(findings)))
                    .thenCompose(this::act)
                    .whenComplete((ignored, error) -> {
                        if (error != null) {
                            quietly(error);
                        }
                        schedule();
                    });
        });
    }

    // ---------------------------------------------------------------- ArkContent-DB: plan

    private Plan plan() {
        Map<UUID, Map<Long, List<PlacedContent>>> rows = new HashMap<>();
        for (UUID world : placed.worlds()) {
            // Until a world's rows are all in, memory is not the whole story.
            if (!placed.loading(world)) {
                rows.put(world, AuditPlan.byChunk(placed.entries(world)));
            }
        }
        return new Plan(rows);
    }

    // ---------------------------------------------------------------- server thread: look

    private CompletableFuture<Findings> observe(Plan plan) {
        CompletableFuture<Findings> done = new CompletableFuture<>();
        plugin.pipeline().mainThread().execute(() -> {
            if (stopped) {
                return;
            }
            new Audit(plan, done).runTaskTimer(plugin, 0, 1);
        });
        return done;
    }

    /** One audit on the server thread: loaded chunks, a slice per tick. */
    private final class Audit extends BukkitRunnable {

        private final Plan plan;
        private final CompletableFuture<Findings> done;
        private final ArrayDeque<Chunk> chunks = new ArrayDeque<>();
        /** Per world, its liquid sources by chunk. */
        private final Map<UUID, Map<Long, List<Map.Entry<Long, LiquidService.Source>>>> sources = new HashMap<>();
        private final List<LeftOver> leftOvers = new ArrayList<>();
        private final List<StaleRow> rows = new ArrayList<>();
        private final List<StaleLiquid> stale = new ArrayList<>();
        private boolean started;
        private int audited;
        private int ticks;
        private long nanos;

        Audit(Plan plan, CompletableFuture<Findings> done) {
            this.plan = plan;
            this.done = done;
        }

        @Override
        public void run() {
            long began = System.nanoTime();
            try {
                if (stopped) {
                    cancel();
                    return;
                }
                if (!started) {
                    started = true;
                    for (World world : plugin.getServer().getWorlds()) {
                        chunks.addAll(List.of(world.getLoadedChunks()));
                        Map<Long, List<Map.Entry<Long, LiquidService.Source>>> byChunk = new HashMap<>();
                        for (Map.Entry<Long, LiquidService.Source> source : liquids.sources(world).entrySet()) {
                            long key = source.getKey();
                            byChunk.computeIfAbsent(AuditPlan.chunkOf(BlockKey.x(key), BlockKey.z(key)),
                                    ignored -> new ArrayList<>()).add(source);
                        }
                        sources.put(world.getUID(), byChunk);
                    }
                }
                long budget = (long) (settings.maxMillisPerTick() * 1_000_000);
                do {
                    Chunk chunk = chunks.poll();
                    if (chunk == null) {
                        break;
                    }
                    if (chunk.isLoaded()) {
                        audit(chunk);
                        audited++;
                    }
                } while (System.nanoTime() - began < budget);
                ticks++;
                nanos += System.nanoTime() - began;
                if (chunks.isEmpty()) {
                    cancel();
                    done.complete(new Findings(List.copyOf(leftOvers), List.copyOf(rows), List.copyOf(stale), audited,
                            ticks, nanos));
                }
            } catch (RuntimeException | LinkageError error) {
                cancel();
                done.completeExceptionally(error);
            }
        }

        private void audit(Chunk chunk) {
            World world = chunk.getWorld();
            UUID id = world.getUID();
            if (chunk.isEntitiesLoaded()) {
                for (Entity entity : chunk.getEntities()) {
                    LeftOver found = leftOver(entity);
                    if (found != null) {
                        leftOvers.add(found);
                    }
                }
            }
            Map<Long, List<PlacedContent>> worldRows = plan.rows().get(id);
            List<PlacedContent> here = worldRows == null ? null : worldRows.get(chunk.getChunkKey());
            if (here != null) {
                for (PlacedContent row : here) {
                    String found = staleRow(world, row);
                    if (found != null) {
                        rows.add(new StaleRow(row, found));
                    }
                }
            }
            List<Map.Entry<Long, LiquidService.Source>> poured = sources.getOrDefault(id, Map.of())
                    .get(chunk.getChunkKey());
            if (poured != null) {
                for (Map.Entry<Long, LiquidService.Source> source : poured) {
                    long key = source.getKey();
                    StaleLiquid found = staleLiquid(world, BlockKey.x(key), BlockKey.y(key), BlockKey.z(key),
                            source.getValue());
                    if (found != null) {
                        stale.add(found);
                    }
                }
            }
        }
    }

    /** A display of this plugin's that is left over, with its anchor's chunk loaded; else null. */
    private @Nullable LeftOver leftOver(Entity entity) {
        if (!(entity instanceof ItemDisplay) || !entity.isValid()) {
            return null;
        }
        String id = furniture.furnitureId(entity);
        if (id == null) {
            return null;
        }
        Block anchor = furniture.anchorOf(entity);
        // Judging needs the anchor's block, and an audit never loads a chunk to read one.
        if (!entity.getWorld().isChunkLoaded(anchor.getX() >> 4, anchor.getZ() >> 4) || !furniture.isLeftOver(entity)) {
            return null;
        }
        return new LeftOver(entity.getUniqueId(), id, anchor.getX(), anchor.getY(), anchor.getZ());
    }

    /** What the block of a row is, when it is not what the row says; null when it is, or cannot be told. */
    private @Nullable String staleRow(World world, PlacedContent row) {
        if (!world.isChunkLoaded(row.x() >> 4, row.z() >> 4)) {
            return null;
        }
        Block block = world.getBlockAt(row.x(), row.y(), row.z());
        return switch (row.kind()) {
            case BLOCK -> {
                Optional<CustomBlock> defined = plugin.blocks().get(row.contentId());
                if (defined.isEmpty()) {
                    yield null;
                }
                Optional<CustomBlock> there = blocks.identify(block);
                yield there.isPresent() && there.get().id().equals(row.contentId()) ? null
                        : block.getBlockData().getAsString();
            }
            case FURNITURE -> {
                Optional<CustomItem> item = plugin.items().get(row.contentId());
                boolean stands = item.isPresent() && item.get().placement() instanceof Placement.Furniture definition
                        ? FurnitureService.matches(block, definition)
                        : FurnitureService.isSupport(block.getType());
                yield stands ? null : block.getType().getKey().toString();
            }
        };
    }

    private @Nullable StaleLiquid staleLiquid(World world, int x, int y, int z, LiquidService.Source source) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        Optional<LiquidRegistry.Liquid> liquid = liquidRegistry.get(source.liquid());
        if (liquid.isEmpty()) {
            return null;
        }
        BlockData data = world.getBlockAt(x, y, z).getBlockData();
        return data.equals(liquid.get().sourceData()) ? null
                : new StaleLiquid(world.getUID(), x, y, z, source.liquid(), source.owner(), data.getAsString());
    }

    // ---------------------------------------------------------------- ArkContent-DB: judge

    private record Confirmed(List<LeftOver> leftOvers, List<StaleRow> rows, List<StaleLiquid> liquids,
                             Findings findings, int suspects) {
    }

    private Confirmed judge(Findings findings) {
        List<LeftOver> leftOvers = displayJudge.judge(findings.leftOvers());
        List<StaleRow> rows = rowJudge.judge(findings.rows());
        List<StaleLiquid> stale = liquidJudge.judge(findings.liquids());
        return new Confirmed(leftOvers, rows, stale, findings,
                displayJudge.suspects() + rowJudge.suspects() + liquidJudge.suspects());
    }

    // ---------------------------------------------------------------- server thread: act

    private CompletableFuture<Void> act(Confirmed confirmed) {
        return CompletableFuture.supplyAsync(() -> removeConfirmed(confirmed), plugin.pipeline().mainThread())
                .thenCompose(purges -> CompletableFuture.allOf(purges.rows().toArray(CompletableFuture[]::new))
                        .thenCombine(CompletableFuture.allOf(purges.liquids().toArray(CompletableFuture[]::new)),
                                (a, b) -> purges))
                .thenAccept(purges -> report(confirmed, purges));
    }

    private record Purges(int displays, List<CompletableFuture<Boolean>> rows, List<CompletableFuture<Boolean>> liquids) {
    }

    /** Each confirmed finding looked at once more - the world may have changed since - then removed. */
    private Purges removeConfirmed(Confirmed confirmed) {
        int displays = 0;
        for (LeftOver found : confirmed.leftOvers()) {
            Entity entity = plugin.getServer().getEntity(found.entity());
            LeftOver now = entity == null ? null : leftOver(entity);
            if (found.equals(now)) {
                furniture.removeLeftOver(entity);
                displays++;
            }
        }
        List<CompletableFuture<Boolean>> rows = new ArrayList<>();
        for (StaleRow found : confirmed.rows()) {
            PlacedContent row = found.row();
            World world = plugin.getServer().getWorld(row.world());
            if (world == null || !found.found().equals(staleRow(world, row))) {
                continue;
            }
            if (row.kind() == PlacedContent.Kind.FURNITURE) {
                furniture.forgetStale(world.getBlockAt(row.x(), row.y(), row.z()));
                // The purge deletes its row with the furniture's, in the same transaction.
                transforms.forget(row.world(), row.x(), row.y(), row.z());
            }
            rows.add(placed.purge(row).exceptionally(error -> {
                quietly(error);
                return false;
            }));
        }
        List<CompletableFuture<Boolean>> stale = new ArrayList<>();
        for (StaleLiquid found : confirmed.liquids()) {
            World world = plugin.getServer().getWorld(found.world());
            if (world == null) {
                continue;
            }
            Optional<LiquidService.Source> source = liquids.source(world.getBlockAt(found.x(), found.y(), found.z()));
            if (source.isEmpty() || !found.equals(staleLiquid(world, found.x(), found.y(), found.z(), source.get()))) {
                continue;
            }
            if (liquids.forgetStale(world.getBlockAt(found.x(), found.y(), found.z()), found.liquid())) {
                stale.add(database.purgeLiquidIfUnchanged(found.world(), new DatabaseManager.LiquidSource(found.x(),
                        found.y(), found.z(), found.liquid(), found.owner())).exceptionally(error -> {
                            quietly(error);
                            return false;
                        }));
            }
        }
        return new Purges(displays, rows, stale);
    }

    private void report(Confirmed confirmed, Purges purges) {
        int rows = (int) purges.rows().stream().filter(future -> Boolean.TRUE.equals(future.join())).count();
        int stale = (int) purges.liquids().stream().filter(future -> Boolean.TRUE.equals(future.join())).count();
        displaysRemoved.addAndGet(purges.displays());
        rowsPurged.addAndGet(rows);
        liquidsPurged.addAndGet(stale);
        Findings findings = confirmed.findings();
        double millis = findings.nanos() / 1_000_000.0;
        last = new Status(System.currentTimeMillis(), findings.chunks(), findings.ticks(), millis, confirmed.suspects(),
                displaysRemoved.get(), rowsPurged.get(), liquidsPurged.get());
        if (purges.displays() + rows + stale > 0) {
            logger.info(String.format(java.util.Locale.ROOT, "Sanity check: removed %d left-over furniture display(s),"
                            + " purged %d stale block/furniture row(s) and %d liquid source(s) whose block was gone"
                            + " (%d chunk(s) audited over %d tick(s), %.1f ms of server time).",
                    purges.displays(), rows, stale, findings.chunks(), findings.ticks(), millis));
        } else {
            logger.log(Level.FINE, "Sanity check: nothing to clean in " + findings.chunks() + " chunk(s); "
                    + confirmed.suspects() + " finding(s) wait for the next audit.");
        }
    }

    /** A failure is told once, briefly; the next audit runs anyway. */
    private void quietly(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getClass().getSimpleName() + ": " + cause.getMessage();
        if (reported.add(message)) {
            logger.warning("The sanity check could not finish (" + message + "); it tries again in "
                    + settings.intervalSeconds() + " s.");
        } else {
            logger.log(Level.FINE, "Sanity check failed again: " + message);
        }
    }
}
