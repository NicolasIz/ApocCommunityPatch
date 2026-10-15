package com.arkcronist.content.bukkit.furniture;

import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.core.furniture.LegacyIds;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 3.1: furniture ItemsAdder placed while it was installed. Its entities stay in the world after
 * ItemsAdder is gone - an item display, or an armour stand or item frame holding the item, drawn by
 * the imported pack, often over barriers and with an interaction box - and nothing manages them:
 * breaking the barrier left the model standing. They are told apart by what their item still
 * carries, ItemsAdder's {@code custom_data: {itemsadder: {namespace, id}}}, or by an ItemsAdder key
 * on the entity, and taken away with the barriers they stand on. Main thread.
 */
public final class LegacyFurniture {

    /** How many barriers a piece of ItemsAdder furniture is taken to stand on at most: more is a wall. */
    private static final int MAX_BARRIERS = 12;
    /** How far a piece's barriers reach from the block its model stands in. */
    private static final int MAX_REACH = 3;

    private final ItemRegistry registry;
    private final ItemFactory items;
    private final NamespacedKey ours;

    /** A piece left by ItemsAdder: what draws it, its other entities, the barriers it stands on, its id. */
    public record Piece(List<Entity> entities, List<Block> barriers, @Nullable String id) {
    }

    public LegacyFurniture(Plugin plugin, ItemRegistry registry, ItemFactory items) {
        this.registry = registry;
        this.items = items;
        this.ours = new NamespacedKey(plugin, "furniture");
    }

    /**
     * The ItemsAdder id this entity draws ({@code namespace:id}), "" when it is ItemsAdder's but its
     * item says nothing, or null when it is not ItemsAdder's at all - this plugin's own included.
     */
    public @Nullable String id(Entity entity) {
        if (entity.getPersistentDataContainer().has(ours)) {
            return null;
        }
        ItemStack shown = shown(entity);
        if (shown != null && shown.hasItemMeta()) {
            ItemMeta meta = shown.getItemMeta();
            Optional<String> id = LegacyIds.itemsAdderId(meta.getAsComponentString());
            if (id.isPresent()) {
                return id.get();
            }
        }
        boolean marked = entity.getPersistentDataContainer().getKeys().stream()
                .anyMatch(key -> key.getNamespace().equals("itemsadder"));
        return marked ? "" : null;
    }

    /** Whether this is a model ItemsAdder left: a display, stand or frame with ItemsAdder's item. */
    public boolean isModel(Entity entity) {
        return (entity instanceof ItemDisplay || entity instanceof ArmorStand || entity instanceof ItemFrame)
                && id(entity) != null;
    }

