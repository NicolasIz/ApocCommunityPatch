package com.arkcronist.content.core.vehicle;

import com.arkcronist.content.core.ballistics.Aabb;
import com.arkcronist.content.core.vehicle.VehicleDefinition.Offset;

import java.util.List;

/**
 * 4.0: how a vehicle moves from one step to the next - throttle, brakes, steering, gravity, and the
 * blocks it runs into, climbs or falls from. Plain arithmetic on what it is given, so it runs on any
 * thread: the server's main thread hands it a {@link Terrain} - the collision boxes around the
 * vehicle, read there - and it hands back the vehicle's next {@link State}.
 *
 * <p>The vehicle is a box on the ground: its footprint turned by its heading, as the smallest box
 * along the world's axes that holds it. It moves the way an entity does in vanilla: up or down
 * first, then along x, then along z, each cut short by the first box in the way. A step no higher
 * than {@code stepHeight} is driven up; anything higher stops it, and the share of the way it did
 * not get is taken off its speed - head-on it stops, along a wall it slows.</p>
 *
 * <p>Yaw is Minecraft's: 0 faces south (+z), 90 west (-x). Turning right raises it.</p>
 */
public final class VehiclePhysics {

    /** Blocks a second squared: about what vanilla pulls a falling minecart down by. */
    public static final double GRAVITY = 28;
    public static final double TERMINAL_SPEED = 40;
    /** Below this speed (blocks a second) the steering is weaker: a vehicle turns as it rolls. */
    static final double FULL_LOCK_SPEED = 2;
    private static final double EPSILON = 1e-7;

    private VehiclePhysics() {
    }

    /**
     * Where a vehicle is and how it moves.
     *
     * @param x        the middle of its footprint
     * @param y        the bottom of its footprint
     * @param z        the middle of its footprint
     * @param yaw      its heading, in degrees
     * @param speed    along its heading, blocks a second; negative backward
     * @param vy       up or down, blocks a second
     * @param onGround whether it stands on something
     * @param rolled   how far its wheels have rolled, in blocks; backward takes it down
     */
    public record State(double x, double y, double z, double yaw, double speed, double vy, boolean onGround,
                        double rolled) {

        public static State parked(double x, double y, double z, double yaw) {
            return new State(x, y, z, yaw, 0, 0, false, 0);
        }

        /** Not moving, on the ground: nothing to work out until someone drives it. */
        public boolean atRest() {
            return onGround && Math.abs(speed) < 1e-3 && Math.abs(vy) < 1e-3;
        }
    }

    /** What the driver holds down. */
    public record Input(boolean forward, boolean backward, boolean left, boolean right, boolean brake) {

        public static final Input NONE = new Input(false, false, false, false, false);
    }

    /**
     * The blocks around a vehicle, as the main thread read them.
     *
     * @param solid the collision boxes of the blocks in reach
     * @param water the water in reach, each a full block
     */
    public record Terrain(List<Aabb> solid, List<Aabb> water) {

        public static final Terrain EMPTY = new Terrain(List.of(), List.of());

        public Terrain {
            solid = List.copyOf(solid);
            water = List.copyOf(water);
        }

