package com.arkcronist.content.core.definition;

import java.util.List;

/**
 * What placing an item puts into the world. An item without a placement stays an item.
 *
 * <p>Always referred to qualified - {@code Placement.Block}, {@code Placement.Furniture} - so it
 * never reads as Bukkit's own {@code Block}.</p>
 */
public sealed interface Placement {

    ContentType type();

    /**
     * A custom block: a note block held in a state of its own, which the pack draws with the item's
     * model. Which state is decided at build time and remembered, not written here.
     *
     * @param dropSelf breaking it outside creative drops the custom item, not a note block
     */
    record Block(boolean dropSelf) implements Placement {

        @Override
        public ContentType type() {
            return ContentType.CUSTOM_BLOCK;
        }
    }

    /**
     * A piece of furniture: an invisible support block, and an item display on it that draws the
     * model.
     *
     * @param light         light level of a {@link Support#LIGHT} support, 0-15
     * @param facePlayer    turn to face whoever placed it, snapped to the nearest quarter turn
     * @param modelEngineId a ModelEngine blueprint to draw it with instead, when ModelEngine is
     *                      installed; null to always use the item display
     * @param seat          what a right click does: sit on it, or null for nothing
     */
    record Furniture(Support support, int light, boolean facePlayer, Display display, String modelEngineId,
                     Seat seat) implements Placement {

        @Override
        public ContentType type() {
            return ContentType.CUSTOM_FURNITURE;
        }
    }

    /**
     * A piece of furniture players can sit on.
     *
     * @param height where the sitter's seat is, in blocks above the bottom of the support block -
     *               0.5 for a chair of ordinary height
     */
    record Seat(float height) {

        public static final Seat DEFAULT = new Seat(0.5f);
    }

    /**
     * A crop: planted from its item onto soil, drawn by an item display that changes model as it
     * grows, and harvested once it reaches its last stage.
     *
     * @param stages       one model per growth stage, first to last; at least two
     * @param stageSeconds the average time a stage takes to grow, in seconds of loaded-chunk time
     * @param minLight     the light level needed to grow, 0-15; 0 grows in the dark
     * @param soils        block materials it can be planted on, e.g. {@code FARMLAND}
     * @param boneMeal     whether bone meal advances it a stage
     * @param drops        what a fully grown crop drops; before that it drops its own item
     */
    record Crop(List<ModelSource> stages, int stageSeconds, int minLight, List<String> soils, boolean boneMeal,
                List<Drop> drops) implements Placement {

        public Crop {
            stages = List.copyOf(stages);
            soils = List.copyOf(soils);
            drops = List.copyOf(drops);
        }

        @Override
        public ContentType type() {
            return ContentType.CUSTOM_CROP;
        }

        public int lastStage() {
            return stages.size() - 1;
        }

        /**
         * The item definition a stage is drawn through: {@code <namespace>:<id>/stage_<n>}. Ids cannot
         * contain a slash, so this never collides with an item's own.
         */
        public static ResourceLocation stageItemModel(ResourceLocation itemModel, int stage) {
            return new ResourceLocation(itemModel.namespace(), itemModel.path() + "/stage_" + stage);
        }
    }

    /**
     * One entry of a crop's harvest.
     *
     * @param item   a custom item's {@code namespace:id}, or a vanilla material
     * @param min    fewest dropped, at least 1
     * @param max    most dropped, at least {@code min}
     * @param chance 0-1, rolled once for the whole entry
     */
    record Drop(String item, int min, int max, double chance) {
    }

    /** The block that stands in for the furniture's body. */
    enum Support {
        /** Solid: players collide with it and can stand on it. */
        BARRIER,
        /** Walk-through, and can glow: a light block. */
        LIGHT
    }

    /**
     * How the item display draws the model.
     *
     * @param transform   which of the model's display contexts to apply - a name from
     *                    {@link #TRANSFORMS}; {@code NONE} draws the model exactly as authored
     * @param translation offset from the block's centre, in blocks
     * @param scale       per axis
     * @param rotation    degrees around x, y and z, applied in that order
     */
    record Display(String transform, Vec3 translation, Vec3 scale, Vec3 rotation) {

        /** The item display transforms the client knows. */
        public static final List<String> TRANSFORMS = List.of("NONE", "THIRDPERSON_LEFTHAND",
                "THIRDPERSON_RIGHTHAND", "FIRSTPERSON_LEFTHAND", "FIRSTPERSON_RIGHTHAND", "HEAD", "GUI",
                "GROUND", "FIXED");

        public static final Display DEFAULT = new Display("NONE", Vec3.ZERO, Vec3.ONE, Vec3.ZERO);
    }

    record Vec3(float x, float y, float z) {

        public static final Vec3 ZERO = new Vec3(0, 0, 0);
        public static final Vec3 ONE = new Vec3(1, 1, 1);
    }
}
