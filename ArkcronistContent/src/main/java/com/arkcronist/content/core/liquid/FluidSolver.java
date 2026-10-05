package com.arkcronist.content.core.liquid;

import com.arkcronist.content.core.storage.BlockKey;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongPredicate;

/**
 * Where a liquid's sources make it flow, worked out from a copy of the blocks around them.
 *
 * <p>The rules are water's, simplified to what two drawn states can show:</p>
 * <ul>
 *   <li>liquid over an open block falls into it, straight down, for at most {@code maxFall}
 *       blocks, and does not spread sideways while it falls;</li>
 *   <li>liquid resting on something - a solid block, or the edge of the copy - spreads to the four
 *       sides, one block weaker per step, for {@code flowDistance} steps from a source, or from
 *       where a fall landed;</li>
 *   <li>a block is open when it is air, or already this liquid; anything else - another liquid
 *       included - stops it.</li>
 * </ul>
 *
 * <p>Pure: the terrain is a function of three ints, so this runs on the liquids' own thread over
 * chunk snapshots, and in tests over a few hand-placed walls.</p>
 */
public final class FluidSolver {

    /** One block of liquid, as the solve wants it. */
    public record Cell(long key, boolean source, int strength, int depth, long origin) {

        public int x() {
            return BlockKey.x(key);
        }

        public int y() {
            return BlockKey.y(key);
        }

        public int z() {
            return BlockKey.z(key);
        }
    }

    /** What the terrain is at a block. */
    @FunctionalInterface
    public interface Terrain {

        /** Whether liquid may be at {@code x, y, z}: air, or this liquid already. */
        boolean open(int x, int y, int z);
    }

    /**
     * A change to make in the world to get from what is there to what the solve wants.
     *
     * @param depth steps from the source: changes are applied in this order, a few ticks apart, so
     *              the liquid is seen to run rather than appear
     */
    public record Change(long key, Kind kind, int depth, long origin) {

        public enum Kind {
            /** Put flowing liquid here. */
            FLOW,
            /** Take the liquid here away. */
            DRAIN
        }
    }

    /** No solve walks more cells than this per source - a source poured over the void stops. */
    public static final int MAX_CELLS_PER_SOURCE = 4096;

    /** North, east, south, west, as x and z steps. */
    private static final int[][] SIDES = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    private final int flowDistance;
    private final int maxFall;
    private final int minY;
    private final int maxY;

    /**
     * @param flowDistance how far it spreads sideways, 1-8
     * @param maxFall      how far it falls at most
     * @param minY         the lowest block it may reach - the world's floor
     * @param maxY         one above the highest block
     */
    public FluidSolver(int flowDistance, int maxFall, int minY, int maxY) {
        this.flowDistance = Math.max(1, flowDistance);
        this.maxFall = Math.max(0, maxFall);
        this.minY = minY;
        this.maxY = maxY;
    }

    /**
     * Every block the sources keep liquid in. Where two sources reach the same block, the stronger
     * flow - the nearer source - is kept, so each block knows one source it came from.
     */
    public Map<Long, Cell> solve(Collection<Long> sources, Terrain terrain) {
        Map<Long, Cell> cells = new HashMap<>();
        for (long source : sources.stream().sorted().toList()) {
            for (Cell cell : flood(source, terrain)) {
                cells.merge(cell.key(), cell, FluidSolver::stronger);
            }
        }
        return cells;
    }

    private static Cell stronger(Cell a, Cell b) {
        if (a.source() != b.source()) {
            return a.source() ? a : b;
        }
        if (a.strength() != b.strength()) {
            return a.strength() > b.strength() ? a : b;
        }
        return a.depth() <= b.depth() ? a : b;
    }

    /**
     * One source's flow: a walk outwards in order of strength, so each block is reached first by its
     * strongest path and walked from once.
     */
    private List<Cell> flood(long source, Terrain terrain) {
        Map<Long, Cell> reached = new HashMap<>();
        // Strength only falls, from flowDistance to 1, and a fall resets it to the top: a queue per
        // strength, emptied strongest first, walks them in order without sorting.
        List<Deque<long[]>> queues = new ArrayList<>();
        for (int strength = 0; strength <= flowDistance; strength++) {
            queues.add(new ArrayDeque<>());
        }
        reached.put(source, new Cell(source, true, flowDistance, 0, source));
        queues.get(flowDistance).add(new long[]{source, 0, 0});

        int strongest = flowDistance;
        while (strongest > 0 && reached.size() < MAX_CELLS_PER_SOURCE) {
            Deque<long[]> queue = queues.get(strongest);
            long[] entry = queue.poll();
            if (entry == null) {
                strongest--;
                continue;
            }
            long key = entry[0];
            int depth = (int) entry[1];
            int fallen = (int) entry[2];
            Cell cell = reached.get(key);
            if (cell == null || cell.strength() != strongest || cell.depth() != depth) {
                // Reached again since by a stronger or shorter path; that visit walks on from here.
                continue;
            }
            int x = BlockKey.x(key);
            int y = BlockKey.y(key);
            int z = BlockKey.z(key);

            if (y - 1 >= minY && terrain.open(x, y - 1, z)) {
                if (fallen < maxFall) {
                    long below = BlockKey.pack(x, y - 1, z);
                    if (offer(reached, below, flowDistance, depth + 1, source)) {
                        queues.get(flowDistance).add(new long[]{below, depth + 1, fallen + 1});
                        strongest = flowDistance;
                    }
                }
                continue;
            }
            if (strongest <= 1) {
                continue;
            }
            int weaker = strongest - 1;
            for (int[] side : SIDES) {
                int nx = x + side[0];
                int nz = z + side[1];
                if (y < minY || y >= maxY || !terrain.open(nx, y, nz)) {
                    continue;
                }
                long next = BlockKey.pack(nx, y, nz);
                if (offer(reached, next, weaker, depth + 1, source)) {
                    queues.get(weaker).add(new long[]{next, depth + 1, 0});
                }
            }
        }
        return new ArrayList<>(reached.values());
    }

    /** Takes {@code key} at this strength if that is better than how it was reached so far. */
    private static boolean offer(Map<Long, Cell> reached, long key, int strength, int depth, long source) {
        Cell known = reached.get(key);
        if (known != null && (known.source() || known.strength() > strength
                || known.strength() == strength && known.depth() <= depth)) {
            return false;
        }
        reached.put(key, new Cell(key, false, strength, depth, source));
        return true;
    }

    /**
     * The changes that take the world from {@code present} to {@code wanted}, nearest the source
     * first.
     *
     * @param present    every block holding this liquid's flowing state now, within the area the
     *                   solve speaks for; sources are never drained here - only their owner takes
     *                   one away
     * @param wanted     the solve
     * @param authority  the blocks this solve may drain: those every source that could reach them
     *                   took part in
     */
    public static List<Change> changes(Set<Long> present, Map<Long, Cell> wanted, LongPredicate authority) {
        List<Change> changes = new ArrayList<>();
        for (Cell cell : wanted.values()) {
            if (!cell.source() && !present.contains(cell.key())) {
                changes.add(new Change(cell.key(), Change.Kind.FLOW, cell.depth(), cell.origin()));
            }
        }
        for (long key : present) {
            if (!wanted.containsKey(key) && authority.test(key)) {
                changes.add(new Change(key, Change.Kind.DRAIN, 0, key));
            }
        }
        changes.sort(Comparator.comparingInt(Change::depth).thenComparingLong(Change::key));
        return changes;
    }
}
