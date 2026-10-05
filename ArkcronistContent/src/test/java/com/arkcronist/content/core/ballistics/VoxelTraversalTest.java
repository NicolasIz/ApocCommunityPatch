package com.arkcronist.content.core.ballistics;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoxelTraversalTest {

    private static final double DELTA = 1e-9;

    @Test
    void aStraightRayVisitsEveryBlockInALine() {
        Ray ray = new Ray(new Vec3(0.5, 64.5, 0.5), new Vec3(1, 0, 0));
        List<VoxelTraversal.Voxel> voxels = VoxelTraversal.all(ray, 3.6);

        assertEquals(List.of("0,64,0", "1,64,0", "2,64,0", "3,64,0", "4,64,0"),
                voxels.stream().map(v -> v.x() + "," + v.y() + "," + v.z()).toList());
        assertNull(voxels.get(0).face());
        assertEquals(VoxelTraversal.Face.WEST, voxels.get(1).face());
        assertEquals(0.5, voxels.get(1).distance(), DELTA);
        assertEquals(3.5, voxels.get(4).distance(), DELTA);
    }

    @Test
    void negativeDirectionsAndNegativeCoordinatesStepTheRightWay() {
        Ray ray = new Ray(new Vec3(-0.5, 10.5, -0.5), new Vec3(0, -1, 0));
        List<VoxelTraversal.Voxel> voxels = VoxelTraversal.all(ray, 2);

        assertEquals(List.of(10, 9, 8), voxels.stream().map(VoxelTraversal.Voxel::y).toList());
        assertTrue(voxels.stream().allMatch(v -> v.x() == -1 && v.z() == -1));
        assertEquals(VoxelTraversal.Face.UP, voxels.get(1).face());
    }

    /** No block skipped and none visited that the ray misses: each next block is one face away. */
    @Test
    void consecutiveBlocksShareAFaceAndTheRayReallyPassesThemAll() {
        Random random = new Random(7);
        for (int run = 0; run < 200; run++) {
            Vec3 origin = new Vec3(random.nextDouble() * 20 - 10, random.nextDouble() * 20, random.nextDouble() * 20 - 10);
            Vec3 direction = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
            Ray ray = new Ray(origin, direction);
            List<VoxelTraversal.Voxel> voxels = VoxelTraversal.all(ray, 30);
            for (int i = 1; i < voxels.size(); i++) {
                VoxelTraversal.Voxel before = voxels.get(i - 1);
                VoxelTraversal.Voxel now = voxels.get(i);
                int steps = Math.abs(now.x() - before.x()) + Math.abs(now.y() - before.y())
                        + Math.abs(now.z() - before.z());
                assertEquals(1, steps, "run " + run + " step " + i);
                assertTrue(now.distance() >= before.distance() - DELTA);
                // The entry point lies in (on the boundary of) the block it names.
                Vec3 entry = ray.at(now.distance());
                assertTrue(Aabb.block(now.x(), now.y(), now.z()).expand(1e-6).contains(entry), "run " + run);
            }
            // And the ray's end lies in the last block visited.
            VoxelTraversal.Voxel last = voxels.get(voxels.size() - 1);
            assertTrue(Aabb.block(last.x(), last.y(), last.z()).expand(1e-6).contains(ray.at(30)), "run " + run);
        }
    }

    @Test
    void theWalkStopsAtTheFirstBlockAskedFor() {
        Ray ray = new Ray(new Vec3(0.5, 70.2, 0.5), new Vec3(0.2, -1, 0.1));
        // The floor is at y = 63.
        Optional<VoxelTraversal.Voxel> floor = VoxelTraversal.walk(ray, 20, voxel -> voxel.y() <= 63);

        assertTrue(floor.isPresent());
        assertEquals(63, floor.get().y());
        assertEquals(VoxelTraversal.Face.UP, floor.get().face());
        // Reached from above after falling 70.2 - 64 = 6.2 blocks, along a slanted ray.
        assertEquals(6.2 / ray.direction().y() * -1, floor.get().distance(), 1e-9);
        assertTrue(VoxelTraversal.walk(ray, 5, voxel -> voxel.y() <= 63).isEmpty());
    }
}
