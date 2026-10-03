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
import com.arkcronist.content.core.storage.BlockKey;
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
    /** Something else living in an invisible block, which a punch at furniture must not pass through. */
    private Predicate<Block> obstacle = block -> false;

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
        List<Block> parts = List.of(block);
        Location at = centre(block);
        float yaw = furniture.facePlayer() ? facing(player) : 0f;
        switch (furniture.support()) {
            case LIGHT -> {
                if (block.getBlockData() instanceof Light light) {
                    // A light item places at level 15; the furniture decides its own.
                    light.setLevel(furniture.light());
                    block.setBlockData(light, false);
                }
            }
            case CHEST -> {
                keepSingle(block, true);
                if (block.getBlockData() instanceof Chest chest) {
                    yaw = yaw(chest.getFacing());
                }
            }
            case BED -> {
                BedLayout layout = bed(block);
                if (layout == null) {
                    return false;
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
        if (furniture.animated() != null) {
            // A Blockbench model's origin is the bottom of its block, not the centre.
            at.subtract(0, 0.5, 0);
        }
        at.setYaw(yaw);
        at.setPitch(0f);

        Block anchor = parts.get(0);
        ItemDisplay root = spawn(at, item, furniture, anchor, parts);
        for (Block part : parts) {
            link(part, root.getUniqueId());
            store.add(new PlacedContent(part.getWorld().getUID(), part.getX(), part.getY(), part.getZ(),
                    item.id(), PlacedContent.Kind.FURNITURE));
        }
        if (furniture.animated() == null) {
            attachModelEngine(root, furniture);
        }
        return true;
    }

    /** The root display, and for an animated model its bones. Everything is set before it joins the world. */
    private ItemDisplay spawn(Location at, CustomItem item, Placement.Furniture furniture, Block anchor, List<Block> parts) {
        Placement.Display settings = drawn(furniture);
        ItemDisplay root = at.getWorld().spawn(at, ItemDisplay.class, display -> {
            tag(display, item.id(), anchor);
            display.getPersistentDataContainer().set(partsTag, PersistentDataType.LONG_ARRAY,
                    parts.stream().mapToLong(part -> BlockKey.pack(part.getX(), part.getY(), part.getZ())).toArray());
            if (furniture.animated() == null) {
                display.setItemStack(items.create(item, 1));
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.valueOf(settings.transform()));
                display.setTransformation(new Transformation(vector(settings.translation()),
                        rotation(settings.rotation()), vector(settings.scale()), new Quaternionf()));
            }
        });
        if (furniture.animated() != null) {
            spawnBones(root, item, furniture);
        }
        return root;
    }

    /** One display per bone with cubes, at rest, listed on the root. */
    private List<ItemDisplay> spawnBones(ItemDisplay root, CustomItem item, Placement.Furniture furniture) {
        AnimatedModel model = furniture.animated().model();
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
                entity.setItemStack(boneItem(item, bone.name()));
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

    /** A bone's look: a plain item carrying the bone's item model, {@code <namespace>:<id>/bone_<name>}. */
    private static ItemStack boneItem(CustomItem item, String bone) {
        ResourceLocation key = AnimatedModel.boneItemModel(item.definition().itemModel(), bone);
        ItemStack stack = ItemStack.of(Material.PAPER);
        stack.editMeta(meta -> meta.setItemModel(new NamespacedKey(key.namespace(), key.path())));
        return stack;
    }

    /**
     * The furniture's display settings as drawn: a bed's are scaled up by {@link #BED_ENCLOSE} around
     * the floor, so its model stays on the floor and grows over the vanilla bed's top and sides.
     */
    static Placement.Display drawn(Placement.Furniture furniture) {
        Placement.Display settings = furniture.display();
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
        boolean wasAnimated = data.has(bonesTag);
        if (wasAnimated != (furniture.animated() != null)) {
            respawn(root, item.get(), furniture, wasAnimated);
            return;
        }
        if (furniture.animated() != null) {
            List<ItemDisplay> bones = bones(root, furniture.animated().model());
            if (bones == null) {
                bones(root).forEach(Entity::remove);
                bones = spawnBones(root, item.get(), furniture);
            }
            AnimationPlayer.pose(bones, Animator.rest(furniture.animated().model()), drawn(furniture));
            return;
        }
        ModelEngineBridge modelEngine = hooks.modelEngine();
        if (modelEngine != null && furniture.modelEngineId() != null && !modelEngine.isAttached(entity)) {
            attachModelEngine(entity, furniture);
        }
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
        bones(root).forEach(Entity::remove);
        root.remove();
        ItemDisplay replacement = spawn(at, item, furniture, anchor, parts);
        parts.forEach(part -> link(part, replacement.getUniqueId()));
        if (furniture.animated() == null) {
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
        Entity root = root(block);
        if (root == null) {
            root = findRoot(block);
        }
        List<Block> parts = parts(block, root);
        if (root != null) {
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
        for (Block part : parts) {
            unlink(part);
            store.remove(part.getWorld().getUID(), part.getX(), part.getY(), part.getZ());
            if (!part.equals(block) && isSupport(part.getType())) {
                // No physics: the other half of a bed would otherwise break with a drop of its own.
                part.setType(Material.AIR, false);
            }
        }
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
        if (furniture.isEmpty() || furniture.get().animated() == null || !(root instanceof ItemDisplay display)) {
            return false;
        }
        AnimatedModel model = furniture.get().animated().model();
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
        return definition(block).map(Placement.Furniture::animated)
                .map(animated -> List.copyOf(animated.model().clips().keySet())).orElse(List.of());
    }

    /**
     * Storage furniture was opened - by the first viewer, in the same tick as their click: its
     * {@code open} animation plays, and a chest's or a barrel's sound.
     */
    public void storageOpened(Block block) {
        Optional<Placement.Furniture> furniture = definition(block);
        if (furniture.isEmpty()) {
            return;
        }
        Placement.Animated animated = furniture.get().animated();
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
        Placement.Animated animated = furniture.get().animated();
        if (animated != null) {
            if (animate && animated.close() != null && animate(block, animated.close())) {
                // Playing.
            } else if (root(block) instanceof ItemDisplay root) {
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
        float yaw = player.getLocation().getYaw() + 180f;
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
