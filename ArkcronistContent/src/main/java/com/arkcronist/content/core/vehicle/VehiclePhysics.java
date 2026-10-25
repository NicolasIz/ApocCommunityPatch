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
 *
 * <p>5.0: the body leans with the ground. Under each axle - and each side - the height of the ground
 * is probed, and the body is pitched and rolled to sit on it, and lowered when an end hangs off a
 * step: only what is drawn, as the footprint it collides with stays level. Other vehicles are
 * obstacles too, as turned rectangles: a vehicle is stopped at the first touch and thrown back, a
 * share of its speed the other way.</p>
 */
public final class VehiclePhysics {

    /** Blocks a second squared: about what vanilla pulls a falling minecart down by. */
    public static final double GRAVITY = 28;
    public static final double TERMINAL_SPEED = 40;
    /** Below this speed (blocks a second) the steering is weaker: a vehicle turns as it rolls. */
    static final double FULL_LOCK_SPEED = 2;
    private static final double EPSILON = 1e-7;
    /** The share of its speed a vehicle keeps, the other way, when it runs into another. */
    public static final double RESTITUTION = 0.3;
    /** The steepest a body leans, either way, in degrees. */
    public static final double MAX_TILT = 30;
    /** How quickly the lean follows the ground: a time constant, in seconds. */
    private static final double TILT_SECONDS = 0.08;

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
     * @param pitch    5.0: how far its nose is up, in degrees; down is negative
     * @param roll     5.0: how far its left side is up, in degrees; the right side up is negative
     * @param lift     5.0: how far its body is drawn above {@code y}, to sit on the ground under its
     *                 axles; below is negative
     */
    public record State(double x, double y, double z, double yaw, double speed, double vy, boolean onGround,
                        double rolled, double pitch, double roll, double lift) {

        /** Level, as a vehicle is before the ground under it is known. */
        public State(double x, double y, double z, double yaw, double speed, double vy, boolean onGround,
                     double rolled) {
            this(x, y, z, yaw, speed, vy, onGround, rolled, 0, 0, 0);
        }

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
     * 5.0: a vehicle as another one sees it - where its footprint is, turned, and how tall it is.
     *
     * @param x      the middle of its footprint
     * @param y      the bottom of its footprint
     * @param z      the middle of its footprint
     * @param yaw    its heading, in degrees
     * @param width  across, in blocks
     * @param length front to back, in blocks
     * @param height in blocks
     */
    public record Body(double x, double y, double z, double yaw, double width, double length, double height) {

        public static Body of(VehicleDefinition vehicle, State state) {
            return new Body(state.x(), state.y(), state.z(), state.yaw(), vehicle.width(), vehicle.length(),
                    vehicle.height());
        }

        Body movedTo(double x, double y, double z, double yaw) {
            return new Body(x, y, z, yaw, width, length, height);
        }

        /**
         * Whether the two touch: their heights overlap, and their turned footprints do - no axis of
         * either rectangle separates them.
         */
        public boolean overlaps(Body other) {
            if (y >= other.y + other.height - EPSILON || other.y >= y + height - EPSILON) {
                return false;
            }
            double dx = other.x - x;
            double dz = other.z - z;
            for (Body side : new Body[]{this, other}) {
                double radians = Math.toRadians(side.yaw);
                double sin = Math.sin(radians);
                double cos = Math.cos(radians);
                // Its forward (-sin, cos) and its right (-cos, -sin).
                double[][] axes = {{-sin, cos}, {-cos, -sin}};
                for (double[] axis : axes) {
                    double gap = Math.abs(dx * axis[0] + dz * axis[1]);
                    if (gap >= reach(this, axis) + reach(other, axis) - EPSILON) {
                        return false;
                    }
                }
            }
            return true;
        }

        /** Half the footprint's extent along {@code axis}. */
        private static double reach(Body body, double[] axis) {
            double radians = Math.toRadians(body.yaw);
            double sin = Math.sin(radians);
            double cos = Math.cos(radians);
            double forward = Math.abs(-sin * axis[0] + cos * axis[1]);
            double right = Math.abs(-cos * axis[0] - sin * axis[1]);
            return forward * body.length / 2 + right * body.width / 2;
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

    /** One step of {@code seconds}, with no other vehicle about: the vehicle's next state. */
    public static State step(VehicleDefinition vehicle, State state, Input input, Terrain terrain, double seconds) {
        return step(vehicle, state, input, terrain, List.of(), seconds);
    }

    /**
     * One step of {@code seconds}: the vehicle's next state. {@code others} are the vehicles around
     * it; one it already touches when the step starts - two put down too close - is let be, so the
     * two can drive apart.
     */
    public static State step(VehicleDefinition vehicle, State state, Input input, Terrain terrain, List<Body> others,
                             double seconds) {
        Body start = Body.of(vehicle, state);
        List<Body> obstacles = new java.util.ArrayList<>(others.size());
        for (Body other : others) {
            if (!start.overlaps(other)) {
                obstacles.add(other);
            }
        }
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
            if (!collides(footprint(vehicle, state.x(), state.y(), state.z(), turned), terrain.solid())
                    && !touches(start.movedTo(state.x(), state.y(), state.z(), turned), obstacles)) {
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
        // Another vehicle in the way: as far as the first touch, and thrown back.
        double share = reachable(start, yaw, moved, obstacles);
        if (share < 1) {
            moved = new Moved(moved.dx() * share, moved.dy(), moved.dz() * share, moved.landed());
            speed = -RESTITUTION * speed;
        }
        boolean onGround = moved.landed() || (wantY <= 0 && moved.dy() > wantY + EPSILON);
        if (onGround || (wantY > 0 && moved.dy() < wantY - EPSILON)) {
            vy = 0;
        }
        // Wheels roll the way it went along its heading: back, after a bump, turns them back.
        double along = -Math.sin(radians) * moved.dx() + Math.cos(radians) * moved.dz();
        double rolled = state.rolled() + Math.signum(along) * moved.horizontal();
        speed = Math.abs(speed) < 1e-3 ? 0 : speed;
        State moved1 = new State(state.x() + moved.dx(), state.y() + moved.dy(), state.z() + moved.dz(), yaw, speed,
                vy, onGround, rolled, state.pitch(), state.roll(), state.lift());
        return lean(vehicle, moved1, terrain.solid(), seconds);
    }

    /**
     * How much of {@code moved}'s way along the ground {@code start}, turned to {@code yaw}, can go
     * before it touches one of {@code obstacles}: 1 for all of it.
     */
    private static double reachable(Body start, double yaw, Moved moved, List<Body> obstacles) {
        if (obstacles.isEmpty() || moved.horizontal() < EPSILON) {
            return 1;
        }
        if (!touches(start.movedTo(start.x() + moved.dx(), start.y() + moved.dy(), start.z() + moved.dz(), yaw),
                obstacles)) {
            return 1;
        }
        double free = 0;
        double blocked = 1;
        for (int i = 0; i < 12; i++) {
            double mid = (free + blocked) / 2;
            Body at = start.movedTo(start.x() + moved.dx() * mid, start.y() + moved.dy(), start.z() + moved.dz() * mid,
                    yaw);
            if (touches(at, obstacles)) {
                blocked = mid;
            } else {
                free = mid;
            }
        }
        return free;
    }

    private static boolean touches(Body body, List<Body> obstacles) {
        for (Body other : obstacles) {
            if (body.overlaps(other)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code state} leaning with the ground under its axles: eased toward it while it moves, at
     * once when it has stopped - a parked vehicle sits as the ground has it. In the air it levels out.
     */
    static State lean(VehicleDefinition vehicle, State state, List<Aabb> solid, double seconds) {
        double[] target = state.onGround() ? groundUnder(vehicle, state, solid) : new double[]{0, 0, 0};
        double k = state.speed() == 0 && state.onGround() ? 1 : 1 - Math.exp(-seconds / TILT_SECONDS);
        double pitch = state.pitch() + (target[0] - state.pitch()) * k;
        double roll = state.roll() + (target[1] - state.roll()) * k;
        double lift = state.lift() + (target[2] - state.lift()) * k;
        return new State(state.x(), state.y(), state.z(), state.yaw(), state.speed(), state.vy(), state.onGround(),
                state.rolled(), Math.abs(pitch) < 1e-4 ? 0 : pitch, Math.abs(roll) < 1e-4 ? 0 : roll,
                Math.abs(lift) < 1e-5 ? 0 : lift);
    }

    /**
     * The pitch, roll and lift the ground under {@code state}'s axles asks for: {pitch, roll, lift}.
     * The axles are where its wheels are; without wheels, four tenths of the way to each end and side.
     */
    static double[] groundUnder(VehicleDefinition vehicle, State state, List<Aabb> solid) {
        double front = vehicle.length() * 0.4;
        double back = -front;
        double right = vehicle.width() * 0.4;
        double left = -right;
        double maxZ = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minX = Double.POSITIVE_INFINITY;
        for (VehicleDefinition.Part part : vehicle.parts()) {
            if (part.wheel()) {
                maxZ = Math.max(maxZ, part.offset().z());
                minZ = Math.min(minZ, part.offset().z());
                maxX = Math.max(maxX, part.offset().x());
                minX = Math.min(minX, part.offset().x());
            }
        }
        if (maxZ - minZ > 0.1) {
            front = maxZ;
            back = minZ;
        }
        if (maxX - minX > 0.1) {
            right = maxX;
            left = minX;
        }
        State level = new State(state.x(), state.y(), state.z(), state.yaw(), 0, 0, true, 0);
        double drop = vehicle.stepHeight() + 0.5;
        double hFront = ground(level, new Offset(0, 0, front), solid, vehicle.stepHeight(), drop);
        double hBack = ground(level, new Offset(0, 0, back), solid, vehicle.stepHeight(), drop);
        double hRight = ground(level, new Offset(right, 0, 0), solid, vehicle.stepHeight(), drop);
        double hLeft = ground(level, new Offset(left, 0, 0), solid, vehicle.stepHeight(), drop);
        double pitch = clamp(Math.toDegrees(Math.atan2(hFront - hBack, front - back)), MAX_TILT);
        double roll = clamp(Math.toDegrees(Math.atan2(hLeft - hRight, right - left)), MAX_TILT);
        double lift = (hFront + hBack + hLeft + hRight) / 4;
        return new double[]{pitch, roll, lift};
    }

    /**
     * How far above (or below) {@code state.y()} the ground under {@code offset} is: the top of the
     * highest collision box there, no higher than {@code up} - a step - and no lower than
     * {@code down} - past that it hangs over a drop, and counts as {@code -down}.
     */
    private static double ground(State state, Offset offset, List<Aabb> solid, double up, double down) {
        double[] at = point(state, offset);
        double best = Double.NEGATIVE_INFINITY;
        for (Aabb box : solid) {
            if (at[0] >= box.minX() - EPSILON && at[0] <= box.maxX() + EPSILON && at[2] >= box.minZ() - EPSILON
                    && at[2] <= box.maxZ() + EPSILON && box.maxY() <= state.y() + up + EPSILON && box.maxY() > best) {
                best = box.maxY();
            }
        }
        return best < state.y() - down ? -down : best - state.y();
    }

    private static double clamp(double value, double limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    /**
     * {@code offset} in the vehicle's frame, in the world: {x, y, z}. The frame leans with the body
     * (5.0): pitched and rolled about the middle of the footprint, raised by its lift.
     */
    public static double[] point(State state, Offset offset) {
        // In the model's axes: +x is the vehicle's left, +y up, +z its front.
        double mx = -offset.x();
        double my = offset.y();
        double mz = offset.z();
        if (state.roll() != 0) {
            // Left side up: about the front axis.
            double r = Math.toRadians(state.roll());
            double x = mx * Math.cos(r) - my * Math.sin(r);
            double y = mx * Math.sin(r) + my * Math.cos(r);
            mx = x;
            my = y;
        }
        if (state.pitch() != 0) {
            // Nose up: about the left axis.
            double p = Math.toRadians(state.pitch());
            double y = my * Math.cos(p) + mz * Math.sin(p);
            double z = -my * Math.sin(p) + mz * Math.cos(p);
            my = y;
            mz = z;
        }
        double radians = Math.toRadians(state.yaw());
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        // Forward is (-sin, 0, cos); the model's +x (the vehicle's left) is (cos, 0, sin).
        return new double[]{
                state.x() + mx * cos - mz * sin,
                state.y() + state.lift() + my,
                state.z() + mx * sin + mz * cos};
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
