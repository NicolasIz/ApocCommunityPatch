package com.arkcronist.content.core.block;

import com.arkcronist.content.core.definition.ModelSource;
import com.arkcronist.content.core.definition.ResourceLocation;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * 5.3: the models and block states a shaped custom block is drawn with - what vanilla writes for a
 * copper slab, stairs, grate, door or trapdoor, with the custom block's textures in it.
 *
 * <p>Every model is a vanilla shape template ({@code block/slab}, {@code block/door_bottom_left},
 * ...) with the block's textures, so the block keeps vanilla's geometry, culling and UVs exactly,
 * and the block state's rotations are vanilla's own for that shape.</p>
 *
 * <p>Textures, from {@code resource.texture} (every face) or {@code resource.textures}:</p>
 * <ul>
 *   <li>slab, stairs - {@code side}, {@code top}, {@code bottom}: top falls back to side, bottom
 *       to top;</li>
 *   <li>transparent - any parent and textures, {@code block/cube_all} by default, or a model of its
 *       own ({@code resource.model});</li>
 *   <li>door - {@code top} and {@code bottom}, the two halves, and {@code item} for the icon (the
 *       bottom half when left out);</li>
 *   <li>trapdoor - {@code texture}.</li>
 * </ul>
 */
public final class ShapedModels {

    /**
     * One model the block state names.
     *
     * @param suffix what follows {@code block/<id>} in its location: "" for the main one
     * @param parent the vanilla template it fills in
     */
    public record Part(String suffix, ResourceLocation parent) {
    }

    private static final String[] FACINGS = {"east", "north", "south", "west"};

    private ShapedModels() {
    }

    /** The models each shape's block state names, the main one first. */
    public static List<Part> parts(BlockShape shape) {
        return switch (shape) {
            case CUBE, TRANSPARENT, LEAVES -> List.of();
            case SLAB -> List.of(part("", "block/slab"), part("_top", "block/slab_top"),
                    part("_double", "block/cube_bottom_top"));
            case STAIRS -> List.of(part("", "block/stairs"), part("_inner", "block/inner_stairs"),
                    part("_outer", "block/outer_stairs"));
            case DOOR -> List.of(part("_bottom_left", "block/door_bottom_left"),
                    part("_bottom_left_open", "block/door_bottom_left_open"),
                    part("_bottom_right", "block/door_bottom_right"),
                    part("_bottom_right_open", "block/door_bottom_right_open"),
                    part("_top_left", "block/door_top_left"), part("_top_left_open", "block/door_top_left_open"),
                    part("_top_right", "block/door_top_right"), part("_top_right_open", "block/door_top_right_open"));
            case TRAPDOOR -> List.of(part("", "block/template_trapdoor_bottom"),
                    part("_top", "block/template_trapdoor_top"), part("_open", "block/template_trapdoor_open"));
        };
    }

    /** {@code <namespace>:block/<id><suffix>}. */
    public static ResourceLocation blockModel(String namespace, String id, String suffix) {
        return new ResourceLocation(namespace, "block/" + id + suffix);
    }

    /**
     * What the loader makes of a shaped block's {@code resource:} - the model its item is drawn with,
     * holding every texture the shape's other models need.
     *
     * @param source what {@code resource:} gave, as for a cube: a model of its own, or textures
     * @return null after adding the reason to {@code problems}
     */
    public static ModelSource itemModel(String namespace, String id, BlockShape shape, ModelSource source,
                                        String prefix, List<String> problems) {
        if (shape == BlockShape.CUBE || shape == BlockShape.TRANSPARENT) {
            return source;
        }
        if (!(source instanceof ModelSource.Generated generated)) {
            problems.add(prefix + "a " + shape.yamlName() + " block is drawn from textures (resource.texture or"
                    + " resource.textures), set into vanilla's " + shape.yamlName() + " models - not from a model of"
                    + " its own");
            return null;
        }
        Map<String, ResourceLocation> given = generated.textures();
        ResourceLocation all = given.get("all");
        Map<String, ResourceLocation> textures = new LinkedHashMap<>();
        switch (shape) {
            case SLAB, STAIRS -> {
                ResourceLocation side = given.getOrDefault("side", all);
                ResourceLocation top = given.getOrDefault("top", all != null ? all : side);
                ResourceLocation bottom = given.getOrDefault("bottom", top);
                if (side == null || top == null) {
                    problems.add(prefix + "a " + shape.yamlName() + " needs resource.texture, or resource.textures"
                            + " with side (and top, bottom if they differ)");
                    return null;
                }
                textures.put("bottom", bottom);
                textures.put("side", side);
                textures.put("top", top);
                return new ModelSource.Generated(generated.sourceRoot(), blockModel(namespace, id, ""),
                        parts(shape).get(0).parent(), textures);
            }
            case DOOR -> {
                ResourceLocation top = given.getOrDefault("top", all);
                ResourceLocation bottom = given.getOrDefault("bottom", all);
                if (top == null || bottom == null) {
                    problems.add(prefix + "a door needs resource.textures with top and bottom - its two halves -"
                            + " and item for its icon");
                    return null;
                }
                textures.put("bottom", bottom);
                textures.put("layer0", given.getOrDefault("item", given.getOrDefault("layer0", bottom)));
                textures.put("top", top);
                return new ModelSource.Generated(generated.sourceRoot(), new ResourceLocation(namespace, "item/" + id),
                        ModelSource.ITEM_GENERATED, textures);
            }
            case TRAPDOOR -> {
                ResourceLocation texture = given.getOrDefault("texture", all);
                if (texture == null) {
                    problems.add(prefix + "a trapdoor needs resource.texture");
                    return null;
                }
                textures.put("texture", texture);
                return new ModelSource.Generated(generated.sourceRoot(), blockModel(namespace, id, ""),
                        parts(shape).get(0).parent(), textures);
            }
            default -> {
                return source;
            }
        }
    }

