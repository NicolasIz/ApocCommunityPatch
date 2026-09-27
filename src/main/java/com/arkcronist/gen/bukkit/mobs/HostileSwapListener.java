package com.arkcronist.gen.bukkit.mobs;

import com.arkcronist.gen.bukkit.ArkWorld;
import com.arkcronist.gen.bukkit.ArkcronistPlugin;
import com.arkcronist.gen.bukkit.mythic.MobRoster;
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

    /**
     * Spawns made in mid-air, never filled from a dimension's list: a ghast appears in the open
     * middle of the Nether, and the land mob standing in for it would appear there too and fall.
     */
    private static final java.util.Set<org.bukkit.entity.EntityType> AIRBORNE = java.util.EnumSet.of(
            org.bukkit.entity.EntityType.GHAST, org.bukkit.entity.EntityType.PHANTOM,
            org.bukkit.entity.EntityType.VEX);

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
        // Only something hostile is ever stood in for, and this guard is not belt-and-braces - it is
        // load-bearing, because CreatureSpawnEvent fires for every living thing the world makes.
        //
        // Two ways a cow used to become a goblin. The biome table below answers for a place rather
        // than for an entity type, so with a biome named in it every natural spawn there was
        // replaced - sheep, squid, bats, villagers. And auto-discovery keys the entity table on the
        // body a pack author chose: a mob built on a WOLF for its looks would have put a WOLF entry
        // in the table and taken every wolf in the world with it. Enemy is exactly the question
        // worth asking, and it covers the ones Monster does not - slimes, ghasts, phantoms, hoglins.
        if (!(event.getEntity() instanceof org.bukkit.entity.Enemy)) {
            return;
        }
        org.bukkit.World bukkitWorld = event.getLocation().getWorld();
        if (bukkitWorld == null) {
            return;
        }
        if (!applies(bukkitWorld)) {
            return;
        }
        Map<String, List<String>> table = tableFor(bukkitWorld);

        // The biome gets asked first. A pack sorted by place - ice mobs, tree ents by wood - is
        // wrong under an entity key: a yeti keyed to VINDICATOR is a yeti in the desert. Where a
        // biome names nothing, this falls through to the entity table as it always did.
        String biome = BiomeMobs.keyAt(bukkitWorld, event.getLocation());
        List<String> candidates = BiomeMobs.candidates(plugin.arkConfig().biomeMobTable(), biome);
        if (candidates == null) {
            // Then what discovery gave this biome from the mobs' own names, for a share of the
            // spawns - the rest keep the general mix.
            candidates = BiomeMobs.discovered(plugin.roster().biomeMobs(biome),
                    plugin.arkConfig().autoDiscoverBiomeShare(),
                    ThreadLocalRandom.current().nextDouble());
        }
        if (candidates == null) {
            String vanilla = event.getEntityType().name().toUpperCase(Locale.ROOT);
            candidates = table.get(vanilla);
        }
        if (candidates == null && !AIRBORNE.contains(event.getEntityType())
                && plugin.arkConfig().fillFromPool(MobRoster.habitatOf(bukkitWorld))) {
            // Nothing is keyed to this mob here, but the dimension has mobs of its own. In the
            // Nether that is the normal case rather than the exception: its packs build their
            // creatures on zombies and skeletons, which the Nether never spawns, while what it does
            // spawn - piglins, blazes, magma cubes, and whatever a pack like Incendium adds to its
            // biomes - has no entry. Without this the Nether's own mobs only ever came from the
            // daylight spawner.
            candidates = plugin.roster().ambientPool(plugin.arkConfig(),
                    MobRoster.habitatOf(bukkitWorld));
        }
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        // Not every spawn of a named type, unless that is what the config asks for. Left vanilla is
        // the same outcome as a name that could not be spawned, so nothing below has to know.
        if (!MobMix.replaces(plugin.arkConfig().replaceChance(),
                ThreadLocalRandom.current().nextDouble())) {
            return;
        }
        // Enough of ours here already. The game's own mob stays - or, if the config asks, not even
        // that - so a hillside cannot fill up with custom mobs however many spawns it gets.
        if (plugin.spawnedMobs().full(event.getLocation())) {
            if (plugin.arkConfig().spawnLimitCancelVanilla()) {
                event.setCancelled(true);
            }
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
        // The game proved that a husk fitted here, and then we put something else in its place.
        // Packs scale their mobs, so the replacement can be a head taller than the mob whose room
        // was measured, and a cave with two blocks of headroom buries it. Undoing costs nothing at
        // this point - the vanilla spawn has not been cancelled yet - so a replacement that cannot
        // stand here is dropped and the world keeps the mob it was going to have.
        if (!MobFit.settle(replacement)) {
            replacement.remove();
            return;
        }
        // As disposable as the zombie it replaced. A pack mob written never to despawn is left out
        // of the game's monster count, so without this every replacement frees a slot for the next
        // spawn and the night never stops adding them. See SpawnedMobs.
        SpawnedMobs.released(plugin, replacement, SpawnedMobs.SWAP_MARK,
                plugin.arkConfig().spawnedMobsDespawn());
        MobFit.watch(plugin, replacement);
        plugin.spawnedMobs().added(bukkitWorld);
        event.setCancelled(true);
    }

    /**
     * Which table applies in this world, or an empty one when none does.
     *
     * <p>Three rules, and they are selected differently on purpose. An overworld is ours only if this
     * generator made it and its preset is one the config named. The Nether and the End are nobody's -
     * they are the server's own vanilla worlds - so they cannot be recognised by preset and are
     * recognised by being the Nether and the End. That distinction is the whole reason the volcanic
     * skeletons can be kept down there and nowhere else.</p>
     *
     * <p>What discovery found for this side of the portal is merged in on top, and only ever into the
     * entity types the config says nothing about - see {@link
     * com.arkcronist.gen.bukkit.mythic.MobTables#swap}. An operator's own entry for ZOMBIE stays
     * exactly as written.</p>
     */
    private Map<String, List<String>> tableFor(org.bukkit.World world) {
        // Already merged with whatever discovery found, and cached there: this runs on every natural
        // spawn, so merging two maps here would allocate hundreds of times a second.
        return applies(world)
                ? plugin.roster().swapTable(plugin.arkConfig(), MobRoster.habitatOf(world))
                : Map.of();
    }

    /** Whether this world gets custom mobs at all. */
    private boolean applies(org.bukkit.World world) {
        return MobWorlds.appliesHere(plugin, world);
    }
}
