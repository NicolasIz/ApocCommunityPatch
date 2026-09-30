package com.arkcronist.content.bukkit.furniture;

import com.arkcronist.content.bukkit.hooks.HookManager;
import com.arkcronist.content.bukkit.hooks.ModelEngineBridge;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.storage.BlockKey;
import com.arkcronist.content.core.storage.PlacedContent;
import com.arkcronist.content.core.storage.PlacedContentStore;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Furniture: an invisible support block that gives it a body, and an {@link ItemDisplay} on that
 * block that gives it a look.
 *
 * <p>Three records tie the two together, each for a different reader:</p>
 * <ul>
 *   <li>the <b>chunk's</b> persistent data holds the display's UUID under a key naming the support
 *       block. A barrier or light block has no persistent data of its own - only block entities do -
 *       so the chunk stands in for it, one key per block. It is saved with the chunk, so it can never
 *       disagree with the blocks it describes;</li>
 *   <li>the <b>display's</b> persistent data holds the furniture id and its support block's
 *       position, so a display that loads without its support can tell it is an orphan;</li>
 *   <li>the {@link PlacedContentStore} answers "is there furniture here" from memory, and keeps the
 *       database row.</li>
 * </ul>
 *
 * <p>Main thread only. Entities cannot be spawned or removed from any other thread - Paper refuses
 * an asynchronous entity add outright - so the display is created on the server thread in the same
 * tick as its block, and only the database write leaves it.</p>
 */
public final class FurnitureService {

    /** How far a punch reaches for furniture a ray passes through, such as a light-block support. */
    private static final int REACH = 5;

    private final Plugin plugin;
    private final ItemRegistry registry;
    private final ItemFactory items;
    private final PlacedContentStore store;
    private final HookManager hooks;
    private final NamespacedKey furnitureTag;
    private final NamespacedKey anchorTag;
    /** Something else living in an invisible block, which a punch at furniture must not pass through. */
    private Predicate<Block> obstacle = block -> false;

    public FurnitureService(Plugin plugin, ItemRegistry registry, ItemFactory items, PlacedContentStore store,
                            HookManager hooks) {
        this.plugin = plugin;
        this.registry = registry;
        this.items = items;
        this.store = store;
        this.hooks = hooks;
        this.furnitureTag = new NamespacedKey(plugin, "furniture");
        this.anchorTag = new NamespacedKey(plugin, "furniture_anchor");
    }

    /** Crops live in light blocks too: a ray looking for furniture stops at one, instead of reaching past it. */
    public void stopAt(Predicate<Block> obstacle) {
        this.obstacle = obstacle;
    }

    public static boolean isSupport(Material material) {
        return material == Material.BARRIER || material == Material.LIGHT;
    }

    public static Material material(Placement.Support support) {
        return support == Placement.Support.LIGHT ? Material.LIGHT : Material.BARRIER;
    }

    /**
     * Places the display on a support block the player has just put down, and records both.
     *
     * @param block already the support block - vanilla placed it from the item
     */
    public void place(Block block, Player player, CustomItem item, Placement.Furniture furniture) {
        if (furniture.support() == Placement.Support.LIGHT && block.getBlockData() instanceof Light light) {
            // A light item places at level 15; the furniture decides its own.
            light.setLevel(furniture.light());
            block.setBlockData(light, false);
        }

        Location at = centre(block);
        at.setYaw(furniture.facePlayer() ? facing(player) : 0f);
        at.setPitch(0f);
        ItemDisplay display = block.getWorld().spawn(at, ItemDisplay.class,
                entity -> configure(entity, item, furniture.display(), block));

        link(block, display.getUniqueId());
        store.add(new PlacedContent(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ(),
                item.id(), PlacedContent.Kind.FURNITURE));
        attachModelEngine(display, furniture);
    }

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
     * A furniture display that has just loaded gets its ModelEngine model back, in case ModelEngine
     * did not restore it itself.
     */
    public void restoreModel(Entity entity) {
        ModelEngineBridge modelEngine = hooks.modelEngine();
        if (modelEngine == null || !(entity instanceof ItemDisplay)) {
            return;
        }
        String id = entity.getPersistentDataContainer().get(furnitureTag, PersistentDataType.STRING);
        Optional<CustomItem> item = id == null ? Optional.empty() : registry.get(id);
        if (item.isPresent() && item.get().placement() instanceof Placement.Furniture furniture
                && furniture.modelEngineId() != null && !modelEngine.isAttached(entity)) {
            attachModelEngine(entity, furniture);
        }
    }

