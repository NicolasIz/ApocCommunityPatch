package com.arkcronist.content.core.cosmetic;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Where a back cosmetic's display puts its model for a {@link BackPhysics.Pose}: the transformation
 * of an item display riding the wearer, whose origin is where a rider sits - the top of the
 * wearer's head - and which never turns by itself, so the body's yaw is in the transformation too.
 *
 * <p>The model hangs from a pivot at the shoulders: the swing turns it about that point, so its
 * bottom moves most. Axes are those of a wearer facing south before their yaw is applied: x to their
 * left, y up, z ahead of them - the cosmetic's offset counts z behind, so it is negated here.</p>
 */
public final class BackPose {

    /** From where a rider sits down to the wearer's shoulders, standing. */
    static final float SHOULDERS = -0.42f;
    /** How far behind the body's middle the back is. */
    static final float BACK = 0.14f;
    static final float SNEAK_LIFT = 0.12f;
    static final float SNEAK_FORWARD = 0.12f;

    /**
     * @param translation where the model's middle goes, from the display
     * @param rotation    how the model is turned - the display's left rotation
     */
    public record Transform(Vector3f translation, Quaternionf rotation, Vector3f scale) {
    }

    private BackPose() {
    }

    public static Transform of(CosmeticDefinition cosmetic, BackPhysics.Pose pose, float bodyYaw, boolean sneaking) {
        float s = cosmetic.scale();
        Vector3f pivot = new Vector3f(0, SHOULDERS, -BACK);
        if (sneaking) {
            // A sneaking wearer is 0.3 shorter, so a rider sits lower; bent forward, their shoulders
            // are a little less far below that, and further forward.
            pivot.add(0, SNEAK_LIFT, SNEAK_FORWARD);
        }
        Vector3f centre = new Vector3f(cosmetic.offset().x(), SHOULDERS + cosmetic.offset().y(),
                -cosmetic.offset().z());
        if (sneaking) {
            centre.add(0, SNEAK_LIFT, SNEAK_FORWARD);
        }
        // Swing out behind: about the wearer's left-right axis, bottom going back (-z).
        Quaternionf swing = new Quaternionf()
                .rotateY((float) pose.twist())
                .rotateX((float) pose.swing())
                .rotateZ((float) -pose.roll());
        Vector3f hanging = new Vector3f(centre).sub(pivot);
        swing.transform(hanging);
        Vector3f placed = new Vector3f(pivot).add(hanging).add(0, (float) -pose.drop(), 0);
        Quaternionf yaw = new Quaternionf().rotateY((float) Math.toRadians(-bodyYaw));
        yaw.transform(placed);
        Quaternionf rotation = new Quaternionf(yaw).mul(swing);
        return new Transform(placed, rotation, new Vector3f(s, s, s));
    }
}
