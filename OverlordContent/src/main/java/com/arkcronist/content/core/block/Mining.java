package com.arkcronist.content.core.block;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/**
 * 5.4: how a custom block is mined - the tool that is right for it, how good that tool must be, and
 * how hard the block is - as vanilla's own blocks are: stone wants a pickaxe and drops nothing
 * without one, wood is quicker with an axe but breaks by hand too.
 *
 * <p>A custom block is a note block or a waxed copper block underneath, and the client mines it as
 * that block. {@link #speedFactor} is what makes the client mine it as this one instead: the factor
 * the player's {@code block_break_speed} attribute is set to while they hit it, so that the client's
 * own reckoning - the vanilla block's hardness, the tool's speed against it - comes out at this
 * block's. The server reckons the same way with the same attribute, so the two agree.</p>
 *
 * @param tool         the right tool; {@link Tool#NONE} for none in particular (any tool mines it as
 *                     a hand does)
 * @param tier         the worst tool of that kind that is still right: a wooden pickaxe does not mine
 *                     iron ore in vanilla
 * @param hardness     vanilla's hardness: stone 1.5, planks 2, dirt 0.5, obsidian 50; 0 breaks at once,
 *                     {@link #UNBREAKABLE} never outside creative
 * @param requiresTool broken without the right tool, it drops nothing - stone's way, not wood's
 */
public record Mining(Tool tool, Tier tier, double hardness, boolean requiresTool) {

    /** A hardness that is never broken outside creative, as bedrock's. */
    public static final double UNBREAKABLE = -1;
    /** The most the attribute takes: block_break_speed is capped at 1024 by vanilla. */
    public static final double MAX_FACTOR = 1024;

    /** The tools a block can ask for. */
    public enum Tool {
        NONE("none", 0), PICKAXE("pickaxe", 1.5), AXE("axe", 2), SHOVEL("shovel", 0.5), HOE("hoe", 0.5),
        SHEARS("shears", 0.8);

        private final String yamlName;
        private final double defaultHardness;

        Tool(String yamlName, double defaultHardness) {
            this.yamlName = yamlName;
            this.defaultHardness = defaultHardness;
        }

        public String yamlName() {
            return yamlName;
        }

        /** The hardness of vanilla's everyday block for this tool: stone, planks, dirt, hay, wool. */
        public double defaultHardness() {
            return defaultHardness;
        }

        public static Optional<Tool> parse(@Nullable String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            String name = raw.trim().toLowerCase(Locale.ROOT);
            for (Tool tool : values()) {
                if (tool.yamlName.equals(name) || (tool.yamlName + "s").equals(name)) {
                    return Optional.of(tool);
                }
            }
            return switch (name) {
                case "any", "hand" -> Optional.of(NONE);
                case "pick" -> Optional.of(PICKAXE);
                case "spade" -> Optional.of(SHOVEL);
                default -> Optional.empty();
            };
        }
    }

    /** How good a tool is, worst first: gold mines what wood mines. */
    public enum Tier {
        ANY("any"), WOOD("wood"), STONE("stone"), IRON("iron"), DIAMOND("diamond"), NETHERITE("netherite");

        private final String yamlName;

        Tier(String yamlName) {
            this.yamlName = yamlName;
        }

        public String yamlName() {
            return yamlName;
        }

        public static Optional<Tier> parse(@Nullable String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            String name = raw.trim().toLowerCase(Locale.ROOT);
            return switch (name) {
                case "any", "none" -> Optional.of(ANY);
                case "wood", "wooden", "gold", "golden" -> Optional.of(WOOD);
                case "stone" -> Optional.of(STONE);
                case "iron", "copper" -> Optional.of(IRON);
                case "diamond" -> Optional.of(DIAMOND);
                case "netherite" -> Optional.of(NETHERITE);
                default -> Optional.empty();
            };
        }

        /**
         * The tier of a vanilla tool by its material's name - {@code IRON_AXE} is iron - or null for
         * anything else (a custom tool built on another material), which is not judged by tier.
         */
        public static @Nullable Tier ofMaterial(String material) {
            String name = material.toUpperCase(Locale.ROOT);
            if (name.startsWith("WOODEN_") || name.startsWith("GOLDEN_")) {
                return WOOD;
            }
            if (name.startsWith("STONE_")) {
                return STONE;
            }
            if (name.startsWith("IRON_") || name.startsWith("COPPER_")) {
                return IRON;
            }
            if (name.startsWith("DIAMOND_")) {
                return DIAMOND;
            }
            if (name.startsWith("NETHERITE_")) {
                return NETHERITE;
            }
            return null;
        }

        /** Whether a tool of {@code held} is good enough; one of no known tier always is. */
        public boolean admits(@Nullable Tier held) {
            return held == null || held.ordinal() >= ordinal();
        }
    }

    public Mining {
        java.util.Objects.requireNonNull(tool, "tool");
        java.util.Objects.requireNonNull(tier, "tier");
    }

    public boolean unbreakable() {
        return hardness < 0;
    }

    /**
     * Vanilla's progress per tick at mining a block: the tool's speed against it, over its hardness,
     * over 30 when the tool is right for its drops and 100 when not. Haste, mining fatigue, water and
     * jumping multiply it the same way for every block, so they are left out: they cancel in
     * {@link #speedFactor}.
     */
    public static double progress(double speed, double hardness, boolean harvests) {
        if (hardness < 0) {
            return 0;
        }
        if (hardness == 0) {
            return Double.POSITIVE_INFINITY;
        }
        return speed / hardness / (harvests ? 30 : 100);
    }

    /**
     * By how much to multiply a player's mining speed so that mining the vanilla block underneath
     * goes as mining this one should: 0 for an unbreakable block, at most {@link #MAX_FACTOR}.
     *
     * @param baseSpeed    the held tool's speed against the vanilla block (efficiency included)
     * @param baseHardness the vanilla block's hardness
     * @param baseHarvests whether the held tool is right for the vanilla block's drops
     * @param speed        the held tool's speed against this block: against vanilla's everyday block
     *                     for this block's tool, or a hand's 1 for any other tool
     * @param harvests     whether the held tool is right for this block
     */
    public double speedFactor(double baseSpeed, double baseHardness, boolean baseHarvests, double speed,
                              boolean harvests) {
        double wanted = progress(speed, hardness, harvests || !requiresTool);
        if (wanted == 0) {
            return 0;
        }
        double base = progress(baseSpeed, baseHardness, baseHarvests);
        if (base <= 0 || Double.isInfinite(wanted)) {
            return MAX_FACTOR;
        }
        return Math.min(MAX_FACTOR, wanted / base);
    }

    /**
     * Whether a block of this hardness breaks at once for a tool of this speed - the client, given
     * the factor, breaks it in the tick it starts.
     */
    public boolean instant(double speed, boolean harvests) {
        return progress(speed, hardness, harvests || !requiresTool) >= 1;
    }

    /** Whether breaking it with a tool that is (not) right for it drops its item. */
    public boolean drops(boolean rightTool) {
        return rightTool || !requiresTool;
    }

    /**
     * Seconds Bedrock takes to break it by hand, the measure its custom blocks are given: vanilla's
     * hardness times one and a half, or a time no one waits out for an unbreakable block.
     */
    public float bedrockSeconds() {
        return unbreakable() ? 1_000_000f : (float) (hardness * 1.5);
    }

    @Override
    public String toString() {
        return tool.yamlName + (tier == Tier.ANY ? "" : "/" + tier.yamlName) + " hardness " + hardness
                + (requiresTool ? ", needs the tool" : "");
    }
}
