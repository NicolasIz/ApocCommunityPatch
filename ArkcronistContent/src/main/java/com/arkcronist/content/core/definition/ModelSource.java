package com.arkcronist.content.core.definition;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Where the model an item, block or piece of furniture is drawn with comes from.
 *
 * <p>Either the content pack supplies a model file, or the compiler writes one from a parent and a
 * set of textures. Whichever it is, {@link #location()} is the model the item definition - and, for
 * a custom block, the note block state - points at.</p>
 */
public sealed interface ModelSource {

    /** The parent vanilla uses for flat items held like a gem. */
    ResourceLocation ITEM_GENERATED = new ResourceLocation(ResourceLocation.MINECRAFT, "item/generated");

    /** The parent vanilla uses for a cube with the same texture on all six faces. */
    ResourceLocation CUBE_ALL = new ResourceLocation(ResourceLocation.MINECRAFT, "block/cube_all");

    /** The model the client is pointed at. */
    ResourceLocation location();

    /** The content folder that {@code models/} and {@code textures/} are read from. */
    Path sourceRoot();

    /**
     * A model file from the content pack, copied together with every parent and texture it pulls in
     * from its own namespace. A model in another namespace ({@code minecraft:block/stone}) is only
     * referenced.
     */
    record Provided(Path sourceRoot, ResourceLocation location) implements ModelSource {
    }

    /**
     * A model the compiler writes: {@code {"parent": ..., "textures": {...}}}.
     *
     * @param textures texture variable to texture, e.g. {@code layer0} or {@code all}. Iterated in
     *                 key order: the model's bytes, and so the pack's hash, must not depend on the
     *                 order a YAML map happened to be read in
     */
    record Generated(Path sourceRoot, ResourceLocation location, ResourceLocation parent,
                     Map<String, ResourceLocation> textures) implements ModelSource {

        public Generated {
            textures = Collections.unmodifiableMap(new TreeMap<>(textures));
        }
    }
}
