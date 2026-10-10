package com.arkcronist.content.core.quest;

import java.util.Locale;

/**
 * A name and a count, as typed at the end of a board command: {@code demo:ruby 8},
 * {@code mythicmobs:SkeletonKing 3}, {@code auraskills/fighting 5}. Read as one piece of text, since
 * a command's word cannot hold the {@code :} and {@code /} these names do.
 */
public record Counted(String name, int count) {

    /**
     * @param typed the text, a name without spaces then a whole number
     * @param max   the largest count allowed
     * @throws IllegalArgumentException what is wrong with it, to tell whoever typed it
     */
    public static Counted parse(String typed, int max) {
        String[] parts = typed.trim().split("\\s+");
        if (parts.length != 2 || parts[0].isEmpty()) {
            throw new IllegalArgumentException("expected a name and a number, as in demo:ruby 8 - not '"
                    + typed.trim() + "'");
        }
        int count;
        try {
            count = Integer.parseInt(parts[1]);
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException("'" + parts[1] + "' is not a whole number");
        }
        if (count < 1 || count > max) {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "the number is 1 to %d, not %d", max, count));
        }
        return new Counted(parts[0], count);
    }
}
