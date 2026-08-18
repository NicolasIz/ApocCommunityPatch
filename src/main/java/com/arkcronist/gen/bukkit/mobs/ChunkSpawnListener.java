package com.arkcronist.gen.bukkit.mobs;

import com.arkcronist.gen.bukkit.ArkWorld;
import com.arkcronist.gen.bukkit.ArkcronistPlugin;
import com.arkcronist.gen.core.structure.MobSpawn;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Spawns queued structure mobs when their chunk first loads.
 *
 * <p>Only fires for freshly generated chunks, and marks the chunk in its persistent data afterwards,
 * so a fortress garrison is placed exactly once no matter how often the chunk is loaded and unloaded
 * afterwards.</p>
 */
public final class ChunkSpawnListener implements Listener {

    private final ArkcronistPlugin plugin;
    private final NamespacedKey populatedKey;
    private final NamespacedKey bossKey;
    private final NamespacedKey tierKey;

    public ChunkSpawnListener(ArkcronistPlugin plugin) {
        this.plugin = plugin;
        this.populatedKey = new NamespacedKey(plugin, "mobs_spawned");
        this.bossKey = new NamespacedKey(plugin, MinibossFactory.MINIBOSS_KEY);
        this.tierKey = new NamespacedKey(plugin, MinibossFactory.TIER_KEY);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        ArkWorld world = plugin.worlds().find(event.getWorld().getName());
        if (world == null) {
            return;
        }
        List<MobSpawn> spawns = world.mobQueue().drain(event.getChunk().getX(), event.getChunk().getZ());
        if (spawns.isEmpty()) {
            return;
        }
        if (event.getChunk().getPersistentDataContainer().has(populatedKey, PersistentDataType.BYTE)) {
            return;
        }
        event.getChunk().getPersistentDataContainer().set(populatedKey, PersistentDataType.BYTE, (byte) 1);

        // Spawning inside the load event itself can re-enter chunk loading; one tick later the chunk
        // is fully live and the entities land in a settled world.
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        World bukkitWorld = event.getWorld();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!bukkitWorld.isChunkLoaded(chunkX, chunkZ)) {
                return;
            }
            for (MobSpawn spawn : spawns) {
                try {
                    MinibossFactory.spawn(bukkitWorld, spawn, plugin.arkConfig(), bossKey, tierKey);
                } catch (RuntimeException exception) {
                    plugin.getLogger().warning("Could not spawn " + spawn.entityType()
                            + ": " + exception.getMessage());
                }
            }
        });
    }
}
