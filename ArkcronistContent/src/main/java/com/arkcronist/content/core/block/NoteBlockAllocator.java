package com.arkcronist.content.core.block;

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
 * Decides which note block state each custom block is drawn through, and keeps that decision.
 *
 * <p>The assignment has to outlive the content that produced it. A placed custom block is nothing
 * but a note block state in the world's save; if states were handed out by position in a sorted
 * list, adding a block called {@code amber} would shift every block after it by one and every
 * placed ruby block would turn into a sapphire block overnight. So:</p>
 * <ul>
 *   <li>a block keeps the state it was first given, for as long as the assignment file says so;</li>
 *   <li>a new block takes the lowest free state, new blocks taken in id order, so the result does
 *       not depend on the order files were read;</li>
 *   <li>a block removed from the contents keeps its state, <em>retired</em>: blocks of it may still
 *       be standing in a world, and giving the state to something else would turn them into that.
 *       Deleting its line from the file is how an admin frees it, once none is left.</li>
 * </ul>
 */
public final class NoteBlockAllocator {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * @param assignments every assignment to keep, retired ones included - what the file should hold
     * @param active      the state of every block currently defined that got one
     * @param problems    blocks left without a state, and entries of the old file that were dropped
     * @param changed     whether {@code assignments} differs from what was read
     */
    public record Allocation(Map<String, Integer> assignments, Map<String, NoteBlockState> active,
                             List<String> problems, boolean changed) {
    }

    private NoteBlockAllocator() {
    }

    /**
     * @param previous what the assignment file held
     * @param blockIds every custom block defined now
     */
    public static Allocation allocate(Map<String, Integer> previous, Collection<String> blockIds) {
        List<String> problems = new ArrayList<>();
        Map<String, Integer> assignments = new TreeMap<>();
        Set<Integer> taken = new HashSet<>();

        // Old entries first, in id order, so a duplicate index is always resolved the same way.
        for (Map.Entry<String, Integer> entry : new TreeMap<>(previous).entrySet()) {
            Integer index = entry.getValue();
            if (index == null || index <= NoteBlockState.VANILLA.index() || index >= NoteBlockState.CAPACITY) {
                problems.add("note block state " + index + " for " + entry.getKey() + " is outside 1-"
                        + (NoteBlockState.CAPACITY - 1) + " - it will be given a new one");
            } else if (!taken.add(index)) {
                problems.add("note block state " + index + " is assigned twice; " + entry.getKey()
                        + " will be given a new one");
            } else {
                assignments.put(entry.getKey(), index);
            }
        }

        int next = 1;
        for (String id : new TreeSet<>(blockIds)) {
            if (assignments.containsKey(id)) {
                continue;
            }
            while (next < NoteBlockState.CAPACITY && taken.contains(next)) {
                next++;
            }
            if (next >= NoteBlockState.CAPACITY) {
                long retired = assignments.keySet().stream().filter(key -> !blockIds.contains(key)).count();
                problems.add(id + ": no note block state left - all " + (NoteBlockState.CAPACITY - 1)
                        + " are assigned (" + retired + " to blocks no longer defined; remove those from"
                        + " note_block_states.json once none are placed anywhere)");
                continue;
            }
            taken.add(next);
            assignments.put(id, next);
        }

        Map<String, NoteBlockState> active = new LinkedHashMap<>();
        for (String id : new TreeSet<>(blockIds)) {
            Integer index = assignments.get(id);
            if (index != null) {
                active.put(id, NoteBlockState.fromIndex(index));
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
            throw new IOException(file.getFileName() + " should be a JSON object of block id to state index");
        }
        Map<String, Integer> assignments = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                assignments.put(entry.getKey(), value.getAsInt());
            } else {
                // Kept as an invalid index, so allocate() reports it rather than it vanishing here.
                assignments.put(entry.getKey(), -1);
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
