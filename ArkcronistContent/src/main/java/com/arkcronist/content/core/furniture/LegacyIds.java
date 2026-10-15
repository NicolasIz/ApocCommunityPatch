package com.arkcronist.content.core.furniture;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 3.1: the ItemsAdder id an item still carries after ItemsAdder is gone. ItemsAdder writes it into
 * the item's custom data as {@code itemsadder: {namespace: "...", id: "..."}}; read here from the
 * item's components as SNBT ({@code ItemMeta#getAsComponentString}), keys in either order, quoted
 * or not.
 */
public final class LegacyIds {

    private static final Pattern COMPOUND = Pattern.compile("[\"']?itemsadder[\"']?\\s*:\\s*\\{([^{}]*)}");
    private static final Pattern ID = Pattern.compile("(?:^|[,{\\s])[\"']?id[\"']?\\s*:\\s*[\"']?([a-z0-9_./-]+)");
    private static final Pattern NAMESPACE = Pattern.compile("(?:^|[,{\\s])[\"']?namespace[\"']?\\s*:\\s*[\"']?([a-z0-9_.-]+)");

    private LegacyIds() {
    }

    /** {@code namespace:id} of the ItemsAdder item these components describe, if they describe one. */
    public static Optional<String> itemsAdderId(String components) {
        if (components == null) {
            return Optional.empty();
        }
        Matcher compound = COMPOUND.matcher(components);
        while (compound.find()) {
            String body = compound.group(1);
            Matcher id = ID.matcher(body);
            Matcher namespace = NAMESPACE.matcher(body);
            if (id.find() && namespace.find()) {
                return Optional.of(namespace.group(1) + ":" + id.group(1));
            }
        }
        return Optional.empty();
    }
}
