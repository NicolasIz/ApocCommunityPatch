package com.arkcronist.content.core.cosmetic;

/**
 * How a cosmetic on the back swings as its wearer moves (3.0): a damped spring for each way it can
 * move - swinging out behind, swinging to the side, twisting, sagging - driven by the wearer's
 * acceleration as their body feels it. Start running and the bag swings out behind, stop and it
 * swings back against the back and settles; turn sharply and it lags and twists; land from a jump
 * and it sags and bounces back.
 *
 * <p>Pure and single-threaded: fed {@link Sample samples} of where the wearer is, it works out the
 * rest. The plugin runs it on its own thread, {@code ArkContent-Physics}; nothing here touches the
 * server.</p>
 *
 * <p>The wearer's velocity is taken between samples that differ - the client sends where it is at
 * most once a tick, and a sample seen twice is not a stop - and the springs are stepped in slices of
 * a few milliseconds, semi-implicitly, so a long gap cannot make them blow up.</p>
 */
public final class BackPhysics {

    /** Swing per unit of acceleration, rad/s² per m/s², at sway 1. */
    static final double SWING_GAIN = 0.6;
    /** Twist per unit of the body's angular acceleration, at sway 1. */
    static final double TWIST_GAIN = 0.08;
    /** Sag per unit of vertical acceleration, blocks/s² per m/s², at sway 1. */
    static final double DROP_GAIN = 0.05;
    /** The farthest a bag sags or lifts, in blocks. */
    static final double MAX_DROP = 0.12;
    /** A bag cannot swing far into the back it hangs on. */
    static final double MAX_FORWARD = Math.toRadians(5);
    /** How far a sneaking wearer's lean tilts the bag. */
    static final double SNEAK_TILT = 0.4;
    /** A sample older than this is a wearer standing still. */
    static final long STALE_NANOS = 120_000_000L;
    /** Farther than this between two samples is a teleport, not movement. */
    static final double TELEPORT = 4;
    private static final double STEP = 0.005;

    /**
     * Where the wearer was, as the server saw it.
     *
     * @param bodyYaw the way their body faces, degrees, Minecraft's: 0 south, 90 west
     */
    public record Sample(double x, double y, double z, float bodyYaw, boolean sneaking, long nanos) {
    }

    /**
     * The cosmetic's pose: angles in radians - {@code swing} out behind the wearer, {@code roll}
     * to their right, {@code twist} turning with them - and {@code drop}, how far it sags, in blocks.
     */
    public record Pose(double swing, double roll, double twist, double drop) {

        public static final Pose REST = new Pose(0, 0, 0, 0);
    }

    private final CosmeticDefinition.Physics physics;
    private double swing;
    private double swingSpeed;
    private double roll;
    private double rollSpeed;
    private double twist;
    private double twistSpeed;
    private double drop;
    private double dropSpeed;

    private Sample last;
    private double vx;
    private double vy;
    private double vz;
    private double yawRate;
    private long clock = Long.MIN_VALUE;

    public BackPhysics(CosmeticDefinition.Physics physics) {
        this.physics = physics;
    }

    public Pose pose() {
        return new Pose(swing, roll, twist, drop);
    }

