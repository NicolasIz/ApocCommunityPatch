package com.arkcronist.content.core.emotes;

import com.arkcronist.content.core.animation.Animator;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 5.0: a rig's pose as its displays draw it - every bone's transform scaled to the emote's size,
 * and the player's own head put where the rig's head would be.
 *
 * <p>The displays stand at the player's feet, turned the way the player looks: a transform here is
 * in that display's frame, in blocks. A bone drawn by its own model is drawn as
 * {@link Animator#pose} has it. The player's head is a player head item instead, which a display
 * draws with its skull's base half a block below its origin, centred: so the skull's display is
 * lifted half a block along the head bone's own up - over the neck, wherever the head turns - and
 * drawn at the emote's size.</p>
 */
public final class EmotePose {

    /** How far a player head item's skull is drawn below its display's origin, in blocks at size 1. */
    static final float SKULL_DROP = 0.5f;

    private EmotePose() {
    }

    /** One bone display's transformation: translation, left rotation, scale. */
    public record Part(float tx, float ty, float tz, float qx, float qy, float qz, float qw, float sx, float sy,
                       float sz) {

        public Vector3f translation() {
            return new Vector3f(tx, ty, tz);
        }

        public Quaternionf rotation() {
            return new Quaternionf(qx, qy, qz, qw);
        }

        public Vector3f scale() {
            return new Vector3f(sx, sy, sz);
        }
    }

    /**
     * Every bone of {@code emote}'s rig in {@code pose}, by bone index; null for a bone with no
     * display.
     */
    public static List<Part> of(EmoteDefinition emote, List<Animator.Transform> pose) {
        float size = emote.scale();
        int head = emote.playerHead() ? emote.headBone() : -1;
        List<Part> parts = new ArrayList<>(pose.size());
        for (int i = 0; i < pose.size(); i++) {
            if (!emote.drawn(i)) {
                parts.add(null);
                continue;
            }
            Animator.Transform transform = pose.get(i);
            Vector3f translation = new Vector3f(transform.translation().x(), transform.translation().y(),
                    transform.translation().z()).mul(size);
            Animator.Quat q = transform.rotation();
            Quaternionf rotation = new Quaternionf(q.x(), q.y(), q.z(), q.w());
            Vector3f scale = new Vector3f(transform.scale().x(), transform.scale().y(), transform.scale().z()).mul(size);
            if (i == head) {
                translation.add(rotation.transform(new Vector3f(0, SKULL_DROP * size, 0)));
                scale.set(size);
            }
            parts.add(new Part(translation.x, translation.y, translation.z, rotation.x, rotation.y, rotation.z,
                    rotation.w, scale.x, scale.y, scale.z));
        }
        return parts;
    }
}
