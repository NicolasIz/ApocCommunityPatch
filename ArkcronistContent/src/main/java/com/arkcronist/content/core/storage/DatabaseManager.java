package com.arkcronist.content.core.storage;

import com.arkcronist.content.core.crop.PlantedCrop;

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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The SQLite file that remembers where custom blocks and furniture stand, and how far each crop
 * has grown.
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

    /**
     * Bumped with each change to the schema, so a later version knows what it opened.
     * 1: custom_blocks_world. 2: custom_crops added; a version 1 file gains the table on open.
     */
    static final int SCHEMA_VERSION = 2;

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
