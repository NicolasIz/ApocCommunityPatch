package com.arkcronist.content.bukkit.furniture;

import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.storage.BlockKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sitting on furniture.
 *
 * <p>A seat is an entity the player rides: an item display holding nothing, so it is invisible and
 * has no hitbox, spawned on the main thread - entities cannot be added from any other - when a
 * player right-clicks a chair, and removed again as soon as they stand up. It is never saved with
 * the chunk: a seat outlives neither its sitter nor a restart. The player takes the vanilla riding
 * pose, as on a horse or in a boat.</p>
 *
 * <p>Main thread only.</p>
 */
public final class SeatService {

    private final Plugin plugin;
    private final NamespacedKey seatTag;
    /** The seat on each support block, by world and position. */
    private final Map<UUID, Map<Long, UUID>> seats = new HashMap<>();

    public SeatService(Plugin plugin) {
        this.plugin = plugin;
        this.seatTag = new NamespacedKey(plugin, "seat");
    }

    /**
     * Sits {@code player} on the furniture standing on {@code support}.
     *
     * @param yaw the direction the chair faces, which the sitter faces too
     */
    public void sit(Player player, Block support, Placement.Seat seat, float yaw) {
        sit(player, support, support.getLocation().add(0.5, seat.height(), 0.5), yaw);
    }

    /**
     * Sits {@code player} at {@code sitting} - where their seat is to be, in the world - on behalf of
     * the furniture standing on {@code support}: the guillotine's stocks, a little behind its frame.
     */
    public void sit(Player player, Block support, Location sitting, float yaw) {
        if (player.isInsideVehicle() || player.isSneaking()) {
            return;
        }
        Entity existing = seatAt(support);
        if (existing != null && !existing.getPassengers().isEmpty()) {
            player.sendActionBar(Component.text("Someone is already sitting there", NamedTextColor.GRAY));
            return;
        }
        if (existing != null) {
            existing.remove();
        }

        // The seat entity goes at the height of the seat itself. Riding puts the sitter's feet 0.6
        // below it - a player's own attachment point - and in the riding pose that is how far the
        // underside of their thighs is above their feet: they rest on the seat. Up to 3.0.2 the
        // entity went 0.6 higher, which put the feet there and left the sitter floating above it.
        Location at = sitting.clone();
        at.setYaw(yaw);
        at.setPitch(0f);
        long anchor = BlockKey.pack(support.getX(), support.getY(), support.getZ());
        ItemDisplay entity = support.getWorld().spawn(at, ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setInvulnerable(true);
            display.setGravity(false);
            display.getPersistentDataContainer().set(seatTag, PersistentDataType.LONG, anchor);
        });
        seats.computeIfAbsent(support.getWorld().getUID(), ignored -> new HashMap<>()).put(anchor, entity.getUniqueId());

        // Facing the way the chair does; then aboard.
        Location facing = player.getLocation();
        facing.setYaw(yaw);
        player.setRotation(yaw, facing.getPitch());
        if (!entity.addPassenger(player)) {
            forget(entity);
            entity.remove();
        }
    }

    /**
     * {@code sitter} got off {@code vehicle}. If that was a seat, it goes, and the sitter is stood up
     * a tick later - see {@link #standUp} - since left where the seat was, they would be inside a
     * solid support.
     */
    public void dismounted(Entity sitter, Entity vehicle) {
        Long anchor = vehicle.getPersistentDataContainer().get(seatTag, PersistentDataType.LONG);
        if (anchor == null) {
            return;
        }
        forget(vehicle);
        World world = vehicle.getWorld();
        Location standUp = standUp(world.getBlockAt(BlockKey.x(anchor), BlockKey.y(anchor), BlockKey.z(anchor)),
                vehicle.getLocation().getYaw());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            vehicle.remove();
            if (sitter.isValid() && !sitter.isInsideVehicle()) {
                standUp.setYaw(sitter.getLocation().getYaw());
                standUp.setPitch(sitter.getLocation().getPitch());
                sitter.teleport(standUp);
            }
        });
    }

    /** The furniture on {@code support} is going: whoever sits on it stands up. */
    public void release(Block support) {
        Entity seat = seatAt(support);
        if (seat != null) {
            List<Entity> sitters = List.copyOf(seat.getPassengers());
            seat.eject();
            forget(seat);
            seat.remove();
            Location standUp = standUp(support, seat.getLocation().getYaw());
            for (Entity sitter : sitters) {
                sitter.teleport(standUp.clone().setDirection(sitter.getLocation().getDirection()));
            }
        }
    }

    /**
     * Where someone getting up from the furniture on {@code support} stands: on top of it, as from
     * a chair - unless the piece is taller than its seat, a throne or the guillotine's frame, and
     * the block above is part of it: then in front of it, where the seat faces, if there is room
     * there, or else on top of the whole piece.
     *
     * @param yaw the way the seat faces
     */
    static Location standUp(Block support, float yaw) {
        Block above = support.getRelative(0, 1, 0);
        if (above.isPassable() && above.getRelative(0, 1, 0).isPassable()) {
            return above.getLocation().add(0.5, 0, 0.5);
        }
        double radians = Math.toRadians(yaw);
        Block front = support.getRelative((int) Math.round(-Math.sin(radians)), 0, (int) Math.round(Math.cos(radians)));
        if (front.isPassable() && front.getRelative(0, 1, 0).isPassable()) {
            return front.getLocation().add(0.5, 0, 0.5);
        }
        Block top = above;
        for (int i = 0; i < 6 && !(top.isPassable() && top.getRelative(0, 1, 0).isPassable()); i++) {
            top = top.getRelative(0, 1, 0);
        }
        return top.getLocation().add(0.5, 0, 0.5);
    }

    /** On disable: nobody stays seated on an entity no plugin will clean up. */
    public void releaseAll() {
        for (Map.Entry<UUID, Map<Long, UUID>> world : seats.entrySet()) {
            for (UUID id : List.copyOf(world.getValue().values())) {
                Entity seat = plugin.getServer().getEntity(id);
                if (seat != null) {
                    seat.eject();
                    seat.remove();
                }
            }
        }
        seats.clear();
    }

    /** Who sits on the furniture standing on {@code support}, if anyone does. */
    public @Nullable Player occupant(Block support) {
        Entity seat = seatAt(support);
        if (seat == null) {
            return null;
        }
        for (Entity passenger : seat.getPassengers()) {
            if (passenger instanceof Player player && player.isValid()) {
                return player;
            }
        }
        return null;
    }

    public boolean isSeat(Entity entity) {
        return entity.getPersistentDataContainer().has(seatTag, PersistentDataType.LONG);
    }

    private @Nullable Entity seatAt(Block support) {
        Map<Long, UUID> world = seats.get(support.getWorld().getUID());
        UUID id = world == null ? null : world.get(BlockKey.pack(support.getX(), support.getY(), support.getZ()));
        Entity seat = id == null ? null : plugin.getServer().getEntity(id);
        if (id != null && (seat == null || !seat.isValid())) {
            world.remove(BlockKey.pack(support.getX(), support.getY(), support.getZ()));
            return null;
        }
        return seat;
    }

    private void forget(Entity seat) {
        Long anchor = seat.getPersistentDataContainer().get(seatTag, PersistentDataType.LONG);
        Map<Long, UUID> world = seats.get(seat.getWorld().getUID());
        if (anchor != null && world != null) {
            world.remove(anchor, seat.getUniqueId());
        }
    }
}
