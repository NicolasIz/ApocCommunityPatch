package com.arkcronist.content.bukkit.liquid;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.core.definition.Placement;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every loaded liquid, by id and by the two tripwire states it is drawn through. Swapped whole on
 * a rebuild; read from any thread - the liquids' own thread compares states with it.
 */
public final class LiquidRegistry {

    /**
     * A liquid on this server.
     *
     * @param item       its bucket
     * @param slot       which pair of tripwire states it has
     * @param sourceData the state a source is held in
     * @param flowData   the state flowing liquid is held in
     */
    public record Liquid(CustomItem item, Placement.Liquid definition, int slot, BlockData sourceData,
                         BlockData flowData) {

        public String id() {
            return item.id();
        }
    }

    /** What a block's state says: which liquid, and whether a source of it. */
    public record State(Liquid liquid, boolean source) {
    }

    private volatile Map<String, Liquid> byId = Map.of();
    private volatile Map<BlockData, State> byData = Map.of();

    public void replace(List<Liquid> liquids) {
        Map<String, Liquid> ids = new HashMap<>();
        Map<BlockData, State> data = new HashMap<>();
        for (Liquid liquid : liquids) {
            ids.put(liquid.id(), liquid);
            data.put(liquid.sourceData(), new State(liquid, true));
            data.put(liquid.flowData(), new State(liquid, false));
        }
        this.byId = Map.copyOf(ids);
        this.byData = Map.copyOf(data);
    }

    public Optional<Liquid> get(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** The liquid drawn by {@code data}, if it is one of the liquids' states. */
    public @Nullable State state(@Nullable BlockData data) {
        return data == null ? null : byData.get(data);
    }

    public Collection<Liquid> all() {
        return byId.values();
    }

    public boolean isEmpty() {
        return byId.isEmpty();
    }
}
