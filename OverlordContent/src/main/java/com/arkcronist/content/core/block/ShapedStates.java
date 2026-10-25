package com.arkcronist.content.core.block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 5.3: every state the vanilla block of a shape can be in - each property and its values, as the
 * game's block report lists them - for whatever must name them all: Geyser, which is told a Bedrock
 * block for each Java state.
 */
public final class ShapedStates {

    private static final List<String> FACINGS = List.of("north", "south", "west", "east");
    private static final List<String> BOOLEANS = List.of("false", "true");

    private ShapedStates() {
    }

    /** The properties of a shape's block, each with its values. */
    public static Map<String, List<String>> properties(BlockShape shape) {
        Map<String, List<String>> properties = new LinkedHashMap<>();
        switch (shape) {
            case CUBE -> throw new IllegalArgumentException("a cube is a note block");
            case SLAB -> {
                properties.put("type", List.of("top", "bottom", "double"));
                properties.put("waterlogged", BOOLEANS);
            }
            case STAIRS -> {
                properties.put("facing", FACINGS);
                properties.put("half", List.of("top", "bottom"));
                properties.put("shape", List.of("straight", "inner_left", "inner_right", "outer_left", "outer_right"));
                properties.put("waterlogged", BOOLEANS);
            }
            case TRANSPARENT -> properties.put("waterlogged", BOOLEANS);
            case DOOR -> {
                properties.put("facing", FACINGS);
                properties.put("half", List.of("upper", "lower"));
                properties.put("hinge", List.of("left", "right"));
                properties.put("open", BOOLEANS);
                properties.put("powered", BOOLEANS);
            }
            case TRAPDOOR -> {
                properties.put("facing", FACINGS);
                properties.put("half", List.of("top", "bottom"));
                properties.put("open", BOOLEANS);
                properties.put("powered", BOOLEANS);
                properties.put("waterlogged", BOOLEANS);
            }
        }
        return properties;
    }

    /** Every combination of the shape's properties, each a property-to-value map. */
    public static List<Map<String, String>> all(BlockShape shape) {
        List<Map<String, String>> states = new ArrayList<>();
        states.add(new TreeMap<>());
        for (Map.Entry<String, List<String>> property : properties(shape).entrySet()) {
            List<Map<String, String>> grown = new ArrayList<>();
            for (Map<String, String> state : states) {
                for (String value : property.getValue()) {
                    Map<String, String> next = new TreeMap<>(state);
                    next.put(property.getKey(), value);
                    grown.add(next);
                }
            }
            states = grown;
        }
        return states;
    }

    /** {@code minecraft:<base>[a=1,b=2]}, the properties in name order, as Bukkit and Geyser write a state. */
    public static String write(String base, Map<String, String> state) {
        StringBuilder text = new StringBuilder("minecraft:").append(base);
        if (!state.isEmpty()) {
            text.append('[');
            new TreeMap<>(state).forEach((key, value) -> text.append(text.charAt(text.length() - 1) == '[' ? "" : ",")
                    .append(key).append('=').append(value));
            text.append(']');
        }
        return text.toString();
    }
}
