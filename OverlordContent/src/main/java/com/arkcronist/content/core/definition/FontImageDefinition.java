package com.arkcronist.content.core.definition;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * A picture drawn as a character - a menu background, an icon in a lore line - by name, written
 * the way ItemsAdder writes its {@code font_images}:
 *
 * <pre>
 * font_images:
 *   skills_menu_book:
 *     path: skills_menu_book.png   # under assets/&lt;namespace&gt;/textures/; also texture:
 *     y_position: 48                # how far above the line it is drawn; also ascent:
 *     scale_ratio: 256              # its height in pixels; also height: - the picture's own if left out
 *     symbol: ""              # the character, when it must be this one
 * </pre>
 *
 * <p>Text names it as {@code :skills_menu_book:} or {@code %img_skills_menu_book%}.</p>
 *
 * @param ascent null when not given: any height a pack already draws it at will do
 * @param height null when not given: the picture's own, or any a pack already draws it at
 * @param symbol the character asked for, or null for any
 */
public record FontImageDefinition(String namespace, String name, ResourceLocation texture, @Nullable Integer ascent,
                                  @Nullable Integer height, @Nullable Integer symbol, Path sourceRoot, Path source) {

    public String fullId() {
        return namespace + ":" + name;
    }
}