        boolean inWater(double x, double y, double z) {
            for (Aabb box : water) {
                if (x >= box.minX() && x <= box.maxX() && y >= box.minY() && y <= box.maxY()
                        && z >= box.minZ() && z <= box.maxZ()) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * The blocks one step of this vehicle can touch, from where it is: what the main thread should
     * read for the next step. Generous - a full second's travel at top speed is not needed, one step
     * is - with a block of margin and room for a step up or a fall.
     */
    public static Aabb reach(VehicleDefinition vehicle, State state, double seconds) {
        double travel = Math.max(vehicle.maxSpeed(), Math.abs(state.speed())) * seconds;
        double fall = Math.max(TERMINAL_SPEED, Math.abs(state.vy())) * seconds;
        Aabb box = footprint(vehicle, state.x(), state.y(), state.z(), state.yaw());
        return new Aabb(box.minX() - travel - 1, box.minY() - fall - 1, box.minZ() - travel - 1,
                box.maxX() + travel + 1, box.maxY() + vehicle.stepHeight() + 1, box.maxZ() + travel + 1);
    }

    /** One step of {@code seconds}: the vehicle's next state. */
    public static State step(VehicleDefinition vehicle, State state, Input input, Terrain terrain, double seconds) {
        boolean inWater = terrain.inWater(state.x(), state.y() + 0.2, state.z());
        double top = vehicle.maxSpeed() * (inWater ? vehicle.waterSpeed() : 1);
        double topBack = vehicle.reverseSpeed() * (inWater ? vehicle.waterSpeed() : 1);

        // Speed along the heading. Wheels push only on the ground; in the air it keeps what it had.
        double speed = state.speed();
        if (state.onGround()) {
            if (input.brake()) {
                speed = toward(speed, 0, vehicle.braking() * seconds);
            } else if (input.forward() && !input.backward()) {
                speed = speed < 0 ? toward(speed, 0, vehicle.braking() * seconds)
                        : Math.min(speed + vehicle.acceleration() * seconds, Math.max(top, speed));
            } else if (input.backward() && !input.forward()) {
                speed = speed > 0 ? toward(speed, 0, vehicle.braking() * seconds)
                        : Math.max(speed - vehicle.acceleration() * seconds, Math.min(-topBack, speed));
            } else {
                speed = toward(speed, 0, vehicle.coasting() * seconds);
            }
            // Faster than it may go here - driven into water: it slows as hard as it brakes.
            if (speed > top) {
                speed = Math.max(top, speed - vehicle.braking() * seconds);
            } else if (speed < -topBack) {
                speed = Math.min(-topBack, speed + vehicle.braking() * seconds);
            }
        }

        // Steering: weaker at a crawl, the other way round in reverse. A turn that would put the
        // vehicle into something is not made.
        double yaw = state.yaw();
        int steer = (input.right() ? 1 : 0) - (input.left() ? 1 : 0);
        if (steer != 0 && speed != 0) {
            double grip = Math.min(1, Math.abs(speed) / FULL_LOCK_SPEED);
            double turned = wrap(yaw + steer * vehicle.turnRate() * grip * seconds * Math.signum(speed));
            if (!collides(footprint(vehicle, state.x(), state.y(), state.z(), turned), terrain.solid())) {
                yaw = turned;
            }
        }

        double vy = inWater ? Math.max(state.vy() - GRAVITY * 0.1 * seconds, -1)
                : Math.max(state.vy() - GRAVITY * seconds, -TERMINAL_SPEED);
        double radians = Math.toRadians(yaw);
        double wantX = -Math.sin(radians) * speed * seconds;
        double wantZ = Math.cos(radians) * speed * seconds;
        double wantY = vy * seconds;

        Aabb box = footprint(vehicle, state.x(), state.y(), state.z(), yaw);
        Moved moved = move(box, wantX, wantY, wantZ, terrain.solid());
        boolean grounded = state.onGround() || moved.landed();
        if (grounded && vehicle.stepHeight() > 0 && moved.blockedSideways(wantX, wantZ)) {
            Moved stepped = stepUp(box, wantX, wantY, wantZ, vehicle.stepHeight(), terrain.solid());
            if (stepped != null && stepped.horizontal() > moved.horizontal() + EPSILON) {
                moved = stepped;
            }
        }

        double want = Math.hypot(wantX, wantZ);
        if (want > EPSILON) {
            // The share of the way it did not get is the share of its speed it loses.
            speed *= Math.min(1, moved.horizontal() / want);
        }
        boolean onGround = moved.landed() || (wantY <= 0 && moved.dy() > wantY + EPSILON);
        if (onGround || (wantY > 0 && moved.dy() < wantY - EPSILON)) {
            vy = 0;
        }
        double rolled = state.rolled() + Math.signum(speed) * moved.horizontal();
        return new State(state.x() + moved.dx(), state.y() + moved.dy(), state.z() + moved.dz(), yaw,
                Math.abs(speed) < 1e-3 ? 0 : speed, vy, onGround, rolled);
    }

    /** {@code offset} in the vehicle's frame, in the world: {x, y, z}. */
    public static double[] point(State state, Offset offset) {
        double radians = Math.toRadians(state.yaw());
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        // Forward is (-sin, 0, cos); the driver's right is (-cos, 0, -sin).
        return new double[]{
                state.x() - offset.x() * cos - offset.z() * sin,
                state.y() + offset.y(),
                state.z() - offset.x() * sin + offset.z() * cos};
    }

    /** The vehicle's footprint turned by {@code yaw}, as the smallest box along the axes holding it. */
    public static Aabb footprint(VehicleDefinition vehicle, double x, double y, double z, double yaw) {
        double radians = Math.toRadians(yaw);
        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));
        double halfX = (cos * vehicle.width() + sin * vehicle.length()) / 2;
        double halfZ = (sin * vehicle.width() + cos * vehicle.length()) / 2;
        return new Aabb(x - halfX, y, z - halfZ, x + halfX, y + vehicle.height(), z + halfZ);
    }

