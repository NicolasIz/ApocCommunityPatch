package com.arkcronist.content.bukkit.crop;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.core.crop.CropGrowth;
import com.arkcronist.content.core.crop.CropStore;
import com.arkcronist.content.core.crop.PlantedCrop;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.storage.BlockKey;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BlockIterator;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * Custom crops in the world.
 *
 * <p>A crop is built like light-block furniture: an invisible light block (level 0) holds its
 * place - nothing can be put there, water washes it away, a punch finds it - and an item display
 * on that block draws the current stage. Growing swaps the display's item model for the next
 * stage's; nothing else about the crop changes.</p>
 *
 * <p>Main thread only, except {@link #isLoaded}, which the growth scheduler reads. The chunk link
 * and display tags mirror furniture's, and for the same reason: a display that loads before its
 * world's crop rows have arrived can still be told apart from a leftover.</p>
 */
public final class CropService {

    private static final int REACH = 5;

    private final Plugin plugin;
    private final ItemRegistry registry;
    private final ItemFactory items;
    private final CropStore store;
    private final NamespacedKey cropTag;
    private final NamespacedKey anchorTag;
    /** Loaded chunks per world: kept by chunk events on the main thread, read by the growth thread. */
    private final Map<UUID, Set<Long>> loadedChunks = new ConcurrentHashMap<>();
    /** Furniture lives in light blocks too: a ray looking for a crop stops at it, instead of reaching past it. */
    private Predicate<Block> obstacle = block -> false;

    public CropService(Plugin plugin, ItemRegistry registry, ItemFactory items, CropStore store) {
        this.plugin = plugin;
        this.registry = registry;
        this.items = items;
        this.store = store;
        this.cropTag = new NamespacedKey(plugin, "crop");
        this.anchorTag = new NamespacedKey(plugin, "crop_anchor");
    }

    public void stopAt(Predicate<Block> obstacle) {
        this.obstacle = obstacle;
    }

    public CropStore store() {
        return store;
    }

    // ---------------------------------------------------------------- what is where

    /** The crop growing on this block, if it is a crop's light block. */
    public Optional<PlantedCrop> at(Block block) {
        if (block.getType() != Material.LIGHT) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.at(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()));
    }

    /** The crop standing on this soil block, if any. */
    public Optional<PlantedCrop> above(Block soil) {
        return at(soil.getRelative(BlockFace.UP));
    }

    /** Whether a crop claims this position, whatever block is there right now. */
    public boolean claims(Block block) {
        return store.at(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()) != null
                || linkedDisplay(block) != null;
    }

    /** The crop's definition, if it is still defined as a crop. */
    public Optional<Placement.Crop> definition(PlantedCrop crop) {
        return registry.get(crop.cropId()).map(CustomItem::placement)
                .filter(Placement.Crop.class::isInstance).map(Placement.Crop.class::cast);
    }

    /** The crop a punch or a click is aimed at: followed through the air, since a light block cannot be targeted. */
    public Optional<Block> target(Player player) {
        BlockIterator ray = new BlockIterator(player, REACH);
        while (ray.hasNext()) {
            Block block = ray.next();
            if (at(block).isPresent()) {
                return Optional.of(block);
            }
            if (!block.isPassable() || obstacle.test(block)) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public static boolean isSoil(Placement.Crop crop, Material material) {
        return crop.soils().contains(material.name().toUpperCase(Locale.ROOT));
    }

    // ---------------------------------------------------------------- planting

    /**
     * Plants {@code item} on top of {@code soil}. A real {@link BlockPlaceEvent} is fired for the
     * crop's block first, so protection plugins decide as for any block placed there.
     *
     * @return whether it was planted
     */
    public boolean plant(Player player, Block soil, CustomItem item, Placement.Crop crop, EquipmentSlot hand) {
        Block block = soil.getRelative(BlockFace.UP);
        if (!isSoil(crop, soil.getType()) || !block.getType().isAir()) {
            return false;
        }
        // A living crop is always a light block. A record here, over air, is one whose block went
        // without an event - a world edit, a rollback - and would otherwise block this spot forever.
        forgetStale(block);
        BlockState replaced = block.getState();
        ItemStack held = player.getInventory().getItem(hand);
        block.setType(Material.LIGHT, false);
        if (block.getBlockData() instanceof Light light) {
            light.setLevel(0);
            block.setBlockData(light, false);
        }
        BlockPlaceEvent event = new BlockPlaceEvent(block, replaced, soil, held, player, true, hand);
        if (!event.callEvent() || !event.canBuild()) {
            replaced.update(true, false);
            return false;
        }

        PlantedCrop planted = new PlantedCrop(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ(),
                item.id(), 0, 0);
        ItemDisplay display = block.getWorld().spawn(centre(block), ItemDisplay.class,
                entity -> configure(entity, item, 0, block));
        link(block, display.getUniqueId());
        store.plant(planted);

        if (player.getGameMode() != GameMode.CREATIVE) {
            held.setAmount(held.getAmount() - 1);
            player.getInventory().setItem(hand, held.getAmount() > 0 ? held : null);
        }
        block.getWorld().playSound(centre(block), Sound.ITEM_CROP_PLANT, 1f, 1f);
        return true;
    }

    // ---------------------------------------------------------------- growing

    /**
     * The main-thread half of growing: moves each crop the scheduler found due on to its next
     * stage, once the world agrees - the crop is still there, still on its soil, and has the light
     * it needs. One that does not stays due and is tried again next time.
     */
    public void advance(List<PlantedCrop> due) {
        for (PlantedCrop candidate : due) {
            World world = plugin.getServer().getWorld(candidate.world());
            if (world == null || !world.isChunkLoaded(candidate.x() >> 4, candidate.z() >> 4)) {
                continue;
            }
            Block block = world.getBlockAt(candidate.x(), candidate.y(), candidate.z());
            PlantedCrop current = store.at(candidate.world(), candidate.x(), candidate.y(), candidate.z());
            Optional<Placement.Crop> crop = current == null ? Optional.empty() : definition(current);
            if (crop.isEmpty() || block.getType() != Material.LIGHT
                    || !CropGrowth.due(current, crop.get().stageSeconds(), crop.get().lastStage())
                    || !isSoil(crop.get(), block.getRelative(BlockFace.DOWN).getType())
                    || block.getLightLevel() < crop.get().minLight()) {
                continue;
            }
            grow(block, current, crop.get());
        }
    }

    /**
     * Bone meal on a crop: one stage on, as vanilla bone meal does for its own crops.
     *
     * @return whether it grew - and the bone meal should be used up
     */
    public boolean boneMeal(Block block, PlantedCrop crop, Placement.Crop definition) {
        if (!definition.boneMeal() || crop.stage() >= definition.lastStage()) {
            return false;
        }
        if (!grow(block, crop, definition)) {
            return false;
        }
        block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, centre(block), 10, 0.3, 0.3, 0.3);
        block.getWorld().playSound(centre(block), Sound.ITEM_BONE_MEAL_USE, 1f, 1f);
        return true;
    }

    /** One stage on: record, save, redraw, and celebrate if that was the last. */
    private boolean grow(Block block, PlantedCrop crop, Placement.Crop definition) {
        PlantedCrop grown = CropGrowth.advance(crop, definition.lastStage());
        if (!store.update(crop, grown, true)) {
            return false;
        }
        Optional<CustomItem> item = registry.get(crop.cropId());
        Entity display = display(block);
        if (item.isPresent() && display instanceof ItemDisplay itemDisplay) {
            itemDisplay.setItemStack(stageStack(item.get(), grown.stage()));
        }
        if (grown.stage() == definition.lastStage()) {
            Location at = centre(block);
            block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 14, 0.35, 0.35, 0.35);
            block.getWorld().spawnParticle(Particle.END_ROD, at, 4, 0.25, 0.3, 0.25, 0.01);
            block.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.4f);
        }
        return true;
    }

    // ---------------------------------------------------------------- removing

    /**
     * Takes a crop out of the world: display, link, record, the light block - and, when asked, drops
     * what it gives: its harvest when fully grown, otherwise its own item back.
     */
    /** Takes out whatever is still recorded for a crop at {@code block}: its record, link and display. */
    private void forgetStale(Block block) {
        if (!claims(block)) {
            return;
        }
        Entity display = display(block);
        if (display != null) {
            display.remove();
        }
        unlink(block);
        store.remove(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public void remove(Block block, PlantedCrop crop, boolean drop) {
        Entity display = display(block);
        if (display != null) {
            display.remove();
        }
        unlink(block);
        store.remove(crop.world(), crop.x(), crop.y(), crop.z());
        if (block.getType() == Material.LIGHT) {
            block.setType(Material.AIR, false);
        }
        if (drop) {
            dropHarvest(centre(block), crop);
        }
    }

    private void dropHarvest(Location at, PlantedCrop crop) {
        Optional<CustomItem> seed = registry.get(crop.cropId());
        Optional<Placement.Crop> definition = definition(crop);
        if (seed.isEmpty() || definition.isEmpty()) {
            return;
        }
        if (crop.stage() < definition.get().lastStage()) {
            at.getWorld().dropItemNaturally(at, items.create(seed.get(), 1));
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Placement.Drop drop : definition.get().drops()) {
            if (random.nextDouble() >= drop.chance()) {
                continue;
            }
            int amount = drop.min() == drop.max() ? drop.min() : random.nextInt(drop.min(), drop.max() + 1);
            dropItems(at, drop.item(), amount);
        }
    }

    /** A custom item or a vanilla material, in stacks no larger than the material allows. */
    private void dropItems(Location at, String id, int amount) {
        Optional<CustomItem> custom = registry.get(id);
        Material material = custom.map(CustomItem::material).orElseGet(() -> Material.matchMaterial(id));
        if (material == null || material.isAir() || !material.isItem()) {
            return;
        }
        for (int left = amount; left > 0; left -= material.getMaxStackSize()) {
            int count = Math.min(material.getMaxStackSize(), left);
            ItemStack stack = custom.isPresent() ? items.create(custom.get(), count) : ItemStack.of(material, count);
            at.getWorld().dropItemNaturally(at, stack);
        }
    }

    // ---------------------------------------------------------------- displays

    /**
     * A display that loaded with its chunk: removed if its crop is gone, or shown at the crop's
     * current stage - the record is the truth if the two ever disagree.
     */
    public void entityLoaded(Entity entity) {
        if (!(entity instanceof ItemDisplay display)) {
            return;
        }
        Long anchor = display.getPersistentDataContainer().get(anchorTag, PersistentDataType.LONG);
        if (anchor == null) {
            return;
        }
        Block block = display.getWorld().getBlockAt(BlockKey.x(anchor), BlockKey.y(anchor), BlockKey.z(anchor));
        if (block.getType() != Material.LIGHT || !display.getUniqueId().equals(linkedDisplay(block))) {
            display.remove();
            return;
        }
        PlantedCrop crop = store.at(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        Optional<CustomItem> item = crop == null ? Optional.empty() : registry.get(crop.cropId());
        item.ifPresent(custom -> display.setItemStack(stageStack(custom, crop.stage())));
    }

    private void configure(ItemDisplay display, CustomItem item, int stage, Block anchor) {
        display.setItemStack(stageStack(item, stage));
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        display.setInvulnerable(true);
        display.setGravity(false);
        display.setPersistent(true);
        PersistentDataContainer data = display.getPersistentDataContainer();
        data.set(cropTag, PersistentDataType.STRING, item.id());
        data.set(anchorTag, PersistentDataType.LONG, BlockKey.pack(anchor.getX(), anchor.getY(), anchor.getZ()));
    }

    /** A bare stack whose only job is to point the display at the stage's item definition. */
    private static ItemStack stageStack(CustomItem item, int stage) {
        ItemStack stack = ItemStack.of(Material.PAPER);
        String key = Placement.Crop.stageItemModel(item.definition().itemModel(), stage).toString();
        stack.editMeta(meta -> meta.setItemModel(Objects.requireNonNull(NamespacedKey.fromString(key))));
        return stack;
    }

    private @Nullable Entity display(Block block) {
        UUID linked = linkedDisplay(block);
        return linked == null ? null : plugin.getServer().getEntity(linked);
    }

    private NamespacedKey linkKey(Block block) {
        return new NamespacedKey(plugin, "crop." + (block.getX() & 15) + "." + block.getY() + "." + (block.getZ() & 15));
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

    // ---------------------------------------------------------------- loaded chunks

    public void chunkLoaded(Chunk chunk) {
        loadedChunks.computeIfAbsent(chunk.getWorld().getUID(), ignored -> ConcurrentHashMap.newKeySet())
                .add(PlantedCrop.chunkKey(chunk.getX(), chunk.getZ()));
    }

    public void chunkUnloaded(Chunk chunk) {
        Set<Long> chunks = loadedChunks.get(chunk.getWorld().getUID());
        if (chunks != null) {
            chunks.remove(PlantedCrop.chunkKey(chunk.getX(), chunk.getZ()));
        }
    }

    public void worldLoaded(World world) {
        for (Chunk chunk : world.getLoadedChunks()) {
            chunkLoaded(chunk);
        }
    }

    public void worldUnloaded(World world) {
        loadedChunks.remove(world.getUID());
    }

    /** Whether the crop's chunk is loaded. Safe from the growth thread. */
    public boolean isLoaded(PlantedCrop crop) {
        Set<Long> chunks = loadedChunks.get(crop.world());
        return chunks != null && chunks.contains(crop.chunk());
    }

    private static Location centre(Block block) {
        return block.getLocation().add(0.5, 0.5, 0.5);
    }
}
