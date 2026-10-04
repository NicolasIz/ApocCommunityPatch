package com.arkcronist.content.bukkit.hooks;

import com.arkcronist.content.bukkit.block.BlockRegistry;
import com.arkcronist.content.bukkit.block.CustomBlock;
import com.arkcronist.content.bukkit.block.CustomBlockService;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * What the item-sharing hooks ask of this plugin, in the words every one of them uses: an id to a
 * stack, a stack to an id, the ids there are; and for the hooks that place blocks, the same for
 * blocks.
 *
 * <p>The item and block bridges of other plugins - eco's lookups, nightcore's adapters, Mimic,
 * ItemBridge, WorldEdit, BetonQuest, DeluxeMenus - are each a few lines over this, so they all
 * agree on what an id is: {@code namespace:id}, or a bare id that only one namespace uses.</p>
 *
 * <p>A plain class of this plugin's: hooks name it, it names nothing of theirs.</p>
 */
public final class ContentAccess {

    private final ItemRegistry items;
    private final ItemFactory factory;
    private final BlockRegistry blocks;
    /** Made after the hooks start; asked for only when a hook places or reads a block. */
    private final Supplier<CustomBlockService> blockService;

    public ContentAccess(ItemRegistry items, ItemFactory factory, BlockRegistry blocks,
                         Supplier<CustomBlockService> blockService) {
        this.items = items;
        this.factory = factory;
        this.blocks = blocks;
        this.blockService = blockService;
    }

    /** A new stack of the item, or empty when no item has that id. */
    public Optional<ItemStack> item(String id, int amount) {
        return items.find(id).map(item -> factory.create(item, Math.max(1, amount)));
    }

    public boolean exists(String id) {
        return items.find(id).isPresent();
    }

    /** The full id ({@code namespace:id}) of one of this plugin's items, or empty for any other stack. */
    public Optional<String> id(@Nullable ItemStack stack) {
        return factory.identify(stack).map(CustomItem::id);
    }

    /** Every item id, sorted. */
    public List<String> ids() {
        return items.ids();
    }

    /** Every custom block id, sorted. */
    public List<String> blockIds() {
        return items.all().stream()
                .map(CustomItem::id)
                .filter(id -> blocks.get(id).isPresent())
                .sorted()
                .toList();
    }

    /** The state a custom block is drawn with, for tools that set block states themselves. */
    public Optional<BlockData> blockData(String id) {
        return items.find(id).flatMap(item -> blocks.get(item.id())).map(block -> block.data().clone());
    }

    /** The custom block drawn by this state, if it is one. */
    public Optional<String> blockIdOf(BlockData data) {
        return blocks.byBlockData(data).map(CustomBlock::id);
    }

    /** Forgets the record of a custom block at {@code block}, for a tool that replaced it itself. */
    public void forgetBlock(Block block) {
        CustomBlockService service = blockService.get();
        if (service != null) {
            service.forgetAt(block);
        }
    }

    /** The custom block standing at {@code block}, by id. */
    public Optional<String> blockAt(Block block) {
        CustomBlockService service = blockService.get();
        return service == null ? Optional.empty() : service.identify(block).map(CustomBlock::id);
    }

    /**
     * Puts the custom block there, recorded as if a player had placed it. Main thread.
     *
     * @return false, and nothing changed, when no custom block has that id
     */
    public boolean setBlock(Block block, String id) {
        CustomBlockService service = blockService.get();
        Optional<CustomBlock> custom = items.find(id).flatMap(item -> blocks.get(item.id()));
        if (service == null || custom.isEmpty()) {
            return false;
        }
        Optional<CustomBlock> there = service.identify(block);
        there.ifPresent(old -> service.forget(block, old, false));
        service.place(block, custom.get());
        return true;
    }

    /**
     * Takes away the custom block there, leaving air, without dropping it. Main thread.
     *
     * @return false, and nothing changed, when it is not one of this plugin's blocks
     */
    public boolean removeBlock(Block block) {
        CustomBlockService service = blockService.get();
        Optional<CustomBlock> there = service == null ? Optional.empty() : service.identify(block);
        if (there.isEmpty()) {
            return false;
        }
        service.forget(block, there.get(), false);
        block.setType(Material.AIR, false);
        return true;
    }
}
