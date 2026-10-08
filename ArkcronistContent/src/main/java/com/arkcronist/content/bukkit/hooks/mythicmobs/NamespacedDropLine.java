package com.arkcronist.content.bukkit.hooks.mythicmobs;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * A MythicMobs drop or equipment line that names an item as ItemsAdder's are written -
 * {@code dragones_epicos:umbraxis_botas 1 0.05}.
 *
 * <p>MythicMobs reads the colon as the old {@code MATERIAL:data} form: it keeps what is before it,
 * {@code dragones_epicos}, as the drop's type, and asks other plugins for that type with the whole
 * line attached. So the type is a namespace, and the id is the line's first word. Kept apart from
 * the hook, which names MythicMobs' classes, so it can be tested without them.</p>
 */
final class NamespacedDropLine {

    private NamespacedDropLine() {
    }

    /**
     * @param dropName the type MythicMobs asks for
     * @param line     the drop's whole line
     * @return {@code namespace:id}, lower-case, when the line starts with {@code <dropName>:<id>};
     *         null otherwise
     */
    static @Nullable String itemId(String dropName, @Nullable String line) {
        if (line == null || dropName.isBlank()) {
            return null;
        }
        String first = line.strip().split("\\s+", 2)[0];
        int brace = first.indexOf('{');
        if (brace >= 0) {
            first = first.substring(0, brace);
        }
        int colon = first.indexOf(':');
        if (colon <= 0 || colon == first.length() - 1 || first.indexOf(':', colon + 1) >= 0
                || !first.substring(0, colon).equalsIgnoreCase(dropName.strip())) {
            return null;
        }
        return first.toLowerCase(Locale.ROOT);
    }
}
