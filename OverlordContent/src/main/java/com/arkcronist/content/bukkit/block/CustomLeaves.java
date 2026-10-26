package com.arkcronist.content.bukkit.block;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.core.block.LeavesState;
import com.arkcronist.content.core.definition.Placement;
import org.bukkit.block.data.BlockData;

/**
 * 5.5: custom leaves as the server uses them: the item they are placed from, the slot of vanilla
 * leaves states they are drawn through, and those states ready to set.
 *
 * @param natural the state of leaves a tree grew, not waterlogged: they fall when no log holds them
 * @param placed  the state of leaves a player placed, not waterlogged: they stay
 */
public record CustomLeaves(CustomItem item, LeavesState state, BlockData natural, BlockData placed) {

    public String id() {
        return item.id();
    }

    public Placement.Block placement() {
        return (Placement.Block) item.placement();
    }

    public Placement.Leaves leaves() {
        return placement().leaves();
    }
}
