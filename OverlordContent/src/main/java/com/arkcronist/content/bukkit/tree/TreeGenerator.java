package com.arkcronist.content.bukkit.tree;

import com.arkcronist.content.bukkit.block.BlockRegistry;
import com.arkcronist.content.bukkit.block.LeavesRegistry;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.tree.TreeSpec;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * 5.4: grows the trees of custom saplings into chunks as they are generated, where their
 * {@code tree: generation:} says - as {@code OreGenerator} lays ores: a {@link BlockPopulator}, run
 * by Paper's generation threads before a chunk is ever loaded, so the main thread never pays for it
 * and a chunk is never seen without its trees.
 *
 * <p>A tree stands on the surface of a soil it may grow on, its trunk in free blocks; one that would
 * reach past the blocks a populator may change, or into anything solid, is not grown at all rather
 * than grown in half. The custom blocks it is made of are known by their state, as ores are - not
 * recorded one by one. Chunks generated before are left as they are.</p>
 */
public final class TreeGenerator extends BlockPopulator implements Listener {

    /** One sapling's tree and one of its generation entries, its blocks resolved. */
    private record Grove(String id, TreeSpec tree, TreeSpec.Generation rule, TreeBlocks blocks, Set<Material> soils) {
    }

    private final Logger logger;
    private final boolean enabled;
    private volatile List<Grove> groves = List.of();
    private final AtomicLong trees = new AtomicLong();
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    public TreeGenerator(Logger logger, boolean enabled) {
        this.logger = logger;
        this.enabled = enabled;
    }

    public void attach(Collection<World> worlds) {
        if (enabled) {
            worlds.forEach(this::attach);
        }
    }

    private void attach(World world) {
        if (!world.getPopulators().contains(this)) {
            world.getPopulators().add(this);
        }
    }

    public void detach(Collection<World> worlds) {
        worlds.forEach(world -> world.getPopulators().remove(this));
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        if (enabled) {
            attach(event.getWorld());
        }
    }

    /** The trees of this build: every sapling with a generation entry whose blocks exist. Main thread. */
    public void publish(ItemRegistry items, BlockRegistry cubes, LeavesRegistry leaves) {
        List<Grove> next = new ArrayList<>();
        for (CustomItem item : items.all()) {
            if (!(item.placement() instanceof Placement.Crop crop) || !crop.sapling()
                    || crop.tree().generation().isEmpty()) {
                continue;
            }
            List<String> problems = new ArrayList<>();
            Optional<TreeBlocks> blocks = TreeBlocks.resolve(crop.tree(), items, cubes, leaves, problems);
            if (blocks.isEmpty()) {
                if (warned.add(item.id() + problems)) {
                    logger.warning(item.id() + ": its trees are not generated - " + String.join("; ", problems));
                }
                continue;
            }
            for (TreeSpec.Generation rule : crop.tree().generation()) {
                Set<Material> soils = EnumSet.noneOf(Material.class);
                for (String soil : rule.soils()) {
                    Material material = Material.matchMaterial(soil);
                    if (material != null && material.isBlock()) {
                        soils.add(material);
                    }
                }
                if (!soils.isEmpty()) {
                    next.add(new Grove(item.id(), crop.tree(), rule, blocks.get(), Set.copyOf(soils)));
                }
            }
        }
        if (enabled && next.size() != groves.size()) {
            logger.info(next.size() + " kind(s) of tree grow into new chunks as they are generated.");
        }
        groves = List.copyOf(next);
    }

    public int groves() {
        return enabled ? groves.size() : 0;
    }

    /** Trees grown into new chunks since the server started. */
    public long trees() {
        return trees.get();
    }

    @Override
    public void populate(@NotNull WorldInfo world, @NotNull Random random, int chunkX, int chunkZ,
                         @NotNull LimitedRegion region) {
        List<Grove> current = groves;
        if (current.isEmpty()) {
            return;
        }
        try {
            String name = world.getName();
            String environment = world.getEnvironment().name();
            for (Grove grove : current) {
                if (!grove.rule().appliesTo(name, environment)) {
                    continue;
                }
                // From the world's seed, the chunk and the tree, so the same world grows the same trees.
                Random own = new Random(world.getSeed() ^ ((long) chunkX * 341873128712L) ^ ((long) chunkZ * 132897987541L)
                        ^ grove.id().hashCode());
                for (int attempt = 0; attempt < grove.rule().perChunk(); attempt++) {
                    if (own.nextDouble() >= grove.rule().chance()) {
                        continue;
                    }
                    int x = (chunkX << 4) + own.nextInt(16);
                    int z = (chunkZ << 4) + own.nextInt(16);
                    if (grow(world, region, grove, x, z, own.nextLong())) {
                        trees.incrementAndGet();
                    }
                }
            }
        } catch (RuntimeException | LinkageError error) {
            String message = error.getClass().getSimpleName() + ": " + error.getMessage();
            if (warned.add(message)) {
                logger.warning("Tree generation skipped a chunk at " + chunkX + ", " + chunkZ + " in "
                        + world.getName() + " (" + message + "); its other chunks still get trees.");
            }
        }
    }

    /** One tree with its trunk's foot over the surface at x, z - or none, if it does not fit there. */
    private boolean grow(WorldInfo world, LimitedRegion region, Grove grove, int x, int z, long seed) {
        if (!region.isInRegion(x, world.getMinHeight(), z)) {
            return false;
        }
        // The height map's top: the highest block, or - as a region in generation gives it - the air
        // over it. The ground is the block under the air.
        int ground = region.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (ground >= world.getMinHeight() && region.isInRegion(x, ground, z) && region.getType(x, ground, z).isAir()) {
            ground--;
        }
        if (ground < world.getMinHeight() || !region.isInRegion(x, ground, z)
                || !grove.soils().contains(region.getType(x, ground, z))) {
            return false;
        }
        if (!grove.rule().biomes().isEmpty()
                && !grove.rule().inBiome(region.getBiome(x, ground, z).getKey().toString())) {
            return false;
        }
        int foot = ground + 1;
        List<TreeSpec.Part> parts = grove.tree().plan(seed);
        // All of it fits, or none of it is grown.
        for (TreeSpec.Part part : parts) {
            int px = x + part.dx();
            int py = foot + part.dy();
            int pz = z + part.dz();
            if (py >= world.getMaxHeight() || !region.isInRegion(px, py, pz)) {
                return false;
            }
            if (part.log() && !free(region.getType(px, py, pz))) {
                return false;
            }
        }
        for (TreeSpec.Part part : parts) {
            int px = x + part.dx();
            int py = foot + part.dy();
            int pz = z + part.dz();
            if (part.log() || free(region.getType(px, py, pz))) {
                region.setBlockData(px, py, pz, grove.blocks().data(part, parts));
            }
        }
        return true;
    }

    /** Air, or what a tree grows through: grass, flowers, snow, vines. */
    private static boolean free(Material material) {
        return material.isAir() || org.bukkit.Tag.REPLACEABLE.isTagged(material)
                || org.bukkit.Tag.REPLACEABLE_BY_TREES.isTagged(material);
    }
}
