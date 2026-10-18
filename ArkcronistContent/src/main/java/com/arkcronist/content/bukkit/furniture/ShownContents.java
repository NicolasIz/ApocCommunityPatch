package com.arkcronist.content.bukkit.furniture;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.furniture.PieceAxes;
import com.arkcronist.content.core.storage.DatabaseManager;
import com.arkcronist.content.core.storage.StoredInventory;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * 3.4: storage furniture that shows what it holds ({@code show:}) - swords hanging on a rack, armour
 * on a mannequin.
 *
 * <p>What is shown is drawn by entities made for it and never saved with the chunk: they are made
 * again from the stored contents whenever the piece's root appears - its chunk loaded, the server
 * started - and taken away with it. So nothing is ever left behind by a crash, and nothing needs
 * cleaning up. Armour goes on an armour stand no one sees, which Java and Bedrock clients both draw
 * wearing it; other things are item displays, which only Java clients draw.</p>
 *
 * <p>Such a piece keeps one inventory, at its anchor, whichever of its blocks is clicked. What an
 * older version kept at its other blocks is moved into it the first time the piece is seen.</p>
 *
 * <p>Main thread only, apart from the database's own work.</p>
 */
public final class ShownContents {

    private final Plugin plugin;
    private final FurnitureService furniture;
    private final ItemRegistry items;
    private final DatabaseManager database;
    /** Whether a storage at this block is open in memory now, its contents newer than the database's. */
    private Predicate<Block> openNow = block -> false;
    private final NamespacedKey shownKey;
    /** What is drawn for each piece, by its root's id. */
    private final Map<UUID, List<Entity>> shown = new HashMap<>();

    public ShownContents(Plugin plugin, FurnitureService furniture, ItemRegistry items, DatabaseManager database) {
        this.plugin = plugin;
        this.furniture = furniture;
        this.items = items;
        this.database = database;
        this.shownKey = new NamespacedKey(plugin, "shown_for");
        furniture.onRootAppeared(this::appeared);
        furniture.onRootGoing(root -> remove(root.getUniqueId()));
    }

