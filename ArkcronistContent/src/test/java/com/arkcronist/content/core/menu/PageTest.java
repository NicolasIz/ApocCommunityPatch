package com.arkcronist.content.core.menu;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageTest {

    private static final List<Integer> HUNDRED = IntStream.range(0, 100).boxed().toList();

    @Test
    void splitsIntoFullPagesAndARemainder() {
        Page<Integer> first = Page.of(HUNDRED, 0, 45);
        Page<Integer> last = Page.of(HUNDRED, 2, 45);

        assertEquals(3, first.count());
        assertEquals(IntStream.range(0, 45).boxed().toList(), first.entries());
        assertEquals(IntStream.range(90, 100).boxed().toList(), last.entries());
        assertEquals(100, last.total());
        assertFalse(first.hasPrevious());
        assertTrue(first.hasNext());
        assertTrue(last.hasPrevious());
        assertFalse(last.hasNext());
    }

    @Test
    void anOutOfRangePageIsClampedToOneThatExists() {
        assertEquals(2, Page.of(HUNDRED, 7, 45).index());
        assertEquals(0, Page.of(HUNDRED, -3, 45).index());
        // The list shrank while someone was on page 3.
        assertEquals(0, Page.of(HUNDRED.subList(0, 10), 2, 45).index());
    }

    @Test
    void anEmptyListIsOneEmptyPage() {
        Page<Integer> page = Page.of(List.of(), 4, 45);

        assertEquals(1, page.count());
        assertEquals(0, page.index());
        assertEquals(List.of(), page.entries());
        assertFalse(page.hasNext());
    }

    @Test
    void anExactMultipleHasNoEmptyTrailingPage() {
        assertEquals(2, Page.of(HUNDRED.subList(0, 90), 0, 45).count());
        assertThrows(IllegalArgumentException.class, () -> Page.of(HUNDRED, 0, 0));
    }
}
