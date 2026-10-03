package com.arkcronist.content.bukkit.furniture;

import com.arkcronist.content.core.animation.AnimatedModel;
import com.arkcronist.content.core.animation.Animator;
import com.arkcronist.content.core.animation.Playback;
import com.arkcronist.content.core.definition.Placement;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Plays Blockbench animations on furniture's bone displays.
 *
 * <p>The server never moves a bone tick by tick. Each {@link Animator.Frame} is one metadata update
 * per bone: the transformation the bone reaches by the next frame, and how many ticks to take. The
 * client glides there by itself, at its own frame rate - so an animation looks as smooth as the
 * client can draw it and costs the server a handful of packets. A lid swinging open in one straight
 * movement is a single update per bone.</p>
 *
 * <p>One task, every tick, runs every animation playing; it stops itself when none is. A piece of
 * furniture plays one animation at a time: a new one takes over from wherever the last one had got
 * to, which is where the client is drawing it.</p>
 *
 * <p>Main thread only: entity metadata can only be changed there.</p>
 */
public final class AnimationPlayer {

    private final Plugin plugin;
    /** What each piece of furniture is playing, by its anchor. */
    private final Map<Object, Playing> playing = new HashMap<>();
    private BukkitTask task;

    private record Playing(List<ItemDisplay> bones, Placement.Display settings, Playback playback) {
    }

    public AnimationPlayer(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts {@code clip} on a piece of furniture's bone displays.
     *
     * @param key   identifies the furniture: a new animation on the same key replaces the old one
     * @param bones by bone index; null where a bone has no model and so no display
     */
    public void play(Object key, List<ItemDisplay> bones, AnimatedModel model, AnimatedModel.Clip clip,
                     Placement.Display settings) {
        Playing animation = new Playing(bones, settings, new Playback(model, clip));
        // The frames at tick 0 go out now, in the same tick as whatever started the animation.
        if (!advance(animation)) {
            playing.remove(key);
            return;
        }
        playing.put(key, animation);
        if (task == null) {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
        }
    }

    /** Puts bones into a pose at once, without gliding: used when they are spawned. */
    public static void pose(List<ItemDisplay> bones, List<Animator.Transform> pose, Placement.Display settings) {
        apply(bones, pose, settings, 0);
    }

    public void stop(Object key) {
        playing.remove(key);
    }

    public void stopAll() {
        playing.clear();
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        for (Iterator<Playing> it = playing.values().iterator(); it.hasNext(); ) {
            Playing animation = it.next();
            if (!advance(animation)) {
                it.remove();
            }
        }
        if (playing.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * Sends the frames due this tick - every bone's transformation, and how long to glide there.
     *
     * @return false once it has nothing more to send, or its displays are gone - unloaded, broken
     */
    private boolean advance(Playing animation) {
        if (animation.bones.stream().allMatch(bone -> bone == null || !bone.isValid())) {
            return false;
        }
        for (Animator.Frame frame : animation.playback.step()) {
            apply(animation.bones, frame.pose(), animation.settings, frame.duration());
        }
        return !animation.playback.finished();
    }

    /** Every bone's transformation, gliding over {@code duration} ticks. */
    private static void apply(List<ItemDisplay> bones, List<Animator.Transform> pose, Placement.Display settings,
                              int duration) {
        for (int i = 0; i < bones.size() && i < pose.size(); i++) {
            ItemDisplay bone = bones.get(i);
            if (bone == null || !bone.isValid()) {
                continue;
            }
            Transformation transformation = transformation(pose.get(i), settings);
            bone.setInterpolationDelay(0);
            bone.setInterpolationDuration(duration);
            bone.setTransformation(transformation);
        }
    }

    /**
     * A bone's transform, carried by the furniture's own {@code display} settings: the whole model
     * is offset, turned and scaled by them, its bones with it.
     */
    static Transformation transformation(Animator.Transform transform, Placement.Display settings) {
        Placement.Vec3 t = transform.translation();
        Animator.Quat q = transform.rotation();
        Placement.Vec3 s = transform.scale();
        Placement.Vec3 offset = settings.translation();
        Placement.Vec3 scale = settings.scale();
        Placement.Vec3 degrees = settings.rotation();
        Quaternionf turn = new Quaternionf().rotationXYZ((float) Math.toRadians(degrees.x()),
                (float) Math.toRadians(degrees.y()), (float) Math.toRadians(degrees.z()));

        Vector3f translation = turn.transform(new Vector3f(t.x() * scale.x(), t.y() * scale.y(), t.z() * scale.z()))
                .add(offset.x(), offset.y(), offset.z());
        Quaternionf rotation = new Quaternionf(turn).mul(new Quaternionf(q.x(), q.y(), q.z(), q.w()));
        Vector3f size = new Vector3f(s.x() * scale.x(), s.y() * scale.y(), s.z() * scale.z());
        return new Transformation(translation, rotation, size, new Quaternionf());
    }
}
