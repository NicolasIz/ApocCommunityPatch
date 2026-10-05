package com.arkcronist.content.core.allocation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Hands out numbers from a fixed range to ids, and keeps them handed out.
 *
 * <p>For things the world remembers by number rather than by name: a placed custom block is only
 * its note block state, and an emoji already on a sign or in a book is only its character. If the
 * numbers followed the order of a sorted list, adding {@code amber} would shift everything after it
 * by one, and every placed ruby block - every ruby emoji on every sign - would become the next
 * thing along. So:</p>
 * <ul>
 *   <li>an id keeps the number it was first given, for as long as the file says so;</li>
 *   <li>a new id takes the lowest free number, new ids taken in sorted order, so the result does not
 *       depend on the order files were read;</li>
 *   <li>an id no longer defined keeps its number, <em>retired</em>, since the world may still hold
 *       it. Deleting its line from the file is how an admin frees it.</li>
 * </ul>
 */
public final class StableAllocator {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * @param assignments every assignment to keep, retired ones included - what the file should hold
     * @param active      the number of every id currently defined that got one
     * @param problems    ids left without a number, and entries of the old file that were dropped
     * @param changed     whether {@code assignments} differs from what was read
     */
    public record Allocation(Map<String, Integer> assignments, Map<String, Integer> active,
                             List<String> problems, boolean changed) {
    }

    private final int first;
    private final int last;
    private final String noun;
    private final String freeingHint;

    /**
     * @param first       lowest number handed out
     * @param last        highest number handed out
     * @param noun        what a number is, for messages: "note block state"
     * @param freeingHint how to free retired numbers, for the message when they run out
     */
    public StableAllocator(int first, int last, String noun, String freeingHint) {
        this.first = first;
        this.last = last;
        this.noun = noun;
        this.freeingHint = freeingHint;
    }

    /**
     * @param previous what the assignment file held
     * @param ids      every id defined now
     */
    public Allocation allocate(Map<String, Integer> previous, Collection<String> ids) {
        return allocate(previous, ids, Set.of());
    }

    /**
     * @param previous what the assignment file held
     * @param ids      every id defined now
     * @param reserved numbers something else already uses - another pack's custom_model_data
     *                 numbers, its font's characters: never handed to a new id
     */
    public Allocation allocate(Map<String, Integer> previous, Collection<String> ids, Set<Integer> reserved) {
        return allocate(previous, ids, reserved, false);
    }

    /**
     * @param previous    what the assignment file held
     * @param ids         every id defined now
     * @param reserved    numbers something else already uses: never handed to a new id
     * @param moveClashes whether an id that holds a reserved number from before is given a free one
     *                    (and that reported): right where the other pack's use of the number wins
     *                    anyway, so keeping it would only keep it broken
     */
    public Allocation allocate(Map<String, Integer> previous, Collection<String> ids, Set<Integer> reserved,
                               boolean moveClashes) {
        List<String> problems = new ArrayList<>();
        Map<String, Integer> assignments = new TreeMap<>();
        Set<Integer> taken = new HashSet<>();

        // Old entries first, in id order, so a duplicate number is always resolved the same way.
        for (Map.Entry<String, Integer> entry : new TreeMap<>(previous).entrySet()) {
            Integer number = entry.getValue();
            if (number == null || number < first || number > last) {
                problems.add(noun + " " + number + " for " + entry.getKey() + " is outside " + first + "-" + last
                        + " - it will be given a new one");
            } else if (moveClashes && reserved.contains(number) && ids.contains(entry.getKey())) {
                problems.add(noun + " " + number + " of " + entry.getKey() + " is also used by a pack merged in"
                        + " - " + entry.getKey() + " is given a free one; what was made with the old one now"
                        + " shows that pack's");
            } else if (!taken.add(number)) {
                problems.add(noun + " " + number + " is assigned twice; " + entry.getKey()
                        + " will be given a new one");
            } else {
                assignments.put(entry.getKey(), number);
            }
        }

        int next = first;
        for (String id : new TreeSet<>(ids)) {
            if (assignments.containsKey(id)) {
                continue;
            }
            while (next <= last && (taken.contains(next) || reserved.contains(next))) {
                next++;
            }
            if (next > last) {
                long retired = assignments.keySet().stream().filter(key -> !ids.contains(key)).count();
                problems.add(id + ": no " + noun + " left - all " + (last - first + 1) + " are assigned ("
                        + retired + " " + freeingHint + ")");
                continue;
            }
            taken.add(next);
            assignments.put(id, next);
        }

        Map<String, Integer> active = new LinkedHashMap<>();
        for (String id : new TreeSet<>(ids)) {
            Integer number = assignments.get(id);
            if (number != null) {
                active.put(id, number);
            }
        }
        return new Allocation(assignments, active, problems, !assignments.equals(new HashMap<>(previous)));
    }

    /** The saved assignments; empty when the file does not exist yet. */
    public static Map<String, Integer> read(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (JsonParseException exception) {
            throw new IOException(file.getFileName() + " is not valid JSON: " + exception.getMessage(), exception);
        }
        if (!root.isJsonObject()) {
            throw new IOException(file.getFileName() + " should be a JSON object of id to number");
        }
        Map<String, Integer> assignments = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                assignments.put(entry.getKey(), value.getAsInt());
            } else {
                // Kept as an invalid number, so allocate() reports it rather than it vanishing here.
                assignments.put(entry.getKey(), Integer.MIN_VALUE);
            }
        }
        return assignments;
    }

    /** Writes the assignments sorted by id, replacing the file in one step. */
    public static void write(Path file, Map<String, Integer> assignments) throws IOException {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, Integer> entry : new TreeMap<>(assignments).entrySet()) {
            root.addProperty(entry.getKey(), entry.getValue());
        }
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temp = parent.resolve(file.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
