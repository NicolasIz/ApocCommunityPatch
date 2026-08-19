package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.BlockShapes;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.structure.*;

/**
 * A trial chamber: copper and tuff arenas linked by corridors, trial spawners in the middle of each
 * fight room, vaults holding the reward, and lit alcoves so the fight is at least visible.
 */
public final class TrialChamberStructure implements Structure {

    @Override
    public Placement placement() {
        return Placement.UNDERGROUND;
    }

    @Override
    public String id() {
        return "trial_chamber";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.TRIAL_CHAMBER;
    }

    @Override
    public int radius() {
        return 44;
    }

    @Override
    public double weight() {
        return 0.8;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        return context.groundY > context.engine().settings().minY + 55;
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        int x = context.originX;
        int z = context.originZ;
        int minY = context.engine().settings().minY;
        int y = Math.max(minY + 14, Math.min(context.groundY - 24, 20) - random.nextInt(0, 14));

        int rooms = random.nextInt(3, 6);
        int previousX = x;
        int previousZ = z;
        for (int i = 0; i < rooms; i++) {
            double angle = (i / (double) rooms) * Math.PI * 2.0 + random.nextDouble(-0.2, 0.2);
            int distance = random.nextInt(14, 26);
            int rx = x + (int) Math.round(Math.cos(angle) * distance);
            int rz = z + (int) Math.round(Math.sin(angle) * distance);
            arena(buffer, random, rx, y, rz);
            corridor(buffer, previousX, y, previousZ, rx, rz);
            previousX = rx;
            previousZ = rz;
        }
        vaultRoom(buffer, random, x, y, z);
    }

    private int wall(FastRandom random) {
        double roll = random.nextDouble();
        return roll < 0.45 ? Blocks.TUFF_BRICKS
                : roll < 0.70 ? Blocks.POLISHED_TUFF
                : roll < 0.85 ? Blocks.CHISELED_TUFF : Blocks.COPPER_BLOCK;
    }

    private void arena(StructureBuffer buffer, FastRandom random, int x, int y, int z) {
        int half = random.nextInt(6, 9);
        int height = 7;
        for (int ox = -half; ox <= half; ox++) {
            for (int oz = -half; oz <= half; oz++) {
                for (int oy = -1; oy <= height; oy++) {
                    boolean shell = Math.abs(ox) == half || Math.abs(oz) == half || oy == -1 || oy == height;
                    buffer.set(x + ox, y + oy, z + oz, shell ? wall(random) : Blocks.AIR);
                }
            }
        }
        // Sunken fighting pit with a walkway around it.
        BuildKit.box(buffer, x - half + 2, y - 2, z - half + 2, x + half - 2, y - 1, z + half - 2, Blocks.AIR);
        BuildKit.box(buffer, x - half + 2, y - 3, z - half + 2, x + half - 2, y - 3, z + half - 2,
                Blocks.POLISHED_TUFF);

        buffer.set(x, y - 2, z, Blocks.TRIAL_SPAWNER);
        for (int corner = 0; corner < 4; corner++) {
            int cx = (corner & 1) == 0 ? x - half + 1 : x + half - 1;
            int cz = (corner & 2) == 0 ? z - half + 1 : z + half - 1;
            buffer.set(cx, y + height - 1, cz, Blocks.COPPER_BULB);
            buffer.set(cx, y, cz, Blocks.COPPER_GRATE);
        }
        for (int i = 0; i < 3; i++) {
            buffer.addSpawn(MobSpawn.mob(x + random.nextInt(-half + 3, half - 3), y - 2,
                    z + random.nextInt(-half + 3, half - 3), fighter(random), 3));
        }
        BuildKit.chest(buffer, x + half - 1, y, z, 2, "trial");
    }

    private void corridor(StructureBuffer buffer, int x0, int y, int z0, int x1, int z1) {
        int x = x0;
        int z = z0;
        while (x != x1 || z != z1) {
            if (x != x1) {
                x += Integer.signum(x1 - x);
            } else {
                z += Integer.signum(z1 - z);
            }
            for (int ox = -2; ox <= 2; ox++) {
                for (int oz = -2; oz <= 2; oz++) {
                    for (int oy = -1; oy <= 4; oy++) {
                        boolean shell = Math.abs(ox) == 2 || Math.abs(oz) == 2 || oy == -1 || oy == 4;
                        buffer.set(x + ox, y + oy, z + oz, shell ? Blocks.TUFF_BRICKS : Blocks.AIR);
                    }
                }
            }
            if ((x + z) % 9 == 0) {
                buffer.set(x, y + 3, z, Blocks.COPPER_BULB);
            }
        }
    }

    private void vaultRoom(StructureBuffer buffer, FastRandom random, int x, int y, int z) {
        int half = 8;
        for (int ox = -half; ox <= half; ox++) {
            for (int oz = -half; oz <= half; oz++) {
                for (int oy = -1; oy <= 8; oy++) {
                    boolean shell = Math.abs(ox) == half || Math.abs(oz) == half || oy == -1 || oy == 8;
                    buffer.set(x + ox, y + oy, z + oz, shell ? wall(random) : Blocks.AIR);
                }
            }
        }
        BuildKit.box(buffer, x - 3, y, z - 3, x + 3, y, z + 3, Blocks.CHISELED_COPPER);
        for (int i = 0; i < 4; i++) {
            int vx = x + (i < 2 ? -2 : 2);
            int vz = z + (i % 2 == 0 ? -2 : 2);
            buffer.set(vx, y + 1, vz, Blocks.VAULT_BLOCK);
            buffer.set(vx, y + 3, vz, Blocks.COPPER_BULB);
        }
        buffer.set(x, y + 1, z, Blocks.TRIAL_SPAWNER);
        BuildKit.chest(buffer, x + 4, y + 1, z, 3, "trial");
        BuildKit.chest(buffer, x - 4, y + 1, z, 3, "trial");
        buffer.addSpawn(MobSpawn.boss(x, y + 1, z + 4, "BREEZE", 4, "trial_warden"));
    }

    private static String fighter(FastRandom random) {
        return switch (random.nextInt(5)) {
            case 0 -> "ZOMBIE";
            case 1 -> "SKELETON";
            case 2 -> "SPIDER";
            case 3 -> "BREEZE";
            default -> "HUSK";
        };
    }
}
