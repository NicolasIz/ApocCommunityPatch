package com.arkcronist.gen.bukkit.mobs;

import com.arkcronist.gen.bukkit.ArkcronistPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * The last line against a mob of ours choking in a wall.
 *
 * <p>Everything upstream decides where a mob goes before it has its final shape, and
 * {@link MobFit#watch} looks again once it has. This is for whatever still gets through: a mob
 * knocked into a wall, one that grew later than the last check, a guard a player walled in. The
 * damage the game is about to deal is the one signal that cannot be wrong about whether the mob is
 * inside a block, so it is used as the trigger: the damage is cancelled and the mob is moved to the
 * nearest place it fits.</p>
 *
 * <p>Only mobs this plugin put in the world are removed when there is nowhere to move them - a
 * dead guard is worse than a missing one, and it floods the console on its way out. A MythicMobs
 * mob that something else placed is moved if it can be and otherwise left to the game.</p>
 */
public final class SuffocationGuard implements Listener {

    private final ArkcronistPlugin plugin;
    private final NamespacedKey[] marks;
    private final Map<UUID, Long> lastMove = new HashMap<>();

    public SuffocationGuard(ArkcronistPlugin plugin) {
        this.plugin = plugin;
        this.marks = new NamespacedKey[] {
                new NamespacedKey(plugin, SpawnedMobs.SWAP_MARK),
                new NamespacedKey(plugin, AmbientSpawnTask.MARK),
                new NamespacedKey(plugin, StructureGarrison.MARK),
                new NamespacedKey(plugin, ChunkSpawnListener.GUARD_MARK),
        };
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.SUFFOCATION) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof Player || !plugin.arkConfig().hostileMobsEnabled()) {
            return;
        }
        boolean ours = marked(entity);
        if (!ours && !(MythicBridge.available() && MobWorlds.appliesHere(plugin, entity.getWorld())
                && MythicBridge.nameOf(entity) != null)) {
            return;
        }
        // Our mobs never die in a wall, whatever happens next.
        event.setCancelled(true);
        if (MobFit.neverMoves(entity)) {
            return;
        }
        boolean fighting = entity instanceof Mob mob && mob.getTarget() != null;
        long now = System.currentTimeMillis();
        if (!shouldMove(fighting, lastMove.get(entity.getUniqueId()), now)) {
            return;
        }
        remember(entity.getUniqueId(), now);
        if (!MobFit.settle(entity) && ours) {
            entity.remove();
        }
    }

    /**
     * Whether to go looking for a better place for a mob that is choking.
     *
     * <p>Moving is for a mob that was put somewhere it never fitted. A big mob brushing a wall in
     * the middle of a fight is not that, and suffocation ticks twice a second: moving it on every
     * tick teleported it back and forth all fight long - it flickered, lost its target and never
     * finished an attack. So a mob with a target is only kept from taking the damage, and any mob is
     * moved at most once every {@link #MOVE_EVERY_MS}.</p>
     *
     * @param fighting whether it has a target right now
     * @param lastMove when it was last moved for this, or null
     */
    public static boolean shouldMove(boolean fighting, Long lastMove, long now) {
        if (fighting) {
            return false;
        }
        return lastMove == null || now - lastMove >= MOVE_EVERY_MS;
    }

    /** How long a mob is left alone after being moved out of a block. */
    public static final long MOVE_EVERY_MS = 10_000L;

    private void remember(UUID id, long now) {
        if (lastMove.size() > 2_000) {
            lastMove.values().removeIf(t -> now - t > MOVE_EVERY_MS);
        }
        lastMove.put(id, now);
    }

    private boolean marked(Entity entity) {
        for (NamespacedKey mark : marks) {
            if (entity.getPersistentDataContainer().has(mark, PersistentDataType.BYTE)) {
                return true;
            }
        }
        return false;
    }
}
