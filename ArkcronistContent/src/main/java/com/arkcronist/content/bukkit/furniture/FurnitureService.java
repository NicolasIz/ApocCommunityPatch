package com.arkcronist.content.bukkit.furniture;

import com.arkcronist.content.bukkit.hooks.HookManager;
import com.arkcronist.content.bukkit.hooks.ModelEngineBridge;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.core.animation.AnimatedModel;
import com.arkcronist.content.core.animation.Animator;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.definition.ResourceLocation;
import com.arkcronist.content.core.furniture.BedLayout;
import com.arkcronist.content.core.furniture.ChestPairing;
import com.arkcronist.content.core.furniture.DisplayTransform;
import com.arkcronist.content.core.furniture.Footprint;
import com.arkcronist.content.core.storage.BlockKey;
import com.arkcronist.content.core.storage.ChestPair;
import com.arkcronist.content.core.storage.ChestPairStore;
import com.arkcronist.content.core.storage.FurnitureTransform;
import com.arkcronist.content.core.storage.FurniturePiece;
import com.arkcronist.content.core.storage.FurnitureTransformStore;
import com.arkcronist.content.core.storage.PlacedContent;
import com.arkcronist.content.core.storage.PlacedContentStore;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Furniture: a support block that gives it a body, and item displays on that block that give it a
 * look.
 *
 * <h2>Supports</h2>
 * <ul>
 *   <li>a <b>barrier</b> or <b>light</b> block - invisible, so the display is all anyone sees;</li>
 *   <li>a vanilla <b>chest</b>, kept single - never half of a double chest - whose own inventory is
 *       never used: the furniture's storage opens instead. The model is drawn around it and must
 *       enclose it, the way a case encloses what it holds;</li>
 *   <li>a vanilla <b>bed</b>, both halves: sleeping is vanilla's. One display stands over the two
 *       blocks, turned the way the bed points - see {@link BedLayout} - and drawn a hair larger than
 *       it is authored, so that a model the exact size of a bed still covers the vanilla one instead
 *       of flickering against it.</li>
 * </ul>
 *
 * <h2>Displays</h2>
 * <p>Furniture with an item model has one display, holding its item. Furniture with an
 * {@link Placement.Animated animated model} has a <i>root</i> display, holding nothing, and one
 * display per bone that has cubes, each holding that bone's item model; an animation changes the
 * bones' transformations - see {@link AnimationPlayer}.</p>
 *
 * <h2>Records</h2>
 * <p>Three records tie the blocks and the displays together, each for a different reader:</p>
 * <ul>
 *   <li>the <b>chunk's</b> persistent data holds the (root) display's UUID under a key naming each
 *       support block - both halves of a bed. A barrier or light block has no persistent data of its
 *       own - only block entities do - so the chunk stands in for it, one key per block. It is saved
 *       with the chunk, so it can never disagree with the blocks it describes;</li>
 *   <li>the <b>displays'</b> persistent data holds the furniture id and the position of the block
 *       they are anchored to - a bed's foot - so a display that loads without its support can tell
 *       it is an orphan. A root also lists its support blocks and its bones; a bone names its root;</li>
 *   <li>the {@link PlacedContentStore} answers "is there furniture here" from memory, and keeps the
 *       database row - one per support block.</li>
 * </ul>
 *
 * <p>Main thread only. Entities cannot be spawned or removed from any other thread - Paper refuses
 * an asynchronous entity add outright - so the displays are created on the server thread in the same
 * tick as their block, and only the database write leaves it.</p>
 */
public final class FurnitureService {

    /** How far a punch reaches for furniture a ray passes through, such as a light-block support. */
    private static final int REACH = 5;
    /**
     * How much larger a bed's model is drawn than authored, around its foot: enough for its faces to
     * win the depth test against the vanilla bed's at any distance, too little to see.
     */
    static final float BED_ENCLOSE = 1.01f;

    private final Plugin plugin;
    private final ItemRegistry registry;
    private final ItemFactory items;
    private final PlacedContentStore store;
    private final HookManager hooks;
    private final AnimationPlayer animations;
    private final NamespacedKey furnitureTag;
    private final NamespacedKey anchorTag;
    /** On a root: every support block, packed. */
    private final NamespacedKey partsTag;
    /** On a root: its bones' UUIDs. */
    private final NamespacedKey bonesTag;
    /** On a bone: its name in the model. */
    private final NamespacedKey boneTag;
    /** On a bone: its root's UUID. */
    private final NamespacedKey rootTag;
    /** On the root of a double chest: drawn by the double's model, standing on two chests. */
    private final NamespacedKey doubleTag;
    /** Double chests, by either half; null until storage is up. */
    private @Nullable ChestPairStore pairs;
    /** Ticks between the click and a double chest's lid starting to move. */
    private long doubleDelayNanos = TimeUnit.MILLISECONDS.toNanos(63);
    /** Told before two chests join or a double chest parts, so an open inventory of either is saved and shut. */
    private Consumer<Block> releaseStorage = block -> { };
    /** Something else living in an invisible block, which a punch at furniture must not pass through. */
    private Predicate<Block> obstacle = block -> false;
    /** Pieces drawn with a transform of their own (the editor's "this piece only"); null until storage is up. */
    private @Nullable FurnitureTransformStore transforms;
    /** Told of every piece's root display as it is spawned, or loads with its chunk. */
    private Consumer<ItemDisplay> rootAppeared = root -> { };
    /** 3.0: a piece about to be taken away - its root, still there to read. */
    private Consumer<ItemDisplay> rootGoing = root -> { };

    /**
     * A piece of furniture as seen from one of its blocks: its root display, the block it was placed
     * on, the way it faces, and where that block is in it - {@code across} its front, from the left
     * of someone looking at it to their right (0 the block it was placed on), and {@code up}.
     */
    public record Piece(ItemDisplay root, Block anchor, float yaw, int across, int up) {
    }

    public FurnitureService(Plugin plugin, ItemRegistry registry, ItemFactory items, PlacedContentStore store,
                            HookManager hooks, AnimationPlayer animations) {
        this.plugin = plugin;
        this.registry = registry;
        this.items = items;
        this.store = store;
        this.hooks = hooks;
        this.animations = animations;
        this.furnitureTag = new NamespacedKey(plugin, "furniture");
        this.anchorTag = new NamespacedKey(plugin, "furniture_anchor");
        this.partsTag = new NamespacedKey(plugin, "furniture_parts");
        this.bonesTag = new NamespacedKey(plugin, "furniture_bones");
        this.boneTag = new NamespacedKey(plugin, "furniture_bone");
        this.rootTag = new NamespacedKey(plugin, "furniture_root");
        this.doubleTag = new NamespacedKey(plugin, "furniture_double");
    }

