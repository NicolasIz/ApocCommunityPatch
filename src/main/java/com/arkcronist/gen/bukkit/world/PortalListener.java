package com.arkcronist.gen.bukkit.world;

import com.arkcronist.gen.bukkit.ArkChunkGenerator;
import com.arkcronist.gen.bukkit.ArkcronistPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Sends a portal in a generated world to that world's own Nether and End.
 *
 * <p>Without this the worlds exist and nothing reaches them: a portal lit in {@code insane} goes
 * wherever the server sends portals, which is the server's own Nether, and the one belonging to that
 * world is never visited.</p>
 *
 * <p><b>Only the destination world is changed, never the coordinates.</b> That is the whole trick and
 * it is worth being explicit about. By the time this event fires the game has already done the hard
 * part - divided by eight going in, multiplied by eight coming out, clamped the height to the
 * dimension - and that arithmetic is a property of the kind of dimension, not of which particular
 * Nether it is. Recomputing it here would be reimplementing something that is already right, and
 * getting the scale wrong is how a plugin drops somebody eight thousand blocks from where they meant
 * to be.</p>
 *
 * <p>Everything is left alone unless the world being left is one this generator made, or one of its
 * companions. A server running this beside ordinary worlds sees no change in those at all.</p>
 */
public final class PortalListener implements Listener {

    private final ArkcronistPlugin plugin;

    public PortalListener(ArkcronistPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        // The two portal causes and nothing else. This event also carries END_GATEWAY, and a gateway
        // moves you from one part of the End to another - the destination world is already right. A
        // first version of this treated anything that was not an end portal as a nether portal, which
        // turned every gateway jump inside the End into a trip out to the overworld.
        boolean end = event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL;
        if (!end && event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            return;
        }
        Location moved = route(event.getFrom().getWorld(), event.getTo(), end);
        if (moved != null) {
            event.setTo(moved);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        // Minecarts, boats with somebody's stuff in them, a pig that wandered in. If the player is
        // routed and their minecart is not, the two end up in different Nethers.
        org.bukkit.PortalType type = event.getPortalType();
        if (type != org.bukkit.PortalType.NETHER && type != org.bukkit.PortalType.ENDER) {
            return;
        }
        Location moved = route(event.getFrom().getWorld(), event.getTo(),
                type == org.bukkit.PortalType.ENDER);
        if (moved != null) {
            event.setTo(moved);
        }
    }

    /**
     * Where this portal should really come out, or null to leave the event alone.
     *
     * <p>Coming <em>out</em> of an End is the one case that does not keep its coordinates. The game
     * hands an exit portal the player's respawn point, and when that is in some other world - the
     * server's main one, because they never slept in this one - copying its coordinates into the
     * world they actually came from puts them at whatever happens to be at that spot. The world's
     * own spawn is the only sane answer there.</p>
     */
    private Location route(World from, Location to, boolean end) {
        if (to == null) {
            return null;
        }
        World target = destinationFor(from, end);
        if (target == null || target.equals(to.getWorld())) {
            return null;
        }
        if (end && from != null && from.getEnvironment() == World.Environment.THE_END) {
            return target.getSpawnLocation();
        }
        return moveTo(target, to);
    }

    /**
     * Where a portal in this world should lead, or null to leave the event exactly as it is.
     *
     * <p>Three cases, and the third is the one that is easy to forget: leaving a companion has to
     * come back to the world it belongs to, or a player walks into their own Nether and out into
     * somebody else's overworld.</p>
     */
    private World destinationFor(World from, boolean end) {
        if (from == null || !plugin.arkConfig().ownDimensions() || !plugin.arkConfig().linkPortals()) {
            return null;
        }
        String netherSuffix = plugin.arkConfig().netherSuffix();
        String endSuffix = plugin.arkConfig().endSuffix();

        // Leaving a world this generator made: go to its own companion.
        if (ours(from)) {
            if (end && plugin.arkConfig().ownEnd()) {
                return plugin.getServer().getWorld(DimensionLinks.endOf(from.getName(), endSuffix));
            }
            if (!end && plugin.arkConfig().ownNether()) {
                return plugin.getServer().getWorld(DimensionLinks.netherOf(from.getName(), netherSuffix));
            }
            return null;
        }

        // Leaving a companion: back to the world it belongs to, and only if that world really is one
        // of ours. A server whose ordinary nether happens to be called world_nether is not touched.
        String base = DimensionLinks.baseOf(from.getName(), netherSuffix, endSuffix);
        if (base == null) {
            return null;
        }
        World home = plugin.getServer().getWorld(base);
        return home != null && ours(home) ? home : null;
    }

    /** Whether this generator made this world. */
    private boolean ours(World world) {
        return world.getGenerator() instanceof ArkChunkGenerator;
    }

    /** The same spot, in another world, with the height kept inside what that world has. */
    private static Location moveTo(World world, Location original) {
        Location moved = original.clone();
        moved.setWorld(world);
        double lowest = world.getMinHeight() + 1;
        double highest = world.getMaxHeight() - 2;
        if (moved.getY() < lowest) {
            moved.setY(lowest);
        } else if (moved.getY() > highest) {
            moved.setY(highest);
        }
        return moved;
    }
}
