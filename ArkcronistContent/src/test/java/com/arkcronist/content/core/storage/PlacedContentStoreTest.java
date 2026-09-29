package com.arkcronist.content.core.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlacedContentStoreTest {

    private static final UUID WORLD = UUID.fromString("0b6f5d0e-2c8a-4f7e-9d1b-3a4c5e6f7a8b");
    private static final Logger LOGGER = Logger.getLogger("test");

    @TempDir
    Path temp;

    @Test
    void aChangeIsVisibleAtOnceAndOnDiskAfterARestart() throws Exception {
        DatabaseManager database = new DatabaseManager(temp.resolve("content.db"), LOGGER);
        database.open();
        PlacedContentStore store = new PlacedContentStore(database, LOGGER);

        store.add(new PlacedContent(WORLD, 10, 64, 10, "demo:ruby_block", PlacedContent.Kind.BLOCK));
        store.add(new PlacedContent(WORLD, 11, 64, 10, "demo:ruby_pedestal", PlacedContent.Kind.FURNITURE));
        // No waiting: memory already has it.
        assertEquals("demo:ruby_block", store.at(WORLD, 10, 64, 10).contentId());
        store.remove(WORLD, 10, 64, 10);
        assertNull(store.at(WORLD, 10, 64, 10));
        database.close();

        DatabaseManager reopened = new DatabaseManager(temp.resolve("content.db"), LOGGER);
        reopened.open();
        PlacedContentStore restarted = new PlacedContentStore(reopened, LOGGER);
        assertNull(restarted.at(WORLD, 11, 64, 10));

        assertEquals(1, restarted.loadWorld(WORLD).get(10, TimeUnit.SECONDS));
        assertEquals(PlacedContent.Kind.FURNITURE, restarted.at(WORLD, 11, 64, 10).kind());
        assertNull(restarted.at(WORLD, 10, 64, 10));
        reopened.close();
    }

    /** A block broken before its world's rows arrived still has its row deleted. */
    @Test
    void removingBeforeTheWorldIsLoadedStillDeletesTheRow() throws Exception {
        DatabaseManager database = new DatabaseManager(temp.resolve("content.db"), LOGGER);
        database.open();
        database.save(new PlacedContent(WORLD, 1, 1, 1, "demo:ruby_block", PlacedContent.Kind.BLOCK));
        PlacedContentStore store = new PlacedContentStore(database, LOGGER);

        assertNull(store.remove(WORLD, 1, 1, 1));

        assertEquals(List.of(), database.loadWorld(WORLD).get(10, TimeUnit.SECONDS));
        database.close();
    }
}
