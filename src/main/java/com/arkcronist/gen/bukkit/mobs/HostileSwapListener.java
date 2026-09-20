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
        Map<String, List<String>> table = tableFor(bukkitWorld);
        if (table.isEmpty()) {
            return;
        }

        // The biome gets asked first. A pack sorted by place - ice mobs, tree ents by wood - is
        // wrong under an entity key: a yeti keyed to VINDICATOR is a yeti in the desert. Where a
        // biome names nothing, this falls through to the entity table as it always did.
        List<String> candidates = BiomeMobs.candidates(plugin.arkConfig().biomeMobTable(),
                BiomeMobs.keyAt(bukkitWorld, event.getLocation()));
        if (candidates == null) {
            String vanilla = event.getEntityType().name().toUpperCase(Locale.ROOT);
            candidates = table.get(vanilla);
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
        Map<String, List<String>> table = plugin.roster()
                .swapTable(plugin.arkConfig(), MobRoster.habitatOf(world));
        return switch (world.getEnvironment()) {
            case NETHER -> plugin.arkConfig().netherApplies(world.getName()) ? table : Map.of();
            case THE_END -> plugin.arkConfig().endApplies(world.getName()) ? table : Map.of();
            default -> MobWorlds.applies(plugin, world) ? table : Map.of();
        };
    }
}
