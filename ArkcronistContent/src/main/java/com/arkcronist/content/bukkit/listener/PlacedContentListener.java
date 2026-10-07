package com.arkcronist.content.bukkit.listener;

import com.arkcronist.content.bukkit.ArkContentPlugin;
import com.arkcronist.content.bukkit.block.CustomBlockService;
import com.arkcronist.content.bukkit.furniture.FurnitureService;
import com.arkcronist.content.core.storage.FurnitureTransformStore;
import com.arkcronist.content.core.storage.PlacedContentStore;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.List;
import java.util.logging.Logger;

/**
 * The world-level side of placed content: which worlds are held in memory, and what may move it.
 *
 * <p>A world's rows are read from the database when it loads and dropped from memory when it
 * unloads. Chunk unloads are deliberately not listened to: entries outlive their chunk, so a chunk
 * coming back costs no database read.</p>
 */
public final class PlacedContentListener implements Listener {

    private final ArkContentPlugin plugin;
    private final PlacedContentStore store;
    private final FurnitureTransformStore transforms;
    private final CustomBlockService blocks;
    private final FurnitureService furniture;
    private final Logger logger;

    public PlacedContentListener(ArkContentPlugin plugin, PlacedContentStore store, FurnitureTransformStore transforms,
                                 CustomBlockService blocks, FurnitureService furniture, Logger logger) {
        this.plugin = plugin;
        this.store = store;
        this.transforms = transforms;
        this.blocks = blocks;
        this.furniture = furniture;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        String name = event.getWorld().getName();
        store.loadWorld(event.getWorld().getUID()).whenComplete((rows, error) -> {
            if (error != null) {
                logger.warning("Could not read placed content for world " + name + ": " + error.getMessage());
            } else if (rows > 0) {
                logger.info("World " + name + ": " + rows + " custom block(s) and furniture loaded.");
            }
        });
        plugin.loadTransforms(event.getWorld().getUID());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        store.unloadWorld(event.getWorld().getUID());
        transforms.unloadWorld(event.getWorld().getUID());
    }

    /**
     * A piston would carry a note block state to another position - the record would stay behind -
     * and would crush a light-block support. Both are refused.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (touchesPlacedContent(event.getBlocks(), event.getDirection())
                || isPlacedContent(event.getBlock().getRelative(event.getDirection()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (touchesPlacedContent(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    /** Any block moved, or any position a moved block moves into. */
    private boolean touchesPlacedContent(List<Block> moved, BlockFace direction) {
        for (Block block : moved) {
            if (isPlacedContent(block) || isPlacedContent(block.getRelative(direction))) {
                return true;
            }
        }
        return false;
    }

    private boolean isPlacedContent(Block block) {
        return blocks.identify(block).isPresent() || furniture.claims(block);
    }
}
