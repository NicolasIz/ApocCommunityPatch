package com.arkcronist.content.core.storage;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Placed content, answered from memory and kept on disk.
 *
 * <p>The one class listeners talk to. A lookup reads the {@link PlacedContentIndex} and returns at
 * once; a change updates the index immediately - so the very next event already sees it - and
 * queues the matching write on the {@link DatabaseManager}'s thread. Nothing here waits for the
 * disk.</p>
 *
 * <p>A write that fails is logged and the index keeps the change: the world is what players see,
 * and it would be wrong to make the running server disagree with it because the file did. The row
 * is corrected the next time that position changes.</p>
 */
public final class PlacedContentStore {

    private final PlacedContentIndex index = new PlacedContentIndex();
    private final DatabaseManager database;
    private final Logger logger;

    public PlacedContentStore(DatabaseManager database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    /** What stands at a position, or null. Memory only. */
    public PlacedContent at(UUID world, int x, int y, int z) {
        return index.get(world, x, y, z);
    }

    public void add(PlacedContent content) {
        index.put(content);
        database.save(content).exceptionally(error -> failed("save", content.world(), content.x(),
                content.y(), content.z(), error));
    }

    /**
     * Forgets a position. The row is deleted even when memory had nothing there: its world's rows
     * may simply not have arrived yet.
     *
     * @return what memory had there, or null
     */
    public PlacedContent remove(UUID world, int x, int y, int z) {
        PlacedContent removed = index.remove(world, x, y, z);
        database.delete(world, x, y, z).exceptionally(error -> {
            failed("delete", world, x, y, z, error);
            return false;
        });
        return removed;
    }

    /**
     * Reads a world's rows into memory, off the calling thread.
     *
     * @return completes with how many rows were let in
     */
    public CompletableFuture<Integer> loadWorld(UUID world) {
        index.beginLoad(world);
        return database.loadWorld(world).handle((rows, error) -> {
            if (error != null) {
                // Still end the load, or the index would go on remembering removals for it forever.
                index.finishLoad(world, List.of());
                throw new CompletionException(error);
            }
            return index.finishLoad(world, rows);
        });
    }

    public CompletableFuture<Void> loadWorlds(Collection<UUID> worlds) {
        return CompletableFuture.allOf(worlds.stream().map(this::loadWorld).toArray(CompletableFuture[]::new));
    }

    /** Drops a world from memory. Its rows stay on disk for when it loads again. */
    public void unloadWorld(UUID world) {
        index.dropWorld(world);
    }

    public int size() {
        return index.size();
    }

    private Void failed(String what, UUID world, int x, int y, int z, Throwable error) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        logger.log(Level.WARNING, "Could not " + what + " the placed content at " + x + "," + y + "," + z
                + " in world " + world + ": " + cause.getMessage());
        return null;
    }
}
