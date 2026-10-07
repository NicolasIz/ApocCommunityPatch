package com.arkcronist.content.core.storage;

import com.arkcronist.content.core.crop.PlantedCrop;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.furniture.DisplayTransform;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The SQLite file that remembers where custom blocks and furniture stand, how far each crop has
 * grown, and what storage furniture holds.
 *
 * <p>Every statement runs on one thread of its own, and every method returns at once with a
 * future. One thread is not a limitation here but the design: SQLite allows a single writer, one
 * connection used from one thread needs no locking, and it keeps writes in the order they were
 * asked for - a block placed and broken in the same second reaches the file as an insert followed
 * by a delete, never the other way round. The server thread only ever queues work.</p>
 *
 * <p>The only wait is {@link #close()}, on shutdown, which lets queued writes finish rather than
 * lose them.</p>
 *
 * <p>The driver is the {@code org.xerial} SQLite driver Paper and Spigot ship with the server; the
 * plugin bundles none.</p>
 */
public final class DatabaseManager {

    public static final String TABLE = "custom_blocks_world";
    public static final String CROP_TABLE = "custom_crops";
    public static final String STORAGE_TABLE = "furniture_storage";
    public static final String UNLOCK_TABLE = "advancement_unlocks";
    public static final String LIQUID_TABLE = "liquid_sources";
    public static final String HUD_TABLE = "hud_values";
    public static final String TRANSFORM_TABLE = "furniture_transforms";

    /**
     * Bumped with each change to the schema, so a later version knows what it opened.
     * 1: custom_blocks_world. 2: custom_crops added. 3: furniture_storage added. 4:
     * advancement_unlocks added. 5: liquid_sources and hud_values added. 6: furniture_transforms
     * added. An older file gains the tables it lacks on open; nothing existing is touched.
     */
    public static final int SCHEMA_VERSION = 6;

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS custom_blocks_world (
                world_uuid TEXT    NOT NULL,
                x          INTEGER NOT NULL,
                y          INTEGER NOT NULL,
                z          INTEGER NOT NULL,
                block_id   TEXT    NOT NULL,
                type       TEXT    NOT NULL CHECK (type IN ('BLOCK', 'FURNITURE')),
                PRIMARY KEY (world_uuid, x, y, z)
            ) WITHOUT ROWID""";

    private static final String CREATE_CROP_TABLE = """
            CREATE TABLE IF NOT EXISTS custom_crops (
                world_uuid TEXT    NOT NULL,
                x          INTEGER NOT NULL,
                y          INTEGER NOT NULL,
                z          INTEGER NOT NULL,
                crop_id    TEXT    NOT NULL,
                stage      INTEGER NOT NULL CHECK (stage >= 0),
                progress   INTEGER NOT NULL DEFAULT 0 CHECK (progress >= 0),
                PRIMARY KEY (world_uuid, x, y, z)
            ) WITHOUT ROWID""";

    // A rowid table, unlike the two above: its rows carry a blob of a few kilobytes, and SQLite's
    // WITHOUT ROWID layout is meant for small rows only.
    private static final String CREATE_STORAGE_TABLE = """
            CREATE TABLE IF NOT EXISTS furniture_storage (
                world_uuid   TEXT    NOT NULL,
                x            INTEGER NOT NULL,
                y            INTEGER NOT NULL,
                z            INTEGER NOT NULL,
                furniture_id TEXT    NOT NULL,
                slots        INTEGER NOT NULL CHECK (slots > 0),
                contents     BLOB    NOT NULL,
                updated_at   INTEGER NOT NULL,
                PRIMARY KEY (world_uuid, x, y, z)
            )""";

    /**
     * The first time each player completed each of the plugin's advancements. The server keeps the
     * progress itself; this keeps the moment, which survives an advancement being revoked and earned
     * again - so its announcement goes out once per player, ever - and a world's advancement files
     * being reset.
     */
    private static final String CREATE_UNLOCK_TABLE = """
            CREATE TABLE IF NOT EXISTS advancement_unlocks (
                player_uuid TEXT    NOT NULL,
                advancement TEXT    NOT NULL,
                unlocked_at INTEGER NOT NULL,
                PRIMARY KEY (player_uuid, advancement)
            ) WITHOUT ROWID""";

    /**
     * Every liquid source poured, by where it is. Only sources: where a liquid flows follows from
     * them, and is worked out again whenever the chunk is loaded.
     *
     * <p>owner_uuid is the player who poured it, kept for protection plugins' sake; null for one
     * placed by a command.</p>
     */
    private static final String CREATE_LIQUID_TABLE = """
            CREATE TABLE IF NOT EXISTS liquid_sources (
                world_uuid  TEXT    NOT NULL,
                x           INTEGER NOT NULL,
                y           INTEGER NOT NULL,
                z           INTEGER NOT NULL,
                liquid_id   TEXT    NOT NULL,
                owner_uuid  TEXT,
                PRIMARY KEY (world_uuid, x, y, z)
            ) WITHOUT ROWID""";

    /** The values of HUD bars kept by this plugin - thirst, a mana of its own - per player. */
    private static final String CREATE_HUD_TABLE = """
            CREATE TABLE IF NOT EXISTS hud_values (
                player_uuid TEXT NOT NULL,
                hud_id      TEXT NOT NULL,
                value       REAL NOT NULL,
                PRIMARY KEY (player_uuid, hud_id)
            ) WITHOUT ROWID""";

    /**
     * One piece of furniture drawn other than its type says: the in-game editor's "this piece only".
     * Keyed by the furniture's anchor block; translation in blocks, scale per axis, rotation in
     * degrees about x, y and z.
     */
    private static final String CREATE_TRANSFORM_TABLE = """
            CREATE TABLE IF NOT EXISTS furniture_transforms (
                world_uuid   TEXT    NOT NULL,
                x            INTEGER NOT NULL,
                y            INTEGER NOT NULL,
                z            INTEGER NOT NULL,
                furniture_id TEXT    NOT NULL,
                tx REAL NOT NULL, ty REAL NOT NULL, tz REAL NOT NULL,
                sx REAL NOT NULL, sy REAL NOT NULL, sz REAL NOT NULL,
                rx REAL NOT NULL, ry REAL NOT NULL, rz REAL NOT NULL,
                updated_at   INTEGER NOT NULL,
                PRIMARY KEY (world_uuid, x, y, z)
            ) WITHOUT ROWID""";

    private static final String UPSERT_TRANSFORM = "INSERT OR REPLACE INTO " + TRANSFORM_TABLE
            + " (world_uuid, x, y, z, furniture_id, tx, ty, tz, sx, sy, sz, rx, ry, rz, updated_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String DELETE_TRANSFORM = "DELETE FROM " + TRANSFORM_TABLE
            + " WHERE world_uuid = ? AND x = ? AND y = ? AND z = ?";
    private static final String SELECT_TRANSFORMS = "SELECT x, y, z, furniture_id, tx, ty, tz, sx, sy, sz, rx, ry, rz FROM "
            + TRANSFORM_TABLE + " WHERE world_uuid = ?";
    private static final String PURGE_ROW = "DELETE FROM " + "custom_blocks_world"
            + " WHERE world_uuid = ? AND x = ? AND y = ? AND z = ? AND block_id = ? AND type = ?";
    private static final String PURGE_LIQUID = "DELETE FROM " + "liquid_sources"
            + " WHERE world_uuid = ? AND x = ? AND y = ? AND z = ? AND liquid_id = ?";

    private static final String UPSERT_LIQUID = "INSERT OR REPLACE INTO " + LIQUID_TABLE
            + " (world_uuid, x, y, z, liquid_id, owner_uuid) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String DELETE_LIQUID = "DELETE FROM " + LIQUID_TABLE
            + " WHERE world_uuid = ? AND x = ? AND y = ? AND z = ?";
    private static final String SELECT_LIQUIDS = "SELECT x, y, z, liquid_id, owner_uuid FROM " + LIQUID_TABLE
            + " WHERE world_uuid = ?";
    private static final String UPSERT_HUD = "INSERT OR REPLACE INTO " + HUD_TABLE
            + " (player_uuid, hud_id, value) VALUES (?, ?, ?)";
    private static final String SELECT_HUDS = "SELECT hud_id, value FROM " + HUD_TABLE + " WHERE player_uuid = ?";

    private static final String INSERT_UNLOCK = "INSERT OR IGNORE INTO " + UNLOCK_TABLE
            + " (player_uuid, advancement, unlocked_at) VALUES (?, ?, ?)";
    private static final String SELECT_UNLOCKS = "SELECT advancement, unlocked_at FROM " + UNLOCK_TABLE
            + " WHERE player_uuid = ? ORDER BY unlocked_at";

    private static final String UPSERT_STORAGE = "INSERT OR REPLACE INTO " + STORAGE_TABLE
            + " (world_uuid, x, y, z, furniture_id, slots, contents, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SELECT_STORAGE = "SELECT furniture_id, slots, contents FROM " + STORAGE_TABLE
            + " WHERE world_uuid = ? AND x = ? AND y = ? AND z = ?";
    private static final String DELETE_STORAGE = "DELETE FROM " + STORAGE_TABLE
            + " WHERE world_uuid = ? AND x = ? AND y = ? AND z = ?";

    private static final String UPSERT_CROP = "INSERT OR REPLACE INTO " + CROP_TABLE
            + " (world_uuid, x, y, z, crop_id, stage, progress) VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String DELETE_CROP = "DELETE FROM " + CROP_TABLE + " WHERE world_uuid = ? AND x = ? AND y = ? AND z = ?";
    private static final String SELECT_CROPS = "SELECT x, y, z, crop_id, stage, progress FROM " + CROP_TABLE
            + " WHERE world_uuid = ?";

    private static final String UPSERT = "INSERT OR REPLACE INTO " + TABLE
            + " (world_uuid, x, y, z, block_id, type) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String DELETE = "DELETE FROM " + TABLE + " WHERE world_uuid = ? AND x = ? AND y = ? AND z = ?";
    private static final String SELECT_WORLD = "SELECT x, y, z, block_id, type FROM " + TABLE + " WHERE world_uuid = ?";

    @FunctionalInterface
    private interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    private final Path file;
    private final Logger logger;
    private final ExecutorService thread;

    /** Touched only from {@link #thread}. */
    private Connection connection;

    public DatabaseManager(Path file, Logger logger) {
        this.file = file;
        this.logger = logger;
        this.thread = Executors.newSingleThreadExecutor(runnable -> {
            Thread worker = new Thread(runnable, "ArkContent-DB");
            worker.setDaemon(true);
            return worker;
        });
    }

    /** Opens the file, creating it and the table as needed. Everything queued after it waits for it. */
    public CompletableFuture<Void> open() {
        return submit(ignored -> null, true);
    }

    /** Records what stands at a position, replacing whatever was recorded there. */
    public CompletableFuture<Void> save(PlacedContent content) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
                statement.setString(1, content.world().toString());
                statement.setInt(2, content.x());
                statement.setInt(3, content.y());
                statement.setInt(4, content.z());
                statement.setString(5, content.contentId());
                statement.setString(6, content.kind().name());
                statement.executeUpdate();
            }
            return null;
        }, false);
    }

    /** @return whether there was a row to delete */
    public CompletableFuture<Boolean> delete(UUID world, int x, int y, int z) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(DELETE)) {
                statement.setString(1, world.toString());
                statement.setInt(2, x);
                statement.setInt(3, y);
                statement.setInt(4, z);
                return statement.executeUpdate() > 0;
            }
        }, false);
    }

    /** Every row of one world. A row that no longer reads as valid is skipped and logged, not fatal. */
    public CompletableFuture<List<PlacedContent>> loadWorld(UUID world) {
        return submit(connection -> {
            List<PlacedContent> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_WORLD)) {
                statement.setString(1, world.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        String type = result.getString(5);
                        try {
                            rows.add(new PlacedContent(world, result.getInt(1), result.getInt(2), result.getInt(3),
                                    result.getString(4), PlacedContent.Kind.valueOf(type)));
                        } catch (IllegalArgumentException | NullPointerException exception) {
                            logger.warning("Skipping a " + TABLE + " row at " + result.getInt(1) + ","
                                    + result.getInt(2) + "," + result.getInt(3) + " with unknown type '" + type + "'");
                        }
                    }
                }
            }
            return rows;
        }, false);
    }

    // ---------------------------------------------------------------- crops

    /** Records a crop and how far it has grown, replacing whatever was recorded at its position. */
    public CompletableFuture<Void> saveCrop(PlantedCrop crop) {
        return saveCrops(List.of(crop));
    }

    /**
     * Records many crops in one transaction - one disk sync, however many rows. Used to save every
     * crop's progress at once when a world unloads or the server stops.
     */
    public CompletableFuture<Void> saveCrops(Collection<PlantedCrop> crops) {
        List<PlantedCrop> rows = List.copyOf(crops);
        return submit(connection -> {
            if (rows.isEmpty()) {
                return null;
            }
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(UPSERT_CROP)) {
                for (PlantedCrop crop : rows) {
                    statement.setString(1, crop.world().toString());
                    statement.setInt(2, crop.x());
                    statement.setInt(3, crop.y());
                    statement.setInt(4, crop.z());
                    statement.setString(5, crop.cropId());
                    statement.setInt(6, crop.stage());
                    statement.setLong(7, crop.progress());
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
            return null;
        }, false);
    }

    /** @return whether there was a crop to delete */
    public CompletableFuture<Boolean> deleteCrop(UUID world, int x, int y, int z) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(DELETE_CROP)) {
                statement.setString(1, world.toString());
                statement.setInt(2, x);
                statement.setInt(3, y);
                statement.setInt(4, z);
                return statement.executeUpdate() > 0;
            }
        }, false);
    }

    /** Every crop of one world. */
    public CompletableFuture<List<PlantedCrop>> loadCrops(UUID world) {
        return submit(connection -> {
            List<PlantedCrop> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_CROPS)) {
                statement.setString(1, world.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        rows.add(new PlantedCrop(world, result.getInt(1), result.getInt(2), result.getInt(3),
                                result.getString(4), result.getInt(5), result.getLong(6)));
                    }
                }
            }
            return rows;
        }, false);
    }

    // ---------------------------------------------------------------- storage furniture

    /**
     * Records what a storage furniture holds, replacing what was recorded there. Writes queue in
     * order on the database thread, so the last save asked for is the one that stays.
     */
    public CompletableFuture<Void> saveInventory(StoredInventory inventory) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPSERT_STORAGE)) {
                statement.setString(1, inventory.world().toString());
                statement.setInt(2, inventory.x());
                statement.setInt(3, inventory.y());
                statement.setInt(4, inventory.z());
                statement.setString(5, inventory.furnitureId());
                statement.setInt(6, inventory.slots());
                statement.setBytes(7, inventory.contents());
                statement.setLong(8, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return null;
        }, false);
    }

    /** What the storage furniture at a position holds, if anything was ever saved there. */
    public CompletableFuture<Optional<StoredInventory>> loadInventory(UUID world, int x, int y, int z) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SELECT_STORAGE)) {
                statement.setString(1, world.toString());
                statement.setInt(2, x);
                statement.setInt(3, y);
                statement.setInt(4, z);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new StoredInventory(world, x, y, z, result.getString(1), result.getInt(2),
                            result.getBytes(3)));
                }
            }
        }, false);
    }

    /**
     * Reads and deletes what a storage furniture holds, in one transaction: for a furniture being
     * broken, whose contents are dropped. Read and delete as two calls, a save queued between them
     * would be lost, or the same contents dropped twice.
     */
    public CompletableFuture<Optional<StoredInventory>> takeInventory(UUID world, int x, int y, int z) {
        return submit(connection -> {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                Optional<StoredInventory> found = Optional.empty();
                try (PreparedStatement select = connection.prepareStatement(SELECT_STORAGE)) {
                    select.setString(1, world.toString());
                    select.setInt(2, x);
                    select.setInt(3, y);
                    select.setInt(4, z);
                    try (ResultSet result = select.executeQuery()) {
                        if (result.next()) {
                            found = Optional.of(new StoredInventory(world, x, y, z, result.getString(1),
                                    result.getInt(2), result.getBytes(3)));
                        }
                    }
                }
                if (found.isPresent()) {
                    try (PreparedStatement delete = connection.prepareStatement(DELETE_STORAGE)) {
                        delete.setString(1, world.toString());
                        delete.setInt(2, x);
                        delete.setInt(3, y);
                        delete.setInt(4, z);
                        delete.executeUpdate();
                    }
                }
                connection.commit();
                return found;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }, false);
    }

    /** @return whether there was an inventory to delete */
    public CompletableFuture<Boolean> deleteInventory(UUID world, int x, int y, int z) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(DELETE_STORAGE)) {
                statement.setString(1, world.toString());
                statement.setInt(2, x);
                statement.setInt(3, y);
                statement.setInt(4, z);
                return statement.executeUpdate() > 0;
            }
        }, false);
    }

    /**
     * Notes that a player completed an advancement.
     *
     * @return whether it was the first time: an earlier completion keeps its row and its moment
     */
    public CompletableFuture<Boolean> recordUnlock(UUID player, String advancement, long unlockedAtMillis) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INSERT_UNLOCK)) {
                statement.setString(1, player.toString());
                statement.setString(2, advancement);
                statement.setLong(3, unlockedAtMillis);
                return statement.executeUpdate() > 0;
            }
        }, false);
    }

    /** When a player first completed each of the plugin's advancements, oldest first. */
    public CompletableFuture<Map<String, Long>> unlocks(UUID player) {
        return submit(connection -> {
            Map<String, Long> unlocks = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_UNLOCKS)) {
                statement.setString(1, player.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        unlocks.put(rows.getString(1), rows.getLong(2));
                    }
                }
            }
            return unlocks;
        }, false);
    }

    /** A liquid source, as stored: where, which liquid, and who poured it (null for nobody). */
    public record LiquidSource(int x, int y, int z, String liquid, @Nullable UUID owner) {
    }

    public CompletableFuture<Void> saveLiquid(UUID world, LiquidSource source) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPSERT_LIQUID)) {
                statement.setString(1, world.toString());
                statement.setInt(2, source.x());
                statement.setInt(3, source.y());
                statement.setInt(4, source.z());
                statement.setString(5, source.liquid());
                statement.setString(6, source.owner() == null ? null : source.owner().toString());
                statement.executeUpdate();
            }
            return null;
        }, false);
    }

    public CompletableFuture<Boolean> deleteLiquid(UUID world, int x, int y, int z) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(DELETE_LIQUID)) {
                statement.setString(1, world.toString());
                statement.setInt(2, x);
                statement.setInt(3, y);
                statement.setInt(4, z);
                return statement.executeUpdate() > 0;
            }
        }, false);
    }

    /** Every liquid source in a world. A row whose owner is not a UUID keeps the source, ownerless. */
    public CompletableFuture<List<LiquidSource>> loadLiquids(UUID world) {
        return submit(connection -> {
            List<LiquidSource> sources = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_LIQUIDS)) {
                statement.setString(1, world.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        String owner = rows.getString(5);
                        UUID ownerId = null;
                        if (owner != null) {
                            try {
                                ownerId = UUID.fromString(owner);
                            } catch (IllegalArgumentException ignored) {
                                // Kept ownerless.
                            }
                        }
                        sources.add(new LiquidSource(rows.getInt(1), rows.getInt(2), rows.getInt(3),
                                rows.getString(4), ownerId));
                    }
                }
            }
            return sources;
        }, false);
    }

    /** Writes a player's HUD values, in one transaction. */
    public CompletableFuture<Void> saveHudValues(UUID player, Map<String, Double> values) {
        Map<String, Double> copy = Map.copyOf(values);
        return submit(connection -> {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(UPSERT_HUD)) {
                for (Map.Entry<String, Double> value : copy.entrySet()) {
                    statement.setString(1, player.toString());
                    statement.setString(2, value.getKey());
                    statement.setDouble(3, value.getValue());
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
            return null;
        }, false);
    }

    /** A player's HUD values, by HUD id. */
    public CompletableFuture<Map<String, Double>> loadHudValues(UUID player) {
        return submit(connection -> {
            Map<String, Double> values = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_HUDS)) {
                statement.setString(1, player.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        values.put(rows.getString(1), rows.getDouble(2));
                    }
                }
            }
            return values;
        }, false);
    }

    // ---------------------------------------------------------------- furniture transforms

    /** A piece of furniture's own display transform, as stored. */
    public record StoredTransform(int x, int y, int z, String furnitureId, DisplayTransform transform) {
    }

    public CompletableFuture<Void> saveTransform(UUID world, StoredTransform stored) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(UPSERT_TRANSFORM)) {
                statement.setString(1, world.toString());
                statement.setInt(2, stored.x());
                statement.setInt(3, stored.y());
                statement.setInt(4, stored.z());
                statement.setString(5, stored.furnitureId());
                DisplayTransform transform = stored.transform();
                float[] values = {transform.translation().x(), transform.translation().y(), transform.translation().z(),
                    transform.scale().x(), transform.scale().y(), transform.scale().z(),
                    transform.rotation().x(), transform.rotation().y(), transform.rotation().z()};
                for (int i = 0; i < values.length; i++) {
                    statement.setDouble(6 + i, values[i]);
                }
                statement.setLong(15, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return null;
        }, false);
    }

    public CompletableFuture<Boolean> deleteTransform(UUID world, int x, int y, int z) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(DELETE_TRANSFORM)) {
                statement.setString(1, world.toString());
                statement.setInt(2, x);
                statement.setInt(3, y);
                statement.setInt(4, z);
                return statement.executeUpdate() > 0;
            }
        }, false);
    }

    public CompletableFuture<List<StoredTransform>> loadTransforms(UUID world) {
        return submit(connection -> {
            List<StoredTransform> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_TRANSFORMS)) {
                statement.setString(1, world.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        rows.add(new StoredTransform(result.getInt(1), result.getInt(2), result.getInt(3),
                                result.getString(4), new DisplayTransform(
                                vec(result, 5), vec(result, 8), vec(result, 11))));
                    }
                }
            }
            return rows;
        }, false);
    }

    private static Placement.Vec3 vec(ResultSet result, int first) throws SQLException {
        return new Placement.Vec3(result.getFloat(first), result.getFloat(first + 1), result.getFloat(first + 2));
    }

    // ---------------------------------------------------------------- sanity

    /**
     * Deletes a row of custom_blocks_world only if it still says what {@code stale} says - and,
     * for furniture, the piece's own transform with it - in one transaction: a row written again
     * since the check is never lost.
     *
     * @return whether the row was deleted
     */
    public CompletableFuture<Boolean> purgeIfUnchanged(PlacedContent stale) {
        return submit(connection -> {
            boolean auto = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                boolean deleted;
                try (PreparedStatement statement = connection.prepareStatement(PURGE_ROW)) {
                    statement.setString(1, stale.world().toString());
                    statement.setInt(2, stale.x());
                    statement.setInt(3, stale.y());
                    statement.setInt(4, stale.z());
                    statement.setString(5, stale.contentId());
                    statement.setString(6, stale.kind().name());
                    deleted = statement.executeUpdate() > 0;
                }
                if (deleted && stale.kind() == PlacedContent.Kind.FURNITURE) {
                    try (PreparedStatement statement = connection.prepareStatement(DELETE_TRANSFORM)) {
                        statement.setString(1, stale.world().toString());
                        statement.setInt(2, stale.x());
                        statement.setInt(3, stale.y());
                        statement.setInt(4, stale.z());
                        statement.executeUpdate();
                    }
                }
                connection.commit();
                return deleted;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(auto);
            }
        }, false);
    }

    /** Deletes a liquid source row only if it is still that liquid's. */
    public CompletableFuture<Boolean> purgeLiquidIfUnchanged(UUID world, LiquidSource stale) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(PURGE_LIQUID)) {
                statement.setString(1, world.toString());
                statement.setInt(2, stale.x());
                statement.setInt(3, stale.y());
                statement.setInt(4, stale.z());
                statement.setString(5, stale.liquid());
                return statement.executeUpdate() > 0;
            }
        }, false);
    }

    /**
     * Runs work that needs no connection on the database's thread - the sanity checker's judging,
     * kept off the server thread and in line with the writes it leads to.
     */
    public <T> CompletableFuture<T> onThread(Supplier<T> work) {
        try {
            return CompletableFuture.supplyAsync(work, thread);
        } catch (RejectedExecutionException exception) {
            return CompletableFuture.failedFuture(new SQLException("the database is closed", exception));
        }
    }

    /**
     * Lets every queued statement finish, then closes the file. Blocks for at most ten seconds;
     * called once, from the plugin's shutdown.
     */
    public void close() {
        try {
            thread.execute(this::closeConnection);
        } catch (RejectedExecutionException alreadyClosed) {
            return;
        }
        thread.shutdown();
        try {
            if (!thread.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("The database did not finish its queued writes within 10 seconds;"
                        + " the last few changes may not have been saved.");
                thread.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------------------------------------------------------------- storage thread

    private <T> CompletableFuture<T> submit(Work<T> work, boolean opening) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    if (opening) {
                        connect();
                    } else if (connection == null) {
                        throw new SQLException("the database is not open");
                    }
                    return work.run(connection);
                } catch (SQLException | IOException exception) {
                    throw new CompletionException(exception);
                }
            }, thread);
        } catch (RejectedExecutionException exception) {
            return CompletableFuture.failedFuture(new SQLException("the database is closed", exception));
        }
    }

    private void connect() throws SQLException, IOException {
        if (connection != null) {
            return;
        }
        try {
            // Paper loads the driver on the server's own class path; naming it makes a missing
            // driver fail here, with a message, instead of as "no suitable driver".
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("the SQLite JDBC driver (org.sqlite.JDBC) is not on the server's class path", exception);
        }
        Files.createDirectories(file.toAbsolutePath().getParent());
        Connection opened = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        try (Statement statement = opened.createStatement()) {
            // WAL lets reads go on while a write is committed, and NORMAL sync is safe under WAL:
            // a power cut can lose the last commits, never corrupt the file.
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute(CREATE_TABLE);
            statement.execute(CREATE_CROP_TABLE);
            statement.execute(CREATE_STORAGE_TABLE);
            statement.execute(CREATE_UNLOCK_TABLE);
            statement.execute(CREATE_LIQUID_TABLE);
            statement.execute(CREATE_HUD_TABLE);
            statement.execute(CREATE_TRANSFORM_TABLE);
            int version;
            try (ResultSet result = statement.executeQuery("PRAGMA user_version")) {
                version = result.next() ? result.getInt(1) : 0;
            }
            if (version < SCHEMA_VERSION) {
                statement.execute("PRAGMA user_version=" + SCHEMA_VERSION);
            } else if (version > SCHEMA_VERSION) {
                logger.warning(file.getFileName() + " was written by a newer version of the plugin (schema "
                        + version + "); tables it does not know are left alone.");
            }
        } catch (SQLException exception) {
            opened.close();
            throw exception;
        }
        this.connection = opened;
    }

    private void closeConnection() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            logger.log(Level.WARNING, "Could not close the database cleanly", exception);
        }
        connection = null;
    }
}
