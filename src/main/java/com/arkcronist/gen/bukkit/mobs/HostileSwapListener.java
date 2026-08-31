package com.arkcronist.gen.bukkit.mobs;

import com.arkcronist.gen.bukkit.ArkWorld;
import com.arkcronist.gen.bukkit.ArkcronistPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Swaps the world's hostile mobs for MythicMobs ones as they spawn.
 *
 * <p>Only in worlds this generator made, only on the presets the config names, and only for the
 * spawn reasons it names. A world on another preset, another generator's world, or a mob arriving
 * from an egg or a breeding pair is left alone entirely.</p>
 *
 * <p><b>The order matters and it is the whole safety of this class.</b> The replacement is spawned
 * <em>first</em> and the vanilla mob is cancelled only once the replacement is really standing
 * there. Cancelling first would be simpler and would be wrong: a misspelt name in the config, a pack
 * that failed to load, a MythicMobs that is not installed - each of those would silently empty the
 * world of hostile mobs instead of leaving it as it was. This way the worst case is that nothing
 * changes.</p>
 *
 * <p>Re-entrancy is guarded because the replacement is itself a mob: MythicMobs builds its goblins
 * on a husk and its archers on a skeleton, so spawning one fires this event again. The guard is a
 * plain field rather than anything thread-aware, which is correct here - Bukkit events are the main
 * thread and nowhere else.</p>
 */
public final class HostileSwapListener implements Listener {

    private final ArkcronistPlugin plugin;
    private boolean swapping;

    public HostileSwapListener(ArkcronistPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (swapping || !plugin.arkConfig().hostileMobsEnabled() || !MythicBridge.available()) {
            return;
        }
        if (!plugin.arkConfig().hostileMobReasons().contains(event.getSpawnReason().name())) {
            return;
        }
        org.bukkit.World bukkitWorld = event.getLocation().getWorld();
        if (bukkitWorld == null) {
            return;
        }
        Map<String, List<String>> table = tableFor(bukkitWorld);
        if (table.isEmpty()) {
            return;
        }

        String vanilla = event.getEntityType().name().toUpperCase(Locale.ROOT);
        List<String> candidates = table.get(vanilla);
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        String chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));

        // Stand the replacement where the vanilla mob was about to stand, keeping the facing so a
        // mob that spawned looking down a corridor still is.
        Location where = event.getLocation();
        Entity replacement;
        swapping = true;
        try {
            replacement = MythicBridge.spawn(chosen, where, 1);
        } finally {
            swapping = false;
        }
        if (replacement == null) {
            // Could not be made. Leave the vanilla mob exactly as it was rather than leaving a hole.
            return;
        }
        if (replacement instanceof LivingEntity living && !living.isValid()) {
            return;
        }
        event.setCancelled(true);
    }

    /**
     * Which table applies in this world, or an empty one when none does.
     *
     * <p>Two rules, and they are selected differently on purpose. An overworld is ours only if this
     * generator made it and its preset is one the config named. The Nether is nobody's - it is the
     * server's own vanilla world - so it cannot be recognised by preset and is recognised by being
     * the Nether. That distinction is the whole reason the volcanic skeletons can be kept down there
     * and nowhere else.</p>
     */
    private Map<String, List<String>> tableFor(org.bukkit.World world) {
        if (world.getEnvironment() == org.bukkit.World.Environment.NETHER) {
            return plugin.arkConfig().netherApplies(world.getName())
                    ? plugin.arkConfig().netherMobTable() : Map.of();
        }
        ArkWorld ark = plugin.worlds().find(world.getName());
        if (ark == null || !plugin.arkConfig().hostileMobPresets().contains(ark.preset().name())) {
            return Map.of();
        }
        return plugin.arkConfig().hostileMobTable();
    }
}
