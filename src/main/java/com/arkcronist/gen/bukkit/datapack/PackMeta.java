package com.arkcronist.gen.bukkit.datapack;

/**
 * What a datapack's {@code pack.mcmeta} says about the game versions it will load on.
 *
 * <p>A datapack whose format does not match the server is not an error anybody sees. The server
 * lists it and refuses to load it, the structures never appear, and the admin spends the evening
 * looking for the reason in the wrong place. Reading the range up front turns that into one line in
 * the log before anything is copied anywhere.</p>
 *
 * <p>Mojang has changed how a pack declares its range three times and every form is still in the
 * wild, so all of them are read here:</p>
 *
 * <ul>
 *   <li>{@code "pack_format": 81} - one number, and the pack loads on that format only.</li>
 *   <li>{@code "supported_formats": [48, 88]} or
 *       {@code {"min_inclusive": 48, "max_inclusive": 88}} - an explicit range.</li>
 *   <li>{@code "min_format": [88, 0], "max_format": [88, 0]} - the 1.21.9 form, where a format is a
 *       major and a minor and only the major decides whether a pack loads.</li>
 * </ul>
 *
 * <p>A format can also be written {@code 101.1}, a single number with the minor after the point.
 * Only the part before the point is compared, for the same reason.</p>
 */
public record PackMeta(int min, int max) {

    /** Whether a server on this data pack format will load the pack. */
    public boolean supports(int serverFormat) {
        return serverFormat >= min && serverFormat <= max;
    }

    /** How the pack stands against a server on this format. */
    public Verdict verdictFor(int serverFormat) {
        if (supports(serverFormat)) {
            return Verdict.COMPATIBLE;
        }
        return serverFormat < min ? Verdict.TOO_NEW : Verdict.TOO_OLD;
    }

    /** The range as a person would write it. */
    public String range() {
        return min == max ? String.valueOf(min) : min + "-" + max;
    }

    public enum Verdict {
        /** The server will load it. */
        COMPATIBLE,
        /** Built for a newer game than this server. */
        TOO_NEW,
        /** Built for an older game than this server. */
        TOO_OLD
    }

    /**
     * Reads the range out of the text of a {@code pack.mcmeta}, or null when it says nothing usable.
     *
     * @param mcmeta the whole file, as text
     */
    public static PackMeta read(String mcmeta) {
        if (mcmeta == null) {
            return null;
        }
        // Only the "pack" object counts. An overlay entry carries min_format and max_format of its
        // own - the range that overlay applies over, not the range the pack loads on - and reading
        // the file as one flat bag of keys picks up whichever came last.
        String pack = member(mcmeta, "pack");
        if (pack == null) {
            return null;
        }

        // Newest form first, then the range, then the bare number: a pack that carries several says
        // the same thing in each, and where it does not, the most specific one is the one the
        // current game reads.
        Integer low = major(member(pack, "min_format"));
        Integer high = major(member(pack, "max_format"));
        if (low != null || high != null) {
            int resolvedLow = low != null ? low : high;
            int resolvedHigh = high != null ? high : low;
            return ordered(resolvedLow, resolvedHigh);
        }

        String supported = member(pack, "supported_formats");
        if (supported != null) {
            String trimmed = supported.trim();
            if (trimmed.startsWith("{")) {
                Integer from = major(member(trimmed, "min_inclusive"));
                Integer to = major(member(trimmed, "max_inclusive"));
                if (from != null && to != null) {
                    return ordered(from, to);
                }
            } else if (trimmed.startsWith("[")) {
                int[] numbers = numbers(trimmed);
                if (numbers.length >= 2) {
                    return ordered(numbers[0], numbers[1]);
                }
                if (numbers.length == 1) {
                    return ordered(numbers[0], numbers[0]);
                }
            } else {
                Integer only = major(trimmed);
                if (only != null) {
                    return ordered(only, only);
                }
            }
        }

        Integer exact = major(member(pack, "pack_format"));
        return exact == null ? null : ordered(exact, exact);
    }

    private static PackMeta ordered(int a, int b) {
        return new PackMeta(Math.min(a, b), Math.max(a, b));
    }

    /**
     * The major part of a format, however it is written.
     *
     * <p>{@code 81}, {@code 101.1} and {@code [88, 0]} are all a major with an optional minor after
     * it, and only the major decides whether the game will load the pack.</p>
     */
    private static Integer major(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("[")) {
            int[] numbers = numbers(trimmed);
            return numbers.length == 0 ? null : numbers[0];
        }
        int end = 0;
        while (end < trimmed.length() && (Character.isDigit(trimmed.charAt(end))
                || (end == 0 && trimmed.charAt(end) == '-'))) {
            end++;
        }
        if (end == 0) {
            return null;
        }
        try {
            return Integer.valueOf(trimmed.substring(0, end));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** Every whole number in an array, in order, ignoring anything after a decimal point. */
    private static int[] numbers(String array) {
        java.util.List<Integer> found = new java.util.ArrayList<>(2);
        int index = 0;
        while (index < array.length()) {
            char c = array.charAt(index);
            if (Character.isDigit(c)) {
                int start = index;
                while (index < array.length() && Character.isDigit(array.charAt(index))) {
                    index++;
                }
                try {
                    found.add(Integer.valueOf(array.substring(start, index)));
                } catch (NumberFormatException ignored) {
                    // A number too long to be a format is not one.
                }
                // A minor after the point is not a separate entry.
                if (index < array.length() && array.charAt(index) == '.') {
                    index++;
                    while (index < array.length() && Character.isDigit(array.charAt(index))) {
                        index++;
                    }
                }
            } else {
                index++;
            }
        }
        int[] result = new int[found.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = found.get(i);
        }
        return result;
    }

    /**
     * The raw text of one member of a JSON object, or null when the object has no such member.
     *
     * <p>Hand-written rather than parsed with a library because the plugin ships no dependencies it
     * does not compile against, and because the whole question is four numbers in a small file. It
     * walks the text keeping track of quoting and nesting, so a brace inside a description string -
     * and Incendium's description is a list of objects - is text, not structure. Members of nested
     * objects are not visible: this reads one level, which is what makes the overlay problem above
     * go away by construction.</p>
     */
    static String member(String json, String key) {
        int open = json.indexOf('{');
        if (open < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        int keyStart = -1;
        String pendingKey = null;

        for (int i = open; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }
            if (inString) {
                if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                    if (depth == 1 && keyStart >= 0) {
                        pendingKey = json.substring(keyStart, i);
                    }
                }
                continue;
            }

            switch (c) {
                case '"' -> {
                    inString = true;
                    keyStart = i + 1;
                }
                case '{', '[' -> depth++;
                case '}', ']' -> {
                    depth--;
                    if (depth == 0) {
                        return null;
                    }
                }
                case ':' -> {
                    if (depth == 1 && key.equals(pendingKey)) {
                        return value(json, i + 1);
                    }
                    pendingKey = null;
                }
                default -> {
                    // Whitespace and commas between members.
                }
            }
        }
        return null;
    }

    /** The text of the value starting at {@code from}, up to the comma or brace that ends it. */
    private static String value(String json, int from) {
        int start = from;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start >= json.length()) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (inString) {
                if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '{', '[' -> depth++;
                case '}', ']' -> {
                    if (depth == 0) {
                        return json.substring(start, i);
                    }
                    depth--;
                }
                case ',' -> {
                    if (depth == 0) {
                        return json.substring(start, i);
                    }
                }
                default -> {
                    // Part of the value.
                }
            }
        }
        return json.substring(start);
    }
}