    /**
     * The models to write besides the item's own, each with the textures it uses. The item's model
     * is the main part for a slab, stairs and a trapdoor, so it is not repeated.
     */
    public static List<ModelSource.Generated> extraModels(String namespace, String id, BlockShape shape,
                                                          ModelSource.Generated item) {
        List<ModelSource.Generated> models = new java.util.ArrayList<>();
        for (Part part : parts(shape)) {
            ResourceLocation location = blockModel(namespace, id, part.suffix());
            if (location.equals(item.location())) {
                continue;
            }
            Map<String, ResourceLocation> textures = new TreeMap<>(item.textures());
            textures.remove("layer0");
            models.add(new ModelSource.Generated(item.sourceRoot(), location, part.parent(), textures));
        }
        return models;
    }

    /**
     * The block state file of the copper base, every variant drawn with the custom block's models,
     * turned as vanilla turns that shape.
     *
     * @param model the model for a part, by its suffix
     */
    public static JsonObject blockState(BlockShape shape, Function<String, String> model) {
        Map<String, JsonObject> variants = new TreeMap<>();
        switch (shape) {
            case CUBE -> throw new IllegalArgumentException("a cube is drawn through note_block.json");
            case TRANSPARENT -> variants.put("", variant(model.apply(""), 0, 0, false));
            case SLAB -> {
                variants.put("type=bottom", variant(model.apply(""), 0, 0, false));
                variants.put("type=double", variant(model.apply("_double"), 0, 0, false));
                variants.put("type=top", variant(model.apply("_top"), 0, 0, false));
            }
            case STAIRS -> {
                for (String facing : FACINGS) {
                    int y = turn(facing);
                    for (String half : new String[] {"bottom", "top"}) {
                        int x = half.equals("top") ? 180 : 0;
                        for (String form : new String[] {"inner_left", "inner_right", "outer_left", "outer_right",
                                "straight"}) {
                            String suffix = form.startsWith("inner") ? "_inner" : form.startsWith("outer") ? "_outer" : "";
                            int turn = y;
                            if (form.endsWith("_left") && x == 0) {
                                turn = y - 90;
                            } else if (form.endsWith("_right") && x == 180) {
                                turn = y + 90;
                            }
                            turn = Math.floorMod(turn, 360);
                            variants.put("facing=" + facing + ",half=" + half + ",shape=" + form,
                                    variant(model.apply(suffix), x, turn, x != 0 || turn != 0));
                        }
                    }
                }
            }
            case DOOR -> {
                for (String facing : FACINGS) {
                    for (String half : new String[] {"lower", "upper"}) {
                        for (String hinge : new String[] {"left", "right"}) {
                            for (boolean open : new boolean[] {false, true}) {
                                int y = turn(facing) + (open ? (hinge.equals("left") ? 90 : -90) : 0);
                                String suffix = (half.equals("lower") ? "_bottom_" : "_top_") + hinge + (open ? "_open" : "");
                                variants.put("facing=" + facing + ",half=" + half + ",hinge=" + hinge + ",open=" + open,
                                        variant(model.apply(suffix), 0, Math.floorMod(y, 360), false));
                            }
                        }
                    }
                }
            }
            case TRAPDOOR -> {
                for (String facing : FACINGS) {
                    for (String half : new String[] {"bottom", "top"}) {
                        for (boolean open : new boolean[] {false, true}) {
                            String suffix = open ? "_open" : half.equals("top") ? "_top" : "";
                            // Open, it stands against the side it faces from: north is the model as drawn.
                            int y = open ? Math.floorMod(turn(facing) + 90, 360) : 0;
                            variants.put("facing=" + facing + ",half=" + half + ",open=" + open,
                                    variant(model.apply(suffix), 0, y, false));
                        }
                    }
                }
            }
        }
        JsonObject all = new JsonObject();
        variants.forEach(all::add);
        JsonObject root = new JsonObject();
        root.add("variants", all);
        return root;
    }

    /** The y turn vanilla gives a block facing this way: east as drawn, then clockwise. */
    private static int turn(String facing) {
        return switch (facing) {
            case "east" -> 0;
            case "south" -> 90;
            case "west" -> 180;
            default -> 270;
        };
    }

    private static JsonObject variant(String model, int x, int y, boolean uvlock) {
        JsonObject variant = new JsonObject();
        variant.addProperty("model", model);
        if (uvlock) {
            variant.addProperty("uvlock", true);
        }
        if (x != 0) {
            variant.addProperty("x", x);
        }
        if (y != 0) {
            variant.addProperty("y", y);
        }
        return variant;
    }

    private static Part part(String suffix, String parent) {
        return new Part(suffix, new ResourceLocation(ResourceLocation.MINECRAFT, parent));
    }
}
