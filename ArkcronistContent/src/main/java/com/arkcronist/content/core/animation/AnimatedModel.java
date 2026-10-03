package com.arkcronist.content.core.animation;

import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.definition.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Blockbench model with bones and animations, turned into what Minecraft can draw: one item model
 * per bone, shown by one item display each, moved by changing those displays' transformations.
 *
 * <p>Every coordinate here is already in the displays' frame: in blocks, measured from the model's
 * origin - the bottom centre of the block it stands on - with X and Z mirrored, because an item
 * display draws its model turned half a turn around Y. Blockbench's own numbers stay in
 * {@link BbModelReader}.</p>
 *
 * @param bones    parents before children; a bone's index is its place in this list
 * @param clips    animations by name, as named in Blockbench
 * @param textures the images the bone models use, written into the pack as they are
 * @param icon     a single model of the whole thing at rest, for the item in an inventory; null
 *                 when the bones cannot be merged into one (rotated bones, or too large)
 */
public record AnimatedModel(List<Bone> bones, Map<String, Clip> clips, List<Texture> textures, @Nullable String icon) {

    public AnimatedModel {
        bones = List.copyOf(bones);
        clips = Collections.unmodifiableMap(new LinkedHashMap<>(clips));
        textures = List.copyOf(textures);
    }

    /** The item definition that draws a bone: {@code <namespace>:<id>/bone_<name>}. */
    public static ResourceLocation boneItemModel(ResourceLocation itemModel, String bone) {
        return new ResourceLocation(itemModel.namespace(), itemModel.path() + "/bone_" + bone);
    }

    /**
     * One bone.
     *
     * @param name         lower case, {@code [a-z0-9_]}, unique in the model
     * @param parent       index of the parent bone, or -1 for a root
     * @param offset       the bone's pivot relative to its parent's pivot (or to the model origin, for
     *                     a root), in blocks
     * @param restRotation the bone's own rotation at rest, degrees around X, Y and Z
     * @param modelScale   how much the bone's model was shrunk to fit Minecraft's model bounds, and so
     *                     how much its display scales it back up; 1 for most bones
     * @param model        the bone's own cubes as a model, its pivot at the model's centre; null for a
     *                     bone with no cubes of its own, which only moves its children
     */
    public record Bone(String name, int parent, Placement.Vec3 offset, Placement.Vec3 restRotation, float modelScale,
                       @Nullable String model) {
    }

    /** What happens when an animation reaches its end. */
    public enum Loop {
        /** Back to the rest pose: Blockbench's "play once". */
        ONCE,
        /** Stays on the last frame: a lid stays open until something closes it. */
        HOLD,
        /** Starts over. */
        LOOP
    }

    /**
     * One animation.
     *
     * @param length   in ticks
     * @param channels by bone index; a bone the animation does not move has none
     */
    public record Clip(String name, Loop loop, int length, Map<Integer, Channels> channels) {

        public Clip {
            channels = Map.copyOf(channels);
        }
    }

    /** A bone's keyframes, per property, each list in time order. */
    public record Channels(List<Keyframe> position, List<Keyframe> rotation, List<Keyframe> scale) {

        public Channels {
            position = List.copyOf(position);
            rotation = List.copyOf(rotation);
            scale = List.copyOf(scale);
        }
    }

    /** How a keyframe is reached from the one before it. */
    public enum Interpolation {
        LINEAR,
        /** Smooth through its neighbours, as Blockbench's "catmullrom". */
        SMOOTH,
        /** Holds the previous value, then jumps. */
        STEP
    }

    /**
     * One keyframe, its value already in the displays' frame: position in blocks, rotation in
     * degrees, scale as a factor.
     *
     * @param tick when, in ticks from the start; not rounded
     */
    public record Keyframe(float tick, Placement.Vec3 value, Interpolation interpolation) {
    }

    /** An image of the model, at {@code assets/<namespace>/textures/<location path>.png}. */
    public record Texture(ResourceLocation location, byte[] png) {
    }
}
