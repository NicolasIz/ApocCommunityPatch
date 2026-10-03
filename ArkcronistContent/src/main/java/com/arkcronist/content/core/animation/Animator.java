package com.arkcronist.content.core.animation;

import com.arkcronist.content.core.definition.Placement;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Where every bone of an {@link AnimatedModel} is at any moment of an animation, and how to play
 * that on item displays.
 *
 * <h2>Poses</h2>
 * <p>A bone's transform is its rest pose plus the animation's value at that moment - position
 * added, rotation added in degrees then turned into a quaternion around Z, Y and X in that order,
 * scale multiplied - and then carried by its parent: the parent's rotation turns the bone's
 * offset, the parent's scale stretches it, the parent's position adds to it. The result is in the
 * furniture's frame, which is exactly what an item display's transformation is.</p>
 *
 * <h2>Playing</h2>
 * <p>An item display moves smoothly on its own: given a new transformation and an interpolation
 * duration, the client glides to it over that many ticks. So an animation is played as a short list
 * of {@link Frame}s - at the frame's tick, send the pose the animation reaches by the next frame,
 * with the ticks in between as the duration - and the client draws everything in between. Frames
 * fall on every keyframe; smooth (catmullrom) stretches get one every two ticks, since the client
 * only interpolates in straight lines.</p>
 */
public final class Animator {

    /** How often a smooth curve is sampled, in ticks. */
    static final int SMOOTH_STEP = 2;

    private Animator() {
    }

    /** A rotation as a unit quaternion. */
    public record Quat(float x, float y, float z, float w) {

        public static final Quat IDENTITY = new Quat(0, 0, 0, 1);
    }

    /**
     * A bone display's transformation.
     *
     * @param translation where the bone's pivot is, in blocks from the furniture's origin
     * @param scale       including the bone model's own {@link AnimatedModel.Bone#modelScale()}
     */
    public record Transform(Placement.Vec3 translation, Quat rotation, Placement.Vec3 scale) {
    }

    /**
     * One step of an animation on the displays: at {@code tick}, give every bone display its
     * transform from {@code pose}, gliding there over {@code duration} ticks.
     */
    public record Frame(int tick, int duration, List<Transform> pose) {

        public Frame {
            pose = List.copyOf(pose);
        }
    }

    /** Every bone at rest. */
    public static List<Transform> rest(AnimatedModel model) {
        return pose(model, null, 0);
    }

    /** Every bone at {@code tick} ticks into {@code clip}; null for the rest pose. */
    public static List<Transform> pose(AnimatedModel model, @Nullable AnimatedModel.Clip clip, float tick) {
        List<AnimatedModel.Bone> bones = model.bones();
        Vector3f[] positions = new Vector3f[bones.size()];
        Quaternionf[] rotations = new Quaternionf[bones.size()];
        Vector3f[] scales = new Vector3f[bones.size()];
        List<Transform> pose = new ArrayList<>(bones.size());
        for (int i = 0; i < bones.size(); i++) {
            AnimatedModel.Bone bone = bones.get(i);
            AnimatedModel.Channels channels = clip == null ? null : clip.channels().get(i);

            Vector3f position = vector(bone.offset());
            Vector3f degrees = vector(bone.restRotation());
            Vector3f scale = new Vector3f(1);
            if (channels != null) {
                position.add(value(channels.position(), tick, 0));
                degrees.add(value(channels.rotation(), tick, 0));
                scale.mul(value(channels.scale(), tick, 1));
            }
            Quaternionf rotation = quaternion(degrees);

            if (bone.parent() >= 0) {
                int parent = bone.parent();
                rotations[parent].transform(position).mul(scales[parent]).add(positions[parent]);
                rotation = new Quaternionf(rotations[parent]).mul(rotation);
                scale.mul(scales[parent]);
            }
            positions[i] = position;
            rotations[i] = rotation;
            scales[i] = scale;
            Vector3f drawn = new Vector3f(scale).mul(bone.modelScale());
            pose.add(new Transform(vec(position), new Quat(clean(rotation.x), clean(rotation.y), clean(rotation.z),
                    clean(rotation.w)), vec(drawn)));
        }
        return pose;
    }

