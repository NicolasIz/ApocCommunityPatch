package com.arkcronist.content.bukkit.sanity;

import com.arkcronist.content.bukkit.furniture.FurnitureService;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;

/**
 * The integrity check's ear for displays going away: one of this plugin's furniture displays
 * removed by anything but its chunk unloading - {@code /kill}, an entity cleaner, another plugin, a
 * rollback - has its piece looked at a couple of seconds later, so a piece left without its display
 * is drawn again in seconds rather than at the next round.
 *
 * <p>The plugin's own removals (breaking, parting a double chest) come through here too; the look
 * then finds nothing to do, and costs one chunk.</p>
 */
public final class IntegrityListener implements Listener {

    private final SanityChecker sanity;
    private final FurnitureService furniture;

    public IntegrityListener(SanityChecker sanity, FurnitureService furniture) {
        this.sanity = sanity;
        this.furniture = furniture;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveEvent event) {
        EntityRemoveEvent.Cause cause = event.getCause();
        if (cause == EntityRemoveEvent.Cause.UNLOAD || cause == EntityRemoveEvent.Cause.PLAYER_QUIT) {
            return;
        }
        Entity entity = event.getEntity();
        if (!(entity instanceof ItemDisplay) || furniture.furnitureId(entity) == null) {
            return;
        }
        Block anchor = furniture.anchorOf(entity);
        sanity.touched(entity.getWorld(), anchor.getX(), anchor.getZ());
    }
}