    /**
     * Where double chests are kept, how long after a click their lid moves, and who to tell before
     * two chests join or a double chest parts.
     */
    public void usePairs(ChestPairStore pairs, int delayMillis, Consumer<Block> releaseStorage) {
        this.pairs = pairs;
        this.doubleDelayNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0, delayMillis));
        this.releaseStorage = releaseStorage;
    }

    /** Where pieces with a transform of their own are kept. */
    public void useTransforms(FurnitureTransformStore transforms) {
        this.transforms = transforms;
    }

    /**
     * Also tells {@code listener} of every piece's root display as the piece is taken down, just
     * before the display is removed: Bedrock players' carriers.
     */
    public void onRootGoing(Consumer<ItemDisplay> listener) {
        this.rootGoing = this.rootGoing.andThen(listener);
    }

    /**
     * Also tells {@code listener} of every piece's root display as it is spawned or loads: the
     * ambient effects, Bedrock players' carriers.
     */
    public void onRootAppeared(Consumer<ItemDisplay> listener) {
        this.rootAppeared = this.rootAppeared.andThen(listener);
    }

    /** Crops live in light blocks too: a ray looking for furniture stops at one, instead of reaching past it. */
    public void stopAt(Predicate<Block> obstacle) {
        this.obstacle = obstacle;
    }

    // ---------------------------------------------------------------- supports

    public static boolean isSupport(Material material) {
        return material == Material.BARRIER || material == Material.LIGHT || isVanillaBlock(material);
    }

    /**
     * A support players see and use as the vanilla block it is - mined like one, slept in, blown up -
     * rather than an invisible stand-in.
     */
    public static boolean isVanillaBlock(Material material) {
        return material == Material.CHEST || Tag.BEDS.isTagged(material);
    }

    /** Whether {@code block} is the support block {@code furniture} stands on. */
    public static boolean matches(Block block, Placement.Furniture furniture) {
        return switch (furniture.support()) {
            case BARRIER -> block.getType() == Material.BARRIER;
            case LIGHT -> block.getType() == Material.LIGHT;
            case CHEST -> block.getType() == Material.CHEST;
            case BED -> block.getType().name().equals(furniture.bed().material());
        };
    }

    // ---------------------------------------------------------------- placing

    /**
     * Places the displays on a support block the player has just put down, and records them.
     *
     * @param block already the support block - vanilla placed it from the item; for a bed, the foot,
     *              with the head already beside it
     * @return false if a bed has lost its head and nothing was placed
     */
    public boolean place(Block block, Player player, CustomItem item, Placement.Furniture furniture) {
        return place(block, placementYaw(player, furniture), item, furniture);
    }

    /**
     * As {@link #place(Block, Player, CustomItem, Placement.Furniture)}, turned to {@code facing} -
     * where it faces unless it stands on a chest or a bed, which decide that themselves.
     */
    public boolean place(Block block, float facing, CustomItem item, Placement.Furniture furniture) {
        switch (furniture.support()) {
            case LIGHT -> {
                if (block.getBlockData() instanceof Light light) {
                    // A light item places at level 15; the furniture decides its own.
                    light.setLevel(furniture.light());
                    block.setBlockData(light, false);
                }
            }
            case CHEST -> keepSingle(block, true);
            default -> {
            }
        }
        Layout layout = layout(block, facing, furniture);
        if (layout == null) {
            return false;
        }
        List<Block> parts = layout.parts();
        Location at = layout.at();
        float yaw = layout.yaw();
        if (!furniture.footprint().single()) {
            // The rest of a wide or tall piece: a support of the same kind on every block it covers.
            for (Block part : parts.subList(1, parts.size())) {
                part.setType(block.getType(), false);
                if (part.getBlockData() instanceof Light light) {
                    light.setLevel(furniture.light());
                    part.setBlockData(light, false);
                }
            }
        }

        Block anchor = parts.get(0);
        ItemDisplay root = spawn(at, item, furniture, anchor, parts, false);
        List<PlacedContent> rows = new ArrayList<>(parts.size());
        for (Block part : parts) {
            link(part, root.getUniqueId());
            rows.add(new PlacedContent(part.getWorld().getUID(), part.getX(), part.getY(), part.getZ(),
                    item.id(), PlacedContent.Kind.FURNITURE));
        }
        // Every block of the piece in one database task and one transaction: none of it, or all -
        // and which way it faces, so it can be drawn again if its display is ever killed.
        store.addAll(rows, new FurniturePiece(anchor.getWorld().getUID(), anchor.getX(), anchor.getY(), anchor.getZ(),
                item.id(), yaw));
        if (furniture.animated() == null) {
            attachModelEngine(root, furniture);
        }
        return true;
    }

    /** The way a piece placed by {@code player} will face: towards them, or south. */
    public static float placementYaw(Player player, Placement.Furniture furniture) {
        return placementYaw(player.getLocation().getYaw(), furniture);
    }

    /** As {@link #placementYaw(Player, Placement.Furniture)}, for a player who looks along {@code playerYaw}. */
    public static float placementYaw(float playerYaw, Placement.Furniture furniture) {
        return furniture.facePlayer() ? facing(playerYaw) : 0f;
    }

    /** Where a piece stands and is drawn: the blocks it covers (anchor first), its display's place, its yaw. */
    private record Layout(List<Block> parts, Location at, float yaw) {
    }

    /**
     * Where a piece on {@code block}, turned to {@code facing}, stands and is drawn - placing it, or
     * drawing it again. A chest or a bed turns it its own way. Changes nothing.
     *
     * @return null for a bed that has lost its other half
     */
    private @Nullable Layout layout(Block block, float facing, Placement.Furniture furniture) {
        List<Block> parts = List.of(block);
        Location at = centre(block);
        float yaw = facing;
        switch (furniture.support()) {
            case CHEST -> {
                if (block.getBlockData() instanceof Chest chest) {
                    yaw = yaw(chest.getFacing());
                }
            }
            case BED -> {
                BedLayout layout = bed(block);
                if (layout == null) {
                    return null;
                }
                Block foot = block.getWorld().getBlockAt(layout.footX(), layout.footY(), layout.footZ());
                Block head = block.getWorld().getBlockAt(layout.headX(), layout.headY(), layout.headZ());
                parts = List.of(foot, head);
                at = new Location(block.getWorld(), layout.displayX(), layout.displayY() + 0.5, layout.displayZ());
                yaw = layout.yaw();
            }
            default -> {
            }
        }
        if (!furniture.footprint().single()) {
            parts = footprint(block, furniture, yaw);
            double[] shift = furniture.footprint().centreShift(yaw);
            at.add(shift[0], 0, shift[1]);
        }
        if (furniture.animated() != null) {
            // A Blockbench model's origin is the bottom of its block, not the centre.
            at.subtract(0, 0.5, 0);
        }
        at.setYaw(yaw);
        at.setPitch(0f);
        return new Layout(parts, at, yaw);
    }

    /**
     * Every block a piece placed on {@code anchor}, turned to {@code yaw}, covers: {@code anchor}
     * first, then the rest of its {@link Placement.Furniture#footprint() footprint}.
     */
    public static List<Block> footprint(Block anchor, Placement.Furniture furniture, float yaw) {
        List<Block> blocks = new ArrayList<>();
        for (int[] offset : furniture.footprint().offsets(yaw)) {
            blocks.add(anchor.getRelative(offset[0], offset[1], offset[2]));
        }
        return blocks;
    }

    /**
     * The root display, and for an animated model its bones. Everything is set before it joins the
     * world.
     *
     * @param doubled the root of a double chest: drawn by the double's model
     */
    private ItemDisplay spawn(Location at, CustomItem item, Placement.Furniture furniture, Block anchor, List<Block> parts,
                              boolean doubled) {
        Placement.Display settings = drawn(furniture);
        Placement.Animated animated = doubled ? furniture.doubleChest().animated() : furniture.animated();
        ItemDisplay root = at.getWorld().spawn(at, ItemDisplay.class, display -> {
            tag(display, item.id(), anchor);
            display.getPersistentDataContainer().set(partsTag, PersistentDataType.LONG_ARRAY,
                    parts.stream().mapToLong(part -> BlockKey.pack(part.getX(), part.getY(), part.getZ())).toArray());
            if (doubled) {
                display.getPersistentDataContainer().set(doubleTag, PersistentDataType.BYTE, (byte) 1);
            }
            if (animated == null) {
                display.setItemStack(doubled ? doubleItem(item) : items.create(item, 1));
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.valueOf(settings.transform()));
                display.setTransformation(new Transformation(vector(settings.translation()),
                        rotation(settings.rotation()), vector(settings.scale()), new Quaternionf()));
            }
        });
        if (animated != null) {
            spawnBones(root, item, furniture);
        }
        rootAppeared.accept(root);
        return root;
    }

    /** A double chest's plain model: an item drawn by {@code <namespace>:<id>/double}. */
    private static ItemStack doubleItem(CustomItem item) {
        ResourceLocation key = Placement.DoubleChest.itemModel(item.definition().itemModel());
        ItemStack stack = ItemStack.of(Material.PAPER);
        stack.editMeta(meta -> meta.setItemModel(new NamespacedKey(key.namespace(), key.path())));
        return stack;
    }

    /** Whether a root draws a double chest. */
    private boolean doubled(Entity root) {
        return root.getPersistentDataContainer().has(doubleTag);
    }

    /**
     * The Blockbench model drawing a piece: a double chest's root draws the double's, any other the
     * furniture's own; null for an item model.
     */
    private @Nullable Placement.Animated animatedOf(@Nullable Entity root, Placement.Furniture furniture) {
        if (root != null && doubled(root) && furniture.doubleChest() != null) {
            return furniture.doubleChest().animated();
        }
        return furniture.animated();
    }

    /** One display per bone with cubes, at rest, listed on the root. */
    private List<ItemDisplay> spawnBones(ItemDisplay root, CustomItem item, Placement.Furniture furniture) {
        boolean doubled = doubled(root);
        AnimatedModel model = animatedOf(root, furniture).model();
        Placement.Display settings = drawn(furniture);
        List<Animator.Transform> rest = Animator.rest(model);
        Block anchor = anchor(root);
        List<ItemDisplay> bones = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < model.bones().size(); i++) {
            AnimatedModel.Bone bone = model.bones().get(i);
            if (bone.model() == null) {
                bones.add(null);
                continue;
            }
            Transformation pose = AnimationPlayer.transformation(rest.get(i), settings);
            ItemDisplay display = root.getWorld().spawn(root.getLocation(), ItemDisplay.class, entity -> {
                tag(entity, item.id(), anchor);
                PersistentDataContainer data = entity.getPersistentDataContainer();
                data.set(boneTag, PersistentDataType.STRING, bone.name());
                data.set(rootTag, PersistentDataType.STRING, root.getUniqueId().toString());
                entity.setItemStack(boneItem(item, bone.name(), doubled));
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                entity.setTransformation(pose);
            });
            bones.add(display);
            ids.add(display.getUniqueId().toString());
        }
        root.getPersistentDataContainer().set(bonesTag, PersistentDataType.STRING, String.join(",", ids));
        return bones;
    }

    private void tag(ItemDisplay display, String id, Block anchor) {
        display.setInvulnerable(true);
        display.setGravity(false);
        display.setPersistent(true);
        PersistentDataContainer data = display.getPersistentDataContainer();
        data.set(furnitureTag, PersistentDataType.STRING, id);
        data.set(anchorTag, PersistentDataType.LONG, BlockKey.pack(anchor.getX(), anchor.getY(), anchor.getZ()));
    }

    /**
     * A bone's look: a plain item carrying the bone's item model, {@code <namespace>:<id>/bone_<name>}
     * - or {@code <namespace>:<id>/double/bone_<name>} for a double chest's.
     */
    private static ItemStack boneItem(CustomItem item, String bone, boolean doubled) {
        ResourceLocation base = doubled ? Placement.DoubleChest.itemModel(item.definition().itemModel())
                : item.definition().itemModel();
        ResourceLocation key = AnimatedModel.boneItemModel(base, bone);
        ItemStack stack = ItemStack.of(Material.PAPER);
        stack.editMeta(meta -> meta.setItemModel(new NamespacedKey(key.namespace(), key.path())));
        return stack;
    }

    /**
     * The furniture's display settings as drawn: a bed's are scaled up by {@link #BED_ENCLOSE} around
     * the floor, so its model stays on the floor and grows over the vanilla bed's top and sides.
     */
    static Placement.Display drawn(Placement.Furniture furniture) {
        return drawn(furniture, furniture.display());
    }

    /** {@link #drawn(Placement.Furniture)} for display settings other than the type's own. */
    static Placement.Display drawn(Placement.Furniture furniture, Placement.Display settings) {
        if (furniture.support() != Placement.Support.BED) {
            return settings;
        }
        Placement.Vec3 scale = settings.scale();
        Placement.Vec3 translation = settings.translation();
        // An item display's model is centred half a block above the floor; an animated one starts on it.
        float lift = furniture.animated() == null ? 0.5f * (BED_ENCLOSE - 1) * scale.y() : 0f;
        return new Placement.Display(settings.transform(),
                new Placement.Vec3(translation.x(), translation.y() + lift, translation.z()),
                new Placement.Vec3(scale.x() * BED_ENCLOSE, scale.y() * BED_ENCLOSE, scale.z() * BED_ENCLOSE),
                settings.rotation());
    }

    /**
     * A chest that is ours stays single, and so does any chest beside it that vanilla joined to it:
     * a double chest would show a second chest's half through the model, and share one inventory
     * between two pieces of furniture.
     */
    public void keepSingle(Block chest) {
        keepSingle(chest, false);
    }

    /** @param ours whether {@code chest} is furniture already, before it is recorded as such */
    private void keepSingle(Block chest, boolean ours) {
        if (!(chest.getBlockData() instanceof Chest data) || data.getType() == Chest.Type.SINGLE) {
            return;
        }
        if (!ours && pairAt(chest).isPresent()) {
            // A double chest of furniture: joined on purpose.
            return;
        }
        Block partner = chest.getRelative(joined(data));
        if (!ours && identify(chest).isEmpty() && identify(partner).isEmpty()) {
            // Two vanilla chests: a double chest like any other.
            return;
        }
        for (Block block : List.of(chest, partner)) {
            if (block.getBlockData() instanceof Chest half && half.getType() != Chest.Type.SINGLE) {
                half.setType(Chest.Type.SINGLE);
                block.setBlockData(half, false);
            }
        }
    }

    /** Where a chest's other half is: vanilla's {@code ChestBlock.getConnectedDirection}. */
    private static BlockFace joined(Chest chest) {
        BlockFace facing = chest.getFacing();
        boolean left = chest.getType() == Chest.Type.LEFT;
        return switch (facing) {
            case NORTH -> left ? BlockFace.EAST : BlockFace.WEST;
            case EAST -> left ? BlockFace.SOUTH : BlockFace.NORTH;
            case SOUTH -> left ? BlockFace.WEST : BlockFace.EAST;
            default -> left ? BlockFace.NORTH : BlockFace.SOUTH;
        };
    }

    /** The bed whose half is {@code block}, if both halves are there. */
    static @Nullable BedLayout bed(Block block) {
        if (!(block.getBlockData() instanceof Bed half)) {
            return null;
        }
        BedLayout.Facing facing = facing(half.getFacing());
        if (facing == null) {
            return null;
        }
        BedLayout layout = BedLayout.from(block.getX(), block.getY(), block.getZ(),
                half.getPart() == Bed.Part.FOOT ? BedLayout.Part.FOOT : BedLayout.Part.HEAD, facing);
        Block foot = block.getWorld().getBlockAt(layout.footX(), layout.footY(), layout.footZ());
        Block head = block.getWorld().getBlockAt(layout.headX(), layout.headY(), layout.headZ());
        boolean whole = foot.getBlockData() instanceof Bed f && f.getPart() == Bed.Part.FOOT
                && head.getBlockData() instanceof Bed h && h.getPart() == Bed.Part.HEAD
                && foot.getType() == head.getType();
        return whole ? layout : null;
    }

    private static @Nullable BedLayout.Facing facing(BlockFace face) {
        return switch (face) {
            case NORTH -> BedLayout.Facing.NORTH;
            case SOUTH -> BedLayout.Facing.SOUTH;
            case WEST -> BedLayout.Facing.WEST;
            case EAST -> BedLayout.Facing.EAST;
            default -> null;
        };
    }

    /** The yaw of something facing {@code face}: south 0, west 90, north 180, east 270. */
    private static float yaw(BlockFace face) {
        BedLayout.Facing facing = facing(face);
        return facing == null ? 0f : facing.yaw;
    }

    // ---------------------------------------------------------------- double chests

    /** The double chest one of whose halves stands on {@code block}, if it is one. Memory only. */
    public Optional<ChestPair> pairAt(Block block) {
        ChestPairStore store = pairs;
        return store == null ? Optional.empty()
                : Optional.ofNullable(store.at(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()));
    }

    /**
     * The chest a chest of furniture just placed on {@code placed} joins, as vanilla would join two
     * chests: one of the same furniture, beside it - to its left or right, not behind or in front -
     * facing the same way, still single. Never when the placer sneaks, as in vanilla, nor for
     * furniture without a {@code double:} look.
     */
    public Optional<Block> partnerFor(Block placed, CustomItem item, Placement.Furniture furniture, Player player) {
        if (furniture.doubleChest() == null || pairs == null || player.isSneaking()
                || !(placed.getBlockData() instanceof Chest chest)) {
            return Optional.empty();
        }
        BedLayout.Facing facing = facing(chest.getFacing());
        if (facing == null) {
            return Optional.empty();
        }
        for (BedLayout.Facing side : ChestPairing.sides(facing)) {
            Block neighbour = placed.getRelative(side.dx, 0, side.dz);
            if (neighbour.getBlockData() instanceof Chest other && other.getFacing() == chest.getFacing()
                    && other.getType() == Chest.Type.SINGLE
                    && identify(neighbour).filter(item.id()::equals).isPresent()
                    && pairAt(neighbour).isEmpty()) {
                return Optional.of(neighbour);
            }
        }
        return Optional.empty();
    }

    /**
     * Joins two single chests of the same furniture into a double chest: their own displays go, the
     * vanilla chests underneath become the two halves of a vanilla double chest - its hitbox, its
     * shape for hoppers and pistons - and one display, centred between them, draws the double's
     * model. The pair is in memory at once and written to the database in one transaction; each half
     * keeps its own storage row, so no item moves.
     *
     * <p>Open inventories of either chest are saved and shut first.</p>
     */
    public void join(Block a, Block b, CustomItem item, Placement.Furniture furniture) {
        if (!(a.getBlockData() instanceof Chest chest) || pairs == null) {
            return;
        }
        BedLayout.Facing facing = facing(chest.getFacing());
        BedLayout.Facing towards = towards(a, b);
        if (facing == null || towards == null) {
            return;
        }
        releaseStorage.accept(a);
        releaseStorage.accept(b);
        boolean aFirst = ChestPairing.firstIs(facing, towards);
        Block first = aFirst ? a : b;
        Block second = aFirst ? b : a;
        for (Block half : List.of(first, second)) {
            removeDisplays(half);
        }
        half(first, Chest.Type.RIGHT);
        half(second, Chest.Type.LEFT);
        ItemDisplay root = spawnDouble(item, furniture, first, second);
        link(first, root.getUniqueId());
        link(second, root.getUniqueId());
        pairs.put(new ChestPair(first.getWorld().getUID(), first.getX(), first.getY(), first.getZ(), second.getX(),
                second.getY(), second.getZ(), item.id()));
    }

    /** The double chest's root, centred between its halves, facing the way they do. */
    private ItemDisplay spawnDouble(CustomItem item, Placement.Furniture furniture, Block first, Block second) {
        Location at = new Location(first.getWorld(), (first.getX() + second.getX()) / 2.0 + 0.5, first.getY() + 0.5,
                (first.getZ() + second.getZ()) / 2.0 + 0.5);
        if (furniture.doubleChest().animated() != null) {
            at.subtract(0, 0.5, 0);
        }
        at.setYaw(first.getBlockData() instanceof Chest chest ? yaw(chest.getFacing()) : 0f);
        at.setPitch(0f);
        return spawn(at, item, furniture, first, List.of(first, second), true);
    }

    /**
     * Parts the double chest one of whose halves is {@code block}: the double display goes, the
     * vanilla chests go back to single, and each half that stays gets its own display again.
     *
     * @param leaving a half that is being taken away and gets no display of its own; null to keep both
     */
    public void part(Block block, @Nullable Block leaving) {
        Optional<ChestPair> pair = pairAt(block);
        Entity root = root(block);
        List<Block> halves = new ArrayList<>();
        if (pair.isPresent()) {
            ChestPair p = pair.get();
            halves.add(block.getWorld().getBlockAt(p.x(), p.y(), p.z()));
            halves.add(block.getWorld().getBlockAt(p.px(), p.py(), p.pz()));
        } else if (root != null) {
            halves.addAll(parts(block, root));
        } else {
            halves.add(block);
        }
        releaseStorage.accept(halves.get(0));
        if (root != null && doubled(root)) {
            animations.stop(root.getUniqueId());
            bones(root).forEach(Entity::remove);
            root.remove();
        }
        if (pairs != null) {
            pairs.remove(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
        for (Block half : halves) {
            unlink(half);
            if (half.getBlockData() instanceof Chest chest && chest.getType() != Chest.Type.SINGLE) {
                chest.setType(Chest.Type.SINGLE);
                half.setBlockData(chest, false);
            }
        }
        for (Block half : halves) {
            if (half.equals(leaving)) {
                continue;
            }
            Optional<CustomItem> item = recorded(half).flatMap(registry::get);
            if (item.isPresent() && item.get().placement() instanceof Placement.Furniture furniture
                    && half.getType() == Material.CHEST) {
                Location at = centre(half);
                if (furniture.animated() != null) {
                    at.subtract(0, 0.5, 0);
                }
                at.setYaw(half.getBlockData() instanceof Chest chest ? yaw(chest.getFacing()) : 0f);
                at.setPitch(0f);
                ItemDisplay single = spawn(at, item.get(), furniture, half, List.of(half), false);
                link(half, single.getUniqueId());
            }
        }
    }

    /**
     * A double chest's root that has just loaded is checked against the database, and a single
     * chest's against it too - the two are written at different moments, and a crash between them
     * leaves them disagreeing. What players see wins: a double drawn on two chests that still join is
     * recorded again; one whose chests no longer join is parted; a pair recorded for a chest drawn
     * single is forgotten.
     *
     * @return false when the root was replaced and there is nothing more to restore
     */
    private boolean pairStillStands(ItemDisplay root, CustomItem item, Placement.Furniture furniture) {
        ChestPairStore store = pairs;
        if (store == null || !store.loaded(root.getWorld().getUID())) {
            return true;
        }
        Block anchor = anchor(root);
        Optional<ChestPair> pair = pairAt(anchor);
        if (!doubled(root)) {
            if (pair.isPresent() && furniture.support() == Placement.Support.CHEST) {
                store.remove(anchor.getWorld().getUID(), anchor.getX(), anchor.getY(), anchor.getZ());
            }
            return true;
        }
        List<Block> halves = parts(anchor, root);
        boolean joined = furniture.doubleChest() != null && halves.size() == 2
                && halves.stream().allMatch(half -> half.getBlockData() instanceof Chest chest
                && chest.getType() != Chest.Type.SINGLE && recorded(half).filter(item.id()::equals).isPresent());
        if (!joined) {
            part(anchor, null);
            return false;
        }
        if (pair.isEmpty()) {
            Block first = halves.get(0);
            Block second = halves.get(1);
            store.put(new ChestPair(first.getWorld().getUID(), first.getX(), first.getY(), first.getZ(), second.getX(),
                    second.getY(), second.getZ(), item.id()));
        }
        return true;
    }

    /** Takes away a piece's displays, keeping its records and links to be replaced. */
    private void removeDisplays(Block block) {
        Entity root = root(block);
        if (root == null) {
            root = findRoot(block);
        }
        if (root != null) {
            animations.stop(root.getUniqueId());
            bones(root).forEach(Entity::remove);
            root.remove();
        }
    }

    private static void half(Block block, Chest.Type type) {
        if (block.getBlockData() instanceof Chest chest) {
            chest.setType(type);
            block.setBlockData(chest, false);
        }
    }

    /** Which way {@code b} is from {@code a}, if it is right beside it. */
    private static @Nullable BedLayout.Facing towards(Block a, Block b) {
        int dx = b.getX() - a.getX();
        int dz = b.getZ() - a.getZ();
        if (b.getY() != a.getY()) {
            return null;
        }
        for (BedLayout.Facing facing : BedLayout.Facing.values()) {
            if (facing.dx == dx && facing.dz == dz) {
                return facing;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- ModelEngine

    /**
     * Hands the look to ModelEngine when the furniture names a blueprint and ModelEngine is here.
     * Anything short of success leaves the item display drawing the item model.
     */
    private void attachModelEngine(Entity display, Placement.Furniture furniture) {
        ModelEngineBridge modelEngine = hooks.modelEngine();
        if (furniture.modelEngineId() == null || modelEngine == null) {
            return;
        }
        try {
            modelEngine.attach(display, furniture.modelEngineId(), furniture.display().scale());
        } catch (RuntimeException | LinkageError error) {
            plugin.getLogger().log(Level.WARNING, "ModelEngine could not draw blueprint '" + furniture.modelEngineId()
                    + "'; the furniture keeps its item model.", error);
        }
    }

    /**
     * A furniture display that has just loaded is brought back in line with its definition: an
     * animated model's bones are put back at rest - a crash may have saved them mid-animation - and
     * respawned if the model's bones changed since; a ModelEngine model is restored in case
     * ModelEngine did not restore it itself.
     */
    public void restore(Entity entity) {
        if (!(entity instanceof ItemDisplay root) || !entity.isValid()) {
            return;
        }
        PersistentDataContainer data = entity.getPersistentDataContainer();
        String id = data.get(furnitureTag, PersistentDataType.STRING);
        if (id == null || data.has(rootTag)) {
            return;
        }
        Optional<CustomItem> item = registry.get(id);
        if (item.isEmpty() || !(item.get().placement() instanceof Placement.Furniture furniture)) {
            return;
        }
        if (!pairStillStands(root, item.get(), furniture)) {
            return;
        }
        rootAppeared.accept(root);
        Placement.Animated animated = animatedOf(root, furniture);
        boolean wasAnimated = data.has(bonesTag);
        if (wasAnimated != (animated != null)) {
            respawn(root, item.get(), furniture, wasAnimated);
            return;
        }
        if (animated != null) {
            List<ItemDisplay> bones = bones(root, animated.model());
            if (bones == null) {
                bones(root).forEach(Entity::remove);
                bones = spawnBones(root, item.get(), furniture);
            }
            AnimationPlayer.pose(bones, Animator.rest(animated.model()), drawn(furniture));
            return;
        }
        ModelEngineBridge modelEngine = hooks.modelEngine();
        if (modelEngine != null && furniture.modelEngineId() != null && !modelEngine.isAttached(entity)) {
            attachModelEngine(entity, furniture);
        }
        // Drawn as its type says now - or as the piece's own transform says - whatever it was saved with.
        apply(root, furniture, effectiveDisplay(root, furniture));
    }

    // ---------------------------------------------------------------- editing

    /**
     * How a piece is to be drawn: its own transform when the editor gave it one (for this furniture,
     * not one that stood here before), its type's otherwise.
     */
    public Placement.Display effectiveDisplay(Entity root, Placement.Furniture furniture) {
        FurnitureTransformStore store = transforms;
        String id = furnitureId(root);
        if (store != null && id != null && furniture.animated() == null && !doubled(root)) {
            Block anchor = anchor(root);
            FurnitureTransform own = store.at(anchor.getWorld().getUID(), anchor.getX(), anchor.getY(), anchor.getZ());
            if (own != null && own.furnitureId().equals(id)) {
                return own.transform().applyTo(furniture.display());
            }
        }
        return furniture.display();
    }

    /**
     * Draws a piece with {@code settings} - its single display's transformation, or an animated
     * model's bones at rest - at once, in this tick: what the editor shows while a value changes.
     */
    public void apply(ItemDisplay root, Placement.Furniture furniture, Placement.Display settings) {
        Placement.Display drawn = drawn(furniture, settings);
        Placement.Animated animated = animatedOf(root, furniture);
        if (animated != null) {
            List<ItemDisplay> bones = bones(root, animated.model());
            if (bones != null) {
                AnimationPlayer.pose(bones, Animator.rest(animated.model()), drawn);
            }
            return;
        }
        Transformation wanted = new Transformation(vector(drawn.translation()), rotation(drawn.rotation()),
                vector(drawn.scale()), new Quaternionf());
        if (!wanted.equals(root.getTransformation())) {
            root.setTransformation(wanted);
        }
        ItemDisplay.ItemDisplayTransform transform = ItemDisplay.ItemDisplayTransform.valueOf(drawn.transform());
        if (root.getItemDisplayTransform() != transform) {
            root.setItemDisplayTransform(transform);
        }
    }

    /**
     * Every loaded piece of one furniture drawn again as its type and its own transform say - after
     * the editor saved the type. Main thread.
     *
     * @return how many pieces were drawn again
     */
    public int refresh(String id) {
        Optional<CustomItem> item = registry.get(id);
        if (item.isEmpty() || !(item.get().placement() instanceof Placement.Furniture furniture)) {
            return 0;
        }
        int refreshed = 0;
        for (World world : plugin.getServer().getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                PersistentDataContainer data = display.getPersistentDataContainer();
                if (id.equals(data.get(furnitureTag, PersistentDataType.STRING)) && !data.has(rootTag)) {
                    apply(display, furniture, effectiveDisplay(display, furniture));
                    refreshed++;
                }
            }
        }
        return refreshed;
    }

    /** The (root) display of the furniture standing on {@code block}, if it is loaded. */
    public @Nullable ItemDisplay display(Block block) {
        Entity root = root(block);
        if (root == null) {
            root = findRoot(block);
        }
        return root instanceof ItemDisplay display && display.isValid() ? display : null;
    }

    /** Stops whatever animation the piece is playing, leaving its bones where they are. */
    public void stopAnimation(Entity root) {
        animations.stop(root.getUniqueId());
    }

    /** Whether ModelEngine draws this piece - then its display's transformation is not what players see. */
    public boolean drawnByModelEngine(Placement.Furniture furniture) {
        return furniture.modelEngineId() != null && hooks.modelEngine() != null;
    }

    /** One of this plugin's furniture displays that is a piece's root, not one of its bones. */
    public boolean isPieceRoot(Entity entity) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        return entity instanceof ItemDisplay && data.has(furnitureTag) && !data.has(rootTag);
    }

    /**
     * Draws the piece anchored at a position again, if it is loaded - once its own transform has
     * come from the database, which can be after the piece itself loaded. Main thread.
     */
    public void redraw(World world, int x, int y, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return;
        }
        ItemDisplay display = display(world.getBlockAt(x, y, z));
        String id = display == null ? null : furnitureId(display);
        Optional<CustomItem> item = id == null ? Optional.empty() : registry.get(id);
        if (item.isPresent() && item.get().placement() instanceof Placement.Furniture furniture
                && animatedOf(display, furniture) == null) {
            apply(display, furniture, effectiveDisplay(display, furniture));
        }
    }

    /** The furniture id a display of this plugin carries, or null for any other entity. */
    public @Nullable String furnitureId(Entity entity) {
        return entity.getPersistentDataContainer().get(furnitureTag, PersistentDataType.STRING);
    }

    /** The block a furniture display is anchored to: its support, a bed's foot. */
    public Block anchorOf(Entity display) {
        return anchor(display);
    }

    /**
     * {@link #isOrphan}, and also a bone whose root is gone - what a rollback or a kill command can
     * leave of an animated piece. For the sanity checker; main thread.
     */
    public boolean isLeftOver(Entity entity) {
        if (isOrphan(entity)) {
            return true;
        }
        String root = entity.getPersistentDataContainer().get(rootTag, PersistentDataType.STRING);
        if (root == null) {
            return false;
        }
        try {
            Entity found = plugin.getServer().getEntity(UUID.fromString(root));
            return found == null || !found.isValid();
        } catch (IllegalArgumentException exception) {
            return true;
        }
    }

    /**
     * Forgets the chunk link of a support position whose block is gone - found so by the sanity
     * checker, twice. The row itself is the checker's to purge.
     */
    public void forgetStale(Block block) {
        unlink(block);
    }

    // ---------------------------------------------------------------- integrity (2.0)

    /** The root display of the piece standing on {@code part}, if it is loaded; null otherwise. */
    public @Nullable ItemDisplay pieceRoot(Block part) {
        Entity root = root(part);
        if (root == null || !root.isValid()) {
            root = findRoot(part);
        }
        return root instanceof ItemDisplay display && display.isValid() && isPieceRoot(display) ? display : null;
    }

    /** Every block the piece of this root stands on, its anchor first. */
    public List<Block> pieceParts(Entity root) {
        Block anchor = anchor(root);
        List<Block> parts = new ArrayList<>(parts(anchor, root));
        parts.remove(anchor);
        parts.add(0, anchor);
        return parts;
    }

    /**
     * The blocks a piece anchored on {@code anchor} and turned to {@code yaw} stands on, as its
     * definition has them now: for a piece whose display is gone, from what was recorded of it.
     */
    public List<Block> layoutParts(Block anchor, Placement.Furniture furniture, float yaw) {
        Layout layout = layout(anchor, yaw, furniture);
        return layout == null ? List.of(anchor) : layout.parts();
    }

    /**
     * Draws again a piece whose display is gone - killed, or rolled back, while every block it
     * stands on stayed - turned as it was placed, on the supports already there. Its own transform,
     * if the editor gave it one, is applied as it would be on loading.
     *
     * @return the new root; null if it cannot stand there now (a bed without its other half)
     */
    public @Nullable ItemDisplay redrawGhost(Block anchor, CustomItem item, Placement.Furniture furniture, float yaw) {
        Layout layout = layout(anchor, yaw, furniture);
        if (layout == null) {
            return null;
        }
        ItemDisplay root = spawn(layout.at(), item, furniture, layout.parts().get(0), layout.parts(), false);
        for (Block part : layout.parts()) {
            link(part, root.getUniqueId());
        }
        if (furniture.animated() == null) {
            attachModelEngine(root, furniture);
        }
        Block first = layout.parts().get(0);
        redraw(first.getWorld(), first.getX(), first.getY(), first.getZ());
        return root;
    }

    /**
     * Takes out what is left of a piece the integrity check found broken - some of its blocks gone -
     * or that cannot be drawn again: its displays, if loaded, their links, the invisible supports it
     * placed that still stand (barriers and light blocks; a chest or a bed is the world's own and
     * stays), and the transform kept for it in memory. Its rows are the caller's to purge, all in one
     * transaction. Main thread; never drops anything.
     */
    public void dismantle(@Nullable Entity root, List<Block> parts) {
        if (root != null) {
            if (root instanceof ItemDisplay display) {
                rootGoing.accept(display);
            }
            animations.stop(root.getUniqueId());
            bones(root).forEach(Entity::remove);
            ModelEngineBridge modelEngine = hooks.modelEngine();
            if (modelEngine != null) {
                try {
                    modelEngine.detach(root);
                } catch (RuntimeException | LinkageError error) {
                    plugin.getLogger().log(Level.FINE, "ModelEngine could not detach a furniture model", error);
                }
            }
            root.remove();
        }
        for (Block part : parts) {
            unlink(part);
            if (transforms != null) {
                transforms.forget(part.getWorld().getUID(), part.getX(), part.getY(), part.getZ());
            }
            if (pairs != null) {
                pairs.forget(part.getWorld().getUID(), part.getX(), part.getY(), part.getZ());
            }
            if (part.getType() == Material.BARRIER || part.getType() == Material.LIGHT) {
                part.setType(Material.AIR, false);
            }
        }
    }

    /** Removes a left-over display, and the bones of one that is a root. */
    public void removeLeftOver(Entity entity) {
        if (entity.getPersistentDataContainer().has(bonesTag)) {
            bones(entity).forEach(Entity::remove);
        }
        animations.stop(entity.getUniqueId());
        entity.remove();
    }

    /**
     * Furniture whose definition switched between an item model and an animated one since it was
     * placed: its displays are replaced, in the same place and facing the same way, and relinked.
     */
    private void respawn(ItemDisplay root, CustomItem item, Placement.Furniture furniture, boolean wasAnimated) {
        Location at = root.getLocation();
        // An animated model stands on the floor; an item model is centred in its block.
        at.add(0, wasAnimated ? 0.5 : -0.5, 0);
        Block anchor = anchor(root);
        List<Block> parts = parts(anchor, root);
        boolean doubled = doubled(root);
        bones(root).forEach(Entity::remove);
        root.remove();
        ItemDisplay replacement = spawn(at, item, furniture, anchor, parts, doubled);
        parts.forEach(part -> link(part, replacement.getUniqueId()));
        if (furniture.animated() == null && !doubled) {
            attachModelEngine(replacement, furniture);
        }
    }

    // ---------------------------------------------------------------- reading

    /**
     * The id of the furniture standing on this block, if it is a furniture support. Answered from
     * memory; the chunk link covers the moment after startup when a world's rows are still loading.
     */
    public Optional<String> identify(Block block) {
        return isSupport(block.getType()) ? recorded(block) : Optional.empty();
    }

    /** The id of the furniture recorded at this position, whatever block is there now. */
    private Optional<String> recorded(Block block) {
        PlacedContent placed = store.at(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        if (placed != null && placed.kind() == PlacedContent.Kind.FURNITURE) {
            return Optional.of(placed.contentId());
        }
        Entity display = root(block);
        return display == null
                ? Optional.empty()
                : Optional.ofNullable(display.getPersistentDataContainer().get(furnitureTag, PersistentDataType.STRING));
    }

    /** The definition of the furniture standing on this block, if it is still defined as furniture. */
    public Optional<Placement.Furniture> definition(Block block) {
        return item(block).map(CustomItem::placement)
                .filter(Placement.Furniture.class::isInstance).map(Placement.Furniture.class::cast);
    }

    /** The custom item of the furniture standing on this block, if it is still defined. */
    public Optional<CustomItem> item(Block block) {
        return identify(block).flatMap(registry::get);
    }

    /**
     * The piece standing on {@code part}, and where {@code part} is in it; empty when its root
     * display is not loaded.
     */
    public Optional<Piece> piece(Block part) {
        if (!(root(part) instanceof ItemDisplay root) || !root.isValid()) {
            return Optional.empty();
        }
        Block anchor = anchor(root);
        float yaw = root.getLocation().getYaw();
        int[] right = Footprint.right(yaw);
        int dx = part.getX() - anchor.getX();
        int dz = part.getZ() - anchor.getZ();
        return Optional.of(new Piece(root, anchor, yaw, dx * right[0] + dz * right[1], part.getY() - anchor.getY()));
    }

    /** The direction the furniture on this block faces: its display's yaw. */
    public Optional<Float> facing(Block block) {
        Entity display = root(block);
        return display == null ? Optional.empty() : Optional.of(display.getLocation().getYaw());
    }

    /**
     * Whether furniture claims this position, whatever block is there right now. Used before a block
     * replaces a light-block support, which vanilla treats like air.
     */
    public boolean claims(Block block) {
        PlacedContent placed = store.at(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        return (placed != null && placed.kind() == PlacedContent.Kind.FURNITURE) || linkedDisplay(block) != null;
    }

    // ---------------------------------------------------------------- removing

    /**
     * Takes the furniture on this support block out of the world: its displays, its links, its
     * records, every other block it stands on (a bed's other half, without drops) and - when asked -
     * drops its item. Leaves {@code block} itself to the caller, since a block break removes it
     * anyway.
     *
     * <p>Works for furniture no longer defined in the contents too, minus the drop: there is no
     * item left to drop.</p>
     */
    public void remove(Block block, String id, boolean dropItem) {
        if (pairAt(block).isPresent() || (root(block) instanceof ItemDisplay display && doubled(display))) {
            // Half of a double chest: the other half goes back to being a chest of its own first.
            part(block, block);
        }
        Entity root = root(block);
        if (root == null) {
            root = findRoot(block);
        }
        List<Block> parts = parts(block, root);
        if (root != null) {
            if (root instanceof ItemDisplay display) {
                rootGoing.accept(display);
            }
            animations.stop(root.getUniqueId());
            for (Entity bone : bones(root)) {
                bone.remove();
            }
            ModelEngineBridge modelEngine = hooks.modelEngine();
            if (modelEngine != null) {
                try {
                    modelEngine.detach(root);
                } catch (RuntimeException | LinkageError error) {
                    plugin.getLogger().log(Level.WARNING, "ModelEngine could not remove a furniture model", error);
                }
            }
            root.remove();
        }
        FurnitureTransformStore own = transforms;
        List<int[]> positions = new ArrayList<>(parts.size());
        for (Block part : parts) {
            unlink(part);
            positions.add(new int[] {part.getX(), part.getY(), part.getZ()});
            if (own != null && own.at(part.getWorld().getUID(), part.getX(), part.getY(), part.getZ()) != null) {
                own.remove(part.getWorld().getUID(), part.getX(), part.getY(), part.getZ());
            }
            if (!part.equals(block) && isSupport(part.getType())) {
                // No physics: the other half of a bed would otherwise break with a drop of its own.
                part.setType(Material.AIR, false);
            }
        }
        // The rows of all its blocks go in one database task and one transaction.
        store.removeAll(block.getWorld().getUID(), positions);
        if (dropItem) {
            registry.get(id).ifPresent(item -> block.getWorld().dropItemNaturally(centre(block), items.create(item, 1)));
        }
    }

    /**
     * A block that blew itself up - a bed outside the overworld - was furniture: vanilla has
     * already removed both halves, and drops nothing for them, so neither does this.
     *
     * @return whether it was furniture
     */
    public boolean exploded(Block block) {
        Optional<String> id = recorded(block);
        id.ifPresent(furniture -> remove(block, furniture, false));
        return id.isPresent();
    }

    /** Every block the furniture standing on {@code block} covers, {@code block} among them. */
    public List<Block> partsOf(Block block) {
        Entity root = root(block);
        return parts(block, root != null ? root : findRoot(block));
    }

    /** Every support block of the furniture at {@code block}: the root's list, or what the block itself says. */
    private List<Block> parts(Block block, @Nullable Entity root) {
        long[] packed = root == null ? null : root.getPersistentDataContainer().get(partsTag, PersistentDataType.LONG_ARRAY);
        if (packed != null && packed.length > 0) {
            List<Block> parts = new ArrayList<>();
            for (long key : packed) {
                parts.add(block.getWorld().getBlockAt(BlockKey.x(key), BlockKey.y(key), BlockKey.z(key)));
            }
            if (!parts.contains(block)) {
                parts.add(block);
            }
            return parts;
        }
        BedLayout bed = bed(block);
        if (bed != null) {
            World world = block.getWorld();
            return List.of(world.getBlockAt(bed.footX(), bed.footY(), bed.footZ()),
                    world.getBlockAt(bed.headX(), bed.headY(), bed.headZ()));
        }
        return List.of(block);
    }

    /**
     * The furniture support the player is punching, if any.
     *
     * <p>A barrier can be clicked directly, but a light block cannot be targeted at all, so the
     * punch is followed through the air, block by block, until it meets furniture or something
     * solid.</p>
     */
    public Optional<Block> target(Player player) {
        BlockIterator ray = new BlockIterator(player, REACH);
        while (ray.hasNext()) {
            Block block = ray.next();
            if (identify(block).isPresent()) {
                return Optional.of(block);
            }
            if (!block.isPassable() || obstacle.test(block)) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * The furniture a click the client sent elsewhere was meant for - only furniture on a light
     * block, which a client cannot target, so a click at it lands on the block behind it, or on the
     * air. A barrier, a chest or a bed the client clicks itself: a click that landed elsewhere was not
     * for it, wherever the server's copy of the player's look - which can be a tick behind the
     * client's - points.
     *
     * @param clicked the block the click landed on, which is not furniture; null for a click at the air
     */
    public Optional<Block> clickedThrough(Player player, @Nullable Block clicked) {
        Location eye = player.getEyeLocation();
        double behind = clicked == null ? Double.MAX_VALUE : eye.distanceSquared(centre(clicked));
        BlockIterator ray = new BlockIterator(player, REACH);
        while (ray.hasNext()) {
            Block block = ray.next();
            if (block.equals(clicked)) {
                return Optional.empty();
            }
            if (identify(block).isPresent()) {
                return block.getType() == Material.LIGHT && eye.distanceSquared(centre(block)) < behind
                        ? Optional.of(block) : Optional.empty();
            }
            if (!block.isPassable() || obstacle.test(block)) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * True for one of this plugin's displays whose support block is gone or belongs to other
     * displays - left behind by a crash, a rollback, or a support removed while its displays were
     * not loaded. A bone is judged by its root's link.
     */
    public boolean isOrphan(Entity entity) {
        if (!(entity instanceof ItemDisplay)) {
            return false;
        }
        PersistentDataContainer data = entity.getPersistentDataContainer();
        Long anchor = data.get(anchorTag, PersistentDataType.LONG);
        if (anchor == null) {
            return false;
        }
        Block block = entity.getWorld().getBlockAt(BlockKey.x(anchor), BlockKey.y(anchor), BlockKey.z(anchor));
        if (!isSupport(block.getType())) {
            return true;
        }
        String root = data.get(rootTag, PersistentDataType.STRING);
        UUID linked = linkedDisplay(block);
        return linked == null || !(root != null ? root.equals(linked.toString()) : entity.getUniqueId().equals(linked));
    }

    // ---------------------------------------------------------------- animation

    /**
     * Plays one of the furniture's animations, by its name in Blockbench.
     *
     * @return false if the furniture has no animated model, or no animation by that name
     */
    public boolean animate(Block block, String clipName) {
        Optional<Placement.Furniture> furniture = definition(block);
        Entity root = root(block);
        Placement.Animated animated = furniture.isEmpty() ? null : animatedOf(root, furniture.get());
        if (animated == null || !(root instanceof ItemDisplay display)) {
            return false;
        }
        AnimatedModel model = animated.model();
        AnimatedModel.Clip clip = model.clips().get(clipName);
        if (clip == null) {
            return false;
        }
        List<ItemDisplay> bones = bones(display, model);
        if (bones == null) {
            return false;
        }
        animations.play(root.getUniqueId(), bones, model, clip, drawn(furniture.get()));
        return true;
    }

    /** The names of the animations the furniture on this block can play. */
    public List<String> clips(Block block) {
        Entity root = root(block);
        return definition(block).map(furniture -> animatedOf(root, furniture))
                .map(animated -> List.copyOf(animated.model().clips().keySet())).orElse(List.of());
    }

    /**
     * Storage furniture was opened - by the first viewer, in the same tick as their click: its
     * {@code open} animation plays, and a chest's or a barrel's sound. A double chest's lid waits for
     * the configured delay, and so does its {@code close}.
     */
    public void storageOpened(Block block) {
        Optional<Placement.Furniture> furniture = definition(block);
        if (furniture.isEmpty()) {
            return;
        }
        Entity root = root(block);
        Placement.Animated animated = animatedOf(root, furniture.get());
        if (root != null && doubled(root)) {
            if (animated != null && animated.open() != null) {
                afterLidDelay(root.getUniqueId(), () -> animate(block, animated.open()));
            }
            sound(block, Sound.BLOCK_CHEST_OPEN);
            return;
        }
        if (animated != null && animated.open() != null) {
            animate(block, animated.open());
        }
        sound(block, furniture.get().support() == Placement.Support.CHEST ? Sound.BLOCK_CHEST_OPEN : Sound.BLOCK_BARREL_OPEN);
    }

    /**
     * Storage furniture was closed by its last viewer.
     *
     * @param animate false when the server is stopping: the lid is put back at once, so it is not
     *                saved open
     */
    public void storageClosed(Block block, boolean animate) {
        Optional<Placement.Furniture> furniture = definition(block);
        if (furniture.isEmpty()) {
            return;
        }
        Entity current = root(block);
        Placement.Animated animated = animatedOf(current, furniture.get());
        if (animated != null) {
            if (animate && animated.close() != null && current != null && doubled(current)) {
                afterLidDelay(current.getUniqueId(), () -> animate(block, animated.close()));
            } else if (animate && animated.close() != null && animate(block, animated.close())) {
                // Playing.
            } else if (current instanceof ItemDisplay root) {
                animations.stop(root.getUniqueId());
                List<ItemDisplay> bones = bones(root, animated.model());
                if (bones != null) {
                    AnimationPlayer.pose(bones, Animator.rest(animated.model()), drawn(furniture.get()));
                }
            }
        }
        if (animate) {
            sound(block, furniture.get().support() == Placement.Support.CHEST
                    ? Sound.BLOCK_CHEST_CLOSE : Sound.BLOCK_BARREL_CLOSE);
        }
    }

    /**
     * Runs a double chest's lid animation on the main thread, on the first tick at least the
     * configured time after now - the click that opened or shut it. Ticks are 50 ms apart and a click
     * is handled between two of them, so a count of ticks alone would land anywhere from one tick
     * after the click to two: the clock decides instead. Opening and shutting keep their order, the
     * earlier one's task being the older.
     */
    private void afterLidDelay(UUID root, Runnable lid) {
        afterDelay(root, System.nanoTime(), doubleDelayNanos, lid);
    }

    /**
     * Runs {@code task} on the main thread on the first tick at least {@code delayNanos} after
     * {@code fromNanos} ({@link System#nanoTime()}) - the click that set it off - if the piece whose
     * root this is still stands then. The clock decides, not a count of ticks: see
     * {@link #afterLidDelay}. With no delay left, it runs now.
     */
    public void afterDelay(UUID root, long fromNanos, long delayNanos, Runnable task) {
        Runnable onTheDisplay = () -> {
            Entity still = plugin.getServer().getEntity(root);
            if (still != null && still.isValid()) {
                task.run();
            }
        };
        long due = fromNanos + Math.max(0, delayNanos);
        if (System.nanoTime() - due >= 0) {
            onTheDisplay.run();
            return;
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, timer -> {
            if (System.nanoTime() - due >= 0) {
                timer.cancel();
                onTheDisplay.run();
            }
        }, 1L, 1L);
    }

    private static void sound(Block block, Sound sound) {
        block.getWorld().playSound(centre(block), sound, 0.6f, 1f);
    }

    // ---------------------------------------------------------------- displays

    private @Nullable Entity root(Block block) {
        UUID linked = linkedDisplay(block);
        return linked == null ? null : plugin.getServer().getEntity(linked);
    }

    /** The root for a support block when its link is gone: ours, not a bone, listing this block. */
    private @Nullable Entity findRoot(Block block) {
        long key = BlockKey.pack(block.getX(), block.getY(), block.getZ());
        return block.getWorld().getNearbyEntities(centre(block), 1.6, 1.1, 1.6, entity -> {
                    if (!(entity instanceof ItemDisplay)) {
                        return false;
                    }
                    PersistentDataContainer data = entity.getPersistentDataContainer();
                    if (data.has(rootTag)) {
                        return false;
                    }
                    long[] parts = data.get(partsTag, PersistentDataType.LONG_ARRAY);
                    return parts != null
                            ? Arrays.stream(parts).anyMatch(part -> part == key)
                            : Long.valueOf(key).equals(data.get(anchorTag, PersistentDataType.LONG));
                })
                .stream().findFirst().orElse(null);
    }

    /** Every bone display of a root that is loaded: those it lists, and any naming it as their root. */
    private List<Entity> bones(Entity root) {
        Set<Entity> bones = new HashSet<>();
        String listed = root.getPersistentDataContainer().get(bonesTag, PersistentDataType.STRING);
        if (listed != null && !listed.isEmpty()) {
            for (String raw : listed.split(",")) {
                try {
                    Entity bone = plugin.getServer().getEntity(UUID.fromString(raw));
                    if (bone != null) {
                        bones.add(bone);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Not a UUID: nothing to remove.
                }
            }
        }
        String id = root.getUniqueId().toString();
        bones.addAll(root.getWorld().getNearbyEntities(root.getLocation(), 0.5, 0.5, 0.5, entity ->
                entity instanceof ItemDisplay && id.equals(entity.getPersistentDataContainer().get(rootTag, PersistentDataType.STRING))));
        return List.copyOf(bones);
    }

    /**
     * A root's bone displays in the model's bone order, null for bones without cubes - or null
     * altogether when they no longer match the model's bones, one display per bone with cubes.
     */
    private @Nullable List<ItemDisplay> bones(ItemDisplay root, AnimatedModel model) {
        Map<String, ItemDisplay> byName = new HashMap<>();
        for (Entity entity : bones(root)) {
            String name = entity.getPersistentDataContainer().get(boneTag, PersistentDataType.STRING);
            if (name == null || !(entity instanceof ItemDisplay display) || byName.put(name, display) != null) {
                return null;
            }
        }
        List<ItemDisplay> ordered = new ArrayList<>();
        int expected = 0;
        for (AnimatedModel.Bone bone : model.bones()) {
            if (bone.model() == null) {
                ordered.add(null);
                continue;
            }
            expected++;
            ItemDisplay display = byName.get(bone.name());
            if (display == null) {
                return null;
            }
            ordered.add(display);
        }
        return expected == byName.size() ? ordered : null;
    }

    private Block anchor(Entity root) {
        Long anchor = root.getPersistentDataContainer().get(anchorTag, PersistentDataType.LONG);
        Location at = root.getLocation();
        return anchor == null
                ? at.getBlock()
                : root.getWorld().getBlockAt(BlockKey.x(anchor), BlockKey.y(anchor), BlockKey.z(anchor));
    }

    // ---------------------------------------------------------------- chunk link

    private NamespacedKey linkKey(Block block) {
        return new NamespacedKey(plugin, "furniture." + (block.getX() & 15) + "." + block.getY() + "." + (block.getZ() & 15));
    }

    private void link(Block block, UUID display) {
        block.getChunk().getPersistentDataContainer().set(linkKey(block), PersistentDataType.STRING, display.toString());
    }

    private void unlink(Block block) {
        block.getChunk().getPersistentDataContainer().remove(linkKey(block));
    }

    private @Nullable UUID linkedDisplay(Block block) {
        String raw = block.getChunk().getPersistentDataContainer().get(linkKey(block), PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    // ---------------------------------------------------------------- geometry

    private static Location centre(Block block) {
        return block.getLocation().add(0.5, 0.5, 0.5);
    }

    /** Towards the player, to the nearest quarter turn: an entity at yaw 0 faces south, +z. */
    private static float facing(Player player) {
        return facing(player.getLocation().getYaw());
    }

    private static float facing(float playerYaw) {
        float yaw = playerYaw + 180f;
        return ((Math.round(yaw / 90f) * 90f) % 360f + 360f) % 360f;
    }

    private static Vector3f vector(Placement.Vec3 vec) {
        return new Vector3f(vec.x(), vec.y(), vec.z());
    }

    private static Quaternionf rotation(Placement.Vec3 degrees) {
        return new Quaternionf().rotationXYZ((float) Math.toRadians(degrees.x()),
                (float) Math.toRadians(degrees.y()), (float) Math.toRadians(degrees.z()));
    }
}
