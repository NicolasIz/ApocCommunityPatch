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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
            assertEquals(DatabaseManager.SCHEMA_VERSION, version.getInt(1));
        }
    }

    /** 1.9: a version 6 file gains the double chest and quest board tables (and, since 2.0, the rest). */
    @Test
    void aVersionSixFileGainsTheNewTablesAndNothingElseChanges() throws Exception {
        Path file = temp.resolve("content.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version=6");
            statement.execute("CREATE TABLE furniture_storage (world_uuid TEXT NOT NULL, x INTEGER NOT NULL,"
                    + " y INTEGER NOT NULL, z INTEGER NOT NULL, furniture_id TEXT NOT NULL, slots INTEGER NOT NULL"
                    + " CHECK (slots > 0), contents BLOB NOT NULL, updated_at INTEGER NOT NULL,"
                    + " PRIMARY KEY (world_uuid, x, y, z))");
            statement.execute("INSERT INTO furniture_storage VALUES ('" + OVERWORLD + "', 1, 64, 1, 'demo:ruby_chest',"
                    + " 27, X'0102', 5)");
        }
        database = open();
        assertEquals(List.of(1, 2), toList(get(database.loadInventory(OVERWORLD, 1, 64, 1)).orElseThrow().contents()));
        database.close();
        database = null;
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement()) {
            try (ResultSet version = statement.executeQuery("PRAGMA user_version")) {
                assertEquals(DatabaseManager.SCHEMA_VERSION, version.getInt(1));
            }
            List<String> tables = new ArrayList<>();
            try (ResultSet result = statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'")) {
                while (result.next()) {
                    tables.add(result.getString(1));
                }
            }
            assertTrue(tables.containsAll(List.of(DatabaseManager.PAIR_TABLE, DatabaseManager.NOTICE_TABLE,
                    DatabaseManager.PROGRESS_TABLE)), tables.toString());
        }
    }

    /** Joining two chests replaces any pair either was in, in one transaction; parting works from either half. */
    @Test
    void pairsAreSavedWholeAndPartedFromEitherHalf() throws Exception {
        database = open();
        DatabaseManager.StoredPair west = new DatabaseManager.StoredPair(0, 64, 0, 1, 64, 0, "demo:ruby_chest");
        get(database.savePair(OVERWORLD, west));
        assertEquals(List.of(west), get(database.loadPairs(OVERWORLD)));
        // The second chest joins a third instead: the first pair is gone, not left half-written.
        DatabaseManager.StoredPair east = new DatabaseManager.StoredPair(2, 64, 0, 1, 64, 0, "demo:ruby_chest");
        get(database.savePair(OVERWORLD, east));
        assertEquals(List.of(east), get(database.loadPairs(OVERWORLD)));
        assertTrue(get(database.loadPairs(NETHER)).isEmpty());
        assertTrue(get(database.deletePair(OVERWORLD, 1, 64, 0)), "parted from its second half");
        assertFalse(get(database.deletePair(OVERWORLD, 2, 64, 0)));
        assertTrue(get(database.loadPairs(OVERWORLD)).isEmpty());
    }

    /** A double chest's two halves: written in one go, read in one go, each its own row. */
    @Test
    void bothHalvesOfADoubleChestAreWrittenAndReadTogether() throws Exception {
        database = open();
        StoredInventory first = new StoredInventory(OVERWORLD, 0, 64, 0, "demo:ruby_chest", 27, new byte[] {1});
        StoredInventory second = new StoredInventory(OVERWORLD, 1, 64, 0, "demo:ruby_chest", 27, new byte[] {2, 2});
        get(database.saveInventories(List.of(first, second)));
        List<java.util.Optional<StoredInventory>> read = get(database.loadInventories(OVERWORLD, new int[] {0, 64, 0},
                new int[] {1, 64, 0}));
        assertEquals(List.of(1), toList(read.get(0).orElseThrow().contents()));
        assertEquals(List.of(2, 2), toList(read.get(1).orElseThrow().contents()));
        List<java.util.Optional<StoredInventory>> none = get(database.loadInventories(OVERWORLD, new int[] {5, 64, 0},
                new int[] {0, 64, 0}));
        assertTrue(none.get(0).isEmpty());
        assertTrue(none.get(1).isPresent());
        // Each half is still its own row: breaking one takes only its contents.
        assertTrue(get(database.takeInventory(OVERWORLD, 1, 64, 0)).isPresent());
        assertTrue(get(database.loadInventory(OVERWORLD, 0, 64, 0)).isPresent());
    }

    private static List<Integer> toList(byte[] bytes) {
        List<Integer> values = new ArrayList<>();
        for (byte b : bytes) {
            values.add((int) b);
        }
        return values;
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

    /** 1.9.5: every block one piece stands on goes in, and out, in one task and one transaction. */
    @Test
    void aPiecesRowsAreWrittenAndDeletedTogether() throws Exception {
        database = open();
        List<PlacedContent> guillotine = new ArrayList<>();
        for (int up = 0; up < 3; up++) {
            for (int across = 0; across < 2; across++) {
                guillotine.add(new PlacedContent(OVERWORLD, 10 + across, 64 + up, -5, "medieval_props:guillotina_plaza",
                        PlacedContent.Kind.FURNITURE));
            }
        }
        get(database.saveAll(guillotine));
        assertEquals(6, get(database.loadWorld(OVERWORLD)).size());

        assertEquals(6, get(database.deleteAll(OVERWORLD, guillotine.stream()
                .map(row -> new int[] {row.x(), row.y(), row.z()}).toList())));
        assertEquals(List.of(), get(database.loadWorld(OVERWORLD)));
    }

    @Test
    void aPieceWithOneBadRowLeavesNoneOfItsRowsBehind() throws Exception {
        database = open();
        List<PlacedContent> rows = List.of(
                new PlacedContent(OVERWORLD, 1, 64, 1, "medieval_props:banco_largo", PlacedContent.Kind.FURNITURE),
                new PlacedContent(OVERWORLD, 2, 64, 1, "medieval_props:banco_largo", PlacedContent.Kind.FURNITURE),
                // block_id is NOT NULL: this row fails, and the transaction with it.
                new PlacedContent(OVERWORLD, 3, 64, 1, null, PlacedContent.Kind.FURNITURE));
        ExecutionException failed = assertThrows(ExecutionException.class, () -> get(database.saveAll(rows)));
        assertInstanceOf(SQLException.class, failed.getCause() instanceof java.util.concurrent.CompletionException
                ? failed.getCause().getCause() : failed.getCause());
        assertEquals(List.of(), get(database.loadWorld(OVERWORLD)), "rolled back: not the first two either");
        // And the connection is usable afterwards, back in autocommit.
        database.save(rows.get(0));
        assertEquals(List.of(rows.get(0)), get(database.loadWorld(OVERWORLD)));
    }

    /**
     * 2.0: a 1.9.x file (schema 7) opens as schema 8 with the two new tables, and every row it had
     * reads back unchanged.
     */
    @Test
    void aVersionSevenFileGainsThePieceAndRecoveryTablesAndKeepsItsRows() throws Exception {
        Path file = temp.resolve("content.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version=7");
            statement.execute("CREATE TABLE custom_blocks_world (world_uuid TEXT NOT NULL, x INTEGER NOT NULL,"
                    + " y INTEGER NOT NULL, z INTEGER NOT NULL, block_id TEXT NOT NULL, type TEXT NOT NULL"
                    + " CHECK (type IN ('BLOCK', 'FURNITURE')), PRIMARY KEY (world_uuid, x, y, z)) WITHOUT ROWID");
            statement.execute("INSERT INTO custom_blocks_world VALUES ('" + OVERWORLD + "', 3, 64, 3,"
                    + " 'medieval_props:trono_real', 'FURNITURE')");
        }
        database = open();
        assertEquals(List.of(new PlacedContent(OVERWORLD, 3, 64, 3, "medieval_props:trono_real",
                PlacedContent.Kind.FURNITURE)), get(database.loadWorld(OVERWORLD)));
        assertTrue(get(database.loadPieces(OVERWORLD)).isEmpty(), "pieces of 1.9 are adopted later, not invented");
        database.close();
        database = null;
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement()) {
            try (ResultSet version = statement.executeQuery("PRAGMA user_version")) {
                assertEquals(DatabaseManager.SCHEMA_VERSION, version.getInt(1));
            }
            List<String> tables = new ArrayList<>();
            try (ResultSet result = statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'")) {
                while (result.next()) {
                    tables.add(result.getString(1));
                }
            }
            assertTrue(tables.containsAll(List.of(DatabaseManager.PIECE_TABLE, DatabaseManager.RECOVERED_TABLE)),
                    tables.toString());
        }
    }

    /**
     * 3.0: a 2.0 file (schema 8) opens as schema 9 with the cosmetic and minigame tables, its
     * furniture and recovered storage untouched.
     */
    @Test
    void aVersionEightFileGainsTheCosmeticAndMinigameTablesAndKeepsItsRows() throws Exception {
        Path file = temp.resolve("content.db");
        database = open();
        get(database.saveAll(List.of(new PlacedContent(OVERWORLD, 1, 70, 1, "medieval_props:trono_real",
                PlacedContent.Kind.FURNITURE)), new FurniturePiece(OVERWORLD, 1, 70, 1, "medieval_props:trono_real", 90f)));
        database.close();
        database = null;
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement()) {
            // What a 2.0 server leaves: schema 8, without the two 3.0 tables.
            statement.execute("DROP TABLE " + DatabaseManager.COSMETIC_TABLE);
            statement.execute("DROP TABLE " + DatabaseManager.MINIGAME_TABLE);
            statement.execute("PRAGMA user_version=8");
        }
        database = open();
        assertEquals(List.of(new PlacedContent(OVERWORLD, 1, 70, 1, "medieval_props:trono_real",
                PlacedContent.Kind.FURNITURE)), get(database.loadWorld(OVERWORLD)));
        assertEquals(90f, get(database.loadPieces(OVERWORLD)).values().iterator().next().yaw());
        assertTrue(get(database.cosmetics(UUID.randomUUID())).isEmpty());
        database.close();
        database = null;
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement();
             ResultSet version = statement.executeQuery("PRAGMA user_version")) {
            assertEquals(9, version.getInt(1));
        }
    }

    /**
     * 3.0: a slot holds one cosmetic. Putting on another hands back the first; taking it off hands it
     * back once - the second call finds nothing - and the bytes are the ones stored.
     */
    @Test
    void aCosmeticSlotHoldsOneAndGivesItBackOnce() throws Exception {
        database = open();
        UUID player = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        StoredCosmetic satchel = new StoredCosmetic(player, "back", "medieval_props:mochila_viaje",
                new byte[]{1, 2, 3}, 1000L);
        StoredCosmetic quiver = new StoredCosmetic(player, "back", "medieval_props:carcaj",
                new byte[]{4, 5}, 2000L);
        assertTrue(get(database.equipCosmetic(satchel)).isEmpty());
        StoredCosmetic replaced = get(database.equipCosmetic(quiver)).orElseThrow();
        assertEquals("medieval_props:mochila_viaje", replaced.itemId());
        assertArrayEquals(new byte[]{1, 2, 3}, replaced.item());
        List<StoredCosmetic> worn = get(database.cosmetics(player));
        assertEquals(1, worn.size());
        assertEquals("medieval_props:carcaj", worn.get(0).itemId());
        assertEquals(2000L, worn.get(0).equippedAtMillis());

        StoredCosmetic off = get(database.unequipCosmetic(player, "back")).orElseThrow();
        assertArrayEquals(new byte[]{4, 5}, off.item());
        assertTrue(get(database.unequipCosmetic(player, "back")).isEmpty(), "taken off once");
        assertTrue(get(database.cosmetics(player)).isEmpty());
    }

    /** 3.0: plays, hits and criticals add up; the best is the smallest error, and a timeout has none. */
    @Test
    void minigameRecordsAddUpAndKeepTheClosestPress() throws Exception {
        database = open();
        UUID player = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
        assertEquals(MinigameStats.NONE, get(database.minigameStats(player, "demo:rare_ore")));
        get(database.recordMinigame(player, "demo:rare_ore", true, false, 6.5));
        get(database.recordMinigame(player, "demo:rare_ore", true, true, 0.5));
        get(database.recordMinigame(player, "demo:rare_ore", false, false, null));
        get(database.recordMinigame(player, "demo:rare_ore", false, false, 30.0));
        get(database.recordMinigame(player, "demo:other", true, true, 0.0));
        MinigameStats stats = get(database.minigameStats(player, "demo:rare_ore"));
        assertEquals(4, stats.plays());
        assertEquals(2, stats.hits());
        assertEquals(1, stats.criticals());
        assertEquals(0.5, stats.bestError());
        assertTrue(stats.lastPlayedMillis() > 0);
    }

    /**
     * What the migration relies on: tables created and user_version set inside one transaction are
     * both undone when it fails - SQLite keeps the version number in the transaction too.
     */
    @Test
    void aMigrationThatFailsHalfwayLeavesTheFileAsItWas() throws Exception {
        Path file = temp.resolve("half.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA user_version=7");
            }
            assertThrows(SQLException.class, () -> DatabaseManager.inTransaction(connection, () -> {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE furniture_pieces (a)");
                    statement.execute("PRAGMA user_version=8");
                    statement.execute("THIS IS NOT SQL");
                }
                return null;
            }));
            try (Statement statement = connection.createStatement()) {
                try (ResultSet version = statement.executeQuery("PRAGMA user_version")) {
                    assertEquals(7, version.getInt(1), "the version number rolled back");
                }
                try (ResultSet tables = statement.executeQuery(
                        "SELECT count(*) FROM sqlite_master WHERE name = 'furniture_pieces'")) {
                    assertEquals(0, tables.getInt(1), "and the table with it");
                }
            }
            // The real migration then goes through on the same connection.
            assertEquals(7, DatabaseManager.migrate(connection));
            try (Statement statement = connection.createStatement();
                 ResultSet version = statement.executeQuery("PRAGMA user_version")) {
                assertEquals(DatabaseManager.SCHEMA_VERSION, version.getInt(1));
            }
        }
    }

    /** 2.0: a piece's facing is written with its rows, in the same transaction, and goes with them. */
    @Test
    void aPiecesFacingIsKeptWithItsRows() throws Exception {
        database = open();
        List<PlacedContent> bench = List.of(
                new PlacedContent(OVERWORLD, 1, 64, 1, "medieval_props:banco_largo", PlacedContent.Kind.FURNITURE),
                new PlacedContent(OVERWORLD, 2, 64, 1, "medieval_props:banco_largo", PlacedContent.Kind.FURNITURE));
        FurniturePiece piece = new FurniturePiece(OVERWORLD, 1, 64, 1, "medieval_props:banco_largo", 90f);
        get(database.saveAll(bench, piece));
        assertEquals(java.util.Map.of(piece.key(), piece), get(database.loadPieces(OVERWORLD)));

        // Adopting never overwrites what placing wrote; it fills in pieces placed before 2.0.
        FurniturePiece old = new FurniturePiece(OVERWORLD, 9, 64, 9, "demo:ruby_stool", -45f);
        assertEquals(1, get(database.adoptPieces(List.of(
                new FurniturePiece(OVERWORLD, 1, 64, 1, "medieval_props:banco_largo", 180f), old))));
        assertEquals(90f, get(database.loadPieces(OVERWORLD)).get(piece.key()).yaw());
        assertEquals(old, get(database.loadPieces(OVERWORLD)).get(old.key()));

        get(database.deleteAll(OVERWORLD, List.of(new int[] {1, 64, 1}, new int[] {2, 64, 1})));
        assertEquals(java.util.Map.of(old.key(), old), get(database.loadPieces(OVERWORLD)));
    }

    /**
     * 2.0: a piece found broken goes whole, in one transaction - rows still as found, its facing,
     * its pair - and what its storage held is moved to recovered_storage, claimable once.
     */
    @Test
    void aBrokenPieceIsPurgedWholeAndItsStorageRecovered() throws Exception {
        database = open();
        List<PlacedContent> stall = List.of(
                new PlacedContent(OVERWORLD, 0, 64, 0, "medieval_props:puesto_mercado", PlacedContent.Kind.FURNITURE),
                new PlacedContent(OVERWORLD, 1, 64, 0, "medieval_props:puesto_mercado", PlacedContent.Kind.FURNITURE),
                new PlacedContent(OVERWORLD, 2, 64, 0, "medieval_props:puesto_mercado", PlacedContent.Kind.FURNITURE));
        get(database.saveAll(stall, new FurniturePiece(OVERWORLD, 0, 64, 0, "medieval_props:puesto_mercado", 0f)));
        get(database.saveInventory(new StoredInventory(OVERWORLD, 0, 64, 0, "medieval_props:puesto_mercado", 27,
                new byte[] {7, 7, 7})));
        // Something else was placed on the third block since the piece was found broken.
        database.save(new PlacedContent(OVERWORLD, 2, 64, 0, "demo:ruby_block", PlacedContent.Kind.BLOCK));

        DatabaseManager.PieceRemoval removal = get(database.purgePiece(OVERWORLD, stall, "its blocks were gone"));
        assertEquals(new DatabaseManager.PieceRemoval(2, 1), removal);
        assertEquals(List.of(new PlacedContent(OVERWORLD, 2, 64, 0, "demo:ruby_block", PlacedContent.Kind.BLOCK)),
                get(database.loadWorld(OVERWORLD)), "the new block stays");
        assertTrue(get(database.loadPieces(OVERWORLD)).isEmpty());
        assertTrue(get(database.loadInventory(OVERWORLD, 0, 64, 0)).isEmpty(), "moved, not copied");

        List<RecoveredStorage> waiting = get(database.recovered(true, 10));
        assertEquals(1, waiting.size());
        RecoveredStorage row = waiting.get(0);
        assertEquals("medieval_props:puesto_mercado", row.furnitureId());
        assertEquals("its blocks were gone", row.reason());
        assertEquals(List.of(7, 7, 7), toList(row.contents()));

        // Claimed once: a second claim - another admin, the same click twice - gets nothing.
        assertEquals(List.of(7, 7, 7), toList(get(database.claimRecovered(row.id(), "Admin")).orElseThrow().contents()));
        assertTrue(get(database.claimRecovered(row.id(), "Other")).isEmpty());
        assertTrue(get(database.recovered(true, 10)).isEmpty());
        assertEquals("Admin", get(database.recovered(false, 10)).get(0).claimedBy());
    }

    /** 2.0: storage left behind by furniture purged before 2.0 - its row deleted, its items not - is recovered. */
    @Test
    void storageWithNoFurnitureLeftIsRecoveredAndTheRestStays() throws Exception {
        database = open();
        database.save(new PlacedContent(OVERWORLD, 5, 64, 5, "demo:ruby_chest", PlacedContent.Kind.FURNITURE));
        get(database.saveInventory(new StoredInventory(OVERWORLD, 5, 64, 5, "demo:ruby_chest", 27, new byte[] {1})));
        get(database.saveInventory(new StoredInventory(OVERWORLD, 8, 64, 8, "demo:ruby_chest", 27, new byte[] {2})));
        assertEquals(1, get(database.recoverOrphanedStorage("left behind by an earlier version")));
        assertEquals(0, get(database.recoverOrphanedStorage("again")), "nothing twice");
        assertTrue(get(database.loadInventory(OVERWORLD, 5, 64, 5)).isPresent(), "a chest still standing keeps its own");
        assertTrue(get(database.loadInventory(OVERWORLD, 8, 64, 8)).isEmpty());
        assertEquals(List.of(2), toList(get(database.recovered(true, 10)).get(0).contents()));
    }

    /** 2.0: the single-row purge of a furniture row recovers its storage too. */
    @Test
    void aPurgedFurnitureRowTakesItsStorageToRecovery() throws Exception {
        database = open();
        PlacedContent chest = new PlacedContent(OVERWORLD, 4, 64, 4, "demo:ruby_chest", PlacedContent.Kind.FURNITURE);
        database.save(chest);
        get(database.saveInventory(new StoredInventory(OVERWORLD, 4, 64, 4, "demo:ruby_chest", 27, new byte[] {3})));
        assertTrue(get(database.purgeIfUnchanged(chest)));
        assertTrue(get(database.loadInventory(OVERWORLD, 4, 64, 4)).isEmpty());
        assertEquals(1, get(database.recovered(true, 10)).size());
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
