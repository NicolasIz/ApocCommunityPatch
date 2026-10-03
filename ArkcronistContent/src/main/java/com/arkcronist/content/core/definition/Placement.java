package com.arkcronist.content.core.definition;

import com.arkcronist.content.core.animation.AnimatedModel;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

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
     * @param skillXp  skill experience for breaking it, given through AuraSkills (Mining), mcMMO
     *                 (Mining) or SkillAPI, whichever is installed; 0 for none
     */
    record Block(boolean dropSelf, double skillXp) implements Placement {

        public Block(boolean dropSelf) {
            this(dropSelf, 0);
        }

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
     * @param seat          a right click sits on it; null when it is not a seat
     * @param storage       a right click opens its inventory; null when it has none. A piece of
     *                      furniture is a seat, a container, or neither - never both
     * @param animated      drawn by a Blockbench model's bones, one display each, which can be
     *                      animated; null for the single display of {@code resource}
     * @param bed           the colour of the vanilla bed underneath a {@link Support#BED}; null for
     *                      other supports
     */
    record Furniture(Support support, int light, boolean facePlayer, Display display, String modelEngineId,
                     Seat seat, Storage storage, Animated animated, Bed bed) implements Placement {

        public Furniture(Support support, int light, boolean facePlayer, Display display, String modelEngineId,
                         Seat seat, Storage storage) {
            this(support, light, facePlayer, display, modelEngineId, seat, storage, null, null);
        }

        @Override
        public ContentType type() {
            return ContentType.CUSTOM_FURNITURE;
        }
    }

    /**
     * A Blockbench model drawing a piece of furniture, and which of its animations play when its
     * inventory opens and closes.
     *
     * @param source the {@code .bbmodel} file, for messages
     */
    record Animated(AnimatedModel model, String open, String close, Path source) {
    }

    /**
     * The vanilla bed under a {@link Support#BED}. Its colour only shows if the model fails to cover
     * it - and on maps.
     */
    record Bed(String color) {

        public static final List<String> COLORS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime",
                "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
        public static final Bed DEFAULT = new Bed("white");

        /** The bed material, e.g. {@code RED_BED}. */
        public String material() {
            return color.toUpperCase(Locale.ROOT) + "_BED";
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
     * A piece of furniture with an inventory: a cabinet, a crate, a wardrobe. Its contents are kept
     * in the database, by the position it stands at.
     *
     * @param slots a chest's worth of rows: 9, 18, 27, 36, 45 or 54
     * @param title MiniMessage shown at the top of the inventory; null to use the item's name
     */
    record Storage(int slots, String title) {

        public static final int DEFAULT_SLOTS = 27;

        /** Whether an inventory can have this many slots: whole rows of nine, one to six of them. */
        public static boolean validSlots(int slots) {
            return slots >= 9 && slots <= 54 && slots % 9 == 0;
        }

        /**
         * The size to open an inventory at whose saved contents reach slot {@code used} (counting from
         * one; 0 when empty): the configured size, or more while items sit in rows the configuration
         * has since taken away - so shrinking {@code slots} never hides anything.
         */
        public int sizeFor(int used) {
            return Math.min(54, Math.max(slots, (used + 8) / 9 * 9));
        }
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
     * @param skillXp      skill experience for harvesting it grown, given through AuraSkills
     *                     (Farming), mcMMO (Herbalism) or SkillAPI; 0 for none
     */
    record Crop(List<ModelSource> stages, int stageSeconds, int minLight, List<String> soils, boolean boneMeal,
                List<Drop> drops, double skillXp) implements Placement {

        public Crop {
            stages = List.copyOf(stages);
            soils = List.copyOf(soils);
            drops = List.copyOf(drops);
        }

        public Crop(List<ModelSource> stages, int stageSeconds, int minLight, List<String> soils, boolean boneMeal,
                    List<Drop> drops) {
            this(stages, stageSeconds, minLight, soils, boneMeal, drops, 0);
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
        LIGHT,
        /**
         * A vanilla chest: mined with an axe like one, with a chest's hitbox. Its own inventory is
         * never used - a right click opens the furniture's storage instead - and hoppers cannot reach
         * it. Implies {@code interactable: storage}.
         */
        CHEST,
        /**
         * A vanilla bed, two blocks long: sleeping, the spawn point and skipping the night are
         * vanilla's own.
         */
        BED
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