    /**
     * Every piece already standing in a loaded chunk: those whose chunk loaded before this was told
     * of roots appearing. Main thread.
     */
    public void scanLoaded() {
        for (World world : plugin.getServer().getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (furniture.isPieceRoot(display) && !shown.containsKey(display.getUniqueId())) {
                    appeared(display);
                }
            }
        }
    }

    /** How to tell an inventory is open now - its contents are then the storage's own to show. */
    public void openNow(Predicate<Block> openNow) {
        this.openNow = openNow;
    }

    /** The show spec of the piece on this root, or null for a piece that shows nothing. */
    private @Nullable Placement.Show show(Entity root) {
        String id = furniture.furnitureId(root);
        Optional<CustomItem> item = id == null ? Optional.empty() : items.get(id);
        if (item.isEmpty() || !(item.get().placement() instanceof Placement.Furniture piece) || piece.storage() == null) {
            return null;
        }
        return piece.storage().show();
    }

    /** Whether the piece on this block shows what it holds - and so keeps it at its anchor. */
    public boolean shows(Block support) {
        ItemDisplay root = furniture.pieceRoot(support);
        return root != null && show(root) != null;
    }

    /** The block a showing piece keeps its inventory at: its anchor. */
    public Block storageBlock(Block support) {
        ItemDisplay root = furniture.pieceRoot(support);
        return root == null ? support : furniture.anchorOf(root);
    }

    // ---------------------------------------------------------------- the piece comes and goes

    private void appeared(ItemDisplay root) {
        Placement.Show spec = show(root);
        if (spec == null || !root.isValid()) {
            return;
        }
        Block anchor = furniture.anchorOf(root);
        UUID world = anchor.getWorld().getUID();
        List<Block> others = new ArrayList<>(furniture.pieceParts(root));
        others.removeIf(part -> part.equals(anchor));
        // What an older version kept at the piece's other blocks is taken out, to go into the anchor's.
        List<java.util.concurrent.CompletableFuture<Optional<StoredInventory>>> taken = new ArrayList<>();
        for (Block part : others) {
            taken.add(database.takeInventory(world, part.getX(), part.getY(), part.getZ()));
        }
        java.util.concurrent.CompletableFuture.allOf(taken.toArray(java.util.concurrent.CompletableFuture[]::new))
                .thenCompose(ignored -> database.loadInventory(world, anchor.getX(), anchor.getY(), anchor.getZ()))
                .whenCompleteAsync((stored, error) -> {
                    if (error != null) {
                        plugin.getLogger().log(Level.WARNING, "Could not read what the piece at " + anchor.getX() + " "
                                + anchor.getY() + " " + anchor.getZ() + " holds, to show it", error);
                        return;
                    }
                    ItemStack[] contents = stored.map(inventory -> ItemStack.deserializeItemsFromBytes(inventory.contents()))
                            .orElse(new ItemStack[0]);
                    List<ItemStack> moved = new ArrayList<>();
                    for (var future : taken) {
                        future.join().ifPresent(old -> {
                            for (ItemStack stack : ItemStack.deserializeItemsFromBytes(old.contents())) {
                                if (stack != null && !stack.isEmpty()) {
                                    moved.add(stack);
                                }
                            }
                        });
                    }
                    if (!moved.isEmpty() && !openNow.test(anchor)) {
                        contents = merged(root, anchor, contents, moved);
                    }
                    if (root.isValid()) {
                        draw(root, spec, contents);
                    }
                }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin));
    }

    /** Older contents put into the anchor's inventory, in its empty slots; what does not fit is dropped there. */
    private ItemStack[] merged(ItemDisplay root, Block anchor, ItemStack[] contents, List<ItemStack> moved) {
        String id = furniture.furnitureId(root);
        Placement.Storage storage = items.get(id).map(CustomItem::placement)
                .filter(Placement.Furniture.class::isInstance).map(p -> ((Placement.Furniture) p).storage()).orElse(null);
        int size = storage == null ? Math.max(9, contents.length) : storage.slots();
        ItemStack[] all = new ItemStack[Math.max(size, contents.length)];
        for (int i = 0; i < all.length; i++) {
            all[i] = i < contents.length && contents[i] != null ? contents[i] : ItemStack.empty();
        }
        for (ItemStack stack : moved) {
            int free = -1;
            for (int i = 0; i < all.length && free < 0; i++) {
                if (all[i].isEmpty()) {
                    free = i;
                }
            }
            if (free >= 0) {
                all[free] = stack;
            } else {
                anchor.getWorld().dropItemNaturally(anchor.getLocation().add(0.5, 0.5, 0.5), stack);
            }
        }
        database.saveInventory(new StoredInventory(anchor.getWorld().getUID(), anchor.getX(), anchor.getY(),
                        anchor.getZ(), id, all.length, ItemStack.serializeItemsAsBytes(all)))
                .exceptionally(error -> {
                    plugin.getLogger().log(Level.WARNING, "Could not save the merged contents of " + id, error);
                    return null;
                });
        return all;
    }

    /** The storage at {@code support} holds {@code contents} now: what is shown follows. */
    public void changed(Block support, ItemStack[] contents) {
        ItemDisplay root = furniture.pieceRoot(support);
        Placement.Show spec = root == null ? null : show(root);
        if (spec != null) {
            draw(root, spec, contents);
        }
    }

    // ---------------------------------------------------------------- drawing

    private void draw(ItemDisplay root, Placement.Show spec, ItemStack[] contents) {
        remove(root.getUniqueId());
        List<ItemStack> held = new ArrayList<>();
        for (ItemStack stack : contents) {
            if (stack != null && !stack.isEmpty()) {
                held.add(stack);
            }
        }
        if (held.isEmpty()) {
            return;
        }
        World world = root.getWorld();
        Location base = root.getLocation();
        float yaw = base.getYaw();
        double y = furniture.anchorOf(root).getY();
        List<Entity> made = new ArrayList<>();
        String tag = root.getUniqueId().toString();
        if (spec.armor()) {
            double[] at = PieceAxes.offset(yaw, spec.offset().x(), spec.offset().y(), spec.offset().z());
            Location where = new Location(world, base.getX() + at[0], y + at[1], base.getZ() + at[2], yaw + spec.yaw(), 0);
            made.add(world.spawn(where, ArmorStand.class, stand -> {
                stand.setPersistent(false);
                stand.setInvisible(true);
                stand.setMarker(true);
                stand.setGravity(false);
                stand.setInvulnerable(true);
                stand.setSilent(true);
                stand.setBasePlate(false);
                stand.setArms(true);
                stand.setCanTick(false);
                stand.addDisabledSlots(EquipmentSlot.values());
                stand.getPersistentDataContainer().set(shownKey, PersistentDataType.STRING, tag);
                wear(stand.getEquipment(), held);
            }));
        }
        for (int i = 0; i < spec.items().size() && i < held.size(); i++) {
            Placement.ShownItem place = spec.items().get(i);
            ItemStack stack = held.get(i).asOne();
            double[] at = PieceAxes.offset(yaw, place.offset().x(), place.offset().y(), place.offset().z());
            Location where = new Location(world, base.getX() + at[0], y + at[1], base.getZ() + at[2], yaw, 0);
            made.add(world.spawn(where, ItemDisplay.class, display -> {
                display.setPersistent(false);
                display.setItemStack(stack);
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                Quaternionf turn = new Quaternionf().rotateXYZ((float) Math.toRadians(place.rotation().x()),
                        (float) Math.toRadians(place.rotation().y()), (float) Math.toRadians(place.rotation().z()));
                display.setTransformation(new Transformation(new Vector3f(), turn,
                        new Vector3f(place.scale(), place.scale(), place.scale()), new Quaternionf()));
                display.getPersistentDataContainer().set(shownKey, PersistentDataType.STRING, tag);
            }));
        }
        shown.put(root.getUniqueId(), made);
    }

    /**
     * Armour where it is worn - by its own {@code equippable} slot - and the first two other things in
     * the hands.
     */
    static Map<EquipmentSlot, ItemStack> wearing(List<ItemStack> held) {
        Map<EquipmentSlot, ItemStack> worn = new EnumMap<>(EquipmentSlot.class);
        List<ItemStack> rest = new ArrayList<>();
        for (ItemStack stack : held) {
            Equippable equippable = stack.getData(DataComponentTypes.EQUIPPABLE);
            EquipmentSlot slot = equippable == null ? null : equippable.slot();
            if (slot != null && slot.isArmor() && slot != EquipmentSlot.BODY && !worn.containsKey(slot)) {
                worn.put(slot, stack.asOne());
            } else {
                rest.add(stack);
            }
        }
        if (!rest.isEmpty()) {
            worn.put(EquipmentSlot.HAND, rest.get(0).asOne());
        }
        if (rest.size() > 1) {
            worn.put(EquipmentSlot.OFF_HAND, rest.get(1).asOne());
        }
        return worn;
    }

    private static void wear(EntityEquipment equipment, List<ItemStack> held) {
        wearing(held).forEach(equipment::setItem);
    }

    private void remove(UUID root) {
        List<Entity> old = shown.remove(root);
        if (old != null) {
            old.forEach(Entity::remove);
        }
    }

    /** As the plugin stops: what is drawn is taken away - it is made again as the pieces appear. */
    public void shutdown() {
        shown.values().forEach(list -> list.forEach(Entity::remove));
        shown.clear();
    }
}
