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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 5.3: decides which waxed copper block each shaped custom block is drawn through, and keeps it.
 *
 * <p>The same rules as note block states ({@link NoteBlockAllocator}), for the same reason - a
 * placed slab is only a waxed copper slab in the world's save, and handing that base to another
 * slab turns every placed one into it:</p>
 * <ul>
 *   <li>a block keeps the base it was first given, while it keeps its shape;</li>
 *   <li>a new block takes the first free base of its shape, new blocks in id order;</li>
 *   <li>a block no longer defined keeps its base, retired, until its line is deleted from the file.</li>
 * </ul>
 * <p>The file is {@code data/copper_block_bases.json}, id to base block, so it reads as what it is.</p>
 */
public final class CopperBaseAllocator {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * @param assignments every assignment to keep, retired ones included - what the file should hold
     * @param active      the base of every block defined now that got one, by id
     * @param problems    blocks left without a base, and entries of the old file that were dropped
     * @param changed     whether {@code assignments} differs from what was read
     */
    public record Allocation(Map<String, String> assignments, Map<String, String> active, List<String> problems,
                             boolean changed) {
    }

    private CopperBaseAllocator() {
    }

    /**
     * @param previous           what the file held, id to base
     * @param blocks             every shaped block defined now, with its shape; cubes are ignored
     * @param reserved           bases a merged pack draws blocks of its own with: never handed to a new
     *                           block. One holding it from before keeps it - it is in the world as that
     * @param trialChamberCopper whether the bases trial chambers are built from may be handed out
     */
    public static Allocation allocate(Map<String, String> previous, Map<String, BlockShape> blocks,
                                      Set<String> reserved, boolean trialChamberCopper) {
        List<String> problems = new ArrayList<>();
        Map<String, String> assignments = new TreeMap<>();
        Set<String> taken = new HashSet<>();

        for (Map.Entry<String, String> entry : new TreeMap<>(previous).entrySet()) {
            String id = entry.getKey();
            String base = entry.getValue();
            BlockShape shape = blocks.get(id);
            if (base == null || BlockShape.ofBase(base).isEmpty()) {
                problems.add("copper_block_bases.json: '" + base + "' for " + id + " is not a block a custom block"
                        + " can be drawn through - dropped");
            } else if (shape != null && shape.copper() && !shape.bases().contains(base)) {
                problems.add(id + " was drawn through " + base + " and is now a " + shape.yamlName() + " - it is given"
                        + " a base of its new shape; what was placed before shows as a vanilla " + base + " again");
            } else if (shape != null && !shape.copper()) {
                problems.add(id + " was drawn through " + base + " and is now a note block - placed ones show as"
                        + " a vanilla " + base + " again");
            } else if (!taken.add(base)) {
                problems.add("copper_block_bases.json: " + base + " is assigned twice; " + id + " will be given"
                        + " another");
            } else {
                assignments.put(id, base);
            }
        }

        for (Map.Entry<String, BlockShape> entry : new TreeMap<>(blocks).entrySet()) {
            String id = entry.getKey();
            BlockShape shape = entry.getValue();
            if (!shape.copper() || assignments.containsKey(id)) {
                continue;
            }
            List<String> offered = trialChamberCopper ? shape.bases() : shape.outsideStructures();
            String free = offered.stream()
                    .filter(base -> !taken.contains(base) && !reserved.contains(base))
                    .findFirst()
                    .orElse(null);
            if (free == null) {
                long retired = assignments.entrySet().stream()
                        .filter(other -> !blocks.containsKey(other.getKey()) && shape.bases().contains(other.getValue()))
                        .count();
                problems.add(id + ": no " + shape.yamlName() + " block left to draw it through - " + offered.size()
                        + " can be custom " + shape.yamlName() + " blocks"
                        + (trialChamberCopper ? "" : " (" + shape.bases().size() + " with blocks.trial-chamber-copper:"
                        + " true in config.yml)")
                        + (retired == 0 ? "" : "; " + retired + " held by blocks no longer defined - delete their"
                        + " lines from copper_block_bases.json once none is placed")
                        + (shape == BlockShape.TRANSPARENT ? "; it is made a note block instead - whole, but not"
                        + " see-through" : "; it can be given but not placed"));
                continue;
            }
            taken.add(free);
            assignments.put(id, free);
        }

        Map<String, String> active = new LinkedHashMap<>();
        for (String id : new TreeMap<>(blocks).keySet()) {
            String base = assignments.get(id);
            if (base != null) {
                active.put(id, base);
            }
        }
        return new Allocation(assignments, active, problems, !assignments.equals(new HashMap<>(previous)));
    }

    /** The saved assignments; empty when the file does not exist yet. */
    public static Map<String, String> read(Path file) throws IOException {
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
            throw new IOException(file.getFileName() + " should be a JSON object of id to block");
        }
        Map<String, String> assignments = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            // Anything but a string is kept as nothing, so allocate() reports it rather than it vanishing.
            assignments.put(entry.getKey(), value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                    ? value.getAsString() : null);
        }
        return assignments;
    }

    /** Writes the assignments sorted by id, replacing the file in one step. */
    public static void write(Path file, Map<String, String> assignments) throws IOException {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, String> entry : new TreeMap<>(assignments).entrySet()) {
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
