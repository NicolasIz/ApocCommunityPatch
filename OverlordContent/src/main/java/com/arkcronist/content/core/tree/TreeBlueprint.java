package com.arkcronist.content.core.tree;

import com.arkcronist.content.core.block.BlockAxis;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 5.5: a tree drawn block by block - {@code tree: blueprints:} - instead of grown to a shape: the
 * layers of characters a YAML gives, from the bottom up, each character a block of its legend.
 *
 * @param weight  how often it is picked among a sapling's blueprints, against theirs
 * @param rotates whether a tree of it is turned a random quarter turn, so a grove is not all alike
 * @param mirrors whether one is also, at random, its own mirror image
 * @param cells   its blocks, from the sapling's block - which is the cell at {@code 0, 0, 0}
 */
public record TreeBlueprint(int weight, boolean rotates, boolean mirrors, List<Cell> cells) {

    /**
     * One block of a blueprint.
     *
     * @param block  {@link TreeSpec#LOG}, {@link TreeSpec#LEAVES}, or a block of its own: a custom
     *               block's {@code namespace:id} or a vanilla block, with its state if it has one
     *               ({@code minecraft:vine[north=true]})
     * @param axis   which way a log lies; {@link BlockAxis#Y} for anything else
     * @param chance the chance it is there in each tree, 0-1
     */
    public record Cell(int dx, int dy, int dz, String block, BlockAxis axis, double chance) {
    }

    public TreeBlueprint {
        cells = List.copyOf(cells);
        if (weight < 1 || cells.isEmpty()) {
            throw new IllegalArgumentException("weight " + weight + ", " + cells.size() + " cells");
        }
    }

    /** The farthest it reaches from the sapling across, either way: turned, x and z swap. */
    public int reach() {
        int reach = 0;
        for (Cell cell : cells) {
            reach = Math.max(reach, Math.max(Math.abs(cell.dx()), Math.abs(cell.dz())));
        }
        return reach;
    }

    /** How tall it is, from the sapling's block up. */
    public int height() {
        int top = 0;
        for (Cell cell : cells) {
            top = Math.max(top, cell.dy());
        }
        return top + 1;
    }

    /**
     * How far, at most, each kind of leaves is from a log, counted as vanilla counts a leaf's
     * distance: steps from one block to the next through leaves. A leaf no log reaches that way is
     * not counted. Decay works out the same distance in the world, so it knows how far this tree's
     * leaves may be from its wood and still be held.
     *
     * @param leaves which cell blocks are leaves - {@link TreeSpec#LEAVES} and custom leaves
     */
    public Map<String, Integer> leafDistances(Predicate<String> leaves) {
        Map<Long, Cell> byPosition = new HashMap<>();
        for (Cell cell : cells) {
            byPosition.put(key(cell.dx(), cell.dy(), cell.dz()), cell);
        }
        Map<Long, Integer> distance = new HashMap<>();
        Deque<Cell> queue = new ArrayDeque<>();
        for (Cell cell : cells) {
            if (cell.block().equals(TreeSpec.LOG)) {
                distance.put(key(cell.dx(), cell.dy(), cell.dz()), 0);
                queue.add(cell);
            }
        }
        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        Map<String, Integer> farthest = new HashMap<>();
        while (!queue.isEmpty()) {
            Cell at = queue.poll();
            int next = distance.get(key(at.dx(), at.dy(), at.dz())) + 1;
            for (int[] step : steps) {
                long there = key(at.dx() + step[0], at.dy() + step[1], at.dz() + step[2]);
                Cell cell = byPosition.get(there);
                if (cell == null || distance.containsKey(there) || !leaves.test(cell.block())) {
                    continue;
                }
                distance.put(there, next);
                farthest.merge(cell.block(), next, Math::max);
                queue.add(cell);
            }
        }
        return farthest;
    }

    private static long key(int dx, int dy, int dz) {
        return ((long) (dx & 0xFFFF) << 32) | ((long) (dy & 0xFFFF) << 16) | (dz & 0xFFFF);
    }
}