    /** {@code clip} as frames for the displays, in tick order. */
    public static List<Frame> frames(AnimatedModel model, AnimatedModel.Clip clip) {
        TreeSet<Integer> ticks = new TreeSet<>();
        ticks.add(0);
        ticks.add(clip.length());
        for (AnimatedModel.Channels channels : clip.channels().values()) {
            for (List<AnimatedModel.Keyframe> keys : List.of(channels.position(), channels.rotation(), channels.scale())) {
                for (int i = 0; i < keys.size(); i++) {
                    int at = clamp(Math.round(keys.get(i).tick()), clip.length());
                    ticks.add(at);
                    if (i + 1 < keys.size()) {
                        AnimatedModel.Keyframe a = keys.get(i);
                        AnimatedModel.Keyframe b = keys.get(i + 1);
                        int next = clamp(Math.round(b.tick()), clip.length());
                        if (smooth(a, b)) {
                            for (int t = at + SMOOTH_STEP; t < next; t += SMOOTH_STEP) {
                                ticks.add(t);
                            }
                        } else if (a.interpolation() == AnimatedModel.Interpolation.STEP && next - 1 > at) {
                            // Held until the tick before, then a one-tick jump.
                            ticks.add(next - 1);
                        }
                    }
                }
            }
        }
        List<Integer> ordered = new ArrayList<>(ticks);
        List<Frame> frames = new ArrayList<>();
        for (int i = 0; i + 1 < ordered.size(); i++) {
            int from = ordered.get(i);
            int to = ordered.get(i + 1);
            frames.add(new Frame(from, to - from, pose(model, clip, to)));
        }
        if (clip.loop() == AnimatedModel.Loop.ONCE) {
            // Blockbench puts a played-once animation back to rest the moment it ends.
            frames.add(new Frame(clip.length(), 0, rest(model)));
        }
        return frames;
    }

    /** A channel's value at {@code tick}: before the first keyframe its value, after the last, the last's. */
    static Vector3f value(List<AnimatedModel.Keyframe> keys, float tick, float fallback) {
        if (keys.isEmpty()) {
            return new Vector3f(fallback);
        }
        if (tick <= keys.getFirst().tick()) {
            return vector(keys.getFirst().value());
        }
        if (tick >= keys.getLast().tick()) {
            return vector(keys.getLast().value());
        }
        int i = 0;
        while (keys.get(i + 1).tick() <= tick) {
            i++;
        }
        AnimatedModel.Keyframe a = keys.get(i);
        AnimatedModel.Keyframe b = keys.get(i + 1);
        float t = (tick - a.tick()) / (b.tick() - a.tick());
        if (a.interpolation() == AnimatedModel.Interpolation.STEP) {
            return vector(a.value());
        }
        if (smooth(a, b)) {
            Vector3f before = vector(keys.get(Math.max(0, i - 1)).value());
            Vector3f after = vector(keys.get(Math.min(keys.size() - 1, i + 2)).value());
            return catmullRom(before, vector(a.value()), vector(b.value()), after, t);
        }
        return vector(a.value()).lerp(vector(b.value()), t);
    }

    /** As Blockbench decides: smooth if either end of the stretch asks for it. */
    private static boolean smooth(AnimatedModel.Keyframe a, AnimatedModel.Keyframe b) {
        return a.interpolation() == AnimatedModel.Interpolation.SMOOTH || b.interpolation() == AnimatedModel.Interpolation.SMOOTH;
    }

    /** The uniform Catmull-Rom spline through p1 and p2, guided by p0 and p3. */
    static Vector3f catmullRom(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        Vector3f out = new Vector3f();
        for (int axis = 0; axis < 3; axis++) {
            float a = p0.get(axis);
            float b = p1.get(axis);
            float c = p2.get(axis);
            float d = p3.get(axis);
            out.setComponent(axis, 0.5f * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3));
        }
        return out;
    }

    /** Degrees around X, Y and Z, applied Z first, then Y, then X - Blockbench's order. */
    static Quaternionf quaternion(Vector3f degrees) {
        return new Quaternionf().rotateZYX((float) Math.toRadians(degrees.z), (float) Math.toRadians(degrees.y),
                (float) Math.toRadians(degrees.x));
    }

    private static int clamp(int tick, int length) {
        return Math.max(0, Math.min(length, tick));
    }

    private static Vector3f vector(Placement.Vec3 value) {
        return new Vector3f(value.x(), value.y(), value.z());
    }

    private static Placement.Vec3 vec(Vector3f value) {
        return new Placement.Vec3(clean(value.x), clean(value.y), clean(value.z));
    }

    /** Rounded to a millionth, and never -0, so equal poses compare equal. */
    private static float clean(float value) {
        float rounded = Math.round(value * 1_000_000f) / 1_000_000f;
        return rounded == 0 ? 0 : rounded;
    }
}
