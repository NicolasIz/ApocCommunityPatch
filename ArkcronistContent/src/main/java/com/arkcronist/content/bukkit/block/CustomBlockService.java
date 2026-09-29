package com.arkcronist.content.bukkit.block;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.core.block.NoteBlockState;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.storage.PlacedContent;
import com.arkcronist.content.core.storage.PlacedContentStore;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * What the server does with custom blocks: recognise one, put one down, take one away.
 *
 * <p>A block is recognised by its note block state - the state is what players see, so it is also
 * what decides - and the {@link PlacedContentStore} is kept in step with every block this class
 * places or removes. Main thread only: it reads and changes the world.</p>
 */
public final class CustomBlockService {

    private final BlockRegistry blocks;
    private final ItemFactory items;
    private final PlacedContentStore store;
    private final BlockData vanillaState;

    public CustomBlockService(Server server, BlockRegistry blocks, ItemFactory items, PlacedContentStore store) {
        this.blocks = blocks;
        this.items = items;
        this.store = store;
        this.vanillaState = server.createBlockData(NoteBlockState.VANILLA.asBlockData());
    }

    /**
     * True once at least one custom block is loaded. Until then note blocks are vanilla in every
     * way: no state is pinned, no tuning refused, no physics cancelled.
     */
    public boolean active() {
        return !blocks.isEmpty();
    }

    public Optional<CustomBlock> identify(Block block) {
        if (block.getType() != Material.NOTE_BLOCK || blocks.isEmpty()) {
            return Optional.empty();
        }
        return blocks.byBlockData(block.getBlockData());
    }

    /** The custom block an item places, if it is a custom block's item and that block got a state. */
    public Optional<CustomBlock> forItem(CustomItem item) {
        return item.placement() instanceof Placement.Block ? blocks.get(item.id()) : Optional.empty();
    }

    /** Turns a note block just placed into the custom block, and records it. */
    public void place(Block block, CustomBlock custom) {
        block.setBlockData(custom.data(), false);
        store.add(new PlacedContent(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ(),
                custom.id(), PlacedContent.Kind.BLOCK));
    }

    /**
     * Holds a vanilla note block in the one state no custom block uses. Otherwise the instrument
     * picked from the block underneath - basedrum on stone, snare on sand - could land it in a state
     * that draws as a custom block.
     */
    public void pinVanilla(Block block) {
        block.setBlockData(vanillaState, false);
    }

    /**
     * The block is being broken or destroyed: forget it and, when asked, drop its item in place of
     * the note block vanilla would have dropped. Does not change the block itself.
     */
    public void forget(Block block, CustomBlock custom, boolean dropItem) {
        store.remove(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        if (dropItem && custom.placement().dropSelf()) {
            block.getWorld().dropItemNaturally(centre(block), items.create(custom.item(), 1));
        }
    }

    /**
     * Sends a note block's real state to the players near it.
     *
     * <p>A client predicts shape updates on its own: place a block under a note block and it
     * recomputes the instrument locally, which here means drawing a different custom block. The
     * server has refused that change, so it has nothing new to send; this sends it anyway.</p>
     */
    public void resync(Block block) {
        Location location = block.getLocation();
        BlockData real = block.getBlockData();
        for (Player player : block.getWorld().getNearbyPlayers(centre(block), 16)) {
            player.sendBlockChange(location, real);
        }
    }

    private static Location centre(Block block) {
        return block.getLocation().add(0.5, 0.5, 0.5);
    }
}
