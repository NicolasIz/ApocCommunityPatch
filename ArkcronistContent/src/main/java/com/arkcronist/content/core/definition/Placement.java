package com.arkcronist.content.core.definition;

import com.arkcronist.content.core.animation.AnimatedModel;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
     * @param jobs     what each job pays for breaking it, by job id; empty for nothing
     */
    record Block(boolean dropSelf, double skillXp, Map<String, JobReward> jobs) implements Placement {

        public Block {
            jobs = Map.copyOf(jobs);
        }

        public Block(boolean dropSelf, double skillXp) {
            this(dropSelf, skillXp, Map.of());
        }

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
     * @param jobs         what each job pays for harvesting it grown, by job id; empty for nothing
     */
    record Crop(List<ModelSource> stages, int stageSeconds, int minLight, List<String> soils, boolean boneMeal,
                List<Drop> drops, double skillXp, Map<String, JobReward> jobs) implements Placement {

        public Crop {
            stages = List.copyOf(stages);
            soils = List.copyOf(soils);
            drops = List.copyOf(drops);
            jobs = Map.copyOf(jobs);
        }

        public Crop(List<ModelSource> stages, int stageSeconds, int minLight, List<String> soils, boolean boneMeal,
                    List<Drop> drops, double skillXp) {
            this(stages, stageSeconds, minLight, soils, boneMeal, drops, skillXp, Map.of());
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

    /**
     * A liquid: poured from its item - a bucket - it becomes a source, and flows from there the way
     * water does: down while it can, then out to the sides for {@code flowDistance} blocks.
     *
     * <p>Drawn by two tripwire states that no vanilla tripwire takes (disarmed, unpowered): one for
     * a source, one for flowing liquid. Tripwire is drawn translucent and has no collision, so a
     * liquid can be seen through and walked into.</p>
     *
     * @param flowDistance how far it runs sideways from a source, or from where it lands, 1-8
     * @param tickRate     ticks between one step of the flow and the next: 5 is water, 30 lava
     * @param maxFall      how many blocks it falls at most before it stops
     * @param source       how a source is drawn
     * @param flowing      how flowing liquid is drawn
     * @param contact      what it does to a player standing in it
     */
    record Liquid(int flowDistance, int tickRate, int maxFall, ModelSource source, ModelSource flowing,
                  Contact contact) implements Placement {

        @Override
        public ContentType type() {
            return ContentType.CUSTOM_LIQUID;
        }
    }

    /**
     * What a liquid does to a player in it, every {@code intervalTicks} they stay in.
     *
     * @param damage     half hearts each time; 0 for none
     * @param damageType the vanilla damage type it is dealt as, e.g. {@code minecraft:in_fire}
     * @param fireTicks  sets the player alight for this long; 0 for not
     * @param freezeTicks freezes the player, as powder snow does, for this long; 0 for not
     * @param effects    potion effects given each time
     */
    record Contact(int intervalTicks, double damage, String damageType, int fireTicks, int freezeTicks,
                   List<Effect> effects) {

        public static final Contact HARMLESS = new Contact(10, 0, "minecraft:generic", 0, 0, List.of());

        public Contact {
            effects = List.copyOf(effects);
        }

        public boolean harmless() {
            return damage <= 0 && fireTicks <= 0 && freezeTicks <= 0 && effects.isEmpty();
        }
    }

    /**
     * A potion effect: its type by key ({@code minecraft:poison}), how long, and its level counted
     * from 0.
     */
    record Effect(String type, int durationTicks, int amplifier) {
    }
}
