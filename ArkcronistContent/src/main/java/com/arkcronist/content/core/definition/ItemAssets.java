package com.arkcronist.content.core.definition;

import java.nio.file.Path;

/**
 * Where an item's look comes from.
 *
 * <p>At most one of {@code model} and {@code texture} drives the result. A model is used as it is,
 * together with every model and texture it pulls in from its own namespace. A texture on its own
 * gets a flat model written for it at {@code <namespace>:item/<id>}, the way a vanilla item such as
 * a diamond is drawn. With neither, the item keeps its base material's look.</p>
 *
 * @param sourceRoot the content folder that {@code models/} and {@code textures/} are read from
 * @param model      the model to show, or null
 * @param texture    the texture of a generated flat model, or null; ignored when {@code model} is set
 * @param parent     the parent written into a generated flat model
 */
public record ItemAssets(Path sourceRoot, ResourceLocation model, ResourceLocation texture,
                         ResourceLocation parent) {

    /** The parent vanilla uses for flat items held like a gem. */
    public static final ResourceLocation GENERATED = new ResourceLocation(ResourceLocation.MINECRAFT, "item/generated");

    public static ItemAssets none(Path sourceRoot) {
        return new ItemAssets(sourceRoot, null, null, GENERATED);
    }

    /** True when the pack will carry an item definition for this item. */
    public boolean hasLook() {
        return model != null || texture != null;
    }
}
