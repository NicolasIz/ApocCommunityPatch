package com.arkcronist.content.core.furniture;

import org.jetbrains.annotations.Nullable;

/**
 * The furniture editor's menu: five rows of a chest, and what a click on each slot does. Kept apart
 * from Bukkit so a click can be followed from slot to matrix in a unit test.
 *
 * <pre>
 *  row 0   [Translation] [Scale] [Rotation]  .  [piece]  .  [save to]  .  [help]
 *  row 1 X   .  [-1] [-0.1] [-0.01]  [x value]  [+0.01] [+0.1] [+1]  .
 *  row 2 Y   .  [-1] [-0.1] [-0.01]  [y value]  [+0.01] [+0.1] [+1]  .
 *  row 3 Z   .  [-1] [-0.1] [-0.01]  [z value]  [+0.01] [+0.1] [+1]  .
 *  row 4   [part to default]  .  [revert]  .  [save]  .  [cancel]  .  .
 * </pre>
 *
 * <p>The three middle rows change one axis of the part selected in the top row, by the step the
 * button shows; a shift-click moves {@value #SHIFT_FACTOR} times as far - ten degrees from the
 * "1" button of the rotation, say.</p>
 */
public final class EditorLayout {

    public static final int ROWS = 5;
    public static final int SIZE = ROWS * 9;
    /** The steps of the buttons on each side of an axis' value, smallest next to it. */
    public static final double[] STEPS = {0.01, 0.1, 1.0};
    public static final int SHIFT_FACTOR = 10;

    public static final int INFO = 4;
    public static final int SCOPE = 6;
    public static final int HELP = 8;
    public static final int DEFAULT_PART = 36;
    public static final int REVERT = 38;
    public static final int SAVE = 40;
    public static final int CANCEL = 42;

    /** What a click on a slot asks for. */
    public sealed interface Action permits Adjust, Select, Button {
    }

    /** Moves one axis of the selected part by {@code step} (negative to decrease). */
    public record Adjust(DisplayTransform.Axis axis, double step) implements Action {

        /** The change a click makes: the step, or ten of them with shift held. */
        public double delta(boolean shift) {
            return shift ? step * SHIFT_FACTOR : step;
        }
    }

    /** Makes translation, scale or rotation the part the axis buttons change. */
    public record Select(DisplayTransform.Part part) implements Action {
    }

    public enum Button implements Action {
        /** Every piece of this furniture (its YAML file), or this piece only (the database). */
        SCOPE,
        /** The selected part back to nothing: no offset, a scale of 1, no turn. */
        DEFAULT_PART,
        /** Every value back to what it was when the editor opened. */
        REVERT,
        SAVE,
        CANCEL
    }

    private EditorLayout() {
    }

    /** The slot that selects {@code part}. */
    public static int selector(DisplayTransform.Part part) {
        return part.ordinal();
    }

    /** The row of an axis' buttons. */
    public static int row(DisplayTransform.Axis axis) {
        return 1 + axis.ordinal();
    }

    /** The slot showing an axis' value, between its buttons. */
    public static int value(DisplayTransform.Axis axis) {
        return row(axis) * 9 + 4;
    }

    /**
     * The slot of one button: {@code step} indexes {@link #STEPS}, the smallest step sitting next to
     * the value and the largest furthest out.
     */
    public static int button(DisplayTransform.Axis axis, int step, boolean increase) {
        if (step < 0 || step >= STEPS.length) {
            throw new IllegalArgumentException("no step " + step);
        }
        return row(axis) * 9 + (increase ? 5 + step : 3 - step);
    }

    /** What a click on {@code slot} does, or null for a slot that does nothing. */
    public static @Nullable Action at(int slot) {
        if (slot < 0 || slot >= SIZE) {
            return null;
        }
        for (DisplayTransform.Part part : DisplayTransform.Part.values()) {
            if (slot == selector(part)) {
                return new Select(part);
            }
        }
        switch (slot) {
            case SCOPE -> {
                return Button.SCOPE;
            }
            case DEFAULT_PART -> {
                return Button.DEFAULT_PART;
            }
            case REVERT -> {
                return Button.REVERT;
            }
            case SAVE -> {
                return Button.SAVE;
            }
            case CANCEL -> {
                return Button.CANCEL;
            }
            default -> {
            }
        }
        int row = slot / 9;
        int column = slot % 9;
        if (row < 1 || row > DisplayTransform.Axis.values().length) {
            return null;
        }
        DisplayTransform.Axis axis = DisplayTransform.Axis.values()[row - 1];
        if (column >= 1 && column <= 3) {
            return new Adjust(axis, -STEPS[3 - column]);
        }
        if (column >= 5 && column <= 7) {
            return new Adjust(axis, STEPS[column - 5]);
        }
        return null;
    }

    /** The transform after a click on an axis button, with {@code part} selected. */
    public static DisplayTransform click(DisplayTransform transform, DisplayTransform.Part part, Adjust adjust,
                                         boolean shift) {
        return transform.adjust(part, adjust.axis(), adjust.delta(shift));
    }

    /** A step as its button shows it: {@code +0.01}, {@code -1}. */
    public static String label(double step) {
        return (step < 0 ? "-" : "+") + DisplayTransform.number((float) Math.abs(step));
    }
}