    /**
     * The piece a block belongs to, if ItemsAdder left one there: the models standing in the block,
     * or in the barriers it is joined to (a piece of several blocks), with the interaction boxes
     * around them.
     */
    public Optional<Piece> pieceAt(Block block) {
        List<Block> barriers = block.getType() == Material.BARRIER ? cluster(block) : List.of();
        List<Block> cells = barriers.isEmpty() ? List.of(block) : barriers;
        Set<Entity> models = new LinkedHashSet<>();
        for (Block cell : cells) {
            models.addAll(block.getWorld().getNearbyEntities(BoundingBox.of(cell).expand(0.01), this::isModel));
        }
        if (models.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(piece(models, barriers));
    }

    /** The piece an interaction box or model of ItemsAdder's belongs to, if it is one. */
    public Optional<Piece> pieceOf(Entity entity) {
        Set<Entity> models = new LinkedHashSet<>();
        if (isModel(entity)) {
            models.add(entity);
        } else if (entity instanceof Interaction box) {
            BoundingBox reach = box.getBoundingBox().expand(0.6);
            models.addAll(box.getWorld().getNearbyEntities(reach, this::isModel));
            if (models.isEmpty() && id(box) == null) {
                return Optional.empty();
            }
            if (models.isEmpty()) {
                return Optional.of(new Piece(List.of(box), List.of(), null));
            }
        } else {
            return Optional.empty();
        }
        Block at = models.iterator().next().getLocation().getBlock();
        List<Block> barriers = new ArrayList<>();
        for (Block near : List.of(at, at.getRelative(BlockFace.DOWN))) {
            if (near.getType() == Material.BARRIER) {
                barriers.addAll(cluster(near));
                break;
            }
        }
        return Optional.of(piece(models, barriers));
    }

    /** Every piece ItemsAdder left with a model in a loaded chunk within {@code radius} blocks. */
    public List<Piece> around(Location centre, int radius) {
        World world = centre.getWorld();
        List<Piece> pieces = new ArrayList<>();
        Set<Entity> seen = new LinkedHashSet<>();
        for (Entity entity : world.getNearbyEntities(centre, radius, radius, radius, this::isModel)) {
            if (seen.contains(entity)) {
                continue;
            }
            Optional<Piece> piece = pieceOf(entity);
            piece.ifPresent(found -> {
                seen.addAll(found.entities());
                pieces.add(found);
            });
        }
        return pieces;
    }

    /**
     * Takes a piece away: its entities, and the barriers it stood on except {@code keep} (a block
     * being broken, which the break itself takes). Drops its item at its model when asked and this
     * plugin has an item by its id.
     *
     * @return whether an item was dropped
     */
    public boolean remove(Piece piece, @Nullable Block keep, boolean dropItem) {
        Location at = piece.entities().isEmpty() ? null : piece.entities().get(0).getLocation();
        for (Entity entity : piece.entities()) {
            entity.remove();
        }
        for (Block barrier : piece.barriers()) {
            if (!barrier.equals(keep) && barrier.getType() == Material.BARRIER) {
                barrier.setType(Material.AIR, false);
            }
        }
        if (dropItem && at != null && piece.id() != null && !piece.id().isEmpty()) {
            var item = registry.get(piece.id());
            if (item.isPresent()) {
                at.getWorld().dropItemNaturally(at, items.create(item.get(), 1));
                return true;
            }
        }
        return false;
    }

    private Piece piece(Set<Entity> models, List<Block> barriers) {
        Set<Entity> entities = new LinkedHashSet<>(models);
        String id = null;
        for (Entity model : models) {
            String found = id(model);
            if (id == null || id.isEmpty()) {
                id = found;
            }
            BoundingBox reach = BoundingBox.of(model.getLocation(), 1.1, 1.6, 1.1);
            // ItemsAdder's own boxes: marked as its, or carrying nothing at all - not another plugin's.
            entities.addAll(model.getWorld().getNearbyEntities(reach, near -> near instanceof Interaction
                    && (id(near) != null || near.getPersistentDataContainer().isEmpty())));
        }
        return new Piece(List.copyOf(entities), List.copyOf(barriers), id);
    }

    /**
     * The barriers joined to {@code start}, face to face, within a few blocks of it - a piece of
     * furniture several blocks big. A run longer than a piece of furniture has (a wall of barriers
     * around a map) gives just {@code start}.
     */
    private static List<Block> cluster(Block start) {
        List<Block> found = new ArrayList<>();
        Set<Block> visited = new LinkedHashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            Block block = queue.poll();
            found.add(block);
            if (found.size() > MAX_BARRIERS) {
                return List.of(start);
            }
            for (BlockFace face : new BlockFace[] {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
                    BlockFace.UP, BlockFace.DOWN}) {
                Block next = block.getRelative(face);
                if (next.getType() == Material.BARRIER && visited.add(next)
                        && Math.abs(next.getX() - start.getX()) <= MAX_REACH
                        && Math.abs(next.getY() - start.getY()) <= MAX_REACH
                        && Math.abs(next.getZ() - start.getZ()) <= MAX_REACH) {
                    queue.add(next);
                }
            }
        }
        return found;
    }

    private static @Nullable ItemStack shown(Entity entity) {
        if (entity instanceof ItemDisplay display) {
            return display.getItemStack();
        }
        if (entity instanceof ItemFrame frame) {
            return frame.getItem();
        }
        if (entity instanceof ArmorStand stand) {
            EntityEquipment equipment = stand.getEquipment();
            ItemStack head = equipment.getHelmet();
            return head != null && !head.getType().isAir() ? head : equipment.getItemInMainHand();
        }
        return null;
    }
}
