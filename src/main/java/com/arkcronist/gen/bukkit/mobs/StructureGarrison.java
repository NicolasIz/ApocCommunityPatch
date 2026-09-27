package com.arkcronist.gen.bukkit.mobs;

import com.arkcronist.gen.bukkit.ArkcronistPlugin;
import com.arkcronist.gen.bukkit.config.ArkConfig;
import com.arkcronist.gen.bukkit.mythic.MobClassifier.Habitat;
import com.arkcronist.gen.bukkit.mythic.MobRoster;
import com.arkcronist.gen.bukkit.mythic.StructureThemes;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.StructurePiece;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Puts the custom mobs into the structures a datapack generates.
 *
 * <h2>Why a garrison and not the spawn swap</h2>
 *
 * <p>The swap can only replace a spawn the game makes, and inside a datapack's buildings the game
 * often makes none. Incendium's Forbidden Castle is the plainest case: its file sets
 * {@code "spawn_overrides": {"monster": {"spawns": []}}}, which means no monster ever spawns
 * naturally inside it. Whatever lives there has to be put there. So when a chunk that holds part of
 * such a structure is generated, some of its rooms get a guard - once, like this generator's own
 * garrisons, and never again however often the chunk loads.</p>
 *
 * <h2>Which structures</h2>
 *
 * <p>Every structure the server reports for the chunk, whichever pack it came from - Incendium,
 * Nullscape, Just Another Structure Pack, Grim Kingdoms, Ominous Towers - without any of them being
 * named anywhere. The game's own ({@code minecraft:}) are left out unless asked for, and so are the
 * structures whose names say people live there: see {@link StructureThemes#PEACEFUL}.</p>
 *
 * <h2>Which mobs</h2>
 *
 * <p>Chosen by the structure's name against the mobs' names, with {@link StructureThemes}: the
 * knights to the castles, the dead to the crypts. A structure named in
 * {@code hostile-mobs.datapack-structures.table} gets exactly the list written there.</p>
 *
 * <p>The spot is the structure's own: a piece of it (a room, a corridor, a tower) is chosen, a column
 * inside the part of that piece that lies in this chunk, and the floor is found with the same
 * {@link SpawnSpot} search everything else uses. A spot outside the piece's height is refused -
 * a guard for the cellar is not left standing on the roof.</p>
 */
public final class StructureGarrison implements Listener {

    /** Marks a mob placed as a datapack structure's guard. */
    public static final String MARK = "arkcronist_structure";

    private final ArkcronistPlugin plugin;
    private final NamespacedKey mark;
    private final NamespacedKey done;

    /** One place to put a guard. */
    record Target(String key, int x, int z, int minY, int maxY) {
    }

    /** Structure key to the mobs chosen for it, per dimension. Cleared when the mobs change. */
    private final Map<String, StructureThemes.Pick> picks = new ConcurrentHashMap<>();
    private volatile Object[] picksFor;

    public StructureGarrison(ArkcronistPlugin plugin) {
        this.plugin = plugin;
        this.mark = new NamespacedKey(plugin, MARK);
        this.done = new NamespacedKey(plugin, "structure_mobs_spawned");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) {
            return;
        }
        ArkConfig config = plugin.arkConfig();
        if (!config.hostileMobsEnabled() || !config.structureMobsEnabled()
                || config.structureMaxPerChunk() == 0 || !MythicBridge.available()) {
            return;
        }
        World world = event.getWorld();
        if (!MobWorlds.appliesHere(plugin, world)) {
            return;
        }
        Chunk chunk = event.getChunk();
        if (chunk.getPersistentDataContainer().has(done, PersistentDataType.BYTE)) {
            return;
        }
        List<Target> targets = targets(world, chunk, config);
        if (targets.isEmpty()) {
            return;
        }
        chunk.getPersistentDataContainer().set(done, PersistentDataType.BYTE, (byte) 1);
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        // A tick later, as with this generator's own garrisons: spawning from inside the load event
        // can re-enter chunk loading, and a tick later the chunk is live and settled.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                return;
            }
            for (Target target : targets) {
                try {
                    place(world, target);
                } catch (RuntimeException exception) {
                    plugin.getLogger().warning("Could not garrison " + target.key() + ": "
                            + exception.getMessage());
                }
            }
        });
    }

    /** Where the guards of this chunk go. Seeded by the world and the chunk, so it is repeatable. */
    private List<Target> targets(World world, Chunk chunk, ArkConfig config) {
        Collection<GeneratedStructure> found;
        try {
            found = chunk.getStructures();
        } catch (RuntimeException | LinkageError unsupported) {
            return List.of();
        }
        if (found == null || found.isEmpty()) {
            return List.of();
        }
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        SplittableRandom random = new SplittableRandom(world.getSeed()
                ^ (chunkX * 341_873_128_712L + chunkZ * 132_897_987_541L));
        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;
        int max = config.structureMaxPerChunk();
        double chance = config.structurePieceChance();
        List<Target> targets = new ArrayList<>();
        for (GeneratedStructure structure : found) {
            String key = keyOf(structure);
            if (!allowed(key, config)) {
                continue;
            }
            Collection<StructurePiece> pieces;
            try {
                pieces = structure.getPieces();
            } catch (RuntimeException unsupported) {
                continue;
            }
            for (StructurePiece piece : pieces) {
                int[] box = within(piece.getBoundingBox(), x0, z0);
                if (box == null || random.nextDouble() >= chance) {
                    continue;
                }
                int x = box[0] + random.nextInt(box[1] - box[0] + 1);
                int z = box[2] + random.nextInt(box[3] - box[2] + 1);
                targets.add(new Target(key, x, z, box[4], box[5]));
                if (targets.size() >= max) {
                    return targets;
                }
            }
        }
        return targets;
    }

    /**
     * The part of a piece that lies in this chunk, as {@code [minX, maxX, minZ, maxZ, minY, maxY]}
     * in whole blocks, or null when none does. A piece's box is exclusive at its far edges.
     */
    public static int[] within(BoundingBox box, int x0, int z0) {
        int minX = Math.max(x0, (int) Math.floor(box.getMinX()));
        int maxX = Math.min(x0 + 15, (int) Math.ceil(box.getMaxX()) - 1);
        int minZ = Math.max(z0, (int) Math.floor(box.getMinZ()));
        int maxZ = Math.min(z0 + 15, (int) Math.ceil(box.getMaxZ()) - 1);
        if (minX > maxX || minZ > maxZ) {
            return null;
        }
        int minY = (int) Math.floor(box.getMinY());
        int maxY = Math.max(minY, (int) Math.ceil(box.getMaxY()) - 1);
        return new int[] {minX, maxX, minZ, maxZ, minY, maxY};
    }

    private void place(World world, Target target) {
        List<String> names = mobsFor(world, target.key()).mobs();
        if (names.isEmpty()) {
            return;
        }
        Location spot = SpawnSpot.resolve(world, target.x(), target.minY() + 1, target.z(), 2, false);
        if (spot == null || spot.getBlockY() < target.minY() || spot.getBlockY() > target.maxY()) {
            return;
        }
        String name = names.get(Math.floorMod(target.x() * 31 + target.z() * 17 + target.minY(),
                names.size()));
        Entity guard = MythicBridge.spawn(name, spot, 1);
        if (guard == null) {
            return;
        }
        if (!MobFit.settle(guard)) {
            guard.remove();
            return;
        }
        guard.getPersistentDataContainer().set(mark, PersistentDataType.BYTE, (byte) 1);
        MobFit.watch(plugin, guard);
    }

    /**
     * The key of the datapack structure this spot is inside, or null when it is in none the
     * garrison would use. For the daylight spawner, which wants a structure's own mobs when it lands
     * in one.
     */
    public String structureAt(Location where) {
        World world = where.getWorld();
        ArkConfig config = plugin.arkConfig();
        if (world == null || !config.structureMobsEnabled() || !config.structureAmbient()) {
            return null;
        }
        Collection<GeneratedStructure> found;
        try {
            found = world.getStructures(where.getBlockX() >> 4, where.getBlockZ() >> 4);
        } catch (RuntimeException | LinkageError unsupported) {
            return null;
        }
        if (found == null) {
            return null;
        }
        for (GeneratedStructure structure : found) {
            String key = keyOf(structure);
            if (!allowed(key, config)) {
                continue;
            }
            for (StructurePiece piece : structure.getPieces()) {
                if (piece.getBoundingBox().contains(where.getX(), where.getY(), where.getZ())) {
                    return key;
                }
            }
        }
        return null;
    }

    /** The mobs for a structure in this world's dimension, and why. */
    public StructureThemes.Pick mobsFor(World world, String key) {
        ArkConfig config = plugin.arkConfig();
        List<String> written = BiomeMobs.candidates(config.structureMobTable(), key);
        if (written != null) {
            return new StructureThemes.Pick(written, "puesto en config.yml");
        }
        Habitat habitat = MobRoster.habitatOf(world);
        // Identity, not equality: a reload or a MythicMobs re-read replaces these objects, and
        // comparing their contents on every call would cost more than the choice it caches.
        Object[] stamp = {config, plugin.roster().result(), plugin.roster().placement()};
        Object[] last = picksFor;
        if (last == null || last[0] != stamp[0] || last[1] != stamp[1] || last[2] != stamp[2]) {
            picks.clear();
            picksFor = stamp;
        }
        return picks.computeIfAbsent(habitat + "|" + key, ignored -> {
            List<String> general = plugin.roster().ambientPool(config, habitat);
            LinkedHashSet<String> themed = new LinkedHashSet<>(general);
            themed.addAll(plugin.roster().discoveredFor(habitat));
            return StructureThemes.pick(key, List.copyOf(themed), general);
        });
    }

    /** Every structure a decision has been made for, for {@code /ag mythic structures}. */
    public Map<String, StructureThemes.Pick> decided() {
        return Map.copyOf(picks);
    }

    private static boolean allowed(String key, ArkConfig config) {
        if (key == null) {
            return false;
        }
        if (!config.structureIncludeVanilla() && key.startsWith("minecraft:")) {
            return false;
        }
        String path = key.substring(key.indexOf(':') + 1);
        if (config.structureExclude().contains(key) || config.structureExclude().contains(path)) {
            return false;
        }
        return !StructureThemes.peaceful(key, config.structurePeaceful());
    }

    @SuppressWarnings("deprecation")
    private static String keyOf(GeneratedStructure structure) {
        try {
            return structure.getStructure().getKey().toString().toLowerCase(Locale.ROOT);
        } catch (RuntimeException | LinkageError unknown) {
            return null;
        }
    }
}
