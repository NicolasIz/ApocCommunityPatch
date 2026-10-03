package com.arkcronist.content.core.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Storage furniture's contents in the database: schema 3. */
class StoredInventoryTest {

    private static final UUID WORLD = UUID.fromString("5f0e3b1c-0d4e-4a53-9a0b-9f3c1d2e4b61");

    @TempDir
    Path temp;

    private DatabaseManager database;

    @AfterEach
    void close() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void contentsRoundTripByteForByte() throws Exception {
        database = open();
        byte[] contents = new byte[64 * 1024];
        new Random(7).nextBytes(contents);
        database.saveInventory(new StoredInventory(WORLD, -12, 70, 3_000_001, "demo:ruby_crate", 54, contents));

        StoredInventory read = get(database.loadInventory(WORLD, -12, 70, 3_000_001)).orElseThrow();

        assertArrayEquals(contents, read.contents());
        assertEquals("demo:ruby_crate", read.furnitureId());
        assertEquals(54, read.slots());
        assertTrue(get(database.loadInventory(WORLD, -12, 71, 3_000_001)).isEmpty(), "a position, not a column");
        assertTrue(get(database.loadInventory(UUID.randomUUID(), -12, 70, 3_000_001)).isEmpty(), "per world");
    }

    @Test
    void theLastSaveWins() throws Exception {
        database = open();
        List<CompletableFuture<Void>> writes = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            writes.add(database.saveInventory(new StoredInventory(WORLD, 0, 64, 0, "demo:ruby_crate", 27, bytes("save " + i))));
        }
        CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);

        assertArrayEquals(bytes("save 49"), get(database.loadInventory(WORLD, 0, 64, 0)).orElseThrow().contents());
        assertEquals(1, rows(), "replaced, not added");
    }

    @Test
    void writesStayInTheOrderTheyWereAskedFor() throws Exception {
        database = open();
        // Closed, broken and placed again in quick succession: the delete must land between the saves.
        database.saveInventory(new StoredInventory(WORLD, 1, 64, 1, "demo:ruby_crate", 27, bytes("old")));
        CompletableFuture<Boolean> deleted = database.deleteInventory(WORLD, 1, 64, 1);
        database.saveInventory(new StoredInventory(WORLD, 1, 64, 1, "demo:ruby_crate", 9, bytes("new")));

        assertTrue(get(deleted));
        StoredInventory read = get(database.loadInventory(WORLD, 1, 64, 1)).orElseThrow();
        assertArrayEquals(bytes("new"), read.contents());
        assertEquals(9, read.slots());
        assertTrue(get(database.deleteInventory(WORLD, 1, 64, 1)));
        assertFalse(get(database.deleteInventory(WORLD, 1, 64, 1)), "nothing left to delete");
        assertEquals(Optional.empty(), get(database.loadInventory(WORLD, 1, 64, 1)));
    }

    @Test
    void takingReadsAndDeletesAtOnce() throws Exception {
        database = open();
        database.saveInventory(new StoredInventory(WORLD, 2, 64, 2, "demo:ruby_crate", 27, bytes("loot")));

        CompletableFuture<Optional<StoredInventory>> first = database.takeInventory(WORLD, 2, 64, 2);
        CompletableFuture<Optional<StoredInventory>> second = database.takeInventory(WORLD, 2, 64, 2);

        assertArrayEquals(bytes("loot"), get(first).orElseThrow().contents());
        assertTrue(get(second).isEmpty(), "dropped once, never twice");
        assertEquals(0, rows());
        assertTrue(get(database.takeInventory(WORLD, 9, 9, 9)).isEmpty());
    }

    @Test
    void contentsSurviveARestart() throws Exception {
        database = open();
        database.saveInventory(new StoredInventory(WORLD, 5, 5, 5, "demo:ruby_crate", 27, bytes("kept")));
        database.close();

        database = open();
        assertArrayEquals(bytes("kept"), get(database.loadInventory(WORLD, 5, 5, 5)).orElseThrow().contents());
    }

    /** A 1.1 database - blocks and crops, schema 2 - gains the storage table, and keeps its rows. */
    @Test
    void aVersionTwoDatabaseGainsTheStorageTable() throws Exception {
        Path file = temp.resolve("content.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE custom_blocks_world (world_uuid TEXT NOT NULL, x INTEGER NOT NULL,"
                    + " y INTEGER NOT NULL, z INTEGER NOT NULL, block_id TEXT NOT NULL, type TEXT NOT NULL,"
                    + " PRIMARY KEY (world_uuid, x, y, z)) WITHOUT ROWID");
            statement.execute("CREATE TABLE custom_crops (world_uuid TEXT NOT NULL, x INTEGER NOT NULL,"
                    + " y INTEGER NOT NULL, z INTEGER NOT NULL, crop_id TEXT NOT NULL, stage INTEGER NOT NULL,"
                    + " progress INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (world_uuid, x, y, z)) WITHOUT ROWID");
            statement.execute("INSERT INTO custom_blocks_world VALUES ('" + WORLD + "', 1, 2, 3, 'demo:ruby_crate', 'FURNITURE')");
            statement.execute("INSERT INTO custom_crops VALUES ('" + WORLD + "', 4, 5, 6, 'demo:ruby_seeds', 2, 30)");
            statement.execute("PRAGMA user_version=2");
        }

        database = new DatabaseManager(file, Logger.getLogger("test"));
        get(database.open());
        database.saveInventory(new StoredInventory(WORLD, 1, 2, 3, "demo:ruby_crate", 27, bytes("x")));
        assertEquals(1, get(database.loadWorld(WORLD)).size());
        assertEquals(1, get(database.loadCrops(WORLD)).size());
        assertTrue(get(database.loadInventory(WORLD, 1, 2, 3)).isPresent());
        database.close();
        database = null;

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement();
             ResultSet version = statement.executeQuery("PRAGMA user_version")) {
            version.next();
            assertEquals(DatabaseManager.SCHEMA_VERSION, version.getInt(1));
            assertEquals(3, version.getInt(1));
        }
    }

    @Test
    void aClosedDatabaseFailsTheFutureInsteadOfThrowing() throws Exception {
        database = open();
        database.close();
        CompletableFuture<Void> late = database.saveInventory(new StoredInventory(WORLD, 0, 0, 0, "x", 9, bytes("x")));
        assertTrue(late.isCompletedExceptionally());
        database = null;
    }

    @Test
    void distinctPositionsAreDistinctRows() throws Exception {
        database = open();
        database.saveInventory(new StoredInventory(WORLD, 0, 64, 0, "demo:a", 9, bytes("a")));
        database.saveInventory(new StoredInventory(WORLD, 0, 64, 1, "demo:b", 9, bytes("b")));
        assertNotEquals(get(database.loadInventory(WORLD, 0, 64, 0)).orElseThrow().furnitureId(),
                get(database.loadInventory(WORLD, 0, 64, 1)).orElseThrow().furnitureId());
        assertEquals(2, rows());
    }

    private DatabaseManager open() throws Exception {
        DatabaseManager opened = new DatabaseManager(temp.resolve("content.db"), Logger.getLogger("test"));
        get(opened.open());
        return opened;
    }

    private int rows() throws Exception {
        return database.loadInventory(WORLD, 0, 0, 0).thenApply(ignored -> 0).get(10, TimeUnit.SECONDS)
                + countRows();
    }

    private int countRows() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + temp.resolve("content.db"));
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + DatabaseManager.STORAGE_TABLE)) {
            result.next();
            return result.getInt(1);
        }
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static <T> T get(CompletableFuture<T> future) throws Exception {
        return future.get(10, TimeUnit.SECONDS);
    }
}
