package com.arkcronist.content.bukkit.tree;

import com.arkcronist.content.bukkit.block.BlockRegistry;
import com.arkcronist.content.bukkit.block.CustomBlock;
import com.arkcronist.content.bukkit.block.LeafDrops;
import com.arkcronist.content.bukkit.block.LeavesRegistry;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.tree.TreeSpec;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 5.5: custom leaves a tree grew fall once no log holds them, as vanilla's do.
 *
 * <p>Their state cannot keep a distance - it says which leaves they are - so it is worked out when
 * it can have changed: a log or a leaf next to them taken away. From the blocks round the one that
 * went, the leaves touching each other are gathered, and their distance from the logs among and
 * next to them counted as vanilla counts it, a step from leaf to leaf; grown custom leaves farther
 * than theirs may be - 6 as vanilla's, or more when a blueprint draws them farther from its wood -
 * fall over the next few seconds, dropping what they drop. A gathering that reaches too far is
 * left alone rather than judged on part of a forest. Leaves a player placed never fall.</p>
 */
public final class LeavesDecay implements Listener {

    /** Leaves gathered at most from one change: a large tree's crown, not a forest. */
    private static final int MOST_LEAVES = 4096;
    /** How far the gathering reaches from the change, across. */
    private static final int REACH = 20;
    /** The distance custom leaves fall beyond when nothing says otherwise: vanilla's. */
    private static final int VANILLA_LIMIT = 6;
    private static final int[][] STEPS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private record Spot(UUID world, int x, int y, int z) {
    }

    private final Plugin plugin;
    private final LeavesRegistry leaves;
    private final BlockRegistry cubes;
    private final LeafDrops drops;
    private final Set<Spot> changed = new LinkedHashSet<>();
    /** Leaves already due to fall: judged again before they do, they are not given a second fall. */
    private final Set<Spot> due = new HashSet<>();
    private volatile Map<String, Integer> limits = Map.of();
    private volatile Set<String> logs = Set.of();
    private @Nullable BukkitTask task;
    private long fallen;

    public LeavesDecay(Plugin plugin, LeavesRegistry leaves, BlockRegistry cubes, LeafDrops drops) {
        this.plugin = plugin;
        this.leaves = leaves;
        this.cubes = cubes;
        this.drops = drops;
    }

    /**
     * What this build's trees say: the custom blocks that are wood, and how far from it each kind of
     * custom leaves may be. Main thread.
     */
    public void publish(ItemRegistry items) {
        Map<String, Integer> farthest = new HashMap<>();
        Set<String> wood = new HashSet<>();
        for (CustomItem item : items.all()) {
            if (item.placement() instanceof Placement.Block block && block.axis()) {
                wood.add(item.id());
            }
            if (!(item.placement() instanceof Placement.Crop crop) || !crop.sapling()) {
                continue;
            }
            TreeSpec tree = crop.tree();
            wood.add(tree.log());
            tree.leafDistances(key -> isLeaves(items, tree, key)).forEach((key, distance) -> {
                String id = key.equals(TreeSpec.LEAVES) ? tree.leaves() : key;
                farthest.merge(id, distance, Math::max);
            });
        }
        this.limits = Map.copyOf(farthest);
        this.logs = Set.copyOf(wood);
    }

    private static boolean isLeaves(ItemRegistry items, TreeSpec tree, String key) {
        String id = key.equals(TreeSpec.LEAVES) ? tree.leaves() : key;
        Optional<CustomItem> item = items.get(id);
        if (item.isPresent()) {
            return item.get().placement() instanceof Placement.Block block && block.leaves() != null;
        }
        Material material = Material.matchMaterial(id.substring(id.indexOf(':') + 1).replaceAll("\\[.*", ""));
        return material != null && Tag.LEAVES.isTagged(material);
    }

    /** Leaves fallen since the server started. */
    public long fallen() {
        return fallen;
    }

    /**
     * A log or leaf at {@code block} is being taken away: the leaves round it are looked at once it
     * has gone, together with every other change of this tick.
     */
    public void changed(Block block) {
        if (leaves.isEmpty()) {
            return;
        }
        changed.add(new Spot(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()));
        if (task == null) {
            task = plugin.getServer().getScheduler().runTaskLater(plugin, this::look, 2L);
        }
    }

    private void look() {
        task = null;
        List<Spot> now = new ArrayList<>(changed);
        changed.clear();
        Set<Long> judged = new HashSet<>();
        for (Spot spot : now) {
            World world = plugin.getServer().getWorld(spot.world());
            if (world == null || !world.isChunkLoaded(spot.x() >> 4, spot.z() >> 4)) {
                continue;
            }
            Block origin = world.getBlockAt(spot.x(), spot.y(), spot.z());
            for (int[] step : STEPS) {
                Block next = origin.getRelative(step[0], step[1], step[2]);
                if (judged.contains(next.getBlockKey())) {
                    continue;
                }
                Optional<LeavesRegistry.Hit> hit = leaves.identify(next);
                if (hit.isPresent() && hit.get().natural() && hit.get().leaves().leaves().decays()) {
                    judge(origin, next, judged);
                }
            }
        }
    }

