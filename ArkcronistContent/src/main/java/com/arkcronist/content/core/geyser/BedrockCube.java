package com.arkcronist.content.core.geyser;

import com.arkcronist.content.core.definition.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A cube as a Bedrock block draws it, before it is written: in the block's own frame - pixels, the
 * origin at the middle of the block's floor, x east, y up, z south, as the world has them - with
 * each face named for the way it faces in the world.
 *
 * <p>Each face's UV is still Java's {u1, v1, u2, v2} on the 0-16 square, laid the way Java lays a
 * texture on a face facing that way; {@link BedrockGeometry} turns that into Bedrock's own terms.</p>
 *
 * @param axis  a cube turned about one axis: x, y or z, with {@code angle} in degrees right-handed
 *              about it, at {@code pivot}; null for none
 */
public record BedrockCube(float[] min, float[] max, Map<JavaModel.Face, Face> faces, @Nullable Character axis,
                          float angle, float[] pivot) {

    public record Face(float[] uv, ResourceLocation texture) {
    }

    /** The same cube, {@code dx, dy, dz} pixels away. */
    public BedrockCube moved(float dx, float dy, float dz) {
        return new BedrockCube(new float[]{min[0] + dx, min[1] + dy, min[2] + dz},
                new float[]{max[0] + dx, max[1] + dy, max[2] + dz}, faces, axis, angle,
                new float[]{pivot[0] + dx, pivot[1] + dy, pivot[2] + dz});
    }

    public float[] centre() {
        return new float[]{(min[0] + max[0]) / 2, (min[1] + max[1]) / 2, (min[2] + max[2]) / 2};
    }
}
