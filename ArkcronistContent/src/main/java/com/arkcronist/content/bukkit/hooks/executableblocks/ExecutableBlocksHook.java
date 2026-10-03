package com.arkcronist.content.bukkit.hooks.executableblocks;

import com.ssomar.score.api.executableblocks.config.placed.ExecutableBlocksPlacedManagerInterface;
import com.ssomar.score.api.executableblocks.events.ExecutableBlockPlaceEvent;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.function.Predicate;

/**
 * Living side by side with ExecutableBlocks, whose placed blocks are vanilla blocks with its own
 * records - as this plugin's are:
 *
 * <ul>
 *   <li>a block ExecutableBlocks has placed is never taken for one of this plugin's custom blocks,
 *       even when it is a note block whose state happens to be one a custom block uses - it keeps
 *       its own drops and behaviour;</li>
 *   <li>ExecutableBlocks may not place a block - from an item, or from a command such as
 *       {@code SETEXECUTABLEBLOCK} - where a custom block, furniture or a crop of this plugin
 *       already stands: its record would be lost under the other's.</li>
 * </ul>
 *
 * <p>ExecutableBlocks hands out its placed-blocks manager as its own class, which only exists with
 * ExecutableBlocks installed; it is fetched once, by name, and used through SCore's public
 * interface.</p>
 */
public final class ExecutableBlocksHook implements Listener {

    private final ExecutableBlocksPlacedManagerInterface placed;
    private final Predicate<Block> ours;

    /**
     * @param ours whether this plugin has content at a block
     */
    public ExecutableBlocksHook(Predicate<Block> ours) throws ReflectiveOperationException {
        Object manager = Class.forName("com.ssomar.score.api.executableblocks.ExecutableBlocksAPI")
                .getMethod("getExecutableBlocksPlacedManager").invoke(null);
        this.placed = (ExecutableBlocksPlacedManagerInterface) manager;
        this.ours = ours;
    }

    /** Whether ExecutableBlocks has placed a block of its own here. */
    public boolean claims(Block block) {
        return placed.getExecutableBlockPlaced(block).isPresent();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(ExecutableBlockPlaceEvent event) {
        Location at = event.getExecutableBlockPlaced().getLocation();
        if (at != null && at.getWorld() != null && ours.test(at.getBlock())) {
            event.setCancelled(true);
        }
    }
}
