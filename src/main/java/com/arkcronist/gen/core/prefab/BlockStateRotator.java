package com.arkcronist.gen.core.prefab;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rotates a block state string around the Y axis.
 *
 * <p>A prefab that can only ever face one way betrays itself the moment two of them appear in the
 * same view. Rotating the palette instead of the block array means the cost is paid once per
 * schematic per angle, not once per block placed, and every stair, door, banner and rail still ends
 * up pointing the right way.</p>
 *
 * <p>Properties that are defined <em>relative</em> to the block's own facing - stair {@code shape},
 * door {@code hinge}, button {@code face} - are deliberately left alone: rotating {@code facing} has
 * already rotated them.</p>
 */
public final class BlockStateRotator {

    private static final String[] FACING = {"north", "east", "south", "west"};

    private BlockStateRotator() {
    }

    /**
     * @param steps quarter turns clockwise, 0-3
     * @return the rotated block state string, or the original when nothing needed changing
     */
    public static String rotate(String state, int steps) {
        int turns = ((steps % 4) + 4) % 4;
        if (turns == 0) {
            return state;
        }
        int bracket = state.indexOf('[');
        if (bracket < 0 || !state.endsWith("]")) {
            return state;
        }
        String name = state.substring(0, bracket);
        String body = state.substring(bracket + 1, state.length() - 1);
        Map<String, String> properties = new LinkedHashMap<>();
        for (String pair : body.split(",")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                properties.put(pair.substring(0, equals), pair.substring(equals + 1));
            }
        }
        if (properties.isEmpty()) {
            return state;
        }

        rotateFacing(properties, turns);
        rotateAxis(properties, turns);
        rotateSignRotation(properties, turns);
        rotateRailShape(properties, turns);
        rotateConnections(properties, turns);

        StringBuilder out = new StringBuilder(state.length()).append(name).append('[');
        boolean first = true;
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return out.append(']').toString();
    }

    private static void rotateFacing(Map<String, String> properties, int turns) {
        String facing = properties.get("facing");
        if (facing == null) {
            return;
        }
        int index = indexOf(facing);
        if (index >= 0) {
            // up and down survive a Y rotation untouched, and land here as index -1.
            properties.put("facing", FACING[(index + turns) & 3]);
        }
    }

    private static void rotateAxis(Map<String, String> properties, int turns) {
        String axis = properties.get("axis");
        if (axis == null || (turns & 1) == 0) {
            return;
        }
        if ("x".equals(axis)) {
            properties.put("axis", "z");
        } else if ("z".equals(axis)) {
            properties.put("axis", "x");
        }
    }

    private static void rotateSignRotation(Map<String, String> properties, int turns) {
        String rotation = properties.get("rotation");
        if (rotation == null) {
            return;
        }
        try {
            int value = Integer.parseInt(rotation);
            properties.put("rotation", Integer.toString((value + turns * 4) & 15));
        } catch (NumberFormatException ignored) {
            // Not a standing sign; leave it be.
        }
    }

    private static void rotateRailShape(Map<String, String> properties, int turns) {
        String shape = properties.get("shape");
        if (shape == null) {
            return;
        }
        String rotated = shape;
        for (int i = 0; i < turns; i++) {
            rotated = railStepClockwise(rotated);
        }
        if (!rotated.equals(shape)) {
            properties.put("shape", rotated);
        }
    }

    private static String railStepClockwise(String shape) {
        return switch (shape) {
            case "north_south" -> "east_west";
            case "east_west" -> "north_south";
            case "ascending_north" -> "ascending_east";
            case "ascending_east" -> "ascending_south";
            case "ascending_south" -> "ascending_west";
            case "ascending_west" -> "ascending_north";
            case "north_east" -> "south_east";
            case "south_east" -> "south_west";
            case "south_west" -> "north_west";
            case "north_west" -> "north_east";
            // Stair and door shapes are relative to facing, so they stay exactly as they are.
            default -> shape;
        };
    }

    /** Fences, walls, panes, vines and mushroom blocks store one value per horizontal side. */
    private static void rotateConnections(Map<String, String> properties, int turns) {
        String[] values = new String[4];
        boolean any = false;
        for (int i = 0; i < 4; i++) {
            values[i] = properties.get(FACING[i]);
            any |= values[i] != null;
        }
        if (!any) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            String value = values[i];
            if (value != null) {
                properties.put(FACING[(i + turns) & 3], value);
            }
        }
    }

    private static int indexOf(String direction) {
        for (int i = 0; i < 4; i++) {
            if (FACING[i].equals(direction)) {
                return i;
            }
        }
        return -1;
    }
}