    /**
     * Moves the springs on to {@code now}, taking in {@code sample} - the latest the server has,
     * which may be one already seen.
     */
    public Pose step(Sample sample, long now) {
        if (clock == Long.MIN_VALUE) {
            clock = now;
            last = sample;
            return pose();
        }
        double driveForward = 0;
        double driveRight = 0;
        double driveUp = 0;
        double driveTurn = 0;
        if (sample.nanos() != last.nanos()) {
            double dt = (sample.nanos() - last.nanos()) / 1e9;
            double dx = sample.x() - last.x();
            double dy = sample.y() - last.y();
            double dz = sample.z() - last.z();
            if (dt <= 0 || dt > 0.5 || dx * dx + dy * dy + dz * dz > TELEPORT * TELEPORT) {
                // A teleport, a respawn, or a long gap: no acceleration to read from it.
                vx = vy = vz = 0;
                yawRate = 0;
            } else {
                double nvx = dx / dt;
                double nvy = dy / dt;
                double nvz = dz / dt;
                double ax = clamp((nvx - vx) / dt, 60);
                double ay = clamp((nvy - vy) / dt, 250);
                double az = clamp((nvz - vz) / dt, 60);
                double yaw = Math.toRadians(sample.bodyYaw());
                // Facing (-sin, 0, cos); the wearer's right is (-cos, 0, -sin).
                driveForward = ax * -Math.sin(yaw) + az * Math.cos(yaw);
                driveRight = ax * -Math.cos(yaw) + az * -Math.sin(yaw);
                driveUp = ay;
                double rate = Math.toRadians(wrap(sample.bodyYaw() - last.bodyYaw())) / dt;
                driveTurn = clamp((rate - yawRate) / dt, 400);
                vx = nvx;
                vy = nvy;
                vz = nvz;
                yawRate = rate;
            }
            last = sample;
        } else if (now - sample.nanos() > STALE_NANOS && (vx != 0 || vy != 0 || vz != 0)) {
            // Nothing new for a while: they have stopped. That is a deceleration too.
            double dt = Math.max(0.05, (now - sample.nanos()) / 1e9);
            double yaw = Math.toRadians(sample.bodyYaw());
            driveForward = -(vx * -Math.sin(yaw) + vz * Math.cos(yaw)) / dt;
            driveRight = -(vx * -Math.cos(yaw) + vz * -Math.sin(yaw)) / dt;
            driveUp = -vy / dt;
            vx = vy = vz = 0;
            yawRate = 0;
            last = new Sample(sample.x(), sample.y(), sample.z(), sample.bodyYaw(), sample.sneaking(), now);
        }
        double seconds = Math.min(0.25, Math.max(0, (now - clock) / 1e9));
        clock = now;
        // The drive acts over the interval the samples span, spread over this step.
        integrate(seconds, driveForward, driveRight, driveUp, driveTurn, sample.sneaking());
        return pose();
    }

    private void integrate(double seconds, double forward, double right, double up, double turn, boolean sneaking) {
        double k = physics.stiffness();
        double c = physics.damping();
        double sway = physics.sway();
        double max = Math.toRadians(physics.maxAngle());
        double rest = sneaking ? Math.min(SNEAK_TILT, max) : 0;
        int slices = (int) Math.ceil(seconds / STEP);
        double h = slices == 0 ? 0 : seconds / slices;
        for (int i = 0; i < slices; i++) {
            swingSpeed += h * (-k * (swing - rest) - c * swingSpeed + sway * SWING_GAIN * forward);
            rollSpeed += h * (-k * roll - c * rollSpeed - sway * SWING_GAIN * right);
            twistSpeed += h * (-k * twist - c * twistSpeed - sway * TWIST_GAIN * turn);
            dropSpeed += h * (-2.5 * k * drop - 1.2 * c * dropSpeed + sway * DROP_GAIN * up);
            swing += h * swingSpeed;
            roll += h * rollSpeed;
            twist += h * twistSpeed;
            drop += h * dropSpeed;
            double bounce = physics.bounce();
            if (swing > max) {
                swing = max;
                swingSpeed = -Math.abs(swingSpeed) * bounce;
            } else if (swing < -Math.min(MAX_FORWARD, max)) {
                swing = -Math.min(MAX_FORWARD, max);
                swingSpeed = Math.abs(swingSpeed) * bounce;
            }
            if (Math.abs(roll) > max) {
                roll = Math.copySign(max, roll);
                rollSpeed = -rollSpeed * bounce;
            }
            if (Math.abs(twist) > max / 2) {
                twist = Math.copySign(max / 2, twist);
                twistSpeed = -twistSpeed * bounce;
            }
            if (Math.abs(drop) > MAX_DROP) {
                drop = Math.copySign(MAX_DROP, drop);
                dropSpeed = -dropSpeed * bounce;
            }
        }
    }

    private static double clamp(double value, double limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    /** An angle difference in degrees, brought into -180 to 180. */
    private static double wrap(double degrees) {
        double wrapped = degrees % 360;
        if (wrapped > 180) {
            wrapped -= 360;
        } else if (wrapped < -180) {
            wrapped += 360;
        }
        return wrapped;
    }
}
