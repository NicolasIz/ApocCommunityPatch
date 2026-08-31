package com.arkcronist.gen.core.structure.types;

import com.arkcronist.gen.core.biome.StructureTag;
import com.arkcronist.gen.core.block.Blocks;
import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.math.MathUtil;
import com.arkcronist.gen.core.noise.FractalNoise;
import com.arkcronist.gen.core.structure.BufferWriter;
import com.arkcronist.gen.core.structure.MobSpawn;
import com.arkcronist.gen.core.structure.Structure;
import com.arkcronist.gen.core.structure.StructureBuffer;
import com.arkcronist.gen.core.structure.StructureContext;

/**
 * A stratovolcano: a cone of ash and basalt with a crater and a lava lake in it.
 *
 * <p>The shape is the whole point, so it is worth saying what shape and why. A cinder cone is
 * straight-sided and boring from a distance. A shield volcano is a hill. A <em>strato</em>volcano -
 * Fuji, Mayon, Anak Krakatau - is the one everybody pictures: a broad skirt of ash that steepens
 * into a sharp summit, which is what you get from alternating ash falls and lava flows piling up
 * around one vent over a long time.</p>
 *
 * <p>That silhouette comes out of one line. The height at a distance {@code t} of the way to the
 * edge is {@code H * (1 - t^q)} with {@code q} below one: at a tenth of the radius the cone has
 * already given up a quarter of its height, and the last third of the radius is almost flat. A
 * straight cone is the same formula with {@code q = 1}, and it looks like a traffic cone.</p>
 *
 * <p>The rest is texture. The outline is warped so the base is not a circle, the flanks carry
 * gullies where the ash has washed down, the material changes with height - ash and gravel at the
 * foot, basalt on the flanks, blackstone and magma near the top - and one side of the rim is
 * breached, with the old flow running out of it. Anak Krakatau has exactly that notch.</p>
 *
 * <p><b>The lava stays in the crater.</b> A lake in a bowl with solid walls, well below the rim. It
 * would be easy to run rivers of it down the flanks and it would look tremendous for about a minute,
 * until the forest at the bottom caught fire and the server spent its ticks on flowing liquid. The
 * flows down the flanks are magma blocks: they glow, they hurt, and they stay where they are put.</p>
 */
public final class VolcanoStructure implements Structure {

    /** The widest a volcano is ever built, and what the placer reserves for it. */
    private static final int MAX_RADIUS = 88;

    private final FractalNoise outline;
    private final FractalNoise surface;
    private final FractalNoise gullies;

    public VolcanoStructure(long seed) {
        this.outline = FractalNoise.fbm(seed ^ 0x1F0C_A170L, "volcanoOutline", 3, 0.010);
        this.surface = FractalNoise.fbm(seed ^ 0x1F0C_A171L, "volcanoSurface", 4, 0.055);
        this.gullies = FractalNoise.ridged(seed ^ 0x1F0C_A172L, "volcanoGullies", 3, 0.030);
    }

    @Override
    public String id() {
        return "volcano";
    }

    @Override
    public StructureTag tag() {
        return StructureTag.VOLCANO;
    }

    @Override
    public int radius() {
        return MAX_RADIUS;
    }

    @Override
    public Placement placement() {
        return Placement.SURFACE_LARGE;
    }

    @Override
    public double weight() {
        // The landmark of its biome. Nothing else in an ash plain is worth walking to.
        return 8.0;
    }

    @Override
    public boolean canPlace(StructureContext context) {
        if (context.submerged(context.originX, context.originZ)) {
            return false;
        }
        if (context.groundY <= context.seaLevel() + 1) {
            return false;
        }
        if (context.groundY + height(context) + 8 >= context.maxY()) {
            return false;
        }
        // Room to stand, not a parade ground. A cone this size buries whatever it lands on, so the
        // only thing worth refusing is a site already inside a mountain, where the summit would come
        // out level with the ridge behind it and the whole silhouette would be lost.
        return context.relief(context.originX, context.originZ, 40) < 34;
    }

