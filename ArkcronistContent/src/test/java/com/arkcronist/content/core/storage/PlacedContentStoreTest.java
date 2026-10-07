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

    /** The sanity checker's purge: memory and disk, each only if the row is still the stale one. */
    @Test
    void aStaleRowIsPurgedOnlyIfNothingReplacedIt() throws Exception {
        DatabaseManager database = new DatabaseManager(temp.resolve("content.db"), LOGGER);
        database.open();
        PlacedContentStore store = new PlacedContentStore(database, LOGGER);
        PlacedContent stale = new PlacedContent(WORLD, 3, 64, 3, "demo:ruby_block", PlacedContent.Kind.BLOCK);
        store.add(stale);
        store.add(new PlacedContent(WORLD, 4, 64, 3, "demo:ruby_block", PlacedContent.Kind.BLOCK));
        org.junit.jupiter.api.Assertions.assertFalse(store.loading(WORLD));
        assertEquals(2, store.entries(WORLD).size());

        // Placed again by a player between the audit and the purge: a different row, kept.
        store.add(new PlacedContent(WORLD, 4, 64, 3, "demo:sapphire_block", PlacedContent.Kind.BLOCK));
        org.junit.jupiter.api.Assertions.assertFalse(store.purge(new PlacedContent(WORLD, 4, 64, 3, "demo:ruby_block",
                PlacedContent.Kind.BLOCK)).get(10, TimeUnit.SECONDS));
        org.junit.jupiter.api.Assertions.assertTrue(store.purge(stale).get(10, TimeUnit.SECONDS));
        assertNull(store.at(WORLD, 3, 64, 3));
        assertEquals(List.of(new PlacedContent(WORLD, 4, 64, 3, "demo:sapphire_block", PlacedContent.Kind.BLOCK)),
                database.loadWorld(WORLD).get(10, TimeUnit.SECONDS));
        database.close();
    }

    /** A piece's own transform: in memory at once, on disk for the next start, and gone with a purge. */
    @Test
    void furnitureTransformsAreKeptPerPieceAcrossARestart() throws Exception {
        DatabaseManager database = new DatabaseManager(temp.resolve("content.db"), LOGGER);
        database.open();
        FurnitureTransformStore transforms = new FurnitureTransformStore(database, LOGGER);
        com.arkcronist.content.core.furniture.DisplayTransform turned =
                com.arkcronist.content.core.furniture.DisplayTransform.IDENTITY.adjust(
                        com.arkcronist.content.core.furniture.DisplayTransform.Part.ROTATION,
                        com.arkcronist.content.core.furniture.DisplayTransform.Axis.Y, 45);
        FurnitureTransform own = new FurnitureTransform(WORLD, 7, 64, 7, "demo:ruby_stool", turned);
        transforms.remember(own);
        assertEquals(own, transforms.at(WORLD, 7, 64, 7), "memory first");
        transforms.write(own).get(10, TimeUnit.SECONDS);
        transforms.put(new FurnitureTransform(WORLD, 8, 64, 7, "demo:lamp", turned)).get(10, TimeUnit.SECONDS);
        transforms.remove(WORLD, 8, 64, 7).get(10, TimeUnit.SECONDS);
        database.close();

        DatabaseManager reopened = new DatabaseManager(temp.resolve("content.db"), LOGGER);
        reopened.open();
        FurnitureTransformStore restarted = new FurnitureTransformStore(reopened, LOGGER);
        assertEquals(List.of(own), List.copyOf(restarted.loadWorld(WORLD).get(10, TimeUnit.SECONDS)));
        assertNull(restarted.at(WORLD, 8, 64, 7));
        restarted.unloadWorld(WORLD);
        assertNull(restarted.at(WORLD, 7, 64, 7));
        reopened.close();
    }
}
