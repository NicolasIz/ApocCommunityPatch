package com.arkcronist.gen.bukkit.mobs;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.util.BoundingBox;

/**
 * Checks a mob against the blocks it was just placed in, and moves it or gives up.
 *
 * <p>Everything else here decides where a mob goes before it exists, from a height the caller had
 * to guess: {@link SpawnSpot} is told how many blocks of clearance to look for, and the ambient
 * spawner asks for two because that is what a zombie needs. A custom mob is not a zombie. Packs
 * scale their models, and a mob built on a husk can stand three blocks tall while every check
 * upstream was still reasoning about the husk. The vanilla spawn the swap replaces has the same
 * problem from the other end: the game proved a husk fitted there, then we put something bigger in
 * its place and cancelled the husk.</p>
 *
 * <p>The result was in the server log rather than in a test - goblins and spiders dying of
 * suffocation a few seconds after appearing, in caves and against hillsides. A dead guard takes its
 * loot and its slot in the mob cap with it, and fills the console while doing it.</p>
 *
 * <p>So this asks the only source that cannot be wrong about a mob's size: the mob. Once it exists
 * it has a real bounding box, whatever the pack did to it, and the blocks it overlaps are a matter
 * of fact. A mob found inside something solid is moved with the same vertical search everything
 * else uses - now with its true height rather than a guess - and one that still does not fit is
 * reported as not fitting, for the caller to undo.</p>
 *
 * <p>Undoing is why this returns an answer instead of quietly removing things. The swap can put the
 * vanilla mob back by simply not cancelling it, which is a better world than a hole; the spawners
 * have nothing to put back and just drop the request. Only the caller knows which it is.</p>
 */
public final class MobFit {

    /**
     * How much of the box to ignore at its edges.
     *
     * <p>A mob standing against a wall touches it, and a mob standing on the floor touches that.
     * Without this every mob in the world would read as buried.</p>
     */
    private static final double EDGE = 0.05;

    /**
     * The widest box worth examining, in blocks per axis.
     *
     * <p>A boss the size of a building is not what this was written for, and walking its volume
     * block by block on the spawn path would cost more than it saves. Anything past this is left
     * alone.</p>
     */
    private static final int LARGEST = 8;

    /**
     * Whether standing in this block would hurt, asked without a world.
     *
     * <p>The geometry below is the part worth testing and the part that has nothing to do with
     * Bukkit, so the world is passed in as this rather than reached for. A test hands it a wall.</p>
     */
    @FunctionalInterface
    public interface Solid {
        boolean at(int x, int y, int z);
    }

    private MobFit() {
    }

    /**
     * Whether this mob is standing inside something solid right now.
     *
     * <p>Its own bounding box against the blocks that box overlaps, pulled in at the edges so that
     * resting on a floor or brushing a wall is not mistaken for being inside one.</p>
     */
    public static boolean buried(Entity entity) {
        if (entity == null || !entity.isValid()) {
            return false;
        }
        World world = entity.getWorld();
        return buried(entity.getBoundingBox(),
                (x, y, z) -> solid(world.getBlockAt(x, y, z)));
    }

    /**
     * The same question about a box and a world, with nothing of Bukkit's in the way.
     *
     * @param box   where the mob is, in full - the edges are pulled in here and not by the caller
     * @param solid whether a block position is something to be inside of
     */
    public static boolean buried(BoundingBox box, Solid solid) {
        if (box.getWidthX() > LARGEST || box.getHeight() > LARGEST || box.getWidthZ() > LARGEST) {
            return false;
        }
        BoundingBox inner = box.clone().expand(-EDGE, -EDGE, -EDGE);
        if (inner.getVolume() <= 0.0) {
            // Something small enough that pulling the edges in leaves nothing. Ask about the block
            // its middle is in and no more.
            return solid.at((int) Math.floor(box.getCenterX()),
                    (int) Math.floor(box.getCenterY()),
                    (int) Math.floor(box.getCenterZ()));
        }
        int minX = (int) Math.floor(inner.getMinX());
        int minY = (int) Math.floor(inner.getMinY());
        int minZ = (int) Math.floor(inner.getMinZ());
        int maxX = (int) Math.floor(inner.getMaxX());
        int maxY = (int) Math.floor(inner.getMaxY());
        int maxZ = (int) Math.floor(inner.getMaxZ());
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (solid.at(x, y, z)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Makes sure this mob has somewhere to be, moving it if it has not.
     *
     * <p>A mob that is already clear is left exactly where the caller put it - the common case, and
     * it costs one bounding box. One that is buried is looked for again in its own column, with the
     * height it actually turned out to have, and moved there. The move is checked too: a wide mob
     * can pass a column search and still have its shoulders in the rock.</p>
     *
     * @return true when the mob is standing somewhere it can live, false when the caller should
     *         undo the spawn
     */
    public static boolean settle(Entity entity) {
        if (entity == null || !entity.isValid()) {
            return false;
        }
        if (!buried(entity)) {
            return true;
        }
        Location where = entity.getLocation();
        World world = entity.getWorld();
        int needed = Math.max(1, (int) Math.ceil(entity.getBoundingBox().getHeight()));
        Location moved = SpawnSpot.resolve(world, where.getBlockX(), where.getBlockY(),
                where.getBlockZ(), needed, false);
        if (moved == null) {
            return false;
        }
        moved.setYaw(where.getYaw());
        moved.setPitch(where.getPitch());
        entity.teleport(moved);
        return !buried(entity);
    }

    /** Whether standing in this block would hurt. */
    private static boolean solid(Block block) {
        return !block.isPassable();
    }
}
