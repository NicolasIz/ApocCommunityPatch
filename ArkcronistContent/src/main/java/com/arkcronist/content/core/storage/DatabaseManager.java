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
    public static final String PAIR_TABLE = "furniture_pairs";
    public static final String NOTICE_TABLE = "quest_notices";
    public static final String PROGRESS_TABLE = "quest_progress";
    public static final String PIECE_TABLE = "furniture_pieces";
    public static final String RECOVERED_TABLE = "recovered_storage";
    public static final String COSMETIC_TABLE = "player_cosmetics";
    public static final String MINIGAME_TABLE = "minigame_stats";

    /**
     * Bumped with each change to the schema, so a later version knows what it opened.
     * 1: custom_blocks_world. 2: custom_crops added. 3: furniture_storage added. 4:
     * advancement_unlocks added. 5: liquid_sources and hud_values added. 6: furniture_transforms
     * added. 7: furniture_pairs, quest_notices and quest_progress added. 8: furniture_pieces and
     * recovered_storage added. 9: player_cosmetics and minigame_stats added. 10: item_storage
     * added. 11: vehicles added. An older file gains the tables it lacks on open, all of them and the
     * new version number in one transaction - or none of it; nothing existing is touched, so an
     * older plugin still opens the file and leaves the tables it does not know alone.
     */
    public static final int SCHEMA_VERSION = 11;

    /**
     * Each piece of furniture as a whole, by its anchor block: which way it faces. Written in the
     * same transaction as the rows of the blocks it stands on, deleted with them.
     */
    private static final String CREATE_PIECE_TABLE = """
            CREATE TABLE IF NOT EXISTS furniture_pieces (
                world_uuid   TEXT    NOT NULL,
                x            INTEGER NOT NULL,
                y            INTEGER NOT NULL,
                z            INTEGER NOT NULL,
                furniture_id TEXT    NOT NULL,
                yaw          REAL    NOT NULL,
                placed_at    INTEGER NOT NULL,
                PRIMARY KEY (world_uuid, x, y, z)
            ) WITHOUT ROWID""";

    /**
     * Storage contents of furniture the integrity check took out, waiting for an admin to hand them
     * back. Moved here from furniture_storage in the transaction that deletes the furniture's rows.
     */
    private static final String CREATE_RECOVERED_TABLE = """
            CREATE TABLE IF NOT EXISTS recovered_storage (
                id           INTEGER PRIMARY KEY AUTOINCREMENT,
                world_uuid   TEXT    NOT NULL,
                x            INTEGER NOT NULL,
                y            INTEGER NOT NULL,
                z            INTEGER NOT NULL,
                furniture_id TEXT    NOT NULL,
                slots        INTEGER NOT NULL,
                contents     BLOB    NOT NULL,
                reason       TEXT    NOT NULL,
                recovered_at INTEGER NOT NULL,
                claimed_by   TEXT,
                claimed_at   INTEGER NOT NULL DEFAULT 0
            )""";

    /**
     * 3.4: what each item that holds others - a backpack - holds, by the id the item carries. A
     * rowid table, as furniture_storage: its rows carry a blob of a few kilobytes.
     */
    /**
     * 4.0: every vehicle in the world, where it was last left. Its entities are not saved with the
     * chunk - this row is what puts it back when the chunk loads.
     */
    private static final String CREATE_VEHICLE_TABLE = """
            CREATE TABLE IF NOT EXISTS vehicles (
                vehicle_id TEXT    NOT NULL PRIMARY KEY,
                item_id    TEXT    NOT NULL,
                world      TEXT    NOT NULL,
                x          REAL    NOT NULL,
                y          REAL    NOT NULL,
                z          REAL    NOT NULL,
                yaw        REAL    NOT NULL,
                owner      TEXT,
                updated_at INTEGER NOT NULL
            )""";

    private static final String CREATE_ITEM_STORAGE_TABLE = """
            CREATE TABLE IF NOT EXISTS item_storage (
                storage_id TEXT    NOT NULL PRIMARY KEY,
                item_id    TEXT    NOT NULL,
                slots      INTEGER NOT NULL CHECK (slots > 0),
                contents   BLOB    NOT NULL,
                updated_at INTEGER NOT NULL
            )""";

    /**
     * 3.0: what each player wears, one row per slot - the stack itself, as it was taken from their
     * hand, so taking it off gives back exactly that item.
     */
    private static final String CREATE_COSMETIC_TABLE = """
            CREATE TABLE IF NOT EXISTS player_cosmetics (
                player_uuid TEXT    NOT NULL,
                slot        TEXT    NOT NULL,
                item_id     TEXT    NOT NULL,
                item        BLOB    NOT NULL,
                equipped_at INTEGER NOT NULL,
                PRIMARY KEY (player_uuid, slot)
            ) WITHOUT ROWID""";

    /**
     * 3.0: each player's record at each HUD minigame: games played, hits, critical hits, and the
     * closest they came to the middle of the bar, in pixels.
     */
    private static final String CREATE_MINIGAME_TABLE = """
            CREATE TABLE IF NOT EXISTS minigame_stats (
                player_uuid TEXT    NOT NULL,
                game        TEXT    NOT NULL,
                plays       INTEGER NOT NULL DEFAULT 0,
                hits        INTEGER NOT NULL DEFAULT 0,
                criticals   INTEGER NOT NULL DEFAULT 0,
                best_error  REAL,
                last_played INTEGER NOT NULL,
                PRIMARY KEY (player_uuid, game)
            ) WITHOUT ROWID""";

    private static final String SELECT_COSMETICS = "SELECT slot, item_id, item, equipped_at FROM " + COSMETIC_TABLE
            + " WHERE player_uuid = ?";
    private static final String UPSERT_COSMETIC = "INSERT OR REPLACE INTO " + COSMETIC_TABLE
            + " (player_uuid, slot, item_id, item, equipped_at) VALUES (?, ?, ?, ?, ?)";
    private static final String DELETE_COSMETIC = "DELETE FROM " + COSMETIC_TABLE + " WHERE player_uuid = ? AND slot = ?";
    private static final String RECORD_MINIGAME = "INSERT INTO " + MINIGAME_TABLE
            + " (player_uuid, game, plays, hits, criticals, best_error, last_played) VALUES (?, ?, 1, ?, ?, ?, ?)"
            + " ON CONFLICT (player_uuid, game) DO UPDATE SET plays = plays + 1, hits = hits + excluded.hits,"
            + " criticals = criticals + excluded.criticals,"
            + " best_error = CASE WHEN excluded.best_error IS NULL THEN best_error"
            + " WHEN best_error IS NULL OR excluded.best_error < best_error THEN excluded.best_error ELSE best_error END,"
            + " last_played = excluded.last_played";
    private static final String SELECT_MINIGAME = "SELECT plays, hits, criticals, best_error, last_played FROM "
            + MINIGAME_TABLE + " WHERE player_uuid = ? AND game = ?";

    private static final String UPSERT_PIECE = "INSERT OR REPLACE INTO " + PIECE_TABLE
            + " (world_uuid, x, y, z, furniture_id, yaw, placed_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String ADOPT_PIECE = "INSERT OR IGNORE INTO " + PIECE_TABLE
            + " (world_uuid, x, y, z, furniture_id, yaw, placed_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String DELETE_PIECE = "DELETE FROM " + PIECE_TABLE
            + " WHERE world_uuid = ? AND x = ? AND y = ? AND z = ?";
    private static final String SELECT_PIECES = "SELECT x, y, z, furniture_id, yaw FROM " + PIECE_TABLE
            + " WHERE world_uuid = ?";
    private static final String RECOVER_STORAGE = "INSERT INTO " + RECOVERED_TABLE
            + " (world_uuid, x, y, z, furniture_id, slots, contents, reason, recovered_at)"
            + " SELECT world_uuid, x, y, z, furniture_id, slots, contents, ?, ? FROM furniture_storage"
            + " WHERE world_uuid = ? AND x = ? AND y = ? AND z = ?";
    private static final String SELECT_RECOVERED = "SELECT id, world_uuid, x, y, z, furniture_id, slots, contents,"
            + " reason, recovered_at, claimed_by, claimed_at FROM " + RECOVERED_TABLE;
    private static final String CLAIM_RECOVERED = "UPDATE " + RECOVERED_TABLE
            + " SET claimed_by = ?, claimed_at = ? WHERE id = ? AND claimed_by IS NULL";

    /**
     * Two chests of furniture joined into a double chest: the first half - vanilla's RIGHT, rows
     * 1-3 of the double inventory - and its partner. A position is in one pair at most, either way
     * round; each half keeps its own {@code furniture_storage} row, so joining and parting move no
     * items.
     */
    private static final String CREATE_PAIR_TABLE = """
            CREATE TABLE IF NOT EXISTS furniture_pairs (
                world_uuid   TEXT    NOT NULL,
                x            INTEGER NOT NULL,
                y            INTEGER NOT NULL,
                z            INTEGER NOT NULL,
                px           INTEGER NOT NULL,
                py           INTEGER NOT NULL,
                pz           INTEGER NOT NULL,
                furniture_id TEXT    NOT NULL,
                paired_at    INTEGER NOT NULL,
                PRIMARY KEY (world_uuid, x, y, z),
                UNIQUE (world_uuid, px, py, pz)
            ) WITHOUT ROWID""";

    /** Quest board notices, as {@link com.arkcronist.content.core.quest.QuestJson} JSON. */
    private static final String CREATE_NOTICE_TABLE = """
            CREATE TABLE IF NOT EXISTS quest_notices (
                id          TEXT    NOT NULL PRIMARY KEY,
                definition  TEXT    NOT NULL,
                posted      INTEGER NOT NULL DEFAULT 0,
                author      TEXT,
                created_at  INTEGER NOT NULL,
                updated_at  INTEGER NOT NULL
            )""";

    /** Each player's notices: taken or done, and how far along. */
    private static final String CREATE_PROGRESS_TABLE = """
            CREATE TABLE IF NOT EXISTS quest_progress (
                player_uuid  TEXT    NOT NULL,
                quest_id     TEXT    NOT NULL,
                state        TEXT    NOT NULL CHECK (state IN ('ACCEPTED', 'COMPLETED')),
                progress     TEXT    NOT NULL,
                accepted_at  INTEGER NOT NULL,
                completed_at INTEGER NOT NULL DEFAULT 0,
                completions  INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (player_uuid, quest_id)
            ) WITHOUT ROWID""";

    private static final String DELETE_PAIRS_AT = "DELETE FROM " + PAIR_TABLE
            + " WHERE world_uuid = ? AND ((x = ? AND y = ? AND z = ?) OR (px = ? AND py = ? AND pz = ?))";
    private static final String INSERT_PAIR = "INSERT INTO " + PAIR_TABLE
            + " (world_uuid, x, y, z, px, py, pz, furniture_id, paired_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SELECT_PAIRS = "SELECT x, y, z, px, py, pz, furniture_id FROM " + PAIR_TABLE
            + " WHERE world_uuid = ?";

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

    /** Statements to run on the database's thread, with its one connection. */
    @FunctionalInterface
    public interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    /** A stored pair of chests, read back: the first half's position, its partner's, and the furniture. */
    public record StoredPair(int x, int y, int z, int px, int py, int pz, String furnitureId) {
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

    /**
     * Records several positions in one task and one transaction: every block a piece of furniture
     * stands on, so the file never holds half of it.
     */
    public CompletableFuture<Void> saveAll(List<PlacedContent> contents) {
        return saveAll(contents, null);
    }

    /**
     * As {@link #saveAll(List)}, with the piece they belong to - which way it faces - in the same
     * transaction.
     */
    public CompletableFuture<Void> saveAll(List<PlacedContent> contents, @Nullable FurniturePiece piece) {
        return submit(connection -> inTransaction(connection, () -> {
            if (piece != null) {
                try (PreparedStatement statement = connection.prepareStatement(UPSERT_PIECE)) {
                    bindPiece(statement, piece);
                    statement.executeUpdate();
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
                for (PlacedContent content : contents) {
                    statement.setString(1, content.world().toString());
                    statement.setInt(2, content.x());
                    statement.setInt(3, content.y());
                    statement.setInt(4, content.z());
                    statement.setString(5, content.contentId());
                    statement.setString(6, content.kind().name());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        }), false);
    }

    /**
     * Forgets several positions of one world in one task and one transaction.
     *
     * @param positions {x, y, z} each
     * @return how many rows there were
     */
    public CompletableFuture<Integer> deleteAll(UUID world, List<int[]> positions) {
        return submit(connection -> inTransaction(connection, () -> {
            int deleted = 0;
            try (PreparedStatement statement = connection.prepareStatement(DELETE)) {
                for (int[] at : positions) {
                    statement.setString(1, world.toString());
                    statement.setInt(2, at[0]);
                    statement.setInt(3, at[1]);
                    statement.setInt(4, at[2]);
                    statement.addBatch();
                }
                for (int count : statement.executeBatch()) {
                    deleted += Math.max(0, count);
                }
            }
            // The piece is kept by its anchor, which is one of the positions.
            try (PreparedStatement statement = connection.prepareStatement(DELETE_PIECE)) {
                for (int[] at : positions) {
                    bindPosition(statement, world, at[0], at[1], at[2]);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return deleted;
        }), false);
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

    // ---------------------------------------------------------------- items that hold others

    /**
     * 3.4: records what a backpack holds, by the id it carries, replacing what was recorded. Writes
     * queue in order, so the last save asked for is the one that stays.
     */
    public CompletableFuture<Void> saveItemStorage(String storageId, String itemId, int slots, byte[] contents) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO item_storage (storage_id, item_id, slots, contents, updated_at) VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (storage_id) DO UPDATE SET item_id = excluded.item_id, slots = excluded.slots,
                        contents = excluded.contents, updated_at = excluded.updated_at""")) {
                statement.setString(1, storageId);
                statement.setString(2, itemId);
                statement.setInt(3, slots);
                statement.setBytes(4, contents);
                statement.setLong(5, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return null;
        }, false);
    }

    /** 4.0: every vehicle in every world, where it was last left. */
    public CompletableFuture<List<StoredVehicle>> vehicles() {
        return submit(connection -> {
            List<StoredVehicle> vehicles = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT vehicle_id, item_id, world, x, y, z, yaw, owner FROM vehicles ORDER BY vehicle_id")) {
                while (result.next()) {
                    try {
                        String owner = result.getString(8);
                        vehicles.add(new StoredVehicle(UUID.fromString(result.getString(1)), result.getString(2),
                                UUID.fromString(result.getString(3)), result.getDouble(4), result.getDouble(5),
                                result.getDouble(6), result.getFloat(7), owner == null ? null : UUID.fromString(owner)));
                    } catch (IllegalArgumentException unreadable) {
                        logger.warning("vehicles: a row with an unreadable id or world is skipped: "
                                + result.getString(1));
                    }
                }
            }
            return vehicles;
        }, false);
    }

    /** 4.0: where a vehicle is now - a new one, or one that moved. */
    public CompletableFuture<Void> saveVehicle(StoredVehicle vehicle) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO vehicles (vehicle_id, item_id, world, x, y, z, yaw, owner, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (vehicle_id) DO UPDATE SET item_id = excluded.item_id, world = excluded.world,
                        x = excluded.x, y = excluded.y, z = excluded.z, yaw = excluded.yaw, owner = excluded.owner,
                        updated_at = excluded.updated_at""")) {
                statement.setString(1, vehicle.id().toString());
                statement.setString(2, vehicle.itemId());
                statement.setString(3, vehicle.world().toString());
                statement.setDouble(4, vehicle.x());
                statement.setDouble(5, vehicle.y());
                statement.setDouble(6, vehicle.z());
                statement.setFloat(7, vehicle.yaw());
                statement.setString(8, vehicle.owner() == null ? null : vehicle.owner().toString());
                statement.setLong(9, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return null;
        }, false);
    }

    /** 4.0: a vehicle picked up: its row goes. */
    public CompletableFuture<Void> deleteVehicle(UUID id) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM vehicles WHERE vehicle_id = ?")) {
                statement.setString(1, id.toString());
                statement.executeUpdate();
            }
            return null;
        }, false);
    }

    /** 3.4: what a backpack holds, serialized, if anything was ever saved for its id. */
    public CompletableFuture<Optional<byte[]>> loadItemStorage(String storageId) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT contents FROM item_storage WHERE storage_id = ?")) {
                statement.setString(1, storageId);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(result.getBytes(1)) : Optional.<byte[]>empty();
                }
            }
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

    /**
     * Records what several storage furniture hold in one transaction: both halves of a double chest,
     * so the file never holds one half saved and the other not.
     */
    public CompletableFuture<Void> saveInventories(List<StoredInventory> inventories) {
        return submit(connection -> inTransaction(connection, () -> {
            try (PreparedStatement statement = connection.prepareStatement(UPSERT_STORAGE)) {
                long now = System.currentTimeMillis();
                for (StoredInventory inventory : inventories) {
                    statement.setString(1, inventory.world().toString());
                    statement.setInt(2, inventory.x());
                    statement.setInt(3, inventory.y());
                    statement.setInt(4, inventory.z());
                    statement.setString(5, inventory.furnitureId());
                    statement.setInt(6, inventory.slots());
                    statement.setBytes(7, inventory.contents());
                    statement.setLong(8, now);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        }), false);
    }

    /**
     * What two storage furniture hold, read in one go - the two halves of a double chest, as they
     * stood at the same moment. Either may be empty.
     */
    public CompletableFuture<List<Optional<StoredInventory>>> loadInventories(UUID world, int[] first, int[] second) {
        return submit(connection -> {
            List<Optional<StoredInventory>> found = new ArrayList<>();
            for (int[] at : List.of(first, second)) {
                try (PreparedStatement statement = connection.prepareStatement(SELECT_STORAGE)) {
                    statement.setString(1, world.toString());
                    statement.setInt(2, at[0]);
                    statement.setInt(3, at[1]);
                    statement.setInt(4, at[2]);
                    try (ResultSet result = statement.executeQuery()) {
                        found.add(result.next()
                                ? Optional.of(new StoredInventory(world, at[0], at[1], at[2], result.getString(1),
                                result.getInt(2), result.getBytes(3)))
                                : Optional.empty());
                    }
                }
            }
            return found;
        }, false);
    }

    // ---------------------------------------------------------------- double chests

    /**
     * Joins two chests, in one transaction: any pair either position was in before is gone, and
     * this one is in, or - on failure - nothing changed at all.
     */
    public CompletableFuture<Void> savePair(UUID world, StoredPair pair) {
        return submit(connection -> inTransaction(connection, () -> {
            for (int[] at : List.of(new int[] {pair.x(), pair.y(), pair.z()}, new int[] {pair.px(), pair.py(), pair.pz()})) {
                deletePairsAt(connection, world, at[0], at[1], at[2]);
            }
            try (PreparedStatement insert = connection.prepareStatement(INSERT_PAIR)) {
                insert.setString(1, world.toString());
                insert.setInt(2, pair.x());
                insert.setInt(3, pair.y());
                insert.setInt(4, pair.z());
                insert.setInt(5, pair.px());
                insert.setInt(6, pair.py());
                insert.setInt(7, pair.pz());
                insert.setString(8, pair.furnitureId());
                insert.setLong(9, System.currentTimeMillis());
                insert.executeUpdate();
            }
            return null;
        }), false);
    }

    /** Parts the pair one of whose halves is at the position. @return whether there was one */
    public CompletableFuture<Boolean> deletePair(UUID world, int x, int y, int z) {
        return submit(connection -> deletePairsAt(connection, world, x, y, z) > 0, false);
    }

    public CompletableFuture<List<StoredPair>> loadPairs(UUID world) {
        return submit(connection -> {
            List<StoredPair> pairs = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_PAIRS)) {
                statement.setString(1, world.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        pairs.add(new StoredPair(result.getInt(1), result.getInt(2), result.getInt(3), result.getInt(4),
                                result.getInt(5), result.getInt(6), result.getString(7)));
                    }
                }
            }
            return pairs;
        }, false);
    }

    private static int deletePairsAt(Connection connection, UUID world, int x, int y, int z) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(DELETE_PAIRS_AT)) {
            delete.setString(1, world.toString());
            delete.setInt(2, x);
            delete.setInt(3, y);
            delete.setInt(4, z);
            delete.setInt(5, x);
            delete.setInt(6, y);
            delete.setInt(7, z);
            return delete.executeUpdate();
        }
    }

    /** Runs {@code body} as one transaction on {@code connection}: all of it is written, or none. */
    public static <T> T inTransaction(Connection connection, SqlBody<T> body) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            T result = body.run();
            connection.commit();
            return result;
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    /** The statements of one transaction. */
    @FunctionalInterface
    public interface SqlBody<T> {
        T run() throws SQLException;
    }

    /**
     * Runs {@code work} on the database's thread, with its connection, in order with every other
     * statement: what the quest board's store is built on.
     */
    public <T> CompletableFuture<T> run(Work<T> work) {
        return submit(work, false);
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
                    forgetFurnitureAt(connection, stale.world(), stale.x(), stale.y(), stale.z(),
                            "its block was replaced by something else", System.currentTimeMillis());
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

    /** What purging a whole piece did: rows deleted, and storage rows moved to recovered_storage. */
    public record PieceRemoval(int rows, int recovered) {
    }

    /**
     * Deletes the rows of a whole piece of furniture found broken - some of its blocks gone - in one
     * transaction, each row only if it still says what {@code rows} says. For every position whose
     * row went, or that had none left, what depends on it goes too: the piece's own transform, its
     * facing, a chest pair it was half of; and what its storage held is moved to recovered_storage,
     * never dropped or lost.
     */
    public CompletableFuture<PieceRemoval> purgePiece(UUID world, List<PlacedContent> rows, String reason) {
        List<PlacedContent> copy = List.copyOf(rows);
        return submit(connection -> inTransaction(connection, () -> {
            long now = System.currentTimeMillis();
            int deleted = 0;
            int recovered = 0;
            for (PlacedContent row : copy) {
                boolean gone;
                try (PreparedStatement statement = connection.prepareStatement(PURGE_ROW)) {
                    bindPosition(statement, world, row.x(), row.y(), row.z());
                    statement.setString(5, row.contentId());
                    statement.setString(6, row.kind().name());
                    gone = statement.executeUpdate() > 0;
                }
                if (gone) {
                    deleted++;
                } else if (rowAt(connection, world, row.x(), row.y(), row.z())) {
                    // Written again since it was found: someone placed something there. Leave it.
                    continue;
                }
                recovered += forgetFurnitureAt(connection, world, row.x(), row.y(), row.z(), reason, now);
            }
            return new PieceRemoval(deleted, recovered);
        }), false);
    }

    /**
     * Storage rows with no furniture row at their position any more - left by furniture purged
     * before 2.0, which deleted the furniture's row but not what it held - moved to
     * recovered_storage in one transaction.
     *
     * @return how many were moved
     */
    public CompletableFuture<Integer> recoverOrphanedStorage(String reason) {
        return submit(connection -> inTransaction(connection, () -> {
            String orphaned = " FROM furniture_storage s WHERE NOT EXISTS (SELECT 1 FROM custom_blocks_world c"
                    + " WHERE c.world_uuid = s.world_uuid AND c.x = s.x AND c.y = s.y AND c.z = s.z"
                    + " AND c.type = 'FURNITURE')";
            int moved;
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + RECOVERED_TABLE
                    + " (world_uuid, x, y, z, furniture_id, slots, contents, reason, recovered_at)"
                    + " SELECT s.world_uuid, s.x, s.y, s.z, s.furniture_id, s.slots, s.contents, ?, ?" + orphaned)) {
                statement.setString(1, reason);
                statement.setLong(2, System.currentTimeMillis());
                moved = statement.executeUpdate();
            }
            if (moved > 0) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("DELETE FROM furniture_storage WHERE rowid IN (SELECT s.rowid" + orphaned + ")");
                }
            }
            return moved;
        }), false);
    }

    /** Every piece's facing in one world, by its anchor's packed position. */
    public CompletableFuture<Map<Long, FurniturePiece>> loadPieces(UUID world) {
        return submit(connection -> {
            Map<Long, FurniturePiece> pieces = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_PIECES)) {
                statement.setString(1, world.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        FurniturePiece piece = new FurniturePiece(world, result.getInt(1), result.getInt(2),
                                result.getInt(3), result.getString(4), result.getFloat(5));
                        pieces.put(piece.key(), piece);
                    }
                }
            }
            return pieces;
        }, false);
    }

    /**
     * Records the facing of pieces placed before schema 8, read off their displays - never over a
     * record already there.
     *
     * @return how many were new
     */
    public CompletableFuture<Integer> adoptPieces(List<FurniturePiece> pieces) {
        List<FurniturePiece> copy = List.copyOf(pieces);
        return submit(connection -> inTransaction(connection, () -> {
            int added = 0;
            try (PreparedStatement statement = connection.prepareStatement(ADOPT_PIECE)) {
                for (FurniturePiece piece : copy) {
                    bindPiece(statement, piece);
                    added += statement.executeUpdate();
                }
            }
            return added;
        }), false);
    }

    /** Recovered storage, newest first: those still waiting, or all of them. */
    public CompletableFuture<List<RecoveredStorage>> recovered(boolean waitingOnly, int limit) {
        return submit(connection -> {
            List<RecoveredStorage> found = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_RECOVERED
                    + (waitingOnly ? " WHERE claimed_by IS NULL" : "") + " ORDER BY id DESC LIMIT ?")) {
                statement.setInt(1, limit);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        found.add(recoveredRow(result));
                    }
                }
            }
            return found;
        }, false);
    }

    /**
     * Claims what a recovered row holds for {@code claimer}, once: marked taken and read back in one
     * transaction, so two admins claiming at once cannot both get the items.
     *
     * @return the contents, or empty if there is no such row or it was already taken
     */
    public CompletableFuture<Optional<RecoveredStorage>> claimRecovered(long id, String claimer) {
        return submit(connection -> inTransaction(connection, () -> {
            try (PreparedStatement claim = connection.prepareStatement(CLAIM_RECOVERED)) {
                claim.setString(1, claimer);
                claim.setLong(2, System.currentTimeMillis());
                claim.setLong(3, id);
                if (claim.executeUpdate() == 0) {
                    return Optional.<RecoveredStorage>empty();
                }
            }
            try (PreparedStatement select = connection.prepareStatement(SELECT_RECOVERED + " WHERE id = ?")) {
                select.setLong(1, id);
                try (ResultSet result = select.executeQuery()) {
                    return result.next() ? Optional.of(recoveredRow(result)) : Optional.<RecoveredStorage>empty();
                }
            }
        }), false);
    }

    // ---------------------------------------------------------------- 3.0: cosmetics

    /** What {@code player} wears, by slot. */
    public CompletableFuture<List<StoredCosmetic>> cosmetics(UUID player) {
        return submit(connection -> {
            List<StoredCosmetic> worn = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_COSMETICS)) {
                statement.setString(1, player.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        worn.add(new StoredCosmetic(player, result.getString(1), result.getString(2),
                                result.getBytes(3), result.getLong(4)));
                    }
                }
            }
            return worn;
        }, false);
    }

    /**
     * Puts {@code cosmetic} on, and takes off what was in its slot, in one transaction: the item
     * taken off is returned to be given back, so a slot never holds two and nothing is lost between.
     *
     * @return what the slot held before, if anything
     */
    public CompletableFuture<Optional<StoredCosmetic>> equipCosmetic(StoredCosmetic cosmetic) {
        return submit(connection -> inTransaction(connection, () -> {
            Optional<StoredCosmetic> before = cosmeticIn(connection, cosmetic.player(), cosmetic.slot());
            try (PreparedStatement statement = connection.prepareStatement(UPSERT_COSMETIC)) {
                statement.setString(1, cosmetic.player().toString());
                statement.setString(2, cosmetic.slot());
                statement.setString(3, cosmetic.itemId());
                statement.setBytes(4, cosmetic.item());
                statement.setLong(5, cosmetic.equippedAtMillis());
                statement.executeUpdate();
            }
            return before;
        }), false);
    }

    /**
     * Takes off what {@code player} wears in {@code slot}: read and deleted in one transaction, so
     * two calls at once cannot both hand the item back.
     *
     * @return what was worn there, if anything
     */
    public CompletableFuture<Optional<StoredCosmetic>> unequipCosmetic(UUID player, String slot) {
        return submit(connection -> inTransaction(connection, () -> {
            Optional<StoredCosmetic> worn = cosmeticIn(connection, player, slot);
            if (worn.isPresent()) {
                try (PreparedStatement statement = connection.prepareStatement(DELETE_COSMETIC)) {
                    statement.setString(1, player.toString());
                    statement.setString(2, slot);
                    statement.executeUpdate();
                }
            }
            return worn;
        }), false);
    }

    private static Optional<StoredCosmetic> cosmeticIn(Connection connection, UUID player, String slot)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_COSMETICS + " AND slot = ?")) {
            statement.setString(1, player.toString());
            statement.setString(2, slot);
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(new StoredCosmetic(player, result.getString(1), result.getString(2),
                        result.getBytes(3), result.getLong(4)))
                        : Optional.empty();
            }
        }
    }

    // ---------------------------------------------------------------- 3.0: minigames

    /**
     * Adds one game to {@code player}'s record at {@code game}.
     *
     * @param error how far from the middle of the bar the needle stopped, in pixels; null for a
     *              game that ran out without a press
     */
    public CompletableFuture<Void> recordMinigame(UUID player, String game, boolean hit, boolean critical,
                                                  @Nullable Double error) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(RECORD_MINIGAME)) {
                statement.setString(1, player.toString());
                statement.setString(2, game);
                statement.setInt(3, hit ? 1 : 0);
                statement.setInt(4, critical ? 1 : 0);
                if (error == null) {
                    statement.setNull(5, java.sql.Types.REAL);
                } else {
                    statement.setDouble(5, error);
                }
                statement.setLong(6, System.currentTimeMillis());
                statement.executeUpdate();
            }
            return null;
        }, false);
    }

    /** {@code player}'s record at {@code game}; all zero if they never played it. */
    public CompletableFuture<MinigameStats> minigameStats(UUID player, String game) {
        return submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SELECT_MINIGAME)) {
                statement.setString(1, player.toString());
                statement.setString(2, game);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return MinigameStats.NONE;
                    }
                    double best = result.getDouble(4);
                    return new MinigameStats(result.getInt(1), result.getInt(2), result.getInt(3),
                            result.wasNull() ? null : best, result.getLong(5));
                }
            }
        }, false);
    }

    private static RecoveredStorage recoveredRow(ResultSet result) throws SQLException {
        return new RecoveredStorage(result.getLong(1), UUID.fromString(result.getString(2)), result.getInt(3),
                result.getInt(4), result.getInt(5), result.getString(6), result.getInt(7), result.getBytes(8),
                result.getString(9), result.getLong(10), result.getString(11), result.getLong(12));
    }

    /**
     * What depends on a furniture position whose row is gone: its transform, its facing, a pair it
     * was half of, and its storage - moved to recovered_storage. Inside the caller's transaction.
     *
     * @return storage rows moved (0 or 1)
     */
    private static int forgetFurnitureAt(Connection connection, UUID world, int x, int y, int z, String reason,
                                         long now) throws SQLException {
        for (String sql : List.of(DELETE_TRANSFORM, DELETE_PIECE)) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bindPosition(statement, world, x, y, z);
                statement.executeUpdate();
            }
        }
        deletePairsAt(connection, world, x, y, z);
        int moved;
        try (PreparedStatement statement = connection.prepareStatement(RECOVER_STORAGE)) {
            statement.setString(1, reason);
            statement.setLong(2, now);
            statement.setString(3, world.toString());
            statement.setInt(4, x);
            statement.setInt(5, y);
            statement.setInt(6, z);
            moved = statement.executeUpdate();
        }
        if (moved > 0) {
            try (PreparedStatement statement = connection.prepareStatement(DELETE_STORAGE)) {
                bindPosition(statement, world, x, y, z);
                statement.executeUpdate();
            }
        }
        return moved;
    }

    private static boolean rowAt(Connection connection, UUID world, int x, int y, int z) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM " + TABLE
                + " WHERE world_uuid = ? AND x = ? AND y = ? AND z = ?")) {
            bindPosition(statement, world, x, y, z);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static void bindPosition(PreparedStatement statement, UUID world, int x, int y, int z) throws SQLException {
        statement.setString(1, world.toString());
        statement.setInt(2, x);
        statement.setInt(3, y);
        statement.setInt(4, z);
    }

    private static void bindPiece(PreparedStatement statement, FurniturePiece piece) throws SQLException {
        bindPosition(statement, piece.world(), piece.x(), piece.y(), piece.z());
        statement.setString(5, piece.furnitureId());
        statement.setFloat(6, piece.yaw());
        statement.setLong(7, System.currentTimeMillis());
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
            int version = migrate(opened);
            if (version > SCHEMA_VERSION) {
                logger.warning(file.getFileName() + " was written by a newer version of the plugin (schema "
                        + version + "); tables it does not know are left alone.");
            } else if (version < SCHEMA_VERSION && version > 0) {
                logger.info(file.getFileName() + ": schema " + version + " -> " + SCHEMA_VERSION
                        + " (tables added in one transaction; nothing existing was changed).");
            }
        } catch (SQLException exception) {
            opened.close();
            throw exception;
        }
        this.connection = opened;
    }

    /**
     * Brings a file to this schema in one transaction: every table it lacks and the new version
     * number are written together, or - on any failure, a crash included - none of them, and the
     * file stays as the older plugin left it. SQLite keeps both the tables and user_version inside
     * the transaction.
     *
     * @return the version the file had before
     */
    static int migrate(Connection connection) throws SQLException {
        return inTransaction(connection, () -> {
            int version;
            try (Statement statement = connection.createStatement()) {
                try (ResultSet result = statement.executeQuery("PRAGMA user_version")) {
                    version = result.next() ? result.getInt(1) : 0;
                }
                for (String create : List.of(CREATE_TABLE, CREATE_CROP_TABLE, CREATE_STORAGE_TABLE,
                        CREATE_UNLOCK_TABLE, CREATE_LIQUID_TABLE, CREATE_HUD_TABLE, CREATE_TRANSFORM_TABLE,
                        CREATE_PAIR_TABLE, CREATE_NOTICE_TABLE, CREATE_PROGRESS_TABLE, CREATE_PIECE_TABLE,
                        CREATE_RECOVERED_TABLE, CREATE_COSMETIC_TABLE, CREATE_MINIGAME_TABLE,
                        CREATE_ITEM_STORAGE_TABLE, CREATE_VEHICLE_TABLE)) {
                    statement.execute(create);
                }
                if (version < SCHEMA_VERSION) {
                    statement.execute("PRAGMA user_version=" + SCHEMA_VERSION);
                }
            }
            return version;
        });
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
