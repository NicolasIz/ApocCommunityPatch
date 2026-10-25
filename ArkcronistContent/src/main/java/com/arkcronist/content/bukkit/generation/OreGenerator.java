package com.arkcronist.content.bukkit.generation;

import com.arkcronist.content.bukkit.block.CustomBlock;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.generation.OreGeneration;
import com.arkcronist.content.core.generation.OreVeins;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * 5.0: lays custom blocks into chunks as they are generated, as their {@code block: generation:}
 * says - ores with no world generator plugin.
 *
 * <p>A {@link BlockPopulator}: Paper calls it while it decorates each new chunk, after its own ores
 * and before the chunk is ever loaded, on its generation threads - so it costs the main thread
 * nothing, works the same under a pre-generator, and a chunk is never seen without its ores. Where
 * the veins go comes from the world's seed ({@link OreVeins}); at each of those blocks, the block
 * there and its biome decide.</p>
 *
 * <p>The blocks laid are custom blocks like any other - a note block in the block's own state - but
 * are not recorded one by one: a generated world holds millions of them, and like a schematic's,
 * they are known by their state. The integrity check therefore never puts a mined ore back.</p>
 *
 * <p>Only what is generated after the content is built gets ores: chunks generated before, by this
 * server or another, are left as they are.</p>
 */
public final class OreGenerator extends BlockPopulator implements Listener {

    /** One ore: a custom block's state and one of its generation entries, its blocks resolved. */
    private record Ore(String id, OreGeneration rule, BlockData data, Set<Material> replace) {
    }

    private final Logger logger;
    private final boolean enabled;
    private volatile List<Ore> ores = List.of();
    private final AtomicLong chunks = new AtomicLong();
    private final AtomicLong blocks = new AtomicLong();
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    public OreGenerator(Logger logger, boolean enabled) {
        this.logger = logger;
        this.enabled = enabled;
    }

    /** Added to every world there is, and to each one created later; main thread, at enable. */
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

    /** Taken out of every world again: the plugin is disabled. */
    public void detach(Collection<World> worlds) {
        worlds.forEach(world -> world.getPopulators().remove(this));
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        // Before the world's first chunk is generated.
        if (enabled) {
            attach(event.getWorld());
        }
    }

    /**
     * The ores of this build: every custom block with a generation entry whose blocks to replace
     * exist. Main thread, after each build; generation threads read it as it is when they start a
     * chunk.
     */
    public void publish(Collection<CustomBlock> custom) {
        List<Ore> next = new ArrayList<>();
        for (CustomBlock block : custom) {
            if (!(block.item().placement() instanceof Placement.Block placement)) {
                continue;
            }
            for (OreGeneration rule : placement.generation()) {
                Set<Material> replace = EnumSet.noneOf(Material.class);
                Set<String> unknown = new LinkedHashSet<>();
                for (String key : rule.replace()) {
                    Material material = Material.matchMaterial(key);
                    if (material != null && material.isBlock()) {
                        replace.add(material);
                    } else {
                        unknown.add(key);
                    }
                }
                if (!unknown.isEmpty() && warned.add(block.id() + unknown)) {
                    logger.warning(block.id() + ": generation: 'replace' names no block called "
                            + String.join(", ", unknown) + " - left out");
                }
                if (!replace.isEmpty()) {
                    next.add(new Ore(block.id(), rule, block.data(), Set.copyOf(replace)));
                }
            }
        }
        ores = List.copyOf(next);
    }

    /** How many generation entries are laid; how many chunks and blocks got ores since start. */
    public int ores() {
        return enabled ? ores.size() : 0;
    }

    public long chunks() {
        return chunks.get();
    }

    public long blocks() {
        return blocks.get();
    }

    public boolean enabled() {
        return enabled;
    }

    @Override
    public void populate(@NotNull WorldInfo world, @NotNull Random random, int chunkX, int chunkZ,
                         @NotNull LimitedRegion region) {
        List<Ore> current = ores;
        if (current.isEmpty()) {
            return;
        }
        try {
            String name = world.getName();
            String environment = world.getEnvironment().name();
            int laid = 0;
            for (Ore ore : current) {
                if (!ore.rule().appliesTo(name, environment)) {
                    continue;
                }
                for (int[] at : OreVeins.plan(ore.rule(), ore.id(), world.getSeed(), chunkX, chunkZ,
                        world.getMinHeight(), world.getMaxHeight())) {
                    int x = at[0];
                    int y = at[1];
                    int z = at[2];
                    if (!region.isInRegion(x, y, z) || !ore.replace().contains(region.getType(x, y, z))) {
                        continue;
                    }
                    if (!ore.rule().biomes().isEmpty()
                            && !ore.rule().inBiome(region.getBiome(x, y, z).getKey().toString())) {
                        continue;
                    }
                    region.setBlockData(x, y, z, ore.data());
                    laid++;
                }
            }
            if (laid > 0) {
                chunks.incrementAndGet();
                blocks.addAndGet(laid);
            }
        } catch (RuntimeException | LinkageError error) {
            // A chunk without its ores is better than a chunk that fails to generate; told once.
            String message = error.getClass().getSimpleName() + ": " + error.getMessage();
            if (warned.add(message)) {
                logger.warning("Ore generation skipped a chunk at " + chunkX + ", " + chunkZ + " in "
                        + world.getName() + " (" + message + "); its other chunks still get ores.");
            }
        }
    }
}
