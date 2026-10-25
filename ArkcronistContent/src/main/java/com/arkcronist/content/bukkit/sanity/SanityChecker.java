package com.arkcronist.content.bukkit.sanity;

import com.arkcronist.content.bukkit.ArkContentPlugin;
import com.arkcronist.content.bukkit.EngineSettings;
import com.arkcronist.content.bukkit.block.CustomBlock;
import com.arkcronist.content.bukkit.block.CustomBlockService;
import com.arkcronist.content.bukkit.furniture.FurnitureService;
import com.arkcronist.content.bukkit.furniture.SeatService;
import com.arkcronist.content.bukkit.furniture.StorageService;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.liquid.LiquidRegistry;
import com.arkcronist.content.bukkit.liquid.LiquidService;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.furniture.Footprint;
import com.arkcronist.content.core.sanity.AuditPlan;
import com.arkcronist.content.core.sanity.PieceAudit;
import com.arkcronist.content.core.sanity.RepairBudget;
import com.arkcronist.content.core.sanity.SanityJudge;
import com.arkcronist.content.core.storage.BlockKey;
import com.arkcronist.content.core.storage.BlockRepair;
import com.arkcronist.content.core.storage.DatabaseManager;
import com.arkcronist.content.core.storage.FurniturePiece;
import com.arkcronist.content.core.storage.FurnitureTransformStore;
import com.arkcronist.content.core.storage.PlacedContent;
import com.arkcronist.content.core.storage.PlacedContentStore;
import com.arkcronist.content.core.storage.RecoveredStorage;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The integrity check: every {@code sanity.interval-seconds}, an audit of the loaded chunks for what
 * this plugin left inconsistent when something else changed the world behind its back - a
 * rollback, {@code /fill}, a world editor, {@code /kill}, an entity cleaner, a crash mid-save.
 *
 * <ul>
 *   <li>a piece of furniture is judged whole ({@link PieceAudit}): one whose support blocks are not
 *       all there any more is taken out - displays, the invisible supports left, every row, in one
 *       transaction, and what its storage held moved to {@code recovered_storage}; one whose
 *       display is gone while every support stayed is drawn again, as it faced;</li>
 *   <li>a display of this plugin's with no rows behind it, or a bone without its root, is removed;</li>
 *   <li>a stored custom block whose note block is no longer that block's state, and a liquid source
 *       whose tripwire is no longer the liquid's, are put back (5.0) when their block was turned to
 *       air or is still a note block or tripwire - at most {@code sanity.max-rebuilds-per-block-per-day}
 *       times a day each ({@link RepairBudget}, kept in {@code block_repairs}). Anything else found
 *       there, or past that, has its row purged - each only if it still says what it said when it
 *       was found;</li>
 *   <li>storage rows furniture purged before 2.0 left behind are recovered.</li>
 * </ul>
 *
 * <p>Threads: the cycle is driven from {@code ArkContent-DB}. It plans there - which rows of which
 * worlds, by chunk, from memory, and each piece's recorded facing - and judges there: a finding is
 * acted on only when two audits in a row found it the same way ({@link SanityJudge}). The world
 * itself can only be read on the server thread, so the looking is done there, a slice of chunks
 * per tick within {@code sanity.max-millis-per-tick}, and never loads a chunk. What is confirmed is
 * looked at once more on the server thread before anything is changed; the row deletions then run
 * on {@code ArkContent-DB}. Content no longer defined is left alone: it may be back after the next
 * reload.</p>
 *
 * <p>Besides the cycle, a world edit that replaced a furniture support, or the removal of one of
 * this plugin's displays by anything but a chunk unloading, has just those chunks audited twice a
 * couple of seconds later ({@link #touched}).</p>
 *
 * <p>Quiet: one line when something was done, nothing otherwise; a failure is reported once, without
 * a stack trace, and the next audit runs as planned.</p>
 */
public final class SanityChecker {

    private static final String BROKEN_REASON = "its blocks were gone (a rollback, /fill or a world editor)";
    private static final String STRAY_REASON = "its display was gone and it could not be drawn again";
    private static final String ORPHANED_REASON = "left behind by furniture purged before 2.0";
    /** Ticks from an edit or a removal to the first targeted look, and between its two looks. */
    private static final long TARGET_DELAY = 40;
    private static final long TARGET_GAP = 20;

    /** One of this plugin's displays found left over: an orphan with no rows, or a bone without its root. */
    record LeftOver(UUID entity, String furnitureId, int blockX, int blockY, int blockZ) {
    }

    /** A custom block row whose block is not what it says, and what the block was found to be. */
    record StaleRow(PlacedContent row, String found) {
    }

    /** A liquid source whose block is not the liquid's source state. */
    record StaleLiquid(UUID world, int x, int y, int z, String liquid, @Nullable UUID owner, String found) {
    }

    /**
     * A piece of furniture judged broken, stray or a ghost: everything that was seen of it, so the
     * next look can tell whether it is still exactly so.
     *
     * @param parts    every block it covers, packed, anchor first
     * @param rows     its rows, as found
     * @param root     its display, when seen
     * @param recorded whether its facing was recorded (schema 8), or read off nothing
     * @param paired   half of a double chest
     */
    record PieceFinding(UUID world, long anchor, String furnitureId, PieceAudit.Verdict verdict, List<Long> parts,
                        int supports, List<PlacedContent> rows, @Nullable UUID root, float yaw, boolean recorded,
                        boolean paired) {
    }

    /** One look at a piece: the finding, if it is not healthy; the blocks it covers; a facing to record. */
    private record Look(@Nullable PieceFinding finding, Set<Long> covered, @Nullable FurniturePiece adopt) {
    }

    private record Plan(Map<UUID, Map<Long, List<PlacedContent>>> rows, Map<UUID, Map<Long, FurniturePiece>> pieces,
                        int orphanedStorage) {
    }

    private record Findings(List<LeftOver> leftOvers, List<StaleRow> rows, List<StaleLiquid> liquids,
                            List<PieceFinding> pieces, List<FurniturePiece> adopt, int chunks, int ticks, long nanos) {
    }

    /** The last audit, for {@code /arkcontent info} and {@code /arkcontent integrity}. */
    public record Status(long finishedAtMillis, int chunks, int ticks, double millis, int suspects,
                         int displaysRemoved, int rowsPurged, int liquidsPurged, int piecesRemoved, int piecesRedrawn,
                         int storageRecovered, int piecesAdopted, int blocksRebuilt, int liquidsRebuilt) {
    }

    /** What two looks in a row are judged by. The cycle keeps one set; a targeted audit has its own. */
    private static final class Judges {
        // Kept on ArkContent-DB: judged there, and nowhere else.
        final SanityJudge<LeftOver> displays = new SanityJudge<>(LeftOver::entity);
        final SanityJudge<StaleRow> rows = new SanityJudge<>(found -> found.row().world() + "/" + found.row().key());
        final SanityJudge<StaleLiquid> liquids = new SanityJudge<>(found -> found.world() + "/"
                + BlockKey.pack(found.x(), found.y(), found.z()));
        final SanityJudge<PieceFinding> pieces = new SanityJudge<>(found -> found.world() + "/" + found.anchor());

        int suspects() {
            return displays.suspects() + rows.suspects() + liquids.suspects() + pieces.suspects();
        }
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
    private final SeatService seats;
    private final StorageService storage;
    private final Logger logger;

    private final Judges cycle = new Judges();

    private final AtomicInteger displaysRemoved = new AtomicInteger();
    private final AtomicInteger rowsPurged = new AtomicInteger();
    private final AtomicInteger liquidsPurged = new AtomicInteger();
    private final AtomicInteger piecesRemoved = new AtomicInteger();
    private final AtomicInteger piecesRedrawn = new AtomicInteger();
    private final AtomicInteger storageRecovered = new AtomicInteger();
    private final AtomicInteger piecesAdopted = new AtomicInteger();
    private final AtomicInteger blocksRebuilt = new AtomicInteger();
    private final AtomicInteger liquidsRebuilt = new AtomicInteger();
    private final RepairBudget budget;
    /** 5.0: what was put back in the current window, by {@link BlockRepair#key()}: loaded once, then the server thread's. */
    private final Map<String, BlockRepair> repairs = new ConcurrentHashMap<>();
    private volatile CompletableFuture<Void> repairsLoaded = CompletableFuture.completedFuture(null);
    private final Set<String> reported = ConcurrentHashMap.newKeySet();
    /** Chunks to look at soon, by world: touched by a world edit or a display's removal. */
    private final Map<UUID, Set<Long>> touched = new ConcurrentHashMap<>();
    private final AtomicBoolean targetScheduled = new AtomicBoolean();
    private volatile boolean stopped;
    private volatile @Nullable Status last;

    public SanityChecker(ArkContentPlugin plugin, EngineSettings.Sanity settings, DatabaseManager database,
                         PlacedContentStore placed, FurnitureTransformStore transforms, FurnitureService furniture,
                         CustomBlockService blocks, LiquidService liquids, LiquidRegistry liquidRegistry,
                         SeatService seats, StorageService storage) {
        this.plugin = plugin;
        this.settings = settings;
        this.database = database;
        this.placed = placed;
        this.transforms = transforms;
        this.furniture = furniture;
        this.blocks = blocks;
        this.liquids = liquids;
        this.liquidRegistry = liquidRegistry;
        this.seats = seats;
        this.storage = storage;
        this.logger = plugin.getLogger();
        this.budget = new RepairBudget(settings.rebuildsPerDay(), RepairBudget.DEFAULT.windowMillis());
    }

    public void start() {
        if (settings.enabled()) {
            repairsLoaded = loadRepairs();
            schedule();
        }
    }

    /** 5.0: the repairs of the current window, the ones before it forgotten; the first audit waits for them. */
    private CompletableFuture<Void> loadRepairs() {
        long now = System.currentTimeMillis();
        return database.forgetRepairsBefore(now - budget.windowMillis())
                .thenCompose(ignored -> database.blockRepairs())
                .thenAccept(found -> found.forEach(repair -> repairs.put(repair.key(), repair)))
                .exceptionally(error -> {
                    quietly(error);
                    return null;
                });
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
            audit(cycle, null, true).whenComplete((ignored, error) -> {
                if (error != null) {
                    quietly(error);
                }
                schedule();
            });
        });
    }

    /**
     * Audits the loaded chunks now, twice in a row with judges of their own, and acts on what both
     * looks found: {@code /arkcontent integrity now}.
     */
    public CompletableFuture<Status> runNow() {
        Judges judges = new Judges();
        return audit(judges, null, true).thenCompose(ignored -> audit(judges, null, false))
                .thenApply(ignored -> last);
    }

    /**
     * Something may have broken furniture at this block - a world edit replaced a support, a display
     * was removed - so its chunk is audited on its own, twice, a couple of seconds from now. Any
     * thread; cheap, and many calls in a row make one audit.
     */
    public void touched(World world, int blockX, int blockZ) {
        if (stopped || !settings.enabled()) {
            return;
        }
        touched.computeIfAbsent(world.getUID(), ignored -> ConcurrentHashMap.newKeySet())
                .add(AuditPlan.chunkOf(blockX, blockZ));
        if (targetScheduled.compareAndSet(false, true)) {
            plugin.getServer().getScheduler().runTaskLater(plugin, this::auditTouched, TARGET_DELAY);
        }
    }

    private void auditTouched() {
        targetScheduled.set(false);
        Map<UUID, Set<Long>> chunks = new HashMap<>();
        for (UUID world : Set.copyOf(touched.keySet())) {
            Set<Long> keys = touched.remove(world);
            if (keys != null && !keys.isEmpty()) {
                chunks.put(world, Set.copyOf(keys));
            }
        }
        if (chunks.isEmpty() || stopped) {
            return;
        }
        Judges judges = new Judges();
        audit(judges, chunks, false)
                .thenCompose(ignored -> {
                    CompletableFuture<Void> later = new CompletableFuture<>();
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> later.complete(null), TARGET_GAP);
                    return later;
                })
                .thenCompose(ignored -> audit(judges, chunks, false))
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        quietly(error);
                    }
                });
    }

    /** One audit: plan, look, judge, act, report. */
    private CompletableFuture<Void> audit(Judges judges, @Nullable Map<UUID, Set<Long>> only, boolean sweepStorage) {
        return plan(sweepStorage)
                .thenCompose(plan -> observe(plan, only).thenApply(findings -> Map.entry(plan, findings)))
                .thenCompose(both -> database.onThread(() -> judge(judges, both.getValue()))
                        .thenApply(confirmed -> Map.entry(both.getKey(), confirmed)))
                .thenCompose(both -> act(both.getKey(), both.getValue(), judges));
    }

    // ---------------------------------------------------------------- ArkContent-DB: plan

    private CompletableFuture<Plan> plan(boolean sweepStorage) {
        return repairsLoaded.thenCompose(loaded -> database.onThread(() -> {
            Map<UUID, Map<Long, List<PlacedContent>>> rows = new HashMap<>();
            for (UUID world : placed.worlds()) {
                // Until a world's rows are all in, memory is not the whole story.
                if (!placed.loading(world)) {
                    rows.put(world, AuditPlan.byChunk(placed.entries(world)));
                }
            }
            return rows;
        })).thenCompose(rows -> {
            Map<UUID, Map<Long, FurniturePiece>> pieces = new ConcurrentHashMap<>();
            List<CompletableFuture<?>> reads = new ArrayList<>();
            for (UUID world : rows.keySet()) {
                reads.add(database.loadPieces(world).thenAccept(found -> pieces.put(world, found)));
            }
            CompletableFuture<Integer> orphaned = sweepStorage
                    ? database.recoverOrphanedStorage(ORPHANED_REASON) : CompletableFuture.completedFuture(0);
            return CompletableFuture.allOf(reads.toArray(CompletableFuture[]::new))
                    .thenCombine(orphaned, (ignored, count) -> new Plan(rows, pieces, count));
        });
    }

    // ---------------------------------------------------------------- server thread: look

    private CompletableFuture<Findings> observe(Plan plan, @Nullable Map<UUID, Set<Long>> only) {
        CompletableFuture<Findings> done = new CompletableFuture<>();
        plugin.pipeline().mainThread().execute(() -> {
            if (stopped) {
                done.cancel(false);
                return;
            }
            new Audit(plan, only, done).runTaskTimer(plugin, 0, 1);
        });
        return done;
    }

    /** One audit on the server thread: loaded chunks, a slice per tick. */
    private final class Audit extends BukkitRunnable {

        private final Plan plan;
        private final @Nullable Map<UUID, Set<Long>> only;
        private final CompletableFuture<Findings> done;
        private final ArrayDeque<Chunk> chunks = new ArrayDeque<>();
        /** Per world, its liquid sources by chunk. */
        private final Map<UUID, Map<Long, List<Map.Entry<Long, LiquidService.Source>>>> sources = new HashMap<>();
        /** Per world, every block of a recorded piece to its anchor. */
        private final Map<UUID, Map<Long, Long>> anchors = new HashMap<>();
        /** Per world, the blocks of pieces already looked at in this audit. */
        private final Map<UUID, Set<Long>> covered = new HashMap<>();
        private final List<LeftOver> leftOvers = new ArrayList<>();
        private final List<StaleRow> rows = new ArrayList<>();
        private final List<StaleLiquid> stale = new ArrayList<>();
        private final List<PieceFinding> pieces = new ArrayList<>();
        private final List<FurniturePiece> adopt = new ArrayList<>();
        private boolean started;
        private int audited;
        private int ticks;
        private long nanos;

        Audit(Plan plan, @Nullable Map<UUID, Set<Long>> only, CompletableFuture<Findings> done) {
            this.plan = plan;
            this.only = only;
            this.done = done;
        }

        @Override
        public void run() {
            long began = System.nanoTime();
            try {
                if (stopped) {
                    cancel();
                    done.cancel(false);
                    return;
                }
                if (!started) {
                    started = true;
                    start();
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
                    done.complete(new Findings(List.copyOf(leftOvers), List.copyOf(rows), List.copyOf(stale),
                            List.copyOf(pieces), List.copyOf(adopt), audited, ticks, nanos));
                }
            } catch (RuntimeException | LinkageError error) {
                cancel();
                done.completeExceptionally(error);
            }
        }

        private void start() {
            for (World world : plugin.getServer().getWorlds()) {
                Set<Long> wanted = only == null ? null : only.get(world.getUID());
                if (only != null && wanted == null) {
                    continue;
                }
                for (Chunk chunk : world.getLoadedChunks()) {
                    if (wanted == null || wanted.contains(chunk.getChunkKey())) {
                        chunks.add(chunk);
                    }
                }
                Map<Long, List<Map.Entry<Long, LiquidService.Source>>> byChunk = new HashMap<>();
                for (Map.Entry<Long, LiquidService.Source> source : liquids.sources(world).entrySet()) {
                    long key = source.getKey();
                    byChunk.computeIfAbsent(AuditPlan.chunkOf(BlockKey.x(key), BlockKey.z(key)),
                            ignored -> new ArrayList<>()).add(source);
                }
                sources.put(world.getUID(), byChunk);
                anchors.put(world.getUID(), PieceAudit.anchors(
                        plan.pieces().getOrDefault(world.getUID(), Map.of()), SanityChecker.this::footprint));
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
                Set<Long> seen = covered.computeIfAbsent(id, ignored -> new HashSet<>());
                Map<Long, FurniturePiece> recorded = plan.pieces().getOrDefault(id, Map.of());
                for (PlacedContent row : here) {
                    if (row.kind() == PlacedContent.Kind.FURNITURE) {
                        if (seen.contains(row.key())) {
                            continue;
                        }
                        Look look = lookAt(world, row, recorded, anchors.getOrDefault(id, Map.of()));
                        if (look != null) {
                            seen.addAll(look.covered());
                            if (look.finding() != null) {
                                pieces.add(look.finding());
                            }
                            if (look.adopt() != null) {
                                adopt.add(look.adopt());
                            }
                            continue;
                        }
                    }
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

    /** The footprint furniture with this id has now; null when it is no longer defined as furniture. */
    private @Nullable Footprint footprint(String id) {
        Optional<CustomItem> item = plugin.items().get(id);
        return item.isPresent() && item.get().placement() instanceof Placement.Furniture definition
                ? definition.footprint() : null;
    }

    /**
     * One look at the piece a furniture row belongs to, as a whole. Null when the furniture is no
     * longer defined: its row is then judged on its own, as before 2.0.
     */
    private @Nullable Look lookAt(World world, PlacedContent row, Map<Long, FurniturePiece> recorded,
                                  Map<Long, Long> anchors) {
        Optional<CustomItem> item = plugin.items().get(row.contentId());
        if (item.isEmpty() || !(item.get().placement() instanceof Placement.Furniture definition)) {
            return null;
        }
        Block block = world.getBlockAt(row.x(), row.y(), row.z());
        ItemDisplay root = furniture.pieceRoot(block);
        if (root != null && !row.contentId().equals(furniture.furnitureId(root))) {
            root = null;
        }
        List<Block> parts;
        float yaw;
        boolean known;
        FurniturePiece record = null;
        if (root != null) {
            parts = furniture.pieceParts(root);
            yaw = root.getLocation().getYaw();
            known = true;
            record = recorded.get(BlockKey.pack(parts.get(0).getX(), parts.get(0).getY(), parts.get(0).getZ()));
        } else {
            Long anchorKey = anchors.get(row.key());
            FurniturePiece candidate = anchorKey == null ? null : recorded.get(anchorKey);
            if (candidate != null && candidate.furnitureId().equals(row.contentId())
                    && world.isChunkLoaded(candidate.x() >> 4, candidate.z() >> 4)) {
                record = candidate;
                Block anchor = world.getBlockAt(candidate.x(), candidate.y(), candidate.z());
                List<Block> laid = furniture.layoutParts(anchor, definition, candidate.yaw());
                // Only the blocks it has rows on: a definition changed since it was placed is not
                // a reason to call blocks it never stood on its own.
                parts = laid.stream().filter(part -> ours(world, part, row.contentId())).toList();
                if (!parts.contains(block)) {
                    parts = List.of(block);
                }
                yaw = candidate.yaw();
                known = parts.size() == laid.size();
            } else {
                parts = List.of(block);
                yaw = 0f;
                known = definition.footprint().single() && definition.support() != Placement.Support.BED;
            }
        }
        int supports = 0;
        boolean blocksLoaded = true;
        boolean entitiesLoaded = true;
        Set<Long> covered = new HashSet<>();
        List<Long> packed = new ArrayList<>(parts.size());
        List<PlacedContent> rows = new ArrayList<>(parts.size());
        for (Block part : parts) {
            long key = BlockKey.pack(part.getX(), part.getY(), part.getZ());
            covered.add(key);
            packed.add(key);
            if (!world.isChunkLoaded(part.getX() >> 4, part.getZ() >> 4)) {
                blocksLoaded = false;
                entitiesLoaded = false;
                continue;
            }
            if (!world.getChunkAt(part.getX() >> 4, part.getZ() >> 4).isEntitiesLoaded()) {
                entitiesLoaded = false;
            }
            if (FurnitureService.matches(part, definition)) {
                supports++;
            }
            PlacedContent there = placed.at(world.getUID(), part.getX(), part.getY(), part.getZ());
            if (there != null && there.kind() == PlacedContent.Kind.FURNITURE && there.contentId().equals(row.contentId())) {
                rows.add(there);
            }
        }
        Block anchor = parts.get(0);
        boolean paired = furniture.pairAt(anchor).isPresent();
        PieceAudit.Verdict verdict = PieceAudit.judge(new PieceAudit.Seen(parts.size(), supports, root != null,
                blocksLoaded, entitiesLoaded, known || paired));
        FurniturePiece adopt = null;
        if (verdict == PieceAudit.Verdict.HEALTHY && root != null && record == null) {
            adopt = new FurniturePiece(world.getUID(), anchor.getX(), anchor.getY(), anchor.getZ(), row.contentId(), yaw);
        }
        PieceFinding finding = null;
        if (verdict == PieceAudit.Verdict.BROKEN || verdict == PieceAudit.Verdict.GHOST
                || verdict == PieceAudit.Verdict.STRAY) {
            finding = new PieceFinding(world.getUID(), packed.get(0), row.contentId(), verdict, List.copyOf(packed),
                    supports, List.copyOf(rows), root == null ? null : root.getUniqueId(), yaw, record != null, paired);
        }
        return new Look(finding, covered, adopt);
    }

    private boolean ours(World world, Block part, String id) {
        PlacedContent there = placed.at(world.getUID(), part.getX(), part.getY(), part.getZ());
        return there != null && there.kind() == PlacedContent.Kind.FURNITURE && there.contentId().equals(id);
    }

    /**
     * A display of this plugin's that is left over - its support gone with no rows behind it, or a
     * bone whose root is gone - with its anchor's chunk loaded; else null. A root whose anchor still
     * has its rows is the piece check's.
     */
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
        if (furniture.isPieceRoot(entity) && ours(entity.getWorld(), anchor, id) && plugin.items().get(id).isPresent()) {
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
            // Furniture no longer defined: a row whose block is no support of any kind.
            case FURNITURE -> FurnitureService.isSupport(block.getType()) ? null : block.getType().getKey().toString();
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
                             List<PieceFinding> pieces, Findings findings, int suspects) {
    }

    private Confirmed judge(Judges judges, Findings findings) {
        List<LeftOver> leftOvers = judges.displays.judge(findings.leftOvers());
        List<StaleRow> rows = judges.rows.judge(findings.rows());
        List<StaleLiquid> stale = judges.liquids.judge(findings.liquids());
        List<PieceFinding> pieces = judges.pieces.judge(findings.pieces());
        return new Confirmed(leftOvers, rows, stale, pieces, findings, judges.suspects());
    }

    // ---------------------------------------------------------------- server thread: act

    /**
     * What was done on the server thread, and the database writes it started.
     *
     * @param rebuiltBlocks  5.0: custom blocks put back
     * @param rebuiltLiquids 5.0: liquid sources put back
     * @param letGo          5.0: blocks and sources that could have been put back, but were too often today
     * @param repairs        5.0: the repair counts saved or forgotten
     */
    private record Purges(int displays, List<CompletableFuture<Boolean>> rows, List<CompletableFuture<Boolean>> liquids,
                          List<CompletableFuture<DatabaseManager.PieceRemoval>> pieces, int redrawn,
                          List<FurniturePiece> adopt, int rebuiltBlocks, int rebuiltLiquids, int letGo,
                          List<CompletableFuture<Void>> repairs) {
    }

    private CompletableFuture<Void> act(Plan plan, Confirmed confirmed, Judges judges) {
        return CompletableFuture.supplyAsync(() -> removeConfirmed(plan, confirmed), plugin.pipeline().mainThread())
                .thenCompose(purges -> {
                    CompletableFuture<Integer> adopted = purges.adopt().isEmpty()
                            ? CompletableFuture.completedFuture(0)
                            : database.adoptPieces(purges.adopt()).exceptionally(error -> {
                                quietly(error);
                                return 0;
                            });
                    List<CompletableFuture<?>> all = new ArrayList<>();
                    all.addAll(purges.rows());
                    all.addAll(purges.liquids());
                    all.addAll(purges.pieces());
                    all.addAll(purges.repairs());
                    all.add(adopted);
                    return CompletableFuture.allOf(all.toArray(CompletableFuture[]::new))
                            .thenAccept(ignored -> report(plan, confirmed, purges, adopted.join(), judges));
                });
    }

    /** Each confirmed finding looked at once more - the world may have changed since - then acted on. */
    private Purges removeConfirmed(Plan plan, Confirmed confirmed) {
        int displays = 0;
        for (LeftOver found : confirmed.leftOvers()) {
            Entity entity = plugin.getServer().getEntity(found.entity());
            LeftOver now = entity == null ? null : leftOver(entity);
            if (found.equals(now)) {
                furniture.removeLeftOver(entity);
                displays++;
            }
        }
        long now = System.currentTimeMillis();
        List<CompletableFuture<Void>> repairWrites = new ArrayList<>();
        int rebuiltBlocks = 0;
        int rebuiltLiquids = 0;
        int letGo = 0;
        List<CompletableFuture<Boolean>> rows = new ArrayList<>();
        for (StaleRow found : confirmed.rows()) {
            PlacedContent row = found.row();
            World world = plugin.getServer().getWorld(row.world());
            if (world == null || !found.found().equals(staleRow(world, row))) {
                continue;
            }
            if (row.kind() == PlacedContent.Kind.BLOCK) {
                Block block = world.getBlockAt(row.x(), row.y(), row.z());
                Optional<CustomBlock> defined = plugin.blocks().get(row.contentId());
                if (rebuilding() && defined.isPresent() && blocks.restorable(block)) {
                    BlockRepair past = repairs.get(BlockRepair.key(row.world(), row.x(), row.y(), row.z(),
                            BlockRepair.BLOCK));
                    if (budget.allows(past, now) && blocks.restore(block, defined.get())) {
                        repairWrites.add(repaired(budget.after(past, row.world(), row.x(), row.y(), row.z(),
                                BlockRepair.BLOCK, row.contentId(), now)));
                        rebuiltBlocks++;
                        continue;
                    }
                    letGo++;
                }
                forgetRepair(row.world(), row.x(), row.y(), row.z(), BlockRepair.BLOCK, repairWrites);
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
            Block block = world.getBlockAt(found.x(), found.y(), found.z());
            Optional<LiquidService.Source> source = liquids.source(block);
            if (source.isEmpty() || !found.equals(staleLiquid(world, found.x(), found.y(), found.z(), source.get()))) {
                continue;
            }
            if (rebuilding() && liquids.restorable(block, found.liquid())) {
                BlockRepair past = repairs.get(BlockRepair.key(found.world(), found.x(), found.y(), found.z(),
                        BlockRepair.LIQUID));
                if (budget.allows(past, now) && liquids.restoreSource(block, found.liquid())) {
                    repairWrites.add(repaired(budget.after(past, found.world(), found.x(), found.y(), found.z(),
                            BlockRepair.LIQUID, found.liquid(), now)));
                    rebuiltLiquids++;
                    continue;
                }
                letGo++;
            }
            forgetRepair(found.world(), found.x(), found.y(), found.z(), BlockRepair.LIQUID, repairWrites);
            if (liquids.forgetStale(block, found.liquid())) {
                stale.add(database.purgeLiquidIfUnchanged(found.world(), new DatabaseManager.LiquidSource(found.x(),
                        found.y(), found.z(), found.liquid(), found.owner())).exceptionally(error -> {
                            quietly(error);
                            return false;
                        }));
            }
        }
        List<CompletableFuture<DatabaseManager.PieceRemoval>> pieces = new ArrayList<>();
        List<FurniturePiece> adopt = new ArrayList<>(confirmed.findings().adopt());
        int redrawn = 0;
        for (PieceFinding found : confirmed.pieces()) {
            World world = plugin.getServer().getWorld(found.world());
            if (world == null || found.rows().isEmpty()) {
                continue;
            }
            PlacedContent first = found.rows().get(0);
            Look again = lookAt(world, first, plan.pieces().getOrDefault(found.world(), Map.of()),
                    PieceAudit.anchors(plan.pieces().getOrDefault(found.world(), Map.of()), this::footprint));
            if (again == null || !found.equals(again.finding())) {
                continue;
            }
            try {
                switch (found.verdict()) {
                    case GHOST -> {
                        if (redraw(world, found)) {
                            redrawn++;
                            if (!found.recorded() && !found.paired()) {
                                adopt.add(new FurniturePiece(found.world(), BlockKey.x(found.anchor()),
                                        BlockKey.y(found.anchor()), BlockKey.z(found.anchor()), found.furnitureId(),
                                        found.yaw()));
                            }
                        }
                    }
                    case BROKEN, STRAY -> pieces.add(takeOut(world, found));
                    default -> {
                    }
                }
            } catch (RuntimeException | LinkageError error) {
                // One piece that cannot be put right is no reason to leave the others; told once.
                quietly(error);
            }
        }
        return new Purges(displays, rows, stale, pieces, redrawn, adopt, rebuiltBlocks, rebuiltLiquids, letGo,
                repairWrites);
    }

    /** 5.0: whether blocks and sources found gone are put back, rather than their rows purged. */
    private boolean rebuilding() {
        return settings.rebuild() && budget.max() > 0;
    }

    /** 5.0: a block or source was put back: its count kept, in memory now and in SQLite after. */
    private CompletableFuture<Void> repaired(BlockRepair repair) {
        repairs.put(repair.key(), repair);
        return database.saveRepair(repair).exceptionally(error -> {
            quietly(error);
            return null;
        });
    }

    /** 5.0: a block or source let go: what was counted of it goes with its row. */
    private void forgetRepair(UUID world, int x, int y, int z, String kind, List<CompletableFuture<Void>> writes) {
        if (repairs.remove(BlockRepair.key(world, x, y, z, kind)) != null) {
            writes.add(database.forgetRepair(world, x, y, z, kind).exceptionally(error -> {
                quietly(error);
                return null;
            }));
        }
    }

    private boolean redraw(World world, PieceFinding found) {
        Block anchor = block(world, found.anchor());
        if (found.paired()) {
            // Half of a double chest without its display: parted, each half drawn on its own again.
            furniture.part(anchor, null);
            return true;
        }
        Optional<CustomItem> item = plugin.items().get(found.furnitureId());
        if (item.isEmpty() || !(item.get().placement() instanceof Placement.Furniture definition)) {
            return false;
        }
        return furniture.redrawGhost(anchor, item.get(), definition, found.yaw()) != null;
    }

    /** Takes out a broken or stray piece: whoever sits on it stands up, what is open of it is saved, then it goes. */
    private CompletableFuture<DatabaseManager.PieceRemoval> takeOut(World world, PieceFinding found) {
        List<Block> parts = found.parts().stream().map(key -> block(world, key)).toList();
        for (Block part : parts) {
            seats.release(part);
            storage.release(part);
        }
        Entity root = found.root() == null ? null : plugin.getServer().getEntity(found.root());
        if (found.paired() && found.verdict() == PieceAudit.Verdict.BROKEN) {
            // A double chest with a half gone: the other half goes back to a single chest of its own.
            Optional<Placement.Furniture> definition = plugin.items().get(found.furnitureId())
                    .map(CustomItem::placement).filter(Placement.Furniture.class::isInstance)
                    .map(Placement.Furniture.class::cast);
            List<Block> gone = definition.isEmpty() ? parts
                    : parts.stream().filter(part -> !FurnitureService.matches(part, definition.get())).toList();
            for (Block half : gone) {
                furniture.part(half, half);
            }
            List<PlacedContent> rows = found.rows().stream()
                    .filter(row -> gone.stream().anyMatch(half -> half.getX() == row.x() && half.getY() == row.y()
                            && half.getZ() == row.z())).toList();
            furniture.dismantle(null, gone);
            return purge(world, rows, BROKEN_REASON);
        }
        furniture.dismantle(root, parts);
        return purge(world, found.rows(), found.verdict() == PieceAudit.Verdict.STRAY ? STRAY_REASON : BROKEN_REASON);
    }

    private CompletableFuture<DatabaseManager.PieceRemoval> purge(World world, List<PlacedContent> rows, String reason) {
        return placed.purgePiece(world.getUID(), rows, reason).exceptionally(error -> {
            quietly(error);
            return new DatabaseManager.PieceRemoval(0, 0);
        });
    }

    private static Block block(World world, long key) {
        return world.getBlockAt(BlockKey.x(key), BlockKey.y(key), BlockKey.z(key));
    }

    private void report(Plan plan, Confirmed confirmed, Purges purges, int adopted, Judges judges) {
        int rows = (int) purges.rows().stream().filter(future -> Boolean.TRUE.equals(future.join())).count();
        int stale = (int) purges.liquids().stream().filter(future -> Boolean.TRUE.equals(future.join())).count();
        int pieceRows = 0;
        int recovered = plan.orphanedStorage();
        int removed = 0;
        for (CompletableFuture<DatabaseManager.PieceRemoval> future : purges.pieces()) {
            DatabaseManager.PieceRemoval removal = future.join();
            removed++;
            pieceRows += removal.rows();
            recovered += removal.recovered();
        }
        displaysRemoved.addAndGet(purges.displays());
        rowsPurged.addAndGet(rows + pieceRows);
        liquidsPurged.addAndGet(stale);
        piecesRemoved.addAndGet(removed);
        piecesRedrawn.addAndGet(purges.redrawn());
        storageRecovered.addAndGet(recovered);
        piecesAdopted.addAndGet(adopted);
        blocksRebuilt.addAndGet(purges.rebuiltBlocks());
        liquidsRebuilt.addAndGet(purges.rebuiltLiquids());
        Findings findings = confirmed.findings();
        double millis = findings.nanos() / 1_000_000.0;
        last = new Status(System.currentTimeMillis(), findings.chunks(), findings.ticks(), millis, judges.suspects(),
                displaysRemoved.get(), rowsPurged.get(), liquidsPurged.get(), piecesRemoved.get(), piecesRedrawn.get(),
                storageRecovered.get(), piecesAdopted.get(), blocksRebuilt.get(), liquidsRebuilt.get());
        int rebuilt = purges.rebuiltBlocks() + purges.rebuiltLiquids();
        if (purges.displays() + rows + stale + removed + purges.redrawn() + recovered + rebuilt > 0) {
            List<String> done = new ArrayList<>();
            if (purges.rebuiltBlocks() > 0) {
                done.add("put back " + purges.rebuiltBlocks() + " custom block(s) found gone");
            }
            if (purges.rebuiltLiquids() > 0) {
                done.add("put back " + purges.rebuiltLiquids() + " liquid source(s) found gone");
            }
            if (purges.letGo() > 0) {
                done.add("let " + purges.letGo() + " go that something keeps taking away (more than "
                        + budget.max() + " time(s) today)");
            }
            if (removed > 0) {
                done.add("took out " + removed + " broken piece(s) of furniture (" + pieceRows + " row(s))");
            }
            if (purges.redrawn() > 0) {
                done.add("drew " + purges.redrawn() + " piece(s) whose display was gone again");
            }
            if (purges.displays() > 0) {
                done.add("removed " + purges.displays() + " left-over display(s)");
            }
            if (rows > 0) {
                done.add("purged " + rows + " stale block row(s)");
            }
            if (stale > 0) {
                done.add("purged " + stale + " liquid source(s) whose block was gone");
            }
            if (recovered > 0) {
                done.add("kept what " + recovered + " storage(s) held for /arkcontent integrity recovered");
            }
            logger.info(String.format(java.util.Locale.ROOT, "Integrity check: %s (%d chunk(s) audited over %d tick(s),"
                    + " %.1f ms of server time).", String.join("; ", done), findings.chunks(), findings.ticks(), millis));
        } else {
            logger.log(Level.FINE, "Integrity check: nothing to put right in " + findings.chunks() + " chunk(s); "
                    + judges.suspects() + " finding(s) wait for the next look.");
        }
    }

    // ---------------------------------------------------------------- recovered storage

    /** Recovered storage, newest first: those waiting, or all. */
    public CompletableFuture<List<RecoveredStorage>> recovered(boolean waitingOnly, int limit) {
        return database.recovered(waitingOnly, limit);
    }

    /** Takes what a recovered row holds, once; empty if it is not there or was already taken. */
    public CompletableFuture<Optional<RecoveredStorage>> claim(long id, String claimer) {
        return database.claimRecovered(id, claimer);
    }

    /** A failure is told once, briefly - no stack trace in the console; the next audit runs anyway. */
    private void quietly(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof java.util.concurrent.CancellationException) {
            return;
        }
        String message = cause.getClass().getSimpleName() + ": " + cause.getMessage();
        if (reported.add(message)) {
            logger.warning("The integrity check could not finish (" + message + "); it tries again in "
                    + settings.intervalSeconds() + " s.");
        } else {
            logger.log(Level.FINE, "Integrity check failed again: " + message);
        }
    }
}
