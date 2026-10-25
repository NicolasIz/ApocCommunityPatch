package com.arkcronist.content.core.storage;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The pieces of furniture with a transform of their own, answered from memory and kept on disk -
 * the same arrangement as {@link PlacedContentStore}: a change is in memory at once, and its row is
 * written on the database's thread.
 */
public final class FurnitureTransformStore {

    private final PositionIndex<FurnitureTransform> index = new PositionIndex<>();
    private final DatabaseManager database;
    private final Logger logger;

    public FurnitureTransformStore(DatabaseManager database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    /** The piece's own transform, or null when it is drawn as its type says. Memory only. */
    public FurnitureTransform at(UUID world, int x, int y, int z) {
        return index.get(world, x, y, z);
    }

    /** @return completes once the row is written */
    public CompletableFuture<Void> put(FurnitureTransform transform) {
        remember(transform);
        return write(transform);
    }

    /** @return completes with whether there was a row */
    public CompletableFuture<Boolean> remove(UUID world, int x, int y, int z) {
        forget(world, x, y, z);
        return delete(world, x, y, z);
    }

    /** {@link #put}'s half in memory: what {@link #at} answers from now on. */
    public void remember(FurnitureTransform transform) {
        index.put(transform);
    }

    /** {@link #put}'s half on disk: the row, written on the database's thread. */
    public CompletableFuture<Void> write(FurnitureTransform transform) {
        return database.saveTransform(transform.world(), new DatabaseManager.StoredTransform(transform.x(),
                transform.y(), transform.z(), transform.furnitureId(), transform.transform()))
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        failed("save", transform.x(), transform.y(), transform.z(), error);
                    }
                });
    }

    /** {@link #remove}'s half on disk. */
    public CompletableFuture<Boolean> delete(UUID world, int x, int y, int z) {
        return database.deleteTransform(world, x, y, z).whenComplete((ignored, error) -> {
            if (error != null) {
                failed("delete", x, y, z, error);
            }
        });
    }

    /** Forgets a piece's own transform in memory only: its row went with a purge already, or goes next. */
    public void forget(UUID world, int x, int y, int z) {
        index.remove(world, x, y, z);
    }

    /** @return completes with the transforms read */
    public CompletableFuture<Collection<FurnitureTransform>> loadWorld(UUID world) {
        index.beginLoad(world);
        return database.loadTransforms(world).handle((rows, error) -> {
            if (error != null) {
                index.finishLoad(world, List.of());
                throw new CompletionException(error);
            }
            List<FurnitureTransform> transforms = rows.stream().map(row -> new FurnitureTransform(world, row.x(), row.y(),
                    row.z(), row.furnitureId(), row.transform())).toList();
            index.finishLoad(world, transforms);
            return index.entries(world);
        });
    }

    public void unloadWorld(UUID world) {
        index.dropWorld(world);
    }

    public int size() {
        return index.size();
    }

    private void failed(String what, int x, int y, int z, Throwable error) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        logger.log(Level.WARNING, "Could not " + what + " the furniture transform at " + x + "," + y + "," + z
                + ": " + cause.getMessage());
    }
}
