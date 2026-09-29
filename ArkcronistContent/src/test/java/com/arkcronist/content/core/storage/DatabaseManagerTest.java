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
