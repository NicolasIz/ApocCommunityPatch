package com.arkcronist.content.bukkit.block;

import com.arkcronist.content.core.block.BlockAxis;
import com.arkcronist.content.core.block.NoteBlockState;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Every custom block currently loaded, by id and by note block state.
 *
 * <p>The state lookup is the one that matters in the world: a placed custom block is nothing but a
 * note block state, so reading a block's state and looking it up here is how a block is recognised,
 * whatever put it there - a player, a schematic paste, a world copied from another server. Like the
 * item registry, it is replaced whole by each rebuild and read without locks.</p>
 */
public final class BlockRegistry {

    private record Snapshot(Map<String, CustomBlock> byId, Map<Integer, CustomBlock> byState,
                            Map<String, CustomBlock> byKey) {
    }

    private volatile Snapshot snapshot = new Snapshot(Map.of(), Map.of(), Map.of());

    /** Replaces everything. Main thread, once per rebuild. */
    public void replace(Collection<CustomBlock> blocks) {
        Map<String, CustomBlock> byId = new HashMap<>();
        Map<Integer, CustomBlock> byState = new HashMap<>();
        Map<String, CustomBlock> byKey = new HashMap<>();
        for (CustomBlock block : blocks) {
            if (block.axis() == BlockAxis.Y) {
                byId.put(block.id(), block);
            }
            byKey.put(block.axis().key(block.id()), block);
            byState.put(block.state().index(), block);
        }
        this.snapshot = new Snapshot(Map.copyOf(byId), Map.copyOf(byState), Map.copyOf(byKey));
    }

    /** The block upright: what its item places unless it is a log placed on its side. */
    public Optional<CustomBlock> get(String id) {
        return Optional.ofNullable(snapshot.byId().get(id));
    }

    /**
     * 5.5: the block lying {@code axis} - itself upright, and upright too when it does not turn, or
     * its turned state could not be given.
     */
    public Optional<CustomBlock> get(String id, BlockAxis axis) {
        CustomBlock turned = snapshot.byKey().get(axis.key(id));
        return turned != null ? Optional.of(turned) : get(id);
    }

    /** The custom block this block data draws, if it is one. */
    public Optional<CustomBlock> byBlockData(BlockData data) {
        if (data.getMaterial() != Material.NOTE_BLOCK) {
            return Optional.empty();
        }
        return NoteBlockState.parse(data.getAsString()).flatMap(this::byState);
    }

    public Optional<CustomBlock> byState(NoteBlockState state) {
        int index = state.index();
        // The vanilla state is never a custom block's, whatever an edited assignment file says.
        return index <= 0 ? Optional.empty() : Optional.ofNullable(snapshot.byState().get(index));
    }

    /** No custom blocks loaded: note blocks are then left entirely to vanilla. */
    public boolean isEmpty() {
        return snapshot.byId().isEmpty();
    }

    /** Blocks, each counted once however many ways it lies. */
    public int size() {
        return snapshot.byId().size();
    }
}
