package com.arkcronist.content.bukkit.block;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.core.block.NoteBlockState;
import com.arkcronist.content.core.definition.Placement;
import org.bukkit.block.data.BlockData;

/**
 * A custom block as the server uses it: the item it is placed from, the note block state it is
 * drawn through, and that state ready to set.
 *
 * @param data never modified: {@code Block#setBlockData} copies what it is given
 */
public record CustomBlock(CustomItem item, NoteBlockState state, BlockData data) {

    public String id() {
        return item.id();
    }

    public Placement.Block placement() {
        return (Placement.Block) item.placement();
    }
}
