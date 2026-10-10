package com.arkcronist.content.core.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Double chests in memory and on disk: found from either half, and read back after a restart. */
class ChestPairStoreTest {

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
    void aPairIsFoundFromEitherHalfAndSurvivesARestart() throws Exception {
        database = new DatabaseManager(temp.resolve("content.db"), Logger.getLogger("test"));
        database.open().get(10, TimeUnit.SECONDS);
        ChestPairStore store = new ChestPairStore(database, Logger.getLogger("test"));
        ChestPair pair = new ChestPair(WORLD, 10, 64, 10, 11, 64, 10, "demo:ruby_chest");
        store.put(pair).get(10, TimeUnit.SECONDS);
        assertEquals(pair, store.at(WORLD, 10, 64, 10));
        assertEquals(pair, store.at(WORLD, 11, 64, 10));
        assertNull(store.at(WORLD, 12, 64, 10));
        assertEquals(1, store.size());
        assertTrue(pair.isFirst(10, 64, 10));
        assertEquals(List.of(11, 64, 10), List.of(pair.other(10, 64, 10)[0], pair.other(10, 64, 10)[1],
                pair.other(10, 64, 10)[2]));

        // A restart: a new store reads the world's pairs back.
        ChestPairStore restarted = new ChestPairStore(database, Logger.getLogger("test"));
        assertEquals(List.of(pair), List.copyOf(restarted.loadWorld(WORLD).get(10, TimeUnit.SECONDS)));
        assertTrue(restarted.loaded(WORLD));
        assertEquals(pair, restarted.at(WORLD, 11, 64, 10));

        // Parted from the second half: both halves are free again, in memory and on disk.
        assertTrue(restarted.remove(WORLD, 11, 64, 10).get(10, TimeUnit.SECONDS));
        assertNull(restarted.at(WORLD, 10, 64, 10));
        assertNull(restarted.at(WORLD, 11, 64, 10));
        assertTrue(database.loadPairs(WORLD).get(10, TimeUnit.SECONDS).isEmpty());
    }
}
