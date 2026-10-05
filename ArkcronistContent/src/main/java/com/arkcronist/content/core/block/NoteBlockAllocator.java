package com.arkcronist.content.core.block;

import com.arkcronist.content.core.allocation.StableAllocator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private static final StableAllocator STATES = new StableAllocator(NoteBlockState.VANILLA.index() + 1,
            NoteBlockState.CAPACITY - 1, "note block state",
            "to blocks no longer defined; remove those from note_block_states.json once none are placed anywhere");

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
        return allocate(previous, blockIds, java.util.Set.of());
    }

    /**
     * @param reserved states a merged pack draws as blocks of its own: never given to a new block. A
     *                 block holding one from before keeps it - it is in the world in that state
     */
    public static Allocation allocate(Map<String, Integer> previous, Collection<String> blockIds,
                                      java.util.Set<Integer> reserved) {
        StableAllocator.Allocation allocation = STATES.allocate(previous, blockIds, reserved);
        Map<String, NoteBlockState> active = new LinkedHashMap<>();
        allocation.active().forEach((id, index) -> active.put(id, NoteBlockState.fromIndex(index)));
        return new Allocation(allocation.assignments(), active, allocation.problems(), allocation.changed());
    }

    /** The saved assignments; empty when the file does not exist yet. */
    public static Map<String, Integer> read(Path file) throws IOException {
        return StableAllocator.read(file);
    }

    /** Writes the assignments sorted by id, replacing the file in one step. */
    public static void write(Path file, Map<String, Integer> assignments) throws IOException {
        StableAllocator.write(file, assignments);
    }
}
