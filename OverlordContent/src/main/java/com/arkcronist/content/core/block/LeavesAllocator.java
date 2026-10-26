package com.arkcronist.content.core.block;

import com.arkcronist.content.core.allocation.StableAllocator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 5.5: decides which {@link LeavesState} slot each custom leaves block is drawn through, and keeps
 * that decision in {@code data/leaves_states.json} - for the reason {@link NoteBlockAllocator}
 * keeps its own: placed leaves are nothing but their state, so a slot handed to another block turns
 * every one of them into that block. Retired slots are kept the same way.
 */
public final class LeavesAllocator {

    private static final StableAllocator SLOTS = new StableAllocator(0, LeavesState.CAPACITY - 1, "leaves state",
            "to leaves no longer defined; remove those from leaves_states.json once none are left in a world");

    /**
     * @param assignments every assignment to keep, retired ones included - what the file should hold
     * @param active      the slot of every leaves block currently defined that got one
     * @param problems    leaves left without a slot, and entries of the old file that were dropped
     * @param changed     whether {@code assignments} differs from what was read
     */
    public record Allocation(Map<String, Integer> assignments, Map<String, LeavesState> active,
                             List<String> problems, boolean changed) {

        /** The vanilla leaves blocks drawing at least one custom one, retired ones included. */
        public Set<String> materials() {
            Set<String> materials = new HashSet<>();
            assignments.values().forEach(index -> materials.add(LeavesState.fromIndex(index).material()));
            return materials;
        }
    }

    private LeavesAllocator() {
    }

    /**
     * @param previous what the assignment file held
     * @param leaves   every custom leaves block defined now
     * @param reserved vanilla leaves blocks a merged pack draws itself: none of their slots is handed
     *                 out anew. Leaves holding one from before keep it - they are in the world in it
     */
    public static Allocation allocate(Map<String, Integer> previous, Collection<String> leaves, Set<String> reserved) {
        Set<Integer> taken = new HashSet<>();
        for (String material : reserved) {
            int at = LeavesState.POOL.indexOf(material);
            for (int slot = 0; at >= 0 && slot < LeavesState.SLOTS; slot++) {
                taken.add(at * LeavesState.SLOTS + slot);
            }
        }
        StableAllocator.Allocation allocation = SLOTS.allocate(previous, leaves, taken);
        Map<String, LeavesState> active = new LinkedHashMap<>();
        allocation.active().forEach((id, index) -> active.put(id, LeavesState.fromIndex(index)));
        return new Allocation(allocation.assignments(), active, allocation.problems(), allocation.changed());
    }

    public static Map<String, Integer> read(Path file) throws IOException {
        return StableAllocator.read(file);
    }

    public static void write(Path file, Map<String, Integer> assignments) throws IOException {
        StableAllocator.write(file, assignments);
    }
}