    /** Everything is set before the entity joins the world, so no client ever sees it half-made. */
    private void configure(ItemDisplay display, CustomItem item, Placement.Display settings, Block anchor) {
        display.setItemStack(items.create(item, 1));
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.valueOf(settings.transform()));
        display.setTransformation(new Transformation(vector(settings.translation()), rotation(settings.rotation()),
                vector(settings.scale()), new Quaternionf()));
        display.setInvulnerable(true);
        display.setGravity(false);
        display.setPersistent(true);

        PersistentDataContainer data = display.getPersistentDataContainer();
        data.set(furnitureTag, PersistentDataType.STRING, item.id());
        data.set(anchorTag, PersistentDataType.LONG, BlockKey.pack(anchor.getX(), anchor.getY(), anchor.getZ()));
    }

    /**
     * The id of the furniture standing on this block, if it is a furniture support. Answered from
     * memory; the chunk link covers the moment after startup when a world's rows are still loading.
     */
    public Optional<String> identify(Block block) {
        if (!isSupport(block.getType())) {
            return Optional.empty();
        }
        PlacedContent placed = store.at(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        if (placed != null && placed.kind() == PlacedContent.Kind.FURNITURE) {
            return Optional.of(placed.contentId());
        }
        UUID linked = linkedDisplay(block);
        Entity display = linked == null ? null : plugin.getServer().getEntity(linked);
        return display == null
                ? Optional.empty()
                : Optional.ofNullable(display.getPersistentDataContainer().get(furnitureTag, PersistentDataType.STRING));
    }

    /** The definition of the furniture standing on this block, if it is still defined as furniture. */
    public Optional<Placement.Furniture> definition(Block block) {
        return identify(block).flatMap(registry::get).map(CustomItem::placement)
                .filter(Placement.Furniture.class::isInstance).map(Placement.Furniture.class::cast);
    }

    /** The direction the furniture on this block faces: its display's yaw. */
    public Optional<Float> facing(Block block) {
        UUID linked = linkedDisplay(block);
        Entity display = linked == null ? null : plugin.getServer().getEntity(linked);
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

    /**
     * Takes the furniture on this support block out of the world: its display, its link, its record
     * and - when asked - drops its item. Leaves the support block itself to the caller, since a
     * block break removes it anyway.
     *
     * <p>Works for furniture no longer defined in the contents too, minus the drop: there is no
     * item left to drop.</p>
     */
    public void remove(Block block, String id, boolean dropItem) {
        UUID linked = linkedDisplay(block);
        Entity display = linked == null ? null : plugin.getServer().getEntity(linked);
        if (display == null) {
            display = findDisplay(block);
        }
        if (display != null) {
            ModelEngineBridge modelEngine = hooks.modelEngine();
            if (modelEngine != null) {
                try {
                    modelEngine.detach(display);
                } catch (RuntimeException | LinkageError error) {
                    plugin.getLogger().log(Level.WARNING, "ModelEngine could not remove a furniture model", error);
                }
            }
            display.remove();
        }
        unlink(block);
        store.remove(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        if (dropItem) {
            registry.get(id).ifPresent(item -> block.getWorld().dropItemNaturally(centre(block), items.create(item, 1)));
        }
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
     * True for one of this plugin's displays whose support block is gone or belongs to another
     * display - left behind by a crash, a rollback, or a support removed while its display was not
     * loaded.
     */
    public boolean isOrphan(Entity entity) {
        if (!(entity instanceof ItemDisplay)) {
            return false;
        }
        Long anchor = entity.getPersistentDataContainer().get(anchorTag, PersistentDataType.LONG);
        if (anchor == null) {
            return false;
        }
        Block block = entity.getWorld().getBlockAt(BlockKey.x(anchor), BlockKey.y(anchor), BlockKey.z(anchor));
        return !isSupport(block.getType()) || !entity.getUniqueId().equals(linkedDisplay(block));
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

    private UUID linkedDisplay(Block block) {
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

    /** The display for a support block when its link is gone: ours, anchored on exactly this block. */
    private Entity findDisplay(Block block) {
        long anchor = BlockKey.pack(block.getX(), block.getY(), block.getZ());
        return block.getWorld().getNearbyEntities(centre(block), 0.6, 0.6, 0.6, entity ->
                entity instanceof ItemDisplay
                        && Long.valueOf(anchor).equals(entity.getPersistentDataContainer().get(anchorTag, PersistentDataType.LONG)))
                .stream().findFirst().orElse(null);
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
