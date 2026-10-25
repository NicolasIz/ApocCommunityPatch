package com.arkcronist.content.core.crop;

import com.arkcronist.content.core.storage.DatabaseManager;
import com.arkcronist.content.core.storage.PositionIndex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Planted crops, answered from memory and kept on disk.
 *
 * <p>Like the placed-content store: a change is in memory at once and its write queued on the
 * database thread, nothing waits for the disk. Growth is the exception to writing every change -
 * crops gain a few seconds each time the growth scheduler runs, and writing every crop that often
 * would turn a farm into a stream of writes. So progress within a stage lives in memory, and is
 * saved in one transaction per world when that world unloads or the server stops. A new stage is
 * saved as it happens. A crash loses at most the progress into the current stage.</p>
 *
 * <p>Safe from any thread: the growth scheduler updates crops while the server thread plants and
 * harvests them. An update from the scheduler only lands if the crop is still the one it read
 * ({@link PositionIndex#replace}), so a crop harvested in between is never brought back.</p>
 */
public final class CropStore {

    private final PositionIndex<PlantedCrop> index = new PositionIndex<>();
    private final DatabaseManager database;
    private final Logger logger;

    public CropStore(DatabaseManager database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    public PlantedCrop at(UUID world, int x, int y, int z) {
        return index.get(world, x, y, z);
    }

    public void plant(PlantedCrop crop) {
        index.put(crop);
        database.saveCrop(crop).exceptionally(error -> failed("save", crop, error));
    }

    /** @return what grew there, or null */
    public PlantedCrop remove(UUID world, int x, int y, int z) {
        PlantedCrop removed = index.remove(world, x, y, z);
        database.deleteCrop(world, x, y, z).exceptionally(error -> {
            failed("delete", new PlantedCrop(world, x, y, z, "?", 0, 0), error);
            return false;
        });
        return removed;
    }

    /**
     * Replaces {@code current} with {@code updated} if nothing changed it meanwhile.
     *
     * @param save write it now - for a new stage; progress alone waits for {@link #saveWorld}
     * @return whether it was replaced
     */
    public boolean update(PlantedCrop current, PlantedCrop updated, boolean save) {
        if (!index.replace(current, updated)) {
            return false;
        }
        if (save) {
            database.saveCrop(updated).exceptionally(error -> failed("save", updated, error));
        }
        return true;
    }

    /** A live view of one world's crops. */
    public Collection<PlantedCrop> crops(UUID world) {
        return index.entries(world);
    }

    public Collection<UUID> worlds() {
        return index.worlds();
    }

    /** Reads a world's crops into memory, off the calling thread. */
    public CompletableFuture<Integer> loadWorld(UUID world) {
        index.beginLoad(world);
        return database.loadCrops(world).handle((rows, error) -> {
            if (error != null) {
                index.finishLoad(world, List.of());
                throw new CompletionException(error);
            }
            return index.finishLoad(world, rows);
        });
    }

    /** Writes every crop of a world, progress included, in one transaction. */
    public CompletableFuture<Void> saveWorld(UUID world) {
        return database.saveCrops(new ArrayList<>(index.entries(world))).exceptionally(error -> {
            Throwable cause = error.getCause() != null ? error.getCause() : error;
            logger.log(Level.WARNING, "Could not save the crops of world " + world + ": " + cause.getMessage());
            return null;
        });
    }

    /** Saves a world's crops, then forgets them. */
    public CompletableFuture<Void> unloadWorld(UUID world) {
        CompletableFuture<Void> saved = saveWorld(world);
        index.dropWorld(world);
        return saved;
    }

    public int size() {
        return index.size();
    }

    private Void failed(String what, PlantedCrop crop, Throwable error) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        logger.log(Level.WARNING, "Could not " + what + " the crop at " + crop.x() + "," + crop.y() + "," + crop.z()
                + " in world " + crop.world() + ": " + cause.getMessage());
        return null;
    }
}
