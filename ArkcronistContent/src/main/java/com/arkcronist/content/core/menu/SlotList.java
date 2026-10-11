package com.arkcronist.content.core.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Which of a menu's slots show content, as written in config: {@code 0-44}, or
 * {@code 10-16, 19-25, 28-34} for a picture whose frame covers the border slots.
 */
public final class SlotList {

    private SlotList() {
    }

    /**
     * @param max      the highest slot allowed
     * @param problems where a part that does not read, or is out of range, is reported; it is skipped
     * @return the slots, in order, without repeats; empty if nothing read
     */
    public static List<Integer> parse(String text, int max, List<String> problems) {
        TreeSet<Integer> slots = new TreeSet<>();
        for (String part : text.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] ends = trimmed.split("\\s*-\\s*", 2);
            try {
                int from = Integer.parseInt(ends[0].trim());
                int to = ends.length == 2 ? Integer.parseInt(ends[1].trim()) : from;
                if (from < 0 || to > max || from > to) {
                    problems.add("'" + trimmed + "' is not a range of slots from 0 to " + max);
                    continue;
                }
                for (int slot = from; slot <= to; slot++) {
                    slots.add(slot);
                }
            } catch (NumberFormatException unreadable) {
                problems.add("'" + trimmed + "' is not a slot number or a range like 10-16");
            }
        }
        return new ArrayList<>(slots);
    }
}
