package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.bukkit.mobs.SpawnSpot;
import com.arkcronist.gen.core.structure.MobSpawn;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The last check before a structure's mob is let into the world.
 *
 * <p>This is the guard for the bug behind "the mobs come out underground and die". A structure
 * decides where its garrison goes while it is being generated, from its own buffer, and several of
 * them scatter guards sideways while keeping one height - so on sloping ground the mob is asked to
 * appear inside the hill. {@code SpawnSpot} is the point where that is caught, because by then the
 * chunk is real and a block query answers with the truth.</p>
 *
 * <p>There is no server here, so the world is a stub: {@link Proxy} over Bukkit's own interfaces
 * answering from a column of materials this class writes out by hand. That is enough, because the
 * only thing being tested is the decision - is this a place something can stand - and the decision
 * is made entirely from what those three or four block queries come back with.</p>
 */
class SpawnSpotTest {

    private static final int MIN_HEIGHT = -64;
    private static final int MAX_HEIGHT = 320;

    /**
     * A world that is one column of blocks and nothing else.
     *
     * <p>Anything not written into the column is air, which is what a column of open sky above a
     * hillside looks like anyway.</p>
     */
    private static World worldOf(Map<Integer, Material> column) {
        return (World) Proxy.newProxyInstance(SpawnSpotTest.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getBlockAt" -> blockOf(column.getOrDefault((Integer) args[1], Material.AIR));
                    case "getMinHeight" -> MIN_HEIGHT;
                    case "getMaxHeight" -> MAX_HEIGHT;
                    case "toString" -> "stub-world";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(
                            "the stub world was asked for " + method.getName()
                                    + ", which means SpawnSpot started using something this test "
                                    + "does not model");
                });
    }

    /** One block, answering the three questions the resolver asks of it. */
    private static Block blockOf(Material material) {
        return (Block) Proxy.newProxyInstance(SpawnSpotTest.class.getClassLoader(),
                new Class<?>[]{Block.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getType" -> material;
                    case "isSolid" -> solid(material);
                    case "isPassable" -> !solid(material);
                    case "isLiquid" -> material == Material.WATER || material == Material.LAVA;
                    case "toString" -> "stub-" + material;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(
                            "the stub block was asked for " + method.getName());
                });
    }

    private static boolean solid(Material material) {
        return switch (material) {
            case AIR, CAVE_AIR, WATER, LAVA, FIRE, SHORT_GRASS -> false;
            default -> true;
        };
    }

    /** Fills the column from {@code from} up to but not including {@code to}. */
    private static void fill(Map<Integer, Material> column, int from, int to, Material material) {
        for (int y = from; y < to; y++) {
            column.put(y, material);
        }
    }

    private static Location resolve(Map<Integer, Material> column, int y, EntityType type) {
        return SpawnSpot.resolve(worldOf(column), MobSpawn.mob(100, y, -40, type.name(), 1), type);
    }

    @Test
    @DisplayName("a mob asked for inside the hillside is lifted out of it")
    void aBuriedMobComesUpToTheSurface() {
        // The reported failure exactly: the structure levelled its own footprint at 64 and put a
        // guard a few blocks to the side, where the hill has not been touched and stands at 71.
        Map<Integer, Material> column = new HashMap<>();
        fill(column, MIN_HEIGHT, 71, Material.STONE);

        Location spot = resolve(column, 64, EntityType.ZOMBIE);
        assertNotNull(spot, "the guard was dropped, though there is open ground seven blocks up");
        assertEquals(71, spot.getBlockY(), "the guard is still in the rock");
        assertEquals(100.5, spot.getX(), 1e-9, "not standing in the middle of its block");
        assertEquals(-39.5, spot.getZ(), 1e-9, "not standing in the middle of its block");
    }

    @Test
    @DisplayName("a mob asked for in mid-air is set down on the floor below it")
    void aFloatingMobSettlesOntoTheFloor() {
        Map<Integer, Material> column = new HashMap<>();
        fill(column, MIN_HEIGHT, 65, Material.STONE);

        Location spot = resolve(column, 80, EntityType.SKELETON);
        assertNotNull(spot, "nowhere found, over a floor fifteen blocks down");
        assertEquals(65, spot.getBlockY(), "left hanging in the air");
    }

    @Test
    @DisplayName("a mob with nowhere to stand is not spawned at all")
    void solidRockMeansNoMob() {
        // Better a missing guard than a dead one: a mob sealed in stone suffocates in seconds and
        // takes its loot and its slot in the mob cap with it.
        Map<Integer, Material> column = new HashMap<>();
        fill(column, MIN_HEIGHT, MAX_HEIGHT, Material.STONE);

        assertNull(resolve(column, 64, EntityType.ZOMBIE), "a mob was placed inside solid rock");
    }

    @Test
    @DisplayName("a floor that would kill whatever stands on it is not a floor")
    void nothingIsStoodOnMagma() {
        Map<Integer, Material> column = new HashMap<>();
        fill(column, MIN_HEIGHT, 63, Material.STONE);
        column.put(63, Material.MAGMA_BLOCK);
        // Open air above the magma, and a real ledge further up.
        fill(column, 66, 69, Material.STONE);

        Location spot = resolve(column, 64, EntityType.ZOMBIE);
        assertNotNull(spot, "the ledge above was not found");
        assertEquals(69, spot.getBlockY(), "the mob was left standing on magma");
    }

    @Test
    @DisplayName("water is a home for a guardian and a drowning for a zombie")
    void whatSwimsIsHeldToADifferentRule() {
        Map<Integer, Material> column = new HashMap<>();
        fill(column, MIN_HEIGHT, 41, Material.STONE);
        fill(column, 41, 62, Material.WATER);

        Location guardian = resolve(column, 50, EntityType.GUARDIAN);
        assertNotNull(guardian, "a guardian was refused the middle of the sea");
        assertEquals(50, guardian.getBlockY(), "the guardian was moved out of the water it lives in");

        Location drowned = resolve(column, 50, EntityType.DROWNED);
        assertNotNull(drowned, "a drowned was refused the water");
        assertEquals(50, drowned.getBlockY(), "the drowned was moved out of the water");

        // Nothing on land: the sea floor is twenty blocks down and the surface is above water with
        // no solid block under it, so there is nowhere in this column a zombie survives.
        assertNull(resolve(column, 50, EntityType.ZOMBIE), "a zombie was left standing in open water");
    }

    @Test
    @DisplayName("a mob already standing somewhere sound is left exactly where the structure put it")
    void agoodSpotIsNotMovedAtAll() {
        Map<Integer, Material> column = new HashMap<>();
        fill(column, MIN_HEIGHT, 64, Material.STONE);
        column.put(64, Material.SHORT_GRASS);

        Location spot = resolve(column, 64, EntityType.SKELETON);
        assertNotNull(spot, "a perfectly good spot was rejected");
        assertEquals(64, spot.getBlockY(), "the mob was moved off a spot that was already fine");
    }
}
