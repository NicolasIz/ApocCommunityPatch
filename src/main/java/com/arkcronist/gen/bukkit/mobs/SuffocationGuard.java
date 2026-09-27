package com.arkcronist.gen.bukkit.mobs;

import com.arkcronist.gen.bukkit.ArkcronistPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
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
        if (MobFit.settle(entity)) {
            event.setCancelled(true);
            return;
        }
        if (ours) {
            event.setCancelled(true);
            entity.remove();
        }
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