    // ---------------------------------------------------------------- moving through blocks

    private record Moved(double dx, double dy, double dz, boolean landed) {

        double horizontal() {
            return Math.hypot(dx, dz);
        }

        boolean blockedSideways(double wantX, double wantZ) {
            return Math.abs(dx - wantX) > EPSILON || Math.abs(dz - wantZ) > EPSILON;
        }
    }

    /** Up or down, then x, then z, each as far as nothing is in the way. */
    private static Moved move(Aabb box, double wantX, double wantY, double wantZ, List<Aabb> solid) {
        double dy = clipY(box, wantY, solid);
        Aabb at = box.offset(0, dy, 0);
        double dx = clipX(at, wantX, solid);
        at = at.offset(dx, 0, 0);
        double dz = clipZ(at, wantZ, solid);
        return new Moved(dx, dy, dz, wantY < 0 && dy > wantY + EPSILON);
    }

    /** Lifted by up to {@code step}, moved, and set down again: the way up a slab or a block. */
    private static Moved stepUp(Aabb box, double wantX, double wantY, double wantZ, double step, List<Aabb> solid) {
        double up = clipY(box, step, solid);
        if (up <= EPSILON) {
            return null;
        }
        Aabb raised = box.offset(0, up, 0);
        double dx = clipX(raised, wantX, solid);
        raised = raised.offset(dx, 0, 0);
        double dz = clipZ(raised, wantZ, solid);
        raised = raised.offset(0, 0, dz);
        double down = clipY(raised, -up + Math.min(0, wantY), solid);
        return new Moved(dx, up + down, dz, true);
    }

    private static boolean collides(Aabb box, List<Aabb> solid) {
        for (Aabb other : solid) {
            if (overlaps(box.minX(), box.maxX(), other.minX(), other.maxX())
                    && overlaps(box.minY(), box.maxY(), other.minY(), other.maxY())
                    && overlaps(box.minZ(), box.maxZ(), other.minZ(), other.maxZ())) {
                return true;
            }
        }
        return false;
    }

    private static double clipX(Aabb box, double dx, List<Aabb> solid) {
        for (Aabb other : solid) {
            if (!overlaps(box.minY(), box.maxY(), other.minY(), other.maxY())
                    || !overlaps(box.minZ(), box.maxZ(), other.minZ(), other.maxZ())) {
                continue;
            }
            if (dx > 0 && other.minX() >= box.maxX() - EPSILON) {
                dx = Math.min(dx, other.minX() - box.maxX());
            } else if (dx < 0 && other.maxX() <= box.minX() + EPSILON) {
                dx = Math.max(dx, other.maxX() - box.minX());
            }
        }
        return dx;
    }

    private static double clipY(Aabb box, double dy, List<Aabb> solid) {
        for (Aabb other : solid) {
            if (!overlaps(box.minX(), box.maxX(), other.minX(), other.maxX())
                    || !overlaps(box.minZ(), box.maxZ(), other.minZ(), other.maxZ())) {
                continue;
            }
            if (dy > 0 && other.minY() >= box.maxY() - EPSILON) {
                dy = Math.min(dy, other.minY() - box.maxY());
            } else if (dy < 0 && other.maxY() <= box.minY() + EPSILON) {
                dy = Math.max(dy, other.maxY() - box.minY());
            }
        }
        return dy;
    }

    private static double clipZ(Aabb box, double dz, List<Aabb> solid) {
        for (Aabb other : solid) {
            if (!overlaps(box.minX(), box.maxX(), other.minX(), other.maxX())
                    || !overlaps(box.minY(), box.maxY(), other.minY(), other.maxY())) {
                continue;
            }
            if (dz > 0 && other.minZ() >= box.maxZ() - EPSILON) {
                dz = Math.min(dz, other.minZ() - box.maxZ());
            } else if (dz < 0 && other.maxZ() <= box.minZ() + EPSILON) {
                dz = Math.max(dz, other.maxZ() - box.minZ());
            }
        }
        return dz;
    }

    /** Whether two ranges share more than an edge. */
    private static boolean overlaps(double minA, double maxA, double minB, double maxB) {
        return minA < maxB - EPSILON && maxA > minB + EPSILON;
    }

    private static double toward(double value, double target, double by) {
        return value > target ? Math.max(target, value - by) : Math.min(target, value + by);
    }

    /** Degrees into [-180, 180). */
    static double wrap(double degrees) {
        double wrapped = ((degrees + 180) % 360 + 360) % 360 - 180;
        return wrapped == -180 && degrees > 0 ? -180 : wrapped;
    }
}
