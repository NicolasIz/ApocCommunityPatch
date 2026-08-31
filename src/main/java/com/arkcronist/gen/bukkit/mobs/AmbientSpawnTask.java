package com.arkcronist.gen.bukkit.mobs;

import com.arkcronist.gen.bukkit.ArkWorld;
import com.arkcronist.gen.bukkit.ArkcronistPlugin;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Puts the custom mobs into the world by day as well as by night.
 *
 * <p>{@link HostileSwapListener} can only replace a spawn that the game was going to make anyway,
 * and by daylight on the surface the game makes none: a hostile mob needs darkness, so at noon there
 * is nothing to replace and the world is empty of them. That is the whole reason this class exists.
 * It is not a second way of doing the same job - it is the only way to have them out in the open in
 * the middle of the afternoon.</p>
 *
 * <p>It is built to be boring on a busy server, and every one of these is a deliberate limit:</p>
 *
 * <ul>
 *   <li><b>Only around players</b>, in a ring that starts far enough out that nobody watches one
 *       appear, and ends inside the distance the server keeps loaded.</li>
 *   <li><b>Never in an unloaded chunk.</b> Checked before anything else touches a block. Asking for
 *       a block in a chunk that is not loaded generates it on the spot, on the main thread, which is
 *       how a spawner turns into a server freeze.</li>
 *   <li><b>Capped per player.</b> Every mob it makes is marked, and it counts its own marks nearby
 *       before adding another, so a player standing still does not accumulate an army.</li>
 *   <li><b>Same standing rule as everything else</b>, through {@link SpawnSpot}: solid floor, room
 *       overhead, no lava, no water. A spawner that drops mobs into rock is the bug this repository
 *       spent yesterday removing.</li>
 * </ul>
 */
public final class AmbientSpawnTask implements Runnable {

    /** Marks a mob this task made, so it can count its own and nothing else's. */
    public static final String MARK = "arkcronist_ambient";

    private final ArkcronistPlugin plugin;
    private final NamespacedKey mark;

    public AmbientSpawnTask(ArkcronistPlugin plugin) {
        this.plugin = plugin;
        this.mark = new NamespacedKey(plugin, MARK);
    }

    @Override
    public void run() {
        if (!plugin.arkConfig().hostileMobsEnabled() || !plugin.arkConfig().ambientEnabled()
                || !MythicBridge.available()) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            try {
                around(player);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Ambient spawn around " + player.getName()
                        + " failed: " + exception.getMessage());
            }
        }
    }

    private void around(Player player) {
        World world = player.getWorld();
        if (world.getDifficulty() == Difficulty.PEACEFUL
                || player.getGameMode() == GameMode.SPECTATOR
                || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        List<String> pool = poolFor(world);
        if (pool.isEmpty()) {
            return;
        }
        int cap = plugin.arkConfig().ambientCap();
        if (mine(player) >= cap) {
            return;
        }

        int min = plugin.arkConfig().ambientMinDistance();
        int max = Math.max(min + 4, plugin.arkConfig().ambientMaxDistance());
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location eye = player.getLocation();

        for (int attempt = 0; attempt < plugin.arkConfig().ambientAttempts(); attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = min + random.nextDouble() * (max - min);
            int x = eye.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = eye.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }

            // Where to start looking in that column. On the surface that is one above the highest
            // block, which is where something would stand. Under a nether roof the highest block is
            // bedrock, so the player's own height is the only sensible guess and SpawnSpot searches
            // out from it.
            int from = world.getEnvironment() == World.Environment.NORMAL
                    ? world.getHighestBlockYAt(x, z) + 1
                    : eye.getBlockY();
            Location where = SpawnSpot.resolve(world, x, from, z, 2, false);
            if (where == null || where.distanceSquared(eye) < (double) min * min) {
                continue;
            }

            String chosen = pool.get(random.nextInt(pool.size()));
            Entity spawned = MythicBridge.spawn(chosen, where, 1);
            if (spawned == null) {
                // MythicMobs does not know the name, or refused. Trying the rest of the pool would
                // just be a slower way to find that out.
                return;
            }
            spawned.getPersistentDataContainer().set(mark, PersistentDataType.BYTE, (byte) 1);
            return;
        }
    }

    /**
     * Which list of names applies where this player is standing.
     *
     * <p>The Nether is not a world this plugin generates - it is the server's own - so it cannot be
     * selected the way an Arkcronist world is, by preset. It is selected by being the Nether.</p>
     */
    private List<String> poolFor(World world) {
        if (world.getEnvironment() == World.Environment.NETHER) {
            return plugin.arkConfig().netherApplies(world.getName())
                    ? plugin.arkConfig().ambientNether() : List.of();
        }
        ArkWorld ark = plugin.worlds().find(world.getName());
        if (ark == null || !plugin.arkConfig().hostileMobPresets().contains(ark.preset().name())) {
            return List.of();
        }
        return plugin.arkConfig().ambientOverworld();
    }

    /** How many of this task's own mobs are already keeping this player company. */
    private int mine(Player player) {
        int seen = 0;
        double reach = plugin.arkConfig().ambientMaxDistance() + 12.0;
        for (Entity entity : player.getNearbyEntities(reach, reach, reach)) {
            if (entity.getPersistentDataContainer().has(mark, PersistentDataType.BYTE)) {
                seen++;
            }
        }
        return seen;
    }
}
