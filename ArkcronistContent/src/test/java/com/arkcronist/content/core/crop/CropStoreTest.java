package com.arkcronist.content.core.crop;

import com.arkcronist.content.core.storage.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CropStoreTest {

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
    void plantedCropsAndTheirStagesSurviveARestart() throws Exception {
        CropStore store = open();
        PlantedCrop ruby = new PlantedCrop(WORLD, 1, 64, 1, "demo:ruby_seeds", 0, 0);
        PlantedCrop other = new PlantedCrop(WORLD, 2, 64, 1, "demo:ruby_seeds", 0, 0);
        store.plant(ruby);
        store.plant(other);
        store.update(ruby, ruby.withGrowth(1, 0), true);
        store.remove(WORLD, 2, 64, 1);
        reopen();

        CropStore reloaded = new CropStore(database, Logger.getLogger("test"));
        assertEquals(1, reloaded.loadWorld(WORLD).get(10, TimeUnit.SECONDS));
        assertEquals(ruby.withGrowth(1, 0), reloaded.at(WORLD, 1, 64, 1));
        assertNull(reloaded.at(WORLD, 2, 64, 1));
    }

    /** Progress is only written when asked - on world unload or shutdown - and then all at once. */
    @Test
    void progressWithinAStageIsSavedWithTheWorld() throws Exception {
        CropStore store = open();
        PlantedCrop crop = new PlantedCrop(WORLD, 5, 70, -5, "demo:ruby_seeds", 0, 0);
        store.plant(crop);
        assertTrue(store.update(crop, crop.withGrowth(0, 42), false));
        reopen();

        CropStore unsaved = new CropStore(database, Logger.getLogger("test"));
        unsaved.loadWorld(WORLD).get(10, TimeUnit.SECONDS);
        PlantedCrop stored = unsaved.at(WORLD, 5, 70, -5);
        assertEquals(0, stored.progress());

        unsaved.update(stored, stored.withGrowth(0, 42), false);
        unsaved.unloadWorld(WORLD).get(10, TimeUnit.SECONDS);
        assertEquals(0, unsaved.size());
        reopen();

        CropStore saved = new CropStore(database, Logger.getLogger("test"));
        saved.loadWorld(WORLD).get(10, TimeUnit.SECONDS);
        assertEquals(42, saved.at(WORLD, 5, 70, -5).progress());
    }

    /** The growth thread read a crop, then a player harvested it: the stale update must not bring it back. */
    @Test
    void anUpdateToACropHarvestedMeanwhileIsDropped() throws Exception {
        CropStore store = open();
        PlantedCrop crop = new PlantedCrop(WORLD, 0, 64, 0, "demo:ruby_seeds", 0, 0);
        store.plant(crop);
        store.remove(WORLD, 0, 64, 0);

        assertFalse(store.update(crop, crop.withGrowth(0, 30), true));
        assertNull(store.at(WORLD, 0, 64, 0));
        assertEquals(0, store.size());
    }

    /** A database from version 1.0 has no crops table: opening it adds one and moves it to schema 2. */
    @Test
    void aVersionOneDatabaseGainsTheCropsTable() throws Exception {
        Path file = temp.resolve("content.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE custom_blocks_world (world_uuid TEXT NOT NULL, x INTEGER NOT NULL,"
                    + " y INTEGER NOT NULL, z INTEGER NOT NULL, block_id TEXT NOT NULL, type TEXT NOT NULL,"
                    + " PRIMARY KEY (world_uuid, x, y, z)) WITHOUT ROWID");
            statement.execute("INSERT INTO custom_blocks_world VALUES ('" + WORLD + "', 1, 2, 3, 'demo:ruby_block', 'BLOCK')");
            statement.execute("PRAGMA user_version=1");
        }

        CropStore store = open();
        store.plant(new PlantedCrop(WORLD, 1, 65, 3, "demo:ruby_seeds", 0, 0));
        assertEquals(1, database.loadWorld(WORLD).get(10, TimeUnit.SECONDS).size());
        database.close();
        database = null;

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement();
             ResultSet version = statement.executeQuery("PRAGMA user_version")) {
            version.next();
            assertEquals(2, version.getInt(1));
        }
        CropStore reopened = open();
        reopened.loadWorld(WORLD).get(10, TimeUnit.SECONDS);
        assertEquals(1, reopened.size());
    }

    @Test
    void manyCropsAreSavedInOneTransaction() throws Exception {
        CropStore store = open();
        for (int x = 0; x < 500; x++) {
            store.plant(new PlantedCrop(WORLD, x, 64, 0, "demo:ruby_seeds", 0, 0));
        }
        for (PlantedCrop crop : List.copyOf(store.crops(WORLD))) {
            store.update(crop, crop.withGrowth(0, crop.x()), false);
        }
        store.saveWorld(WORLD).get(10, TimeUnit.SECONDS);
        List<PlantedCrop> rows = database.loadCrops(WORLD).get(10, TimeUnit.SECONDS).stream()
                .sorted(Comparator.comparingInt(PlantedCrop::x)).toList();
        assertEquals(500, rows.size());
        assertEquals(499, rows.get(499).progress());
    }

    private CropStore open() throws Exception {
        database = new DatabaseManager(temp.resolve("content.db"), Logger.getLogger("test"));
        database.open().get(10, TimeUnit.SECONDS);
        return new CropStore(database, Logger.getLogger("test"));
    }

    private void reopen() throws Exception {
        database.close();
        database = new DatabaseManager(temp.resolve("content.db"), Logger.getLogger("test"));
        database.open().get(10, TimeUnit.SECONDS);
    }
}
