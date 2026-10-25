package com.arkcronist.content.core.vehicle;

import com.arkcronist.content.core.vehicle.VehicleDefinition.Offset;
import com.arkcronist.content.core.vehicle.VehicleDefinition.Part;
import com.arkcronist.content.core.vehicle.VehiclePhysics.State;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 4.0: how each display of a vehicle is turned and placed, for a {@link State}.
 *
 * <p>The displays ride the driver's seat, so the client carries them along with it; each is drawn
 * from there by its transformation alone - moved to its own place on the vehicle, turned to the
 * vehicle's heading - leaning with the ground, from 5.0 - and, for a wheel, rolled. A model faces
 * the vehicle's front with its own south
 * side (+z), as Blockbench shows a model's front.</p>
 *
 * <p>The client draws an item display's model turned half a turn about y before its transformation
 * ({@link #CLIENT_TURN}); each rotation here ends with that half turn again, which undoes it.</p>
 */
public final class VehiclePose {

    /** What the client does to an item display's model before its transformation: half a turn about y. */
    public static final Quaternionf CLIENT_TURN = new Quaternionf().rotationY((float) Math.PI);

    private VehiclePose() {
    }

    /**
     * One display's transformation.
     *
     * @param translation from the seat it rides to where its model's centre is, in blocks
     * @param rotation    its heading and, for a wheel, how far it has rolled - after the client's own
     *                    half turn ({@link #CLIENT_TURN})
     * @param scale       the same on every axis
     */
    public record Transform(Vector3f translation, Quaternionf rotation, float scale) {
    }

    /** {@code part}'s transformation, riding the seat at {@code anchor}. */
    public static Transform of(State state, Part part, Offset anchor) {
        double[] at = VehiclePhysics.point(state, part.offset());
        double[] seat = VehiclePhysics.point(state, anchor);
        Vector3f translation = new Vector3f((float) (at[0] - seat[0]), (float) (at[1] - seat[1]),
                (float) (at[2] - seat[2]));
        // A turn of -yaw about y takes a model's south (+z) to the vehicle's forward, (-sin, 0, cos);
        // before it (5.0), the lean: nose up about the model's x, left side up about its z - in the
        // order VehiclePhysics.point leans an offset.
        Quaternionf rotation = new Quaternionf().rotationY((float) Math.toRadians(-state.yaw()));
        if (state.pitch() != 0) {
            rotation.rotateX((float) Math.toRadians(-state.pitch()));
        }
        if (state.roll() != 0) {
            rotation.rotateZ((float) Math.toRadians(state.roll()));
        }
        if (part.wheel()) {
            // Rolling forward turns its top forward: about its own x axis, which the heading has
            // already turned with it.
            rotation.rotateX((float) (state.rolled() / part.radius()));
        }
        rotation.rotateY((float) Math.PI);
        return new Transform(translation, rotation, (float) part.scale());
    }

    /**
     * Whether a client would see the difference: under a millimetre and a tenth of a degree from
     * what was sent last is not worth a packet.
     */
    public static boolean same(Transform before, Transform now) {
        return before.translation().distanceSquared(now.translation()) < 1e-6
                && Math.abs(dot(before.rotation(), now.rotation())) > 1 - 1e-6
                && Math.abs(before.scale() - now.scale()) < 1e-4f;
    }

    /**
     * Written out, not JOML's {@code Quaternionf.dot}: that takes a {@code Quaternionf} in the JOML
     * 1.21.x and 26.1/26.2 servers ship (1.10.8) and a {@code Quaternionfc} in 26.3's (1.10.9), so a
     * call compiled against one is missing from the other.
     */
    private static float dot(Quaternionf a, Quaternionf b) {
        return a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w;
    }
}