    /** Gathers the leaves touching {@code start}, counts their distance from wood, and lets fall what is too far. */
    private void judge(Block origin, Block start, Set<Long> judged) {
        Map<Long, Block> gathered = new HashMap<>();
        Deque<Block> queue = new ArrayDeque<>();
        Set<Long> wood = new HashSet<>();
        Map<Long, Block> woodBlocks = new HashMap<>();
        gathered.put(start.getBlockKey(), start);
        queue.add(start);
        while (!queue.isEmpty()) {
            Block at = queue.poll();
            for (int[] step : STEPS) {
                Block next = at.getRelative(step[0], step[1], step[2]);
                long key = next.getBlockKey();
                if (gathered.containsKey(key) || wood.contains(key)) {
                    continue;
                }
                if (Math.abs(next.getX() - origin.getX()) > REACH || Math.abs(next.getY() - origin.getY()) > REACH
                        || Math.abs(next.getZ() - origin.getZ()) > REACH
                        || !next.getWorld().isChunkLoaded(next.getX() >> 4, next.getZ() >> 4)) {
                    // Reaches past what is looked at: held by wood out there, for all that is known.
                    gathered.values().forEach(block -> judged.add(block.getBlockKey()));
                    return;
                }
                Material type = next.getType();
                if (isWood(next, type)) {
                    wood.add(key);
                    woodBlocks.put(key, next);
                } else if (Tag.LEAVES.isTagged(type)) {
                    gathered.put(key, next);
                    queue.add(next);
                    if (gathered.size() > MOST_LEAVES) {
                        gathered.values().forEach(block -> judged.add(block.getBlockKey()));
                        return;
                    }
                }
            }
        }
        // Distance from the wood, a step from leaf to leaf.
        Map<Long, Integer> distance = new HashMap<>();
        Deque<Block> front = new ArrayDeque<>(woodBlocks.values());
        woodBlocks.keySet().forEach(key -> distance.put(key, 0));
        while (!front.isEmpty()) {
            Block at = front.poll();
            int next = distance.get(at.getBlockKey()) + 1;
            for (int[] step : STEPS) {
                Block leaf = at.getRelative(step[0], step[1], step[2]);
                long key = leaf.getBlockKey();
                if (gathered.containsKey(key) && !distance.containsKey(key)) {
                    distance.put(key, next);
                    front.add(leaf);
                }
            }
        }
        List<Block> falling = new ArrayList<>();
        for (Block leaf : gathered.values()) {
            judged.add(leaf.getBlockKey());
            Optional<LeavesRegistry.Hit> hit = leaves.identify(leaf);
            if (hit.isEmpty() || !hit.get().natural() || !hit.get().leaves().leaves().decays()) {
                continue;
            }
            int limit = Math.max(VANILLA_LIMIT, limits.getOrDefault(hit.get().leaves().id(), VANILLA_LIMIT));
            Integer far = distance.get(leaf.getBlockKey());
            if (far == null || far > limit) {
                falling.add(leaf);
            }
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Block leaf : falling) {
            Spot spot = new Spot(leaf.getWorld().getUID(), leaf.getX(), leaf.getY(), leaf.getZ());
            if (!due.add(spot)) {
                continue;
            }
            BlockData was = leaf.getBlockData();
            // Over a few seconds, as random ticks would take them.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                due.remove(spot);
                fall(leaf, was);
            }, 10L + random.nextInt(90));
        }
    }

    private boolean isWood(Block block, Material type) {
        if (Tag.LOGS.isTagged(type)) {
            return true;
        }
        if (type != Material.NOTE_BLOCK || logs.isEmpty()) {
            return false;
        }
        Optional<CustomBlock> custom = cubes.byBlockData(block.getBlockData());
        return custom.isPresent() && logs.contains(custom.get().id());
    }

    /** Takes a leaf away - if it is still the one judged - dropping what it drops. */
    private void fall(Block leaf, BlockData was) {
        if (!leaf.getWorld().isChunkLoaded(leaf.getX() >> 4, leaf.getZ() >> 4) || !leaf.getBlockData().equals(was)) {
            return;
        }
        Optional<LeavesRegistry.Hit> hit = leaves.identify(was);
        if (hit.isEmpty()) {
            return;
        }
        leaf.getWorld().spawnParticle(Particle.BLOCK, leaf.getLocation().add(0.5, 0.5, 0.5), 6, 0.3, 0.3, 0.3, was);
        leaf.setType(was instanceof Leaves wet && wet.isWaterlogged() ? Material.WATER : Material.AIR, true);
        drops.drop(leaf, hit.get().leaves(), null);
        fallen++;
        // What it held may fall too: the leaves past it are looked at in turn.
        changed(leaf);
    }

    // ---------------------------------------------------------------- what can let leaves fall

    /** Wood broken: a vanilla log, or a custom block a tree is made of. Leaves report their own. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (mayHold(event.getBlock().getType())) {
            changed(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (mayHold(event.getBlock().getType()) || Tag.LEAVES.isTagged(event.getBlock().getType())) {
            changed(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(this::changedIfWood);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(this::changedIfWood);
    }

    /** Vanilla leaves falling next to custom ones may have been what held them. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVanillaDecay(LeavesDecayEvent event) {
        changed(event.getBlock());
    }

    private void changedIfWood(Block block) {
        Material type = block.getType();
        if (mayHold(type) || Tag.LEAVES.isTagged(type)) {
            changed(block);
        }
    }

    private boolean mayHold(Material type) {
        return Tag.LOGS.isTagged(type) || (type == Material.NOTE_BLOCK && !logs.isEmpty());
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        changed.clear();
        due.clear();
    }
}
