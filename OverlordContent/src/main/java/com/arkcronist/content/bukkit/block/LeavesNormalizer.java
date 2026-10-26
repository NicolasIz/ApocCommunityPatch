package com.arkcronist.content.bukkit.block;

import com.arkcronist.content.bukkit.listener.CustomLeavesListener;
import com.arkcronist.content.core.block.LeavesState;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * 5.5: vanilla's own placed leaves, in chunks from before a leaves block drew custom ones, held at
 * distance 7 - where the plugin holds every placed leaf of that block from then on.
 *
 * <p>A player's leaves next to a log are persistent at distance 1 to 6: exactly the states custom
 * leaves are drawn through. Once their block draws custom ones, a hedge built before would show as
 * them. So each chunk, the first time it loads after that, is looked through once - its blocks
 * copied on the main thread, read on another - and every such leaf of vanilla's set to distance 7,
 * which draws as it always did. The chunk keeps which leaves blocks it was looked through for, so
 * it is never looked through again for those; a chunk generated after that has none of vanilla's to
 * hold, and only records it. Custom leaves cannot stand in a chunk not yet looked through: they are
 * placed only in loaded chunks, all looked through when they load or when the leaves block is first
 * used.</p>
 */
public final class LeavesNormalizer implements Listener {

    /** Chunks looked through per tick at most: copying one costs the main thread a little. */
    private static final int PER_TICK = 2;

    private record Due(UUID world, int x, int z) {
    }

    private record Found(int x, int y, int z, BlockData data) {
    }

    private final Plugin plugin;
    private final LeavesRegistry leaves;
    private final NamespacedKey key;
    private final Deque<Due> queue = new ArrayDeque<>();
    private final Set<Due> queued = new HashSet<>();
    private @Nullable BukkitTask task;
    private long held;

    public LeavesNormalizer(Plugin plugin, LeavesRegistry leaves) {
        this.plugin = plugin;
        this.leaves = leaves;
        this.key = new NamespacedKey(plugin, "vanilla_leaves_held");
    }

    /** Vanilla leaves held at distance 7 since the server started. */
    public long held() {
        return held;
    }

    /** A rebuild may have put another leaves block to use: every loaded chunk not yet looked through for it is. */
    public void contentReloaded() {
        if (leaves.held().isEmpty()) {
            return;
        }
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                consider(chunk);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (leaves.held().isEmpty()) {
            return;
        }
        if (event.isNewChunk()) {
            // Generated now: whatever leaves it has were set by this plugin or by vanilla's trees, which grow none persistent.
            mark(event.getChunk(), names(leaves.held()));
            return;
        }
        consider(event.getChunk());
    }

    private void consider(Chunk chunk) {
        if (missing(chunk).isEmpty()) {
            return;
        }
        Due due = new Due(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        if (queued.add(due)) {
            queue.add(due);
        }
        if (task == null) {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::work, 1L, 1L);
        }
    }

    private Set<String> missing(Chunk chunk) {
        Set<String> wanted = names(leaves.held());
        wanted.removeAll(marked(chunk));
        return wanted;
    }

    private void work() {
        for (int i = 0; i < PER_TICK && !queue.isEmpty(); i++) {
            Due due = queue.poll();
            queued.remove(due);
            World world = plugin.getServer().getWorld(due.world());
            if (world == null || !world.isChunkLoaded(due.x(), due.z())) {
                // Looked through when it loads again.
                continue;
            }
            Chunk chunk = world.getChunkAt(due.x(), due.z());
            Set<String> missing = missing(chunk);
            if (missing.isEmpty()) {
                continue;
            }
            Set<Material> materials = EnumSet.noneOf(Material.class);
            for (String name : missing) {
                Material material = Material.matchMaterial(name);
                if (material != null) {
                    materials.add(material);
                }
            }
            ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
            if (!holdsAny(snapshot, materials)) {
                mark(chunk, missing);
                continue;
            }
            int minY = world.getMinHeight();
            int maxY = world.getMaxHeight();
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                List<Found> found = scan(snapshot, materials, minY, maxY);
                plugin.getServer().getScheduler().runTask(plugin, () -> apply(world, due, found, missing));
            });
        }
        if (queue.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Whether the chunk holds any persistent leaf of these blocks below distance 7 - read from its palettes, quickly. */
    private boolean holdsAny(ChunkSnapshot snapshot, Set<Material> materials) {
        for (Material material : materials) {
            for (int distance = 1; distance < LeavesState.VANILLA_DISTANCE; distance++) {
                for (boolean wet : new boolean[]{false, true}) {
                    BlockData data = plugin.getServer().createBlockData(material);
                    if (data instanceof Leaves leaf) {
                        leaf.setPersistent(true);
                        leaf.setDistance(distance);
                        leaf.setWaterlogged(wet);
                        if (snapshot.contains(leaf)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static List<Found> scan(ChunkSnapshot snapshot, Set<Material> materials, int minY, int maxY) {
        List<Found> found = new ArrayList<>();
        for (int y = minY; y < maxY; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (!materials.contains(snapshot.getBlockType(x, y, z))) {
                        continue;
                    }
                    BlockData data = snapshot.getBlockData(x, y, z);
                    if (data instanceof Leaves leaf && leaf.isPersistent()
                            && leaf.getDistance() < LeavesState.VANILLA_DISTANCE) {
                        found.add(new Found(x, y, z, data));
                    }
                }
            }
        }
        return found;
    }

    private void apply(World world, Due due, List<Found> found, Set<String> missing) {
        if (!world.isChunkLoaded(due.x(), due.z())) {
            return;
        }
        Chunk chunk = world.getChunkAt(due.x(), due.z());
        for (Found leaf : found) {
            Block block = chunk.getBlock(leaf.x(), leaf.y(), leaf.z());
            // Only if it is still the leaf read: one placed since may be custom.
            if (block.getBlockData().equals(leaf.data()) && leaf.data() instanceof Leaves vanilla) {
                CustomLeavesListener.pin(block, vanilla);
                held++;
            }
        }
        mark(chunk, missing);
    }

    private Set<String> marked(Chunk chunk) {
        String raw = chunk.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return raw == null || raw.isBlank() ? new HashSet<>() : new HashSet<>(Arrays.asList(raw.split(",")));
    }

    private void mark(Chunk chunk, Set<String> materials) {
        Set<String> all = new TreeSet<>(marked(chunk));
        all.addAll(materials);
        chunk.getPersistentDataContainer().set(key, PersistentDataType.STRING, String.join(",", all));
    }

    private static Set<String> names(Set<Material> materials) {
        Set<String> names = new TreeSet<>();
        materials.forEach(material -> names.add(material.getKey().getKey().toLowerCase(Locale.ROOT)));
        return names;
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        queue.clear();
        queued.clear();
    }
}
