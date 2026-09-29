package com.arkcronist.content.core.definition;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * What a content entry becomes once it leaves a player's hand. Written as {@code type:} in YAML.
 *
 * <p>Every entry is an item first: a custom block or a piece of furniture still has to be held and
 * given before it can be placed. The type only adds what happens on placement.</p>
 */
public enum ContentType {

    /** Stays an item. */
    ITEM("item"),
    /** Placed as a note block in a state reserved for it. */
    CUSTOM_BLOCK("custom_block"),
    /** Placed as an invisible support block with an item display showing its model. */
    CUSTOM_FURNITURE("custom_furniture");

    private final String yamlName;

    ContentType(String yamlName) {
        this.yamlName = yamlName;
    }

    public String yamlName() {
        return yamlName;
    }

    public static Optional<ContentType> parse(String raw) {
        String name = raw.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(type -> type.yamlName.equals(name)).findFirst();
    }
}
