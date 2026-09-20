package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.bukkit.mobs.MobFit;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The geometry behind "this mob is inside the wall".
 *
 * <p>Written from a real server log rather than from an idea: goblins and spiders built on a husk
 * were dying of suffocation seconds after the swap replaced a vanilla spawn with them, in caves and
 * against hillsides. Every check upstream had reasoned about the husk's two blocks; the mob that
 * actually turned up was taller.</p>
 */
class MobFitTest {

    /** A world that is empty apart from the block positions named. */
    private static MobFit.Solid wallAt(int... coords) {
        Set<String> solid = new HashSet<>();
        for (int i = 0; i < coords.length; i += 3) {
            solid.add(coords[i] + "," + coords[i + 1] + "," + coords[i + 2]);
        }
        return (x, y, z) -> solid.contains(x + "," + y + "," + z);
    }

    /** A husk-shaped box standing with its feet on the given block. */
    private static BoundingBox standing(int x, int y, int z, double height) {
        return new BoundingBox(x + 0.2, y, z + 0.2, x + 0.8, y + height, z + 0.8);
    }

    @Test
    @DisplayName("a mob in open air is not buried")
    void openAir() {
        assertFalse(MobFit.buried(standing(0, 64, 0, 1.95), wallAt()));
    }

    @Test
    @DisplayName("the floor it stands on is not the wall it is in")
    void standingOnGround() {
        // The block under its feet is solid - that is what a floor is. Counting it would make every
        // mob in the world read as buried.
        assertFalse(MobFit.buried(standing(0, 64, 0, 1.95), wallAt(0, 63, 0)));
    }

    @Test
    @DisplayName("a mob inside a block is buried")
    void insideStone() {
        assertTrue(MobFit.buried(standing(0, 64, 0, 1.95), wallAt(0, 64, 0)));
    }

    @Test
    @DisplayName("the ceiling only counts when the mob is tall enough to be in it")
    void ceilingHeight() {
        // This is the whole bug. A cave with two blocks of headroom: the game checked that a husk
        // fitted, and it does. Then the swap put a mob a head taller in its place.
        MobFit.Solid cave = wallAt(0, 63, 0, 0, 66, 0);
        assertFalse(MobFit.buried(standing(0, 64, 0, 1.95), cave), "a husk fits under y=66");
        assertTrue(MobFit.buried(standing(0, 64, 0, 2.9), cave), "one scaled to 1.5 does not");
    }

    @Test
    @DisplayName("brushing a wall sideways is not being in it")
    void besideAWall() {
        // Its box ends at x+0.8 and the wall starts at x+1. Without pulling the edges in, a mob
        // with its back against any wall would be dropped.
        assertFalse(MobFit.buried(standing(0, 64, 0, 1.95), wallAt(1, 64, 0, 1, 65, 0)));
    }

    @Test
    @DisplayName("a wide mob is caught by its shoulders")
    void wideMob() {
        // A column search cannot see this: the column is clear, the mob is not. It is why the move
        // is checked again after it happens rather than trusted.
        BoundingBox spider = new BoundingBox(-0.2, 64, 0.2, 1.2, 64.9, 1.8);
        assertTrue(MobFit.buried(spider, wallAt(1, 64, 1)));
        assertFalse(MobFit.buried(spider, wallAt(1, 63, 1)));
    }

    @Test
    @DisplayName("something the size of a building is left alone")
    void enormous() {
        // Walking a boss's volume block by block on the spawn path would cost more than it saves,
        // and a boss that big was placed by hand anyway.
        BoundingBox titan = new BoundingBox(0, 64, 0, 12, 76, 12);
        assertFalse(MobFit.buried(titan, wallAt(5, 70, 5)));
    }
}
