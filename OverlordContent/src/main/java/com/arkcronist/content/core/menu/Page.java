package com.arkcronist.content.core.menu;

import java.util.List;

/**
 * One page of a list shown a fixed number of entries at a time.
 *
 * @param entries the entries on this page, at most the page size
 * @param index   zero-based
 * @param count   pages in total, never less than one - an empty list is one empty page
 * @param total   entries across every page
 */
public record Page<T>(List<T> entries, int index, int count, int total) {

    public Page {
        entries = List.copyOf(entries);
    }

    /**
     * The page {@code requested} of {@code all}, clamped to the pages that exist: asking past the
     * end gives the last page, which is what a menu wants after its list shrank under it.
     */
    public static <T> Page<T> of(List<T> all, int requested, int size) {
        if (size < 1) {
            throw new IllegalArgumentException("page size must be at least 1, not " + size);
        }
        int count = Math.max(1, (all.size() + size - 1) / size);
        int index = Math.max(0, Math.min(requested, count - 1));
        int from = index * size;
        return new Page<>(all.subList(from, Math.min(all.size(), from + size)), index, count, all.size());
    }

    public boolean hasPrevious() {
        return index > 0;
    }

    public boolean hasNext() {
        return index < count - 1;
    }
}
