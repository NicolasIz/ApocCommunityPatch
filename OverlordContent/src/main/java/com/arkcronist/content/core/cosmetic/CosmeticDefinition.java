package com.arkcronist.content.core.cosmetic;

import com.arkcronist.content.core.definition.Placement;

import java.util.Locale;

/**
 * An item worn as a cosmetic (3.0): put on with a right click, drawn on the wearer by a display
 * that only the clients see, and moved by {@link BackPhysics} as the wearer moves.
 *
 * @param slot    where it is worn; one cosmetic per slot
 * @param offset  where its middle sits, in blocks, from the wearer's back at shoulder height:
 *                x to their left, y up, z behind them
 * @param scale   how big the model is drawn
 * @param physics how it swings; {@link Physics#STILL} for a cosmetic that does not
 */
public record CosmeticDefinition(Slot slot, Placement.Vec3 offset, float scale, Physics physics) {

    public enum Slot {
        BACK;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Slot parse(String text) {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        }
    }

    /**
     * @param stiffness how hard it springs back to rest, 1/s² - its swing's frequency is about the
     *                  square root of this over 2π
     * @param damping   how fast a swing dies down, 1/s
     * @param sway      how much the wearer's movement throws it about; 1 is a loaded bag's
     * @param maxAngle  the farthest it swings either way, degrees
     * @param bounce    what is left of a swing that reaches its limit, 0 to 1
     */
    public record Physics(double stiffness, double damping, double sway, double maxAngle, double bounce) {

        public static final Physics DEFAULT = new Physics(40, 5, 1, 30, 0.45);
        public static final Physics STILL = new Physics(40, 5, 0, 0, 0);

        public Physics {
            if (stiffness <= 0 || damping < 0 || sway < 0 || maxAngle < 0 || bounce < 0 || bounce > 1) {
                throw new IllegalArgumentException("physics needs stiffness > 0, damping, sway and max-angle >= 0"
                        + " and bounce from 0 to 1");
            }
        }
    }

    /** A satchel's place: a hand below the shoulders, a little behind. */
    public static final Placement.Vec3 BACK_OFFSET = new Placement.Vec3(0, -0.35f, 0.25f);
}
