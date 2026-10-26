package com.arkcronist.content.core.definition;

import com.arkcronist.content.core.animation.AnimatedModel;
import com.arkcronist.content.core.block.BlockShape;
import com.arkcronist.content.core.furniture.Footprint;
import org.jetbrains.annotations.Nullable;

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
     * model - or, since 5.3, a waxed copper block of its {@link #shape()}. Which state or which copper
     * block is decided at build time and remembered, not written here.
     *
     * @param dropSelf breaking it outside creative drops the custom item, not a note block
     * @param skillXp  skill experience for breaking it, given through AuraSkills (Mining), mcMMO
     *                 (Mining) or SkillAPI, whichever is installed; 0 for none
     * @param jobs     what each job pays for breaking it, by job id; empty for nothing
     * @param generation 5.0: where it is laid into new chunks as an ore; empty for nowhere
     * @param shape    5.3: a note block cube, or a slab, stairs, door, trapdoor or see-through block
     *                 drawn through a waxed copper block of that shape ({@link BlockShape})
     * @param mining   5.4: its tool and hardness; null to be mined as the block underneath it is - a
     *                 note block, with an axe, or copper, with a pickaxe
     */
    record Block(boolean dropSelf, double skillXp, Map<String, JobReward> jobs,
                 List<com.arkcronist.content.core.generation.OreGeneration> generation,
                 BlockShape shape, @Nullable com.arkcronist.content.core.block.Mining mining) implements Placement {

        public Block {
            jobs = Map.copyOf(jobs);
            generation = List.copyOf(generation);
            java.util.Objects.requireNonNull(shape, "shape");
        }

        public Block(boolean dropSelf, double skillXp, Map<String, JobReward> jobs,
                     List<com.arkcronist.content.core.generation.OreGeneration> generation, BlockShape shape) {
            this(dropSelf, skillXp, jobs, generation, shape, null);
        }

        /** The same block drawn as another shape - a see-through block made a cube when no grate is left. */
        public Block withShape(BlockShape other) {
            return new Block(dropSelf, skillXp, jobs, generation, other, mining);
        }

        public Block(boolean dropSelf, double skillXp, Map<String, JobReward> jobs,
                     List<com.arkcronist.content.core.generation.OreGeneration> generation) {
            this(dropSelf, skillXp, jobs, generation, BlockShape.CUBE);
        }

        public Block(boolean dropSelf, double skillXp, Map<String, JobReward> jobs) {
            this(dropSelf, skillXp, jobs, List.of());
        }

        public Block(boolean dropSelf, double skillXp) {
            this(dropSelf, skillXp, Map.of());
        }

        public Block(boolean dropSelf) {
            this(dropSelf, 0);
        }

        /** Whether it is a note block state - false for the shapes drawn through copper. */
        public boolean noteBlock() {
            return shape == BlockShape.CUBE;
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
     * @param light         light level of a {@link Support#LIGHT} support, 0-15; since 3.1 also of a
     *                      {@link Support#BARRIER} one, given by a light block just above the piece
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
     * @param doubleChest   how two of a {@link Support#CHEST} look and open once joined side by
     *                      side; null when they stay single
     * @param footprint     the blocks it stands on, {@link Footprint#SINGLE} for one; more only on a
     *                      barrier or light support
     * @param board         a right click opens the quest board; null for anything else
     * @param ambience      {@code interactable: effect}: particles around it on a loop, and a drink
     *                      or a meal on a right click; null for anything else
     * @param commands      a right click runs commands, as a sign over a shop door does; null for
     *                      anything else
     * @param guillotine    a lever that drops a blade on whoever kneels in it; null for anything else
     */
    record Furniture(Support support, int light, boolean facePlayer, Display display, String modelEngineId,
                     Seat seat, Storage storage, Animated animated, Bed bed, DoubleChest doubleChest,
                     Footprint footprint, QuestBoard board, Ambience ambience, Commands commands,
                     Guillotine guillotine, Connect connect, Hitbox hitbox) implements Placement {

        public Furniture {
            footprint = footprint == null ? Footprint.SINGLE : footprint;
        }

        public Furniture(Support support, int light, boolean facePlayer, Display display, String modelEngineId,
                         Seat seat, Storage storage, Animated animated, Bed bed, DoubleChest doubleChest,
                         Footprint footprint, QuestBoard board, Ambience ambience, Commands commands,
                         Guillotine guillotine) {
            this(support, light, facePlayer, display, modelEngineId, seat, storage, animated, bed, doubleChest, footprint,
                    board, ambience, commands, guillotine, null, null);
        }

        public Furniture(Support support, int light, boolean facePlayer, Display display, String modelEngineId,
                         Seat seat, Storage storage, Animated animated, Bed bed, DoubleChest doubleChest,
                         Footprint footprint, QuestBoard board) {
            this(support, light, facePlayer, display, modelEngineId, seat, storage, animated, bed, doubleChest, footprint,
                    board, null, null, null);
        }

        /** Whether a right click on it does something of its own, rather than what its support block would. */
        public boolean interactive() {
            return seat != null || storage != null || board != null || commands != null || guillotine != null
                    || (ambience != null && ambience.clickable());
        }

        public Furniture(Support support, int light, boolean facePlayer, Display display, String modelEngineId,
                         Seat seat, Storage storage, Animated animated, Bed bed) {
            this(support, light, facePlayer, display, modelEngineId, seat, storage, animated, bed, null,
                    Footprint.SINGLE, null);
        }

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
     * 5.3: furniture that joins its neighbours into one run - a counter, a bench, a sofa. Placed side
     * by side, facing the same way, each piece of a group is drawn as the run's left end, its middle
     * or its right end, as seen from the front; a piece alone keeps its own model.
     *
     * @param group the pieces that join one another: furniture naming the same group joins, so an oak
     *              and a spruce counter can make one run; the furniture's own id when not given
     * @param left  the left end - a neighbour on its right only
     * @param right the right end - a neighbour on its left only
     */
    record Connect(String group, ModelSource left, ModelSource middle, ModelSource right) {

        /** How a piece of a run is drawn, by which of its sides has a neighbour. */
        public enum Variant {
            SINGLE, LEFT, MIDDLE, RIGHT;

            /** {@code onLeft}, {@code onRight}: whether a neighbour of the group stands there, facing the same way. */
            public static Variant of(boolean onLeft, boolean onRight) {
                return onLeft ? (onRight ? MIDDLE : RIGHT) : (onRight ? LEFT : SINGLE);
            }

            public String key() {
                return name().toLowerCase(Locale.ROOT);
            }
        }

        /** The model of a variant; null for {@link Variant#SINGLE}, which is the furniture's own. */
        public @Nullable ModelSource model(Variant variant) {
            return switch (variant) {
                case LEFT -> left;
                case MIDDLE -> middle;
                case RIGHT -> right;
                case SINGLE -> null;
            };
        }

        /** The item definition drawing a variant: {@code <namespace>:<id>/connect_<variant>}. */
        public static ResourceLocation itemModel(ResourceLocation single, Variant variant) {
            return new ResourceLocation(single.namespace(), single.path() + "/connect_" + variant.key());
        }
    }

    /**
     * 5.3: what of a piece is clicked and punched - boxes that follow its shape, held by interaction
     * entities, instead of whole blocks. They change nothing of what is walked into: that is still
     * the support's, a barrier's whole block or a light block's nothing.
     *
     * @param model fitted to the model's own cubes when the pack is built
     * @param boxes set by hand, facing south; used when {@code model} is false
     */
    record Hitbox(boolean model, List<Box> boxes) {

        public Hitbox {
            boxes = List.copyOf(boxes);
        }

        /**
         * A box as an interaction entity holds it: square across, {@code width} wide and deep,
         * {@code height} tall.
         *
         * @param offset where the middle of its bottom is, in blocks, from the middle of the floor of
         *               the block the piece is placed on - for the piece facing south
         */
        public record Box(double width, double height, Vec3 offset) {
        }
    }

    /**
     * What two chests of the same furniture become when placed side by side, facing the same way, as
     * vanilla chests do: one double chest of 54 slots - each half keeps its own 27 - drawn by one
     * model, its {@code animated} one or its plain {@code model}.
     *
     * @param animated a Blockbench model two chests wide, centred between them, with its open and
     *                 close animations ({@code open_double}, {@code close_double}); null for a plain
     *                 model
     * @param model    a plain item model two chests wide, centred between them, drawn by one item
     *                 display; null with an animated model
     * @param title    MiniMessage shown at the top of the double inventory; null for the single's
     */
    record DoubleChest(Animated animated, ModelSource model, String title) {

        /** Each half's slots: a double chest is two of these. */
        public static final int HALF_SLOTS = 27;

        /** The item definition drawing the double model: {@code <namespace>:<id>/double}. */
        public static ResourceLocation itemModel(ResourceLocation single) {
            return new ResourceLocation(single.namespace(), single.path() + "/double");
        }
    }

    /**
     * A quest board: a right click opens its notices.
     *
     * @param title MiniMessage shown over the notices; null for the item's name
     */
    record QuestBoard(String title) {

        public static final QuestBoard DEFAULT = new QuestBoard(null);
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
     * @param height  where the sitter's seat is, in blocks above the bottom of the support block -
     *                0.5 for a chair of ordinary height
     * @param centred 3.2: one seat for the whole piece, at its middle moved by {@code across} and
     *                {@code forward} - a throne several blocks big; otherwise one on each block it
     *                stands on, as a bench's
     * @param across  blocks to the sitter's right of the piece's middle (from the front, to the left)
     * @param forward blocks towards its front
     */
    record Seat(float height, boolean centred, float across, float forward) {

        public static final Seat DEFAULT = new Seat(0.5f);

        public Seat(float height) {
            this(height, false, 0f, 0f);
        }
    }

    /**
     * One kind of particle drawn around a piece of furniture.
     *
     * @param particle a particle id that takes no extra data: {@code campfire_cosy_smoke},
     *                 {@code minecraft:small_flame}
     * @param x        offset from the centre of the support block's bottom, in blocks, across its front
     * @param y        height above the bottom of the support block
     * @param z        offset towards its front
     * @param count    how many at once
     * @param spread   how far they scatter, in blocks, each way
     * @param speed    how fast they move
     */
    record Particles(String particle, double x, double y, double z, int count, double spread, double speed) {
    }

    /**
     * {@code interactable: effect}: particles around the piece on a loop, seen by every player near
     * enough, and what a right click does - a drink, a stew: potion effects, a sound, a line of text.
     *
     * @param intervalTicks how often the particles are drawn
     * @param range         how far away, in blocks, a player still sees them
     * @param potions       potion effects a right click gives; empty for none
     * @param sound         played by a right click; null for none
     * @param cooldownTicks how long one player waits between two clicks on one piece
     * @param message       MiniMessage shown above the hotbar on a click; null for none
     * @param station       3.1: a click opens this vanilla workstation, one of {@link #STATIONS}; null
     *                      for none
     * @param cook          3.1: a click with something in hand that this kind of cooking turns into
     *                      something else ({@link #COOKING}: a furnace's, a blast furnace's, a
     *                      smoker's, a campfire's recipes) cooks one of it, there and then - instead
     *                      of opening the station; null for none
     * @param toggleLight   3.1: a click puts its light out, or lights it again
     * @param clickParticles 3.1: particles drawn once at each click
     * @param hud           3.4: what a click adds to the HUD values this plugin keeps, by HUD id - a
     *                      trough's water to thirst; empty for none
     */
    record Ambience(List<Particles> particles, int intervalTicks, int range, List<Effect> potions,
                    String sound, int cooldownTicks, String message, String station, String cook,
                    boolean toggleLight, List<Particles> clickParticles, Map<String, Double> hud) {

        /** The workstations a click can open, as {@code station:} names them. */
        public static final List<String> STATIONS = List.of("crafting", "anvil", "grindstone", "smithing",
                "stonecutter", "loom", "cartography", "enchanting");
        /** The kinds of cooking {@code cook:} can do, by the vanilla block whose recipes they use. */
        public static final List<String> COOKING = List.of("furnace", "blast", "smoker", "campfire");

        public Ambience {
            particles = List.copyOf(particles);
            potions = List.copyOf(potions);
            clickParticles = clickParticles == null ? List.of() : List.copyOf(clickParticles);
            hud = hud == null ? Map.of() : Map.copyOf(hud);
        }

        public Ambience(List<Particles> particles, int intervalTicks, int range, List<Effect> potions,
                        String sound, int cooldownTicks, String message, String station, String cook,
                        boolean toggleLight, List<Particles> clickParticles) {
            this(particles, intervalTicks, range, potions, sound, cooldownTicks, message, station, cook, toggleLight,
                    clickParticles, Map.of());
        }

        public Ambience(List<Particles> particles, int intervalTicks, int range, List<Effect> potions,
                        String sound, int cooldownTicks, String message) {
            this(particles, intervalTicks, range, potions, sound, cooldownTicks, message, null, null, false, List.of());
        }

        /** Whether a right click does anything: a piece with only particles leaves the click alone. */
        public boolean clickable() {
            return !potions.isEmpty() || sound != null || message != null || station != null || cook != null
                    || toggleLight || !clickParticles.isEmpty() || !hud.isEmpty();
        }
    }

    /**
     * {@code interactable: command}: a right click runs commands - a sign over the bank's door, the
     * tavern's, the smithy's.
     *
     * @param run           the commands, without their slash; see {@code CommandTemplate} for the
     *                      placeholders filled in
     * @param asPlayer      run as the player who clicked, with their permissions; false runs them
     *                      from the console
     * @param cooldownTicks how long one player waits between two clicks on one piece
     * @param permission    what the player needs to use it; null for nothing
     * @param message       MiniMessage shown above the hotbar on a click; null for none
     * @param sound         played on a click; null for none
     */
    record Commands(List<String> run, boolean asPlayer, int cooldownTicks, String permission, String message,
                    String sound) {

        public Commands {
            run = List.copyOf(run);
        }
    }

    /**
     * {@code interactable: guillotine}: a Blockbench model whose lever, pulled, drops its blade on
     * whoever kneels in its stocks.
     *
     * @param drop          the animation the blade falls by
     * @param reset         the one that raises it again; null leaves it down until the next pull
     * @param delayMillis   the blade moves on the first server tick at least this long after the pull
     * @param impactMillis  when it reaches the bottom, after it starts to move: the blow lands then
     * @param resetTicks    how long the blade stays down before {@code reset} plays
     * @param leverAcross   which block of its footprint is the lever: across its front, as
     *                      {@code Footprint} counts from the anchor
     * @param leverUp       and how high
     * @param kneelHeight   where the one in the stocks kneels, above the bottom of the anchor block
     * @param killUser      the blade falls on whoever pulled the lever, not on whoever kneels
     * @param respectPvp    the blow counts as the puller's attack, so a region without PvP stops it
     * @param commands      console commands run when the blade lands on someone: {@code %player%} is
     *                      who it fell on, {@code %killer%} who pulled the lever
     * @param impact        particles where the blade lands
     * @param impactSound   played where it lands; null for none
     */
    record Guillotine(String drop, String reset, int delayMillis, int impactMillis, int resetTicks, int leverAcross,
                      int leverUp, float kneelHeight, boolean killUser, boolean respectPvp, List<String> commands,
                      List<Particles> impact, String impactSound) {

        public Guillotine {
            commands = List.copyOf(commands);
            impact = List.copyOf(impact);
        }
    }

    /**
     * A piece of furniture with an inventory: a cabinet, a crate, a wardrobe. Its contents are kept
     * in the database, by the position it stands at.
     *
     * @param slots a chest's worth of rows: 9, 18, 27, 36, 45 or 54
     * @param title MiniMessage shown at the top of the inventory; null to use the item's name
     * @param show  3.4: what of its contents is shown on the piece - swords on a rack, armour on a
     *              mannequin; null for nothing. A piece that shows its contents keeps one inventory,
     *              at its anchor, whichever of its blocks is clicked
     */
    record Storage(int slots, String title, @Nullable Show show) {

        public static final int DEFAULT_SLOTS = 27;

        public Storage(int slots, String title) {
            this(slots, title, null);
        }

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
     * 3.4: what a storage piece shows of what it holds.
     *
     * <p>{@code armor}: an armour stand nobody sees stands on the piece wearing it - pieces that go
     * on the head, chest, legs and feet each where they go, by their own slot, and the first two other
     * things in its hands - drawn as any armour stand is, by Java and Bedrock clients alike.</p>
     *
     * <p>{@code items}: each listed place shows one thing held, in the order they are held - a sword
     * hanging on a rack - as an item display, which only Java clients draw.</p>
     *
     * @param armor  whether an armour stand wears what it holds
     * @param offset where that armour stand stands, from the piece's centre: across, up, forward, in blocks
     * @param yaw    which way it faces, in degrees from the way the piece faces
     * @param items  where each thing held is shown, in order
     */
    record Show(boolean armor, Vec3 offset, float yaw, List<ShownItem> items) {

        public Show {
            items = List.copyOf(items);
        }
    }

    /**
     * One place a held thing is shown at.
     *
     * @param offset   from the piece's centre: across, up, forward, in blocks
     * @param rotation degrees about x, y and z, in that order - a sword turned blade down
     * @param scale    how big it is drawn; 1 as in an item frame
     */
    record ShownItem(Vec3 offset, Vec3 rotation, float scale) {
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
     * @param tree         5.4: a sapling - after its last stage it grows into this tree instead of
     *                     ripening; null for a crop
     */
    record Crop(List<ModelSource> stages, int stageSeconds, int minLight, List<String> soils, boolean boneMeal,
                List<Drop> drops, double skillXp, Map<String, JobReward> jobs,
                @Nullable com.arkcronist.content.core.tree.TreeSpec tree) implements Placement {

        public Crop {
            stages = List.copyOf(stages);
            soils = List.copyOf(soils);
            drops = List.copyOf(drops);
            jobs = Map.copyOf(jobs);
        }

        public Crop(List<ModelSource> stages, int stageSeconds, int minLight, List<String> soils, boolean boneMeal,
                    List<Drop> drops, double skillXp, Map<String, JobReward> jobs) {
            this(stages, stageSeconds, minLight, soils, boneMeal, drops, skillXp, jobs, null);
        }

        /** 5.4: whether it is a sapling, which grows into a tree rather than ripening. */
        public boolean sapling() {
            return tree != null;
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

        /**
         * The stage it ripens at: its last model. A sapling's is one past its last model - the stage
         * it never shows, because reaching it is growing into its tree.
         */
        public int lastStage() {
            return tree == null ? stages.size() - 1 : stages.size();
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
     * @param glow         0-15: the light its faces are drawn with however dark it is around them - a
     *                     glowing acid, lava of its own - through the model's {@code light_emission}
     *                     (2.0). It is how it is drawn, not light it gives off: it lights nothing else
     */
    record Liquid(int flowDistance, int tickRate, int maxFall, ModelSource source, ModelSource flowing,
                  Contact contact, int glow) implements Placement {

        public Liquid(int flowDistance, int tickRate, int maxFall, ModelSource source, ModelSource flowing,
                      Contact contact) {
            this(flowDistance, tickRate, maxFall, source, flowing, contact, 0);
        }

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
     * @param mobs       whether mobs in it get the same as players (2.0); players always do
     */
    record Contact(int intervalTicks, double damage, String damageType, int fireTicks, int freezeTicks,
                   List<Effect> effects, boolean mobs) {

        public static final Contact HARMLESS = new Contact(10, 0, "minecraft:generic", 0, 0, List.of(), false);

        public Contact {
            effects = List.copyOf(effects);
        }

        public Contact(int intervalTicks, double damage, String damageType, int fireTicks, int freezeTicks,
                       List<Effect> effects) {
            this(intervalTicks, damage, damageType, fireTicks, freezeTicks, effects, false);
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