    private int height(StructureContext context) {
        return switch (context.preset) {
            case BASE -> 52;
            case CHAOTIC -> 64;
            case INSANE -> 74;
        };
    }

    private int spread(StructureContext context) {
        return switch (context.preset) {
            case BASE -> 64;
            case CHAOTIC -> 76;
            case INSANE -> MAX_RADIUS;
        };
    }

    @Override
    public void build(StructureContext context, StructureBuffer buffer) {
        FastRandom random = context.random;
        int x = context.originX;
        int z = context.originZ;
        int reach = spread(context);
        int tall = height(context);

        // The foot of the cone sits on the average ground under the skirt rather than on the one
        // column the grid pointed at, so a volcano on a slope leans into the hill instead of
        // hanging off it.
        int footY = context.averageHeight(Math.min(reach, 48));
        int summitY = footY + tall;
        if (summitY + 4 >= context.maxY()) {
            return;
        }

        int craterRadius = Math.max(8, (int) Math.round(reach * 0.21));
        int craterDepth = Math.max(12, (int) Math.round(tall * 0.30));
        // The summit is a plateau a quarter wider than the crater, and that ring is the whole reason
        // the mountain holds its lava. The first version had none: the profile below drops a third of
        // its height inside the first fifth of the radius, so by the time you reach the crater edge
        // the cone is already thirty blocks under the summit, and a crater measured down from that
        // summit came out deeper than the mountain was tall. Rendered, it was a lava lake sitting in
        // a bowl with no walls at all, which on a live server means a lava fall down every side.
        double plateau = craterRadius * 1.25;
        // One side of the rim is notched, and the old flow runs out of it. Chosen from the site seed
        // so the same volcano always faces the same way.
        double breachAngle = random.nextDouble() * Math.PI * 2.0;
        // Kept well under the lowest the rim can be cut to. The notch takes at most a fourteenth of
        // the height off the rim; the lake sits at half the crater depth below it.
        int lavaTop = summitY - Math.max(6, (int) Math.round(craterDepth * 0.55));

        BufferWriter writer = new BufferWriter(buffer, context.engine.settings().minY, context.maxY());

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int worldX = x + dx;
                int worldZ = z + dz;
                double distance = Math.sqrt((double) dx * dx + (double) dz * dz);
                // The outline is warped before anything else, so the base is a real coastline rather
                // than a compass circle. Everything downstream measures against this edge.
                double edge = reach * (1.0 + 0.14 * outline.noise2(worldX, worldZ));
                // The second half of this guard is what keeps notch() honest: it divides by
                // (edge * 0.55 - plateau), so an outline warped in far enough to put the plateau
                // past the notch's own reach would divide by zero. It cannot happen at the sizes
                // here - the narrowest edge is fifty-five and the widest plateau twenty-three - and
                // it would happen silently the first time somebody made a volcano smaller.
                if (distance > edge || edge <= plateau * 2.0) {
                    continue;
                }
                // Zero across the summit plateau, then climbing to one at the edge. Measuring the
                // profile from the plateau rather than from the centre is what gives the mountain a
                // top to put a crater in.
                double t = distance <= plateau ? 0.0
                        : MathUtil.clamp((distance - plateau) / (edge - plateau), 0.0, 1.0);

                double rise = tall * (1.0 - Math.pow(t, 0.62));
                // Gullies: deepest halfway down, gone at the summit and at the foot, because that is
                // where water and ash actually cut them.
                double flank = Math.sin(t * Math.PI);
                rise -= flank * flank * tall * 0.09 * gullies.unsigned2(worldX, worldZ);
                // No roughness on the rim. Everywhere else it is what stops the cone reading as a
                // machined surface; on the rim it would be the one dip the lake finds.
                if (t > 0.02) {
                    rise += tall * 0.035 * surface.noise2(worldX, worldZ);
                }
                rise -= notch(dx, dz, distance, edge, plateau, breachAngle, tall);
                if (rise <= 0.5) {
                    continue;
                }

                int top = footY + (int) Math.round(rise);
                int ground = context.height(worldX, worldZ);
                int from = Math.min(ground, footY) - 2;

                // Trees standing where the flank now is. Rooted below the new surface and tall
                // enough to come out of it, they leave a wood growing sideways out of a mountain.
                //
                // Only where the cone is really the surface, which is the correction that matters:
                // clearing whenever the old ground was higher, which is what this did first, took a
                // gouge out of every hillside the feather edge of the cone ran into - and the feather
                // edge is precisely where the volcano is meant to disappear into the landscape
                // rather than cut a step in it.
                if (top > ground) {
                    for (int y = top + 1; y <= ground + 26; y++) {
                        writer.set(worldX, y, worldZ, Blocks.AIR);
                    }
                }

                boolean inCrater = distance < craterRadius;
                int craterFloor = Integer.MIN_VALUE;
                if (inCrater) {
                    double cr = distance / craterRadius;
                    craterFloor = summitY - (int) Math.round(craterDepth * (1.0 - cr * cr));
                }

                for (int y = from; y <= top; y++) {
                    if (inCrater && y > craterFloor) {
                        break;
                    }
                    writer.set(worldX, y, worldZ,
                            rock(worldX, y, worldZ, t, summitY, tall, y >= top - 1));
                }

                if (inCrater) {
                    fillCrater(writer, worldX, worldZ, craterFloor, lavaTop);
                }
                flow(writer, worldX, worldZ, dx, dz, top, distance, edge, plateau, breachAngle);
            }
        }

        garrison(context, buffer, random, x, z, summitY, craterRadius, reach, footY);
    }

    /**
     * What the mountain is made of at this height.
     *
     * <p>Ash and gravel at the foot, basalt up the flanks, blackstone and magma at the top. It is
     * not decoration: a cone of one block reads as a grey lump from any distance, and the bands are
     * what make it read as a volcano rather than as a hill somebody painted black.</p>
     */
    private int rock(int x, int y, int z, double t, int summitY, int tall, boolean skin) {
        double roll = surface.unsigned2(x * 1.7 + y * 0.6, z * 1.7 - y * 0.6);
        // The hot band is a fraction of the mountain, not a fixed thirty-four blocks. At a fixed
        // depth the small preset's volcano is hot most of the way down and the large one only at the
        // very tip, which is the same mountain reading as two different ones.
        double band = Math.max(12.0, tall * 0.45);
        double heat = MathUtil.clamp((y - (summitY - band)) / band, 0.0, 1.0);

        if (skin) {
            if (t > 0.72) {
                // The ash fan at the foot, where the fine stuff ends up.
                return roll < 0.45 ? Blocks.GRAVEL : roll < 0.72 ? Blocks.TUFF : Blocks.BASALT;
            }
            if (heat > 0.72 && roll > 0.80) {
                return Blocks.MAGMA_BLOCK;
            }
            if (heat > 0.55) {
                return roll < 0.55 ? Blocks.BLACKSTONE : Blocks.BASALT;
            }
            return roll < 0.62 ? Blocks.BASALT : roll < 0.85 ? Blocks.BLACKSTONE : Blocks.TUFF;
        }
        if (heat > 0.80 && roll > 0.88) {
            return Blocks.MAGMA_BLOCK;
        }
        return roll < 0.55 ? Blocks.BASALT : roll < 0.80 ? Blocks.BLACKSTONE
                : roll < 0.93 ? Blocks.SMOOTH_BASALT : Blocks.TUFF;
    }

    /**
     * The lake in the bowl.
     *
     * <p>Filled to a level well under the rim and walled by the crater itself, so it is a lake and
     * not the start of a lava fall down the outside of the mountain.</p>
     */
    private void fillCrater(BufferWriter writer, int x, int z, int floor, int lavaTop) {
        if (floor >= lavaTop) {
            return;
        }
        writer.set(x, floor, z, Blocks.MAGMA_BLOCK);
        for (int y = floor + 1; y <= lavaTop; y++) {
            writer.set(x, y, z, Blocks.LAVA);
        }
    }

    /**
     * How much the rim is cut away on the breached side.
     *
     * <p>A closed ring of a crater is a bowl on a hill. The notch is what says something came out of
     * this and went somewhere - it is the feature that makes Anak Krakatau look like Anak Krakatau
     * rather than like a bowl. It is deliberately shallow: a fourteenth of the mountain's height,
     * against a lake that sits at over a third of the crater depth below the rim, so the notch is
     * something you can see and walk through and never something the lava finds.</p>
     */
    private double notch(int dx, int dz, double distance, double edge, double plateau,
                         double breachAngle, int tall) {
        if (distance > edge * 0.55) {
            return 0.0;
        }
        double off = angleFrom(dx, dz, breachAngle);
        double width = 0.34;
        if (off > width) {
            return 0.0;
        }
        // Deepest along the middle of the notch and at the rim, fading out down the flank.
        double across = 1.0 - off / width;
        double along = 1.0 - MathUtil.clamp((distance - plateau) / (edge * 0.55 - plateau), 0.0, 1.0);
        return tall * 0.07 * across * across * Math.max(0.0, along);
    }

    /**
     * The old flow, running out of the notch and down the flank.
     *
     * <p>Magma blocks, not lava. See the class note: this is the part that would look tremendous as
     * a lava river for about a minute, and then the forest at the bottom would be on fire and the
     * server would be spending its ticks on flowing liquid.</p>
     */
    private void flow(BufferWriter writer, int worldX, int worldZ, int dx, int dz, int top,
                      double distance, double edge, double plateau, double breachAngle) {
        if (distance < plateau || distance > edge * 0.92) {
            return;
        }
        double off = angleFrom(dx, dz, breachAngle);
        // A wedge that narrows as it runs downhill, the way a flow leaves a notch and finds a channel.
        if (off > 0.30 - 0.16 * (distance / edge)) {
            return;
        }
        writer.set(worldX, top, worldZ, Blocks.MAGMA_BLOCK);
        writer.set(worldX, top - 1, worldZ, Blocks.BLACKSTONE);
    }

    /** How far this column is, in radians, from the direction the volcano is breached towards. */
    private static double angleFrom(int dx, int dz, double breachAngle) {
        double angle = Math.atan2(dz, dx);
        return Math.abs(Math.atan2(Math.sin(angle - breachAngle), Math.cos(angle - breachAngle)));
    }

    /**
     * What lives on a volcano.
     *
     * <p>On the rim, where a player coming up the flank meets them, and on floors the cone itself
     * laid - the summit ring is solid rock by construction, so there is somewhere to stand. Kept to
     * types the hostile-mob table covers, so a server running the custom packs meets those here.</p>
     */
    private void garrison(StructureContext context, StructureBuffer buffer, FastRandom random,
                          int x, int z, int summitY, int craterRadius, int reach, int footY) {
        int guards = switch (context.preset) {
            case BASE -> 3;
            case CHAOTIC -> 6;
            case INSANE -> 10;
        };
        int rim = craterRadius + 3;
        for (int i = 0; i < guards; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int gx = x + (int) Math.round(Math.cos(angle) * rim);
            int gz = z + (int) Math.round(Math.sin(angle) * rim);
            // The rim's height is not worth recomputing here: the settling pass over the finished
            // buffer puts every one of these on the rock this build just wrote, and the server side
            // checks it again against the real world.
            buffer.addSpawn(MobSpawn.mob(gx, summitY + 1, gz,
                    random.chance(0.5) ? "SKELETON" : "ZOMBIE", 3));
        }
        buffer.addSpawn(MobSpawn.boss(x + rim, summitY + 1, z, "WITHER_SKELETON", 4, "cave_horror"));
    }
}
