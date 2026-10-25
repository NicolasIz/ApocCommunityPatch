package com.arkcronist.content.core.allocation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The note block allocator's own tests cover the same rules for its range; these cover emojis'. */
class StableAllocatorTest {

    private static final StableAllocator GLYPHS = new StableAllocator(0xE000, 0xF8FF, "emoji character",
            "to emojis no longer defined");

    @Test
    void emojisGetPrivateUseCharactersInNameOrder() {
        StableAllocator.Allocation allocation = GLYPHS.allocate(Map.of(), List.of("ruby", "heart", "coin"));

        assertEquals(Map.of("coin", 0xE000, "heart", 0xE001, "ruby", 0xE002), allocation.assignments());
        assertEquals(allocation.assignments(), allocation.active());
        assertTrue(allocation.changed());
    }

    /** Signs and books keep the character: adding "amber" must not move "ruby". */
    @Test
    void anEmojiKeepsItsCharacterWhenOthersComeAndGo() {
        Map<String, Integer> before = Map.of("heart", 0xE000, "ruby", 0xE001);

        StableAllocator.Allocation allocation = GLYPHS.allocate(before, List.of("amber", "ruby"));

        assertEquals(0xE001, allocation.active().get("ruby"));
        assertEquals(0xE002, allocation.active().get("amber"));
        // heart is retired, not reused.
        assertEquals(0xE000, allocation.assignments().get("heart"));
        assertFalse(allocation.active().containsKey("heart"));
    }

    @Test
    void aCharacterOutsideTheRangeIsReassigned() {
        StableAllocator.Allocation allocation = GLYPHS.allocate(Map.of("ruby", 65), List.of("ruby"));

        assertEquals(0xE000, allocation.active().get("ruby"));
        assertEquals(1, allocation.problems().size());
        assertTrue(allocation.problems().get(0).startsWith("emoji character 65 for ruby is outside"));
    }

    @Test
    void reservedNumbersAreSkippedForNewIdsButKeptByOldOnes() {
        StableAllocator numbers = new StableAllocator(10000, 10999, "custom_model_data number", "free one");
        StableAllocator.Allocation allocation = numbers.allocate(Map.of("demo:old", 10001),
                List.of("demo:a", "demo:b", "demo:old"), java.util.Set.of(10000, 10001, 10002));

        assertEquals(Map.of("demo:a", 10003, "demo:b", 10004, "demo:old", 10001), allocation.active());
    }

    @Test
    void aClashWithAMergedPackMovesTheIdWhenAskedAndSaysSo() {
        StableAllocator numbers = new StableAllocator(10000, 10999, "custom_model_data number", "free one");
        StableAllocator.Allocation allocation = numbers.allocate(Map.of("demo:old", 10001, "demo:gone", 10002),
                List.of("demo:old"), java.util.Set.of(10000, 10001, 10002), true);

        assertEquals(Map.of("demo:old", 10003), allocation.active());
        assertEquals(10002, allocation.assignments().get("demo:gone"), "a retired id is not moved");
        assertEquals(List.of("custom_model_data number 10001 of demo:old is also used by a pack merged in - demo:old"
                + " is given a free one; what was made with the old one now shows that pack's"), allocation.problems());
    }
}
