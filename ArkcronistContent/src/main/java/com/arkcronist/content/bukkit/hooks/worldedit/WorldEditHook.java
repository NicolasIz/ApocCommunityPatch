package com.arkcronist.content.bukkit.hooks.worldedit;

import com.arkcronist.content.bukkit.hooks.ContentAccess;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.internal.registry.InputParser;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * WorldEdit: custom blocks wherever WorldEdit takes a block - {@code //set}, {@code //replace},
 * patterns, masks, brushes - by their id:
 *
 * <pre>
 * //set demo:ruby_block
 * //replace stone 10%demo:ruby_block,90%stone
 * </pre>
 *
 * <p>A custom block is a note block in its own state, so what WorldEdit is handed is that state,
 * and WorldEdit places, copies, rotates and undoes it like any block. The parser is asked before
 * WorldEdit's own and answers only for this plugin's ids, so {@code minecraft:} blocks and other
 * plugins' are untouched.</p>
 *
 * <p>WorldEdit's side effects then update each block it set as vanilla would, and a vanilla note
 * block re-reads its instrument and whether it is powered - turning a custom block back into a
 * plain one. So every custom block an edit sets is noted on its way to the world and, a tick later,
 * put back in its state, without physics, and recorded as one a player placed; a custom block an
 * edit replaces (an undo, a {@code //set air}) is forgotten.</p>
 */
public final class WorldEditHook {

    private WorldEditHook() {
    }

    /** Adds the custom blocks to WorldEdit's block parser, and keeps the ones it sets. */
    public static Boolean register(ContentAccess content, Plugin plugin) {
        WorldEdit worldEdit = WorldEdit.getInstance();
        worldEdit.getBlockFactory().register(new CustomBlockParser(worldEdit, content));
        worldEdit.getEventBus().register(new Settler(content, plugin));
        return Boolean.TRUE;
    }

    private static final class CustomBlockParser extends InputParser<BaseBlock> {

        private final ContentAccess content;

        CustomBlockParser(WorldEdit worldEdit, ContentAccess content) {
            super(worldEdit);
            this.content = content;
        }

        @Override
        public @Nullable BaseBlock parseFromInput(String input, ParserContext context) throws InputParseException {
            // Only a namespaced id: a bare "ruby_block" could be a vanilla name to WorldEdit.
            String id = input.trim().toLowerCase(Locale.ROOT);
            if (id.indexOf(':') < 0 || id.startsWith("minecraft:")) {
                return null;
            }
            Optional<BlockData> data = content.blockData(id);
            return data.map(state -> BukkitAdapter.adapt(state).toBaseBlock()).orElse(null);
        }

        @Override
        public Stream<String> getSuggestions(String input, ParserContext context) {
            String typed = input.toLowerCase(Locale.ROOT);
            return content.blockIds().stream().filter(id -> id.startsWith(typed));
        }
    }

    /** Notes the custom blocks each edit sets or replaces, and settles them a tick later. */
    public static final class Settler {

        private record Change(World world, int x, int y, int z, @Nullable String id) {
        }

        private final ContentAccess content;
        private final Plugin plugin;
        private final Queue<Change> changes = new ConcurrentLinkedQueue<>();
        private final AtomicBoolean scheduled = new AtomicBoolean();

        Settler(ContentAccess content, Plugin plugin) {
            this.content = content;
            this.plugin = plugin;
        }

        @Subscribe
        public void onEditSession(EditSessionEvent event) {
            if (event.getStage() != EditSession.Stage.BEFORE_CHANGE || event.getWorld() == null) {
                return;
            }
            World world = BukkitAdapter.adapt(event.getWorld());
            event.setExtent(new Noting(event.getExtent(), world));
        }

        private final class Noting extends AbstractDelegateExtent {

            private final World world;

            Noting(Extent extent, World world) {
                super(extent);
                this.world = world;
            }

            @Override
            public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 position, T block)
                    throws WorldEditException {
                // What was there, only when it could have been a custom block.
                boolean wasNoteBlock = getBlock(position).getBlockType() == BlockTypes.NOTE_BLOCK;
                boolean changed = super.setBlock(position, block);
                String id = block.getBlockType() == BlockTypes.NOTE_BLOCK
                        ? content.blockIdOf(BukkitAdapter.adapt(block)).orElse(null) : null;
                if (id != null || wasNoteBlock) {
                    changes.add(new Change(world, position.x(), position.y(), position.z(), id));
                    if (scheduled.compareAndSet(false, true)) {
                        plugin.getServer().getScheduler().runTask(plugin, this::settle);
                    }
                }
                return changed;
            }

            private void settle() {
                scheduled.set(false);
                for (Change change = changes.poll(); change != null; change = changes.poll()) {
                    Block block = change.world().getBlockAt(change.x(), change.y(), change.z());
                    if (change.id() != null && block.getType() == Material.NOTE_BLOCK) {
                        content.setBlock(block, change.id());
                    } else if (change.id() == null && content.blockAt(block).isEmpty()) {
                        content.forgetBlock(block);
                    }
                }
            }
        }
    }
}
