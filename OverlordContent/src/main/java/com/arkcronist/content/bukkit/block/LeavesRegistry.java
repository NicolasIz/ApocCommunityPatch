package com.arkcronist.content.bukkit.block;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.core.block.LeavesState;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 5.5: every custom leaves block loaded, by id and by the vanilla leaves state it is drawn through -
 * which, as for note block blocks, is all a placed one is: reading the state says what stands there,
 * whatever put it there. Replaced whole by each rebuild and read without locks.
 */
public final class LeavesRegistry {

    /** What a block's state is, read as custom leaves. */
    public record Hit(CustomLeaves leaves, boolean natural) {
    }

    private record Snapshot(Map<String, CustomLeaves> byId, Map<Integer, CustomLeaves> byState, Set<Material> held) {
    }

    private volatile Snapshot snapshot = new Snapshot(Map.of(), Map.of(), Set.of());

    /**
     * Replaces everything. Main thread, once per rebuild.
     *
     * @param states    the slot each custom leaves block got
     * @param materials the vanilla leaves blocks drawing custom ones, retired ones included
     */
    public void replace(Collection<CustomItem> items, Map<String, LeavesState> states, Set<String> materials,
                        Server server) {
        Map<String, CustomLeaves> byId = new HashMap<>();
        Map<Integer, CustomLeaves> byState = new HashMap<>();
        for (CustomItem item : items) {
            LeavesState state = states.get(item.id());
            if (state == null || Material.matchMaterial(state.material()) == null) {
                continue;
            }
            CustomLeaves leaves = new CustomLeaves(item, state, server.createBlockData(state.asBlockData(true, false)),
                    server.createBlockData(state.asBlockData(false, false)));
            byId.put(item.id(), leaves);
            byState.put(state.index(), leaves);
        }
        Set<Material> held = EnumSet.noneOf(Material.class);
        for (String material : materials) {
            Material vanilla = Material.matchMaterial(material);
            if (vanilla != null) {
                held.add(vanilla);
            }
        }
        this.snapshot = new Snapshot(Map.copyOf(byId), Map.copyOf(byState), Set.copyOf(held));
    }

    public Optional<CustomLeaves> get(String id) {
        return Optional.ofNullable(snapshot.byId().get(id));
    }

    /** The custom leaves this block is, if it is some. */
    public Optional<Hit> identify(Block block) {
        if (!holds(block.getType())) {
            return Optional.empty();
        }
        return identify(block.getBlockData());
    }

    public Optional<Hit> identify(BlockData data) {
        if (!(data instanceof Leaves leaf) || !holds(data.getMaterial())) {
            return Optional.empty();
        }
        return LeavesState.read(data.getMaterial().getKey().getKey(), leaf.getDistance(), leaf.isPersistent())
                .flatMap(reading -> Optional.ofNullable(snapshot.byState().get(reading.state().index()))
                        .map(leaves -> new Hit(leaves, reading.natural())));
    }

    /**
     * Whether persistent leaves of this vanilla block are held as they are: it draws custom leaves -
     * or did, and placed ones may still stand.
     */
    public boolean holds(Material material) {
        return snapshot.held().contains(material);
    }

    /** The vanilla leaves blocks whose persistent leaves are held. */
    public Set<Material> held() {
        return snapshot.held();
    }

    /** The state to set custom leaves in, waterlogged as the block there was. */
    public BlockData state(CustomLeaves leaves, boolean natural, boolean waterlogged) {
        BlockData data = (natural ? leaves.natural() : leaves.placed()).clone();
        if (data instanceof Leaves leaf) {
            leaf.setWaterlogged(waterlogged);
        }
        return data;
    }

    public boolean isEmpty() {
        return snapshot.byId().isEmpty() && snapshot.held().isEmpty();
    }

    public int size() {
        return snapshot.byId().size();
    }
}
