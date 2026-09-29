package com.arkcronist.content.core.block;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoteBlockAllocatorTest {

    @Test
    void firstRunHandsOutStatesFromOneInIdOrder() {
        NoteBlockAllocator.Allocation allocation = NoteBlockAllocator.allocate(Map.of(),
                List.of("demo:ruby_block", "demo:amber_block"));

        assertEquals(Map.of("demo:amber_block", 1, "demo:ruby_block", 2), allocation.assignments());
        assertEquals(NoteBlockState.fromIndex(1), allocation.active().get("demo:amber_block"));
        assertTrue(allocation.changed());
        assertEquals(List.of(), allocation.problems());
    }

    /** The reason the allocator exists: a new block must not move the ones already placed. */
    @Test
    void aNewBlockNeverMovesAnExistingOne() {
        Map<String, Integer> saved = Map.of("demo:ruby_block", 1, "demo:sapphire_block", 2);

        NoteBlockAllocator.Allocation allocation = NoteBlockAllocator.allocate(saved,
                List.of("demo:amber_block", "demo:ruby_block", "demo:sapphire_block"));

        assertEquals(1, allocation.assignments().get("demo:ruby_block"));
        assertEquals(2, allocation.assignments().get("demo:sapphire_block"));
        assertEquals(3, allocation.assignments().get("demo:amber_block"));
    }

    @Test
    void aRemovedBlockKeepsItsStateRetiredAndItIsNotReused() {
        Map<String, Integer> saved = Map.of("demo:ruby_block", 1, "demo:old_block", 2);

        NoteBlockAllocator.Allocation allocation = NoteBlockAllocator.allocate(saved,
                List.of("demo:ruby_block", "demo:new_block"));

        assertEquals(2, allocation.assignments().get("demo:old_block"));
        assertEquals(3, allocation.assignments().get("demo:new_block"));
        assertFalse(allocation.active().containsKey("demo:old_block"));
        assertEquals(2, allocation.active().size());
    }

    @Test
    void nothingNewMeansNothingToWrite() {
        Map<String, Integer> saved = Map.of("demo:ruby_block", 1);

        assertFalse(NoteBlockAllocator.allocate(saved, List.of("demo:ruby_block")).changed());
    }

    @Test
    void beyondSevenHundredNinetyNineBlocksTheRestAreReported() {
        List<String> ids = new ArrayList<>(IntStream.range(0, 800).mapToObj(i -> String.format("demo:b%03d", i)).toList());

        NoteBlockAllocator.Allocation allocation = NoteBlockAllocator.allocate(Map.of(), ids);

        assertEquals(799, allocation.active().size());
        assertEquals(1, allocation.problems().size());
        assertTrue(allocation.problems().get(0).startsWith("demo:b799: no note block state left"),
                allocation.problems().get(0));
    }

    @Test
    void invalidSavedEntriesAreReassignedAndReported() {
        Map<String, Integer> saved = new LinkedHashMap<>();
        saved.put("demo:a", 0);
        saved.put("demo:b", 5);
        saved.put("demo:c", 5);
        saved.put("demo:d", 800);

        NoteBlockAllocator.Allocation allocation = NoteBlockAllocator.allocate(saved,
                List.of("demo:a", "demo:b", "demo:c", "demo:d"));

        assertEquals(5, allocation.assignments().get("demo:b"));
        assertEquals(List.of(1, 2, 3), List.of(allocation.assignments().get("demo:a"),
                allocation.assignments().get("demo:c"), allocation.assignments().get("demo:d")));
        assertEquals(3, allocation.problems().size(), allocation.problems().toString());
    }

    @Test
    void theFileRoundTripsSortedAndAMissingOneIsEmpty(@TempDir Path temp) throws IOException {
        Path file = temp.resolve("data/note_block_states.json");
        assertEquals(Map.of(), NoteBlockAllocator.read(file));

        NoteBlockAllocator.write(file, Map.of("demo:ruby_block", 2, "demo:amber_block", 1));

        assertEquals(Map.of("demo:amber_block", 1, "demo:ruby_block", 2), NoteBlockAllocator.read(file));
        String text = Files.readString(file);
        assertTrue(text.indexOf("amber") < text.indexOf("ruby"), text);
        assertFalse(Files.exists(temp.resolve("data/note_block_states.json.tmp")));
    }

    @Test
    void aBrokenFileIsAnErrorNotAnEmptyAssignment(@TempDir Path temp) throws IOException {
        // Reading it as empty would hand every placed block's state to someone else.
        Path file = temp.resolve("note_block_states.json");
        Files.writeString(file, "{ \"demo:ruby_block\": 1,");

        assertThrows(IOException.class, () -> NoteBlockAllocator.read(file));
    }
}
