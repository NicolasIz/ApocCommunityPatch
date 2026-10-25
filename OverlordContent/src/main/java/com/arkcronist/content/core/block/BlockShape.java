package com.arkcronist.content.core.block;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 5.3: the shape a custom block takes in the world.
 *
 * <p>A {@link #CUBE} is a note block held in a state of its own, as custom blocks have always been.
 * The other shapes need a vanilla block that already has that shape, its own placement rules and
 * its own render layer - a slab that stacks into a double slab, stairs that turn corners, a door
 * two blocks tall, a grate the client draws with holes in it. The note block has none of that, so
 * each of these is drawn through a <em>waxed copper</em> block of the same shape instead: waxing
 * freezes copper's weathering, so its four variants (plain, exposed, weathered, oxidized) are four
 * blocks that never change by themselves, and the pack can draw each one as something else.</p>
 *
 * <p>That makes these blocks few: four per shape (five slabs, with the petrified oak slab), and two
 * of them fewer still. Trial chambers are
 * built from some waxed copper blocks - the plain and oxidized variants of most shapes - and every
 * chamber generated would show the custom block in their place, so those bases are listed last and
 * handed out only when a server allows it ({@code blocks.trial-chamber-copper} in config.yml).
 * The rest never appear in a world unless a player waxes copper, which is refused for a base that
 * draws a custom block.</p>
 */
public enum BlockShape {

    /** A note block state: as many as there are free states, no shape of its own. */
    CUBE("cube", List.of(), 0),
    /**
     * Bottom, top and double, waterloggable. Besides the copper, the petrified oak slab: a slab
     * left over from old versions that no recipe makes and no world generates.
     */
    SLAB("slab", List.of("waxed_exposed_cut_copper_slab", "waxed_weathered_cut_copper_slab", "petrified_oak_slab",
            "waxed_cut_copper_slab", "waxed_oxidized_cut_copper_slab"), 3),
    /** Straight, inner and outer corners, upside down, waterloggable. */
    STAIRS("stairs", List.of("waxed_exposed_cut_copper_stairs", "waxed_weathered_cut_copper_stairs",
            "waxed_cut_copper_stairs", "waxed_oxidized_cut_copper_stairs"), 2),
    /**
     * A full block drawn with holes: the copper grate, which the client renders in the cutout layer,
     * so a texture's transparent pixels are see-through - glass, lattices, leaves, cages. Light and
     * the faces of the blocks behind it go through it; its collision is still the whole block.
     */
    TRANSPARENT("transparent", List.of("waxed_exposed_copper_grate", "waxed_weathered_copper_grate",
            "waxed_copper_grate", "waxed_oxidized_copper_grate"), 2),
    /** Two blocks tall, opens with a click, hinged on the side the player places it. */
    DOOR("door", List.of("waxed_exposed_copper_door", "waxed_weathered_copper_door",
            "waxed_copper_door", "waxed_oxidized_copper_door"), 2),
    /** Opens with a click, against the top or bottom of the block it is placed on. */
    TRAPDOOR("trapdoor", List.of("waxed_copper_trapdoor", "waxed_exposed_copper_trapdoor",
            "waxed_weathered_copper_trapdoor", "waxed_oxidized_copper_trapdoor"), 3);

    private final String yamlName;
    private final List<String> bases;
    private final int outsideStructures;

    BlockShape(String yamlName, List<String> bases, int outsideStructures) {
        this.yamlName = yamlName;
        this.bases = bases;
        this.outsideStructures = outsideStructures;
    }

    /** As written after {@code block: shape:}. */
    public String yamlName() {
        return yamlName;
    }

    /**
     * The vanilla blocks this shape is drawn through, by id without namespace, in the order they are
     * handed out: first those no vanilla structure is built from, then the trial chambers' ones.
     */
    public List<String> bases() {
        return bases;
    }

    /**
     * The bases handed out without leave: {@code bases().subList(0, n)}. Checked against the
     * structures of 1.21.4 to 26.2: the trial chambers use the plain and oxidized slabs, stairs,
     * grates and doors and the oxidized trapdoor; no structure uses the exposed or weathered
     * variants, the plain trapdoor or the petrified oak slab.
     */
    public List<String> outsideStructures() {
        return bases.subList(0, outsideStructures);
    }

    /** False for {@link #CUBE}: whether this shape is drawn through a copper block. */
    public boolean copper() {
        return !bases.isEmpty();
    }

    /** The shape a vanilla block id is the base of, if it is one. */
    public static Optional<BlockShape> ofBase(String base) {
        String id = base.toLowerCase(Locale.ROOT);
        if (id.startsWith("minecraft:")) {
            id = id.substring("minecraft:".length());
        }
        for (BlockShape shape : values()) {
            if (shape.bases.contains(id)) {
                return Optional.of(shape);
            }
        }
        return Optional.empty();
    }

    /** Accepts the YAML name, upper or lower case, and {@code grate} for {@link #TRANSPARENT}. */
    public static Optional<BlockShape> parse(String raw) {
        String name = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (name.equals("grate") || name.equals("cutout") || name.equals("glass")) {
            return Optional.of(TRANSPARENT);
        }
        if (name.equals("stair")) {
            return Optional.of(STAIRS);
        }
        for (BlockShape shape : values()) {
            if (shape.yamlName.equals(name)) {
                return Optional.of(shape);
            }
        }
        return Optional.empty();
    }
}
