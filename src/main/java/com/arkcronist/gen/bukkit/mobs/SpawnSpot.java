package com.arkcronist.gen.bukkit.mobs;

import com.arkcronist.gen.core.structure.MobSpawn;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.WaterMob;

import java.util.EnumSet;
import java.util.Set;

/**
 * Finds a place a queued mob can actually stand, or decides there is none.
 *
 * <p>Structures choose where their garrison goes while they are being built, off the main thread and
 * with only their own buffer to look at. That works for a mob standing on a floor the structure just
 * laid, and it does not work for one standing a few blocks off the side of it: several structures
 * scatter their guards around an anchor point but keep the anchor's height, so wherever the ground
 * rises the mob is asked to appear inside the hillside. It suffocates in a few seconds and the site
 * ends up empty. That is the "mobs come out underground and die" this class exists for.</p>
 *
 * <p>Rather than patching the height arithmetic in every structure - there are more than sixty spawn
 * points across them and each has its own idea of what its floor is - the position is checked once,
 * here, against the finished world. By this point the chunk is generated, loaded and live, so what a
 * block query returns is the truth rather than an estimate.</p>
 *
 * <p><b>The search is vertical and nothing else, deliberately.</b> Spawns are handed to the queue
 * already sorted into the chunk that contains them, so the column above and below a request is
 * inside a chunk that is known to be loaded. Wandering sideways would ask for neighbouring chunks
 * and force them to be generated synchronously, in the middle of a chunk-load event - a very
 * effective way to turn a garrison into a server freeze.</p>
 *
 * <p>When no spot is found the mob is not placed at all. A missing guard is a small loss; a guard
 * buried in stone is a dead one, and it takes its loot and its place in the mob cap with it.</p>
 */
public final class SpawnSpot {

    /**
     * How far up and down to look, in blocks.
     *
     * <p>Sixteen either way covers the case this was written for - a garrison scattered across a
     * slope the building levelled only part of - without letting a mob meant for a cellar surface on
     * a mountainside.</p>
     */
    private static final int REACH = 16;

    /** Things that kill whatever is standing in or on them, so they are never a spot. */
    private static final Set<Material> DEADLY = EnumSet.of(
            Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.MAGMA_BLOCK, Material.POWDER_SNOW, Material.WITHER_ROSE, Material.CACTUS);

    private SpawnSpot() {
    }

    /**
     * Where this mob should really appear.
     *
     * @return the location, or null when the column has nowhere to put it and it should be skipped
     */
    public static Location resolve(World world, MobSpawn request, EntityType type) {
        // How tall the mob is and whether it stands on anything are the request's own business - the
        // same two answers the structure pass used, so the two checks cannot disagree.
        return resolve(world, request.x(), request.y(), request.z(), request.height(),
                !request.needsFloor() || aquatic(type));
    }

    /**
     * The same search, for a caller that has a column and a shape rather than a queued request.
     *
     * <p>The ambient spawner is the one that needs this: it invents its own positions around a
     * player and has no {@link MobSpawn} to ask, but it wants exactly the same answer about where a
     * mob can stand - one rule for both, so a mob dropped by a structure and a mob dropped beside a
     * player are held to the same thing.</p>
     *
     * @param needed how many blocks of clear space the mob needs above its feet
     * @param floats true for something that swims or flies and so needs no floor
     * @return the location, or null when the column has nowhere to put it
     */
    public static Location resolve(World world, int x, int wantedY, int z, int needed, boolean floats) {
        int lowest = world.getMinHeight() + 1;
        int highest = world.getMaxHeight() - Math.max(1, needed);
        if (lowest > highest) {
            return null;
        }
        int wanted = Math.max(lowest, Math.min(highest, wantedY));

        if (fits(world, x, wanted, z, floats, needed)) {
            return at(world, x, wanted, z);
        }
        // Outwards from where the structure asked, nearest first, so a mob is moved as little as
        // possible: down for one left hanging over its floor, up for one walled into a rise.
        for (int step = 1; step <= REACH; step++) {
            int below = wanted - step;
            if (below >= lowest && fits(world, x, below, z, floats, needed)) {
                return at(world, x, below, z);
            }
            int above = wanted + step;
            if (above <= highest && fits(world, x, above, z, floats, needed)) {
                return at(world, x, above, z);
            }
        }
        return null;
    }

    /**
     * Whether a mob standing here would live.
     *
     * <p>As many blocks of clear space as the mob is tall, and something solid under its feet.
     * Something that swims is held to less: it needs the space but not the floor, because a guardian
     * over the middle of a monument's flooded hall is exactly where it belongs.</p>
     */
    private static boolean fits(World world, int x, int y, int z, boolean floats, int needed) {
        boolean wet = false;
        for (int offset = 0; offset < needed; offset++) {
            Block block = world.getBlockAt(x, y + offset, z);
            if (!open(block)) {
                return false;
            }
            wet |= block.isLiquid();
        }
        if (floats) {
            return true;
        }
        // Water is space a land mob can pass through and not a place to leave it standing: a
        // garrison dropped in a moat drowns as surely as one dropped in a wall.
        if (wet) {
            return false;
        }
        Block floor = world.getBlockAt(x, y - 1, z);
        return floor.isSolid() && !floor.isLiquid() && !DEADLY.contains(floor.getType());
    }

    /** Nothing solid and nothing lethal. */
    private static boolean open(Block block) {
        return block.isPassable() && !DEADLY.contains(block.getType());
    }

    private static Location at(World world, int x, int y, int z) {
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    /**
     * Whether this is something that lives in water.
     *
     * <p>Asked of the entity class rather than a list of names, so a type added by a future version
     * is classified correctly without this being edited. The two that a plain {@code WaterMob} check
     * misses are named: a guardian counts as a monster and a drowned counts as a zombie, and both
     * spend their lives underwater.</p>
     */
    private static boolean aquatic(EntityType type) {
        Class<?> shape = type.getEntityClass();
        return shape != null
                && (WaterMob.class.isAssignableFrom(shape)
                || Guardian.class.isAssignableFrom(shape)
                || Drowned.class.isAssignableFrom(shape));
    }
}
