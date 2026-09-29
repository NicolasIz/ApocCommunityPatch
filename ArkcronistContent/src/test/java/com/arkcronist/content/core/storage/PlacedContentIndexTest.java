package com.arkcronist.content.core.storage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlacedContentIndexTest {

    private static final UUID OVERWORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID NETHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void blockKeysRoundTripAcrossTheWholeWorld() {
        int[][] positions = {{0, 0, 0}, {-1, -64, -1}, {29_999_999, 319, -29_999_999}, {-33_554_432, -2048, 33_554_431},
                {12, 2047, -7}};
        for (int[] p : positions) {
            long key = BlockKey.pack(p[0], p[1], p[2]);
            assertEquals(p[0], BlockKey.x(key));
            assertEquals(p[1], BlockKey.y(key));
            assertEquals(p[2], BlockKey.z(key));
        }
    }

    @Test
    void neighbouringPositionsNeverShareAKey() {
        assertEquals(27, java.util.stream.IntStream.rangeClosed(-1, 1).boxed()
                .flatMap(x -> java.util.stream.IntStream.rangeClosed(-1, 1).boxed()
                        .flatMap(y -> java.util.stream.IntStream.rangeClosed(-1, 1).mapToObj(z -> BlockKey.pack(x, y, z))))
                .distinct().count());
    }

    @Test
    void positionsAreKeptPerWorld() {
        PlacedContentIndex index = new PlacedContentIndex();
        index.put(block(OVERWORLD, 1, 64, 1, "demo:ruby_block"));
        index.put(block(NETHER, 1, 64, 1, "demo:amber_block"));

        assertEquals("demo:ruby_block", index.get(OVERWORLD, 1, 64, 1).contentId());
        assertEquals("demo:amber_block", index.get(NETHER, 1, 64, 1).contentId());
        assertNull(index.get(OVERWORLD, 1, 65, 1));

        assertEquals("demo:ruby_block", index.remove(OVERWORLD, 1, 64, 1).contentId());
        assertNull(index.get(OVERWORLD, 1, 64, 1));
        assertEquals(1, index.size());

        index.dropWorld(NETHER);
        assertEquals(0, index.size());
    }

    /** The race the load bookkeeping exists for: broken while its world's rows were still on the way. */
    @Test
    void aBlockBrokenWhileItsWorldLoadsDoesNotComeBack() {
        PlacedContentIndex index = new PlacedContentIndex();
        index.beginLoad(OVERWORLD);
        index.remove(OVERWORLD, 5, 70, 5);

        int added = index.finishLoad(OVERWORLD, List.of(block(OVERWORLD, 5, 70, 5, "demo:ruby_block"),
                block(OVERWORLD, 6, 70, 5, "demo:ruby_block")));

        assertEquals(1, added);
        assertNull(index.get(OVERWORLD, 5, 70, 5));
        assertEquals("demo:ruby_block", index.get(OVERWORLD, 6, 70, 5).contentId());
    }

    @Test
    void somethingPlacedWhileItsWorldLoadsIsNewerThanTheRow() {
        PlacedContentIndex index = new PlacedContentIndex();
        index.beginLoad(OVERWORLD);
        index.put(block(OVERWORLD, 5, 70, 5, "demo:amber_block"));

        index.finishLoad(OVERWORLD, List.of(block(OVERWORLD, 5, 70, 5, "demo:ruby_block")));

        assertEquals("demo:amber_block", index.get(OVERWORLD, 5, 70, 5).contentId());
    }

    @Test
    void afterTheLoadRemovalsAreNoLongerRemembered() {
        PlacedContentIndex index = new PlacedContentIndex();
        index.beginLoad(OVERWORLD);
        index.remove(OVERWORLD, 5, 70, 5);
        index.finishLoad(OVERWORLD, List.of());

        // A later load - the world unloaded and came back - must see the row again.
        index.beginLoad(OVERWORLD);
        index.finishLoad(OVERWORLD, List.of(block(OVERWORLD, 5, 70, 5, "demo:ruby_block")));
        assertEquals("demo:ruby_block", index.get(OVERWORLD, 5, 70, 5).contentId());
    }

    @Test
    void rowsForAWorldThatUnloadedMeanwhileAreDropped() {
        PlacedContentIndex index = new PlacedContentIndex();
        index.beginLoad(OVERWORLD);
        index.dropWorld(OVERWORLD);

        assertEquals(0, index.finishLoad(OVERWORLD, List.of(block(OVERWORLD, 1, 1, 1, "demo:ruby_block"))));
        assertEquals(0, index.size());
    }

    static PlacedContent block(UUID world, int x, int y, int z, String id) {
        return new PlacedContent(world, x, y, z, id, PlacedContent.Kind.BLOCK);
    }
}
