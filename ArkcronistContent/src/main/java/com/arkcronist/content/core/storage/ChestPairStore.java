package com.arkcronist.content.core.storage;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The double chests of furniture, answered from memory and kept on disk - like
 * {@link FurnitureTransformStore}: a change is in memory at once, and its row is written on the
 * database's thread, in one transaction.
 */
public final class ChestPairStore {

    private final PositionIndex<ChestPair.Half> index = new PositionIndex<>();
    private final DatabaseManager database;
    private final Logger logger;

    public ChestPairStore(DatabaseManager database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    /** The pair one of whose halves is at the position, or null. Memory only. */
    public @Nullable ChestPair at(UUID world, int x, int y, int z) {
        ChestPair.Half half = index.get(world, x, y, z);
        return half == null ? null : half.pair();
    }

    /** Joins two chests. @return completes once the row is written */
    public CompletableFuture<Void> put(ChestPair pair) {
        forget(pair.world(), pair.x(), pair.y(), pair.z());
        forget(pair.world(), pair.px(), pair.py(), pair.pz());
        index.put(new ChestPair.Half(pair.world(), pair.x(), pair.y(), pair.z(), pair));
        index.put(new ChestPair.Half(pair.world(), pair.px(), pair.py(), pair.pz(), pair));
        return database.savePair(pair.world(), new DatabaseManager.StoredPair(pair.x(), pair.y(), pair.z(), pair.px(),
                pair.py(), pair.pz(), pair.furnitureId())).whenComplete((ignored, error) -> {
            if (error != null) {
                failed("save", pair.x(), pair.y(), pair.z(), error);
            }
        });
    }

    /** Parts the pair one of whose halves is at the position. @return completes with whether there was one */
    public CompletableFuture<Boolean> remove(UUID world, int x, int y, int z) {
        forget(world, x, y, z);
        return database.deletePair(world, x, y, z).whenComplete((ignored, error) -> {
            if (error != null) {
                failed("delete", x, y, z, error);
            }
        });
    }

    /**
     * Forgets, in memory, the pair one of whose halves is at the position - both halves of it. Its
     * row is someone else's to delete: the integrity check's purge, in its own transaction.
     */
    public void forget(UUID world, int x, int y, int z) {
        ChestPair pair = at(world, x, y, z);
        if (pair != null) {
            index.remove(world, pair.x(), pair.y(), pair.z());
            index.remove(world, pair.px(), pair.py(), pair.pz());
        }
    }

    /** @return completes with the pairs read */
    public CompletableFuture<Collection<ChestPair>> loadWorld(UUID world) {
        index.beginLoad(world);
        return database.loadPairs(world).handle((rows, error) -> {
            if (error != null) {
                index.finishLoad(world, List.of());
                throw new CompletionException(error);
            }
            List<ChestPair.Half> halves = new ArrayList<>();
            for (DatabaseManager.StoredPair row : rows) {
                ChestPair pair = new ChestPair(world, row.x(), row.y(), row.z(), row.px(), row.py(), row.pz(),
                        row.furnitureId());
                halves.add(new ChestPair.Half(world, row.x(), row.y(), row.z(), pair));
                halves.add(new ChestPair.Half(world, row.px(), row.py(), row.pz(), pair));
            }
            index.finishLoad(world, halves);
            return pairs(world);
        });
    }

    /** Whether the pairs of a world have been read: until then, {@link #at} answers from what is known. */
    public boolean loaded(UUID world) {
        return !index.loading(world);
    }

    public void unloadWorld(UUID world) {
        index.dropWorld(world);
    }

    /** Every pair of a world in memory. */
    public Collection<ChestPair> pairs(UUID world) {
        LinkedHashSet<ChestPair> pairs = new LinkedHashSet<>();
        index.entries(world).forEach(half -> pairs.add(half.pair()));
        return pairs;
    }

    public int size() {
        return index.size() / 2;
    }

    private void failed(String what, int x, int y, int z, Throwable error) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        logger.log(Level.WARNING, "Could not " + what + " the double chest at " + x + "," + y + "," + z + ": "
                + cause.getMessage());
    }
}
