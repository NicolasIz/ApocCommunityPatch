package com.arkcronist.gen.bukkit.mobs;

/**
 * How many of this plugin's mobs a place may hold before it stops adding more.
 *
 * <p>Two numbers, because crowding has two shapes. A hillside can fill up while the rest of the
 * world is empty, and a hundred small groups can fill a world without any one place looking busy.
 * The near limit catches the first, the world limit the second; a limit of 0 is switched off.</p>
 *
 * <p>Pure, so what "full" means is tested without a server; counting is left to
 * {@link SpawnedMobs}, which knows the world.</p>
 */
public final class SpawnLimit {

    private SpawnLimit() {
    }

    /**
     * Whether another mob may not be added.
     *
     * @param near      this plugin's mobs already within the radius of the spot
     * @param world     this plugin's mobs in the whole world
     * @param nearLimit how many may be near one spot, 0 for no limit
     * @param worldLimit how many may be in one world, 0 for no limit
     */
    public static boolean full(int near, int world, int nearLimit, int worldLimit) {
        return (nearLimit > 0 && near >= nearLimit) || (worldLimit > 0 && world >= worldLimit);
    }
}
