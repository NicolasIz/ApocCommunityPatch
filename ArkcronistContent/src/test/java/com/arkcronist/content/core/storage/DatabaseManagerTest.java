package com.arkcronist.content.core.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseManagerTest {

    private static final UUID OVERWORLD = UUID.fromString("5f0e3b1c-0d4e-4a53-9a0b-9f3c1d2e4b61");
    private static final UUID NETHER = UUID.fromString("c3a1f2d4-7b6e-4c1a-8e2f-0a9b8c7d6e5f");

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
    void rowsRoundTripWithEveryField() throws Exception {
        database = open();
        PlacedContent block = new PlacedContent(OVERWORLD, -120, -61, 3_000_001, "demo:ruby_block", PlacedContent.Kind.BLOCK);
        PlacedContent chair = new PlacedContent(OVERWORLD, 4, 70, 4, "demo:ruby_pedestal", PlacedContent.Kind.FURNITURE);
        PlacedContent nether = new PlacedContent(NETHER, 4, 70, 4, "demo:ruby_block", PlacedContent.Kind.BLOCK);
        database.save(block);
        database.save(chair);
        database.save(nether);

        assertEquals(List.of(block, chair), sorted(get(database.loadWorld(OVERWORLD))));
        assertEquals(List.of(nether), get(database.loadWorld(NETHER)));
    }

    @Test
    void aPositionHoldsOneRowAndDeleteRemovesIt() throws Exception {
        database = open();
        database.save(new PlacedContent(OVERWORLD, 1, 2, 3, "demo:ruby_block", PlacedContent.Kind.BLOCK));
        database.save(new PlacedContent(OVERWORLD, 1, 2, 3, "demo:amber_block", PlacedContent.Kind.BLOCK));

        List<PlacedContent> rows = get(database.loadWorld(OVERWORLD));
        assertEquals(1, rows.size());
        assertEquals("demo:amber_block", rows.get(0).contentId());

        assertTrue(get(database.delete(OVERWORLD, 1, 2, 3)));
        assertFalse(get(database.delete(OVERWORLD, 1, 2, 3)));
        assertEquals(List.of(), get(database.loadWorld(OVERWORLD)));
    }

    /** Writes are queued, not awaited: close() is what makes sure the last ones reach the file. */
    @Test
    void queuedWritesSurviveARestart() throws Exception {
        database = open();
        for (int i = 0; i < 200; i++) {
            database.save(new PlacedContent(OVERWORLD, i, 64, 0, "demo:ruby_block", PlacedContent.Kind.BLOCK));
        }
        database.delete(OVERWORLD, 0, 64, 0);
        database.close();

        database = open();
        List<PlacedContent> rows = get(database.loadWorld(OVERWORLD));
        assertEquals(199, rows.size());
        assertTrue(rows.stream().noneMatch(row -> row.x() == 0));
    }

    @Test
    void theTableMatchesTheSpecifiedSchema() throws Exception {
        database = open();
        database.close();
        database = null;

        List<String> columns = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + temp.resolve("content.db"));
             Statement statement = connection.createStatement();
             ResultSet info = statement.executeQuery("PRAGMA table_info(custom_blocks_world)")) {
            while (info.next()) {
                columns.add(info.getString("name") + " " + info.getString("type"));
            }
            assertThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO custom_blocks_world"
                    + " VALUES ('w', 0, 0, 0, 'demo:x', 'SOMETHING_ELSE')"));
        }
        assertEquals(List.of("world_uuid TEXT", "x INTEGER", "y INTEGER", "z INTEGER", "block_id TEXT", "type TEXT"),
                columns);
    }

    @Test
    void workFailsCleanlyWhenTheDatabaseIsNotOpenOrClosed() {
        database = new DatabaseManager(temp.resolve("never-opened.db"), Logger.getLogger("test"));
        ExecutionException notOpen = assertThrows(ExecutionException.class,
                () -> database.loadWorld(OVERWORLD).get(10, TimeUnit.SECONDS));
        assertInstanceOf(SQLException.class, notOpen.getCause());

        database.close();
        ExecutionException closed = assertThrows(ExecutionException.class,
                () -> database.save(new PlacedContent(OVERWORLD, 0, 0, 0, "demo:x", PlacedContent.Kind.BLOCK))
                        .get(10, TimeUnit.SECONDS));
        assertInstanceOf(SQLException.class, closed.getCause());
    }

    @Test
    void anAdvancementIsUnlockedOnceAndAnOlderFileGainsTheTable() throws Exception {
        // A file as 1.4 left it: schema 3, no unlock table.
        Path file = temp.resolve("content.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE custom_blocks_world (world_uuid TEXT NOT NULL, x INTEGER NOT NULL,"
                    + " y INTEGER NOT NULL, z INTEGER NOT NULL, block_id TEXT NOT NULL, type TEXT NOT NULL,"
                    + " PRIMARY KEY (world_uuid, x, y, z)) WITHOUT ROWID");
            statement.execute("PRAGMA user_version=3");
        }
        database = open();
        UUID player = UUID.fromString("f3d28cb0-7225-3cb1-baeb-2dadd2be89ae");

        assertTrue(get(database.recordUnlock(player, "demo:ruby_knight", 1_000L)), "the first time");
        assertFalse(get(database.recordUnlock(player, "demo:ruby_knight", 9_000L)), "revoked and earned again");
        assertTrue(get(database.recordUnlock(player, "demo:first_ruby", 500L)));
        assertEquals(java.util.Map.of("demo:first_ruby", 500L, "demo:ruby_knight", 1_000L), get(database.unlocks(player)),
                "the first moment is the one kept");
        assertEquals(List.of("demo:first_ruby", "demo:ruby_knight"), List.copyOf(get(database.unlocks(player)).keySet()),
                "oldest first");
        database.close();
        database = null;
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement();
             ResultSet version = statement.executeQuery("PRAGMA user_version")) {
            assertEquals(DatabaseManager.SCHEMA_VERSION, version.getInt(1));
        }
    }

    /** Schema 5: a 1.6 file (schema 4) gains the liquid and HUD tables, and both round-trip. */
    @Test
    void liquidSourcesAndHudValuesRoundTripInAnOlderFile() throws Exception {
        Path file = temp.resolve("content.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE advancement_unlocks (player_uuid TEXT NOT NULL, advancement TEXT NOT NULL,"
                    + " unlocked_at INTEGER NOT NULL, PRIMARY KEY (player_uuid, advancement)) WITHOUT ROWID");
            statement.execute("PRAGMA user_version=4");
        }
        database = open();
        UUID player = UUID.fromString("f3d28cb0-7225-3cb1-baeb-2dadd2be89ae");

        DatabaseManager.LiquidSource acid = new DatabaseManager.LiquidSource(10, 64, -3, "demo:acid", player);
        DatabaseManager.LiquidSource poured = new DatabaseManager.LiquidSource(-200, -60, 7, "demo:frost", null);
        get(database.saveLiquid(OVERWORLD, acid));
        get(database.saveLiquid(OVERWORLD, poured));
        get(database.saveLiquid(NETHER, acid));
        // Poured again on the same block: one source, the newer one.
        get(database.saveLiquid(OVERWORLD, new DatabaseManager.LiquidSource(10, 64, -3, "demo:lava_like", null)));

        List<DatabaseManager.LiquidSource> overworld = new ArrayList<>(get(database.loadLiquids(OVERWORLD)));
        overworld.sort(Comparator.comparingInt(DatabaseManager.LiquidSource::x));
        assertEquals(List.of(poured, new DatabaseManager.LiquidSource(10, 64, -3, "demo:lava_like", null)), overworld);
        assertTrue(get(database.deleteLiquid(OVERWORLD, -200, -60, 7)));
        assertFalse(get(database.deleteLiquid(OVERWORLD, -200, -60, 7)));
        assertEquals(List.of(acid), get(database.loadLiquids(NETHER)));

        get(database.saveHudValues(player, java.util.Map.of("demo:thirst", 14.5, "demo:mana", 80.0)));
        get(database.saveHudValues(player, java.util.Map.of("demo:thirst", 3.25)));
        assertEquals(java.util.Map.of("demo:thirst", 3.25, "demo:mana", 80.0), get(database.loadHudValues(player)));
        assertTrue(get(database.loadHudValues(UUID.randomUUID())).isEmpty());

        database.close();
        database = null;
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement();
             ResultSet version = statement.executeQuery("PRAGMA user_version")) {
            assertEquals(DatabaseManager.SCHEMA_VERSION, version.getInt(1));
        }
    }

    /** Schema 6: a 1.7 file (schema 5) gains the editor's furniture_transforms, untouched otherwise. */
    @Test
    void furnitureTransformsRoundTripInAnOlderFile() throws Exception {
        Path file = temp.resolve("content.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version=5");
        }
        database = open();
        com.arkcronist.content.core.furniture.DisplayTransform turned =
                new com.arkcronist.content.core.furniture.DisplayTransform(
                        new com.arkcronist.content.core.definition.Placement.Vec3(0, 0.45f, -0.1f),
                        new com.arkcronist.content.core.definition.Placement.Vec3(0.5f, 0.5f, 0.5f),
                        new com.arkcronist.content.core.definition.Placement.Vec3(0, 90, 0));
        DatabaseManager.StoredTransform stool = new DatabaseManager.StoredTransform(4, 70, -4, "demo:ruby_stool", turned);
        get(database.saveTransform(OVERWORLD, stool));
        get(database.saveTransform(NETHER, new DatabaseManager.StoredTransform(4, 70, -4, "demo:lamp",
                com.arkcronist.content.core.furniture.DisplayTransform.IDENTITY)));
        // Saved again for the same block: one row, the newer one.
        DatabaseManager.StoredTransform again = new DatabaseManager.StoredTransform(4, 70, -4, "demo:ruby_stool",
                turned.adjust(com.arkcronist.content.core.furniture.DisplayTransform.Part.SCALE,
                        com.arkcronist.content.core.furniture.DisplayTransform.Axis.Y, 0.25));
        get(database.saveTransform(OVERWORLD, again));
        assertEquals(List.of(again), get(database.loadTransforms(OVERWORLD)));
        assertTrue(get(database.deleteTransform(NETHER, 4, 70, -4)));
        assertFalse(get(database.deleteTransform(NETHER, 4, 70, -4)));
        assertTrue(get(database.loadTransforms(NETHER)).isEmpty());

        database.close();
        database = null;
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement();
             ResultSet version = statement.executeQuery("PRAGMA user_version")) {
            assertEquals(6, version.getInt(1));
        }
    }

    /** The sanity checker's purge: only a row that still says what it said, and its transform with it. */
    @Test
    void aPurgeDeletesOnlyAnUnchangedRowAndItsTransformInOneGo() throws Exception {
        database = open();
        PlacedContent stool = new PlacedContent(OVERWORLD, 1, 64, 1, "demo:ruby_stool", PlacedContent.Kind.FURNITURE);
        PlacedContent block = new PlacedContent(OVERWORLD, 2, 64, 1, "demo:ruby_block", PlacedContent.Kind.BLOCK);
        database.save(stool);
        database.save(block);
        get(database.saveTransform(OVERWORLD, new DatabaseManager.StoredTransform(1, 64, 1, "demo:ruby_stool",
                com.arkcronist.content.core.furniture.DisplayTransform.IDENTITY)));

        // Replaced since it was found stale: another block placed there. The row stays.
        database.save(new PlacedContent(OVERWORLD, 2, 64, 1, "demo:sapphire_block", PlacedContent.Kind.BLOCK));
        assertFalse(get(database.purgeIfUnchanged(block)));
        assertTrue(get(database.purgeIfUnchanged(stool)));
        assertFalse(get(database.purgeIfUnchanged(stool)), "gone already");
        assertEquals(List.of(new PlacedContent(OVERWORLD, 2, 64, 1, "demo:sapphire_block", PlacedContent.Kind.BLOCK)),
                get(database.loadWorld(OVERWORLD)));
        assertTrue(get(database.loadTransforms(OVERWORLD)).isEmpty(), "the stool's transform went with it");

        DatabaseManager.LiquidSource acid = new DatabaseManager.LiquidSource(5, 60, 5, "demo:acid", null);
        get(database.saveLiquid(OVERWORLD, acid));
        assertFalse(get(database.purgeLiquidIfUnchanged(OVERWORLD, new DatabaseManager.LiquidSource(5, 60, 5,
                "demo:frost", null))), "another liquid's row is not this one's");
        assertTrue(get(database.purgeLiquidIfUnchanged(OVERWORLD, acid)));
        assertTrue(get(database.loadLiquids(OVERWORLD)).isEmpty());
        assertEquals("ArkContent-DB", get(database.onThread(() -> Thread.currentThread().getName())));
    }

    private DatabaseManager open() throws Exception {
        DatabaseManager opened = new DatabaseManager(temp.resolve("content.db"), Logger.getLogger("test"));
        opened.open().get(10, TimeUnit.SECONDS);
        return opened;
    }

    private static <T> T get(CompletableFuture<T> future) throws Exception {
        return future.get(10, TimeUnit.SECONDS);
    }

    private static List<PlacedContent> sorted(List<PlacedContent> rows) {
        return rows.stream().sorted(Comparator.comparingInt(PlacedContent::x)).toList();
    }
}
