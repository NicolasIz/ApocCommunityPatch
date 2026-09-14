package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.bukkit.populator.FeaturePopulator;
import com.arkcronist.gen.bukkit.populator.StructurePopulator;
import com.arkcronist.gen.bukkit.writer.ChunkDataWriter;
import com.arkcronist.gen.core.terrain.Preset;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Random;

/**
 * The Paper entry point.
 *
 * <p>Everything vanilla would normally add is switched off: terrain, surface, caves, decoration and
 * structures all come from ArkcronistGenerator. Only natural mob generation is left to the server,
 * because that is genuinely better handled by vanilla's spawner.</p>
 *
 * <p>The generator is parallel capable. All state it touches is either immutable or explicitly
 * concurrent, and every result is derived from world position rather than call order, so Paper is
 * free to generate chunks on as many threads as it likes.</p>
 */
public final class ArkChunkGenerator extends ChunkGenerator {

    private final ArkcronistPlugin plugin;
    private final Preset preset;

    public ArkChunkGenerator(ArkcronistPlugin plugin, Preset preset) {
        this.plugin = plugin;
        this.preset = preset;
    }

    public Preset preset() {
        return preset;
    }

    private ArkWorld world(WorldInfo info) {
        return plugin.worlds().get(info, preset);
    }

    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ,
                              @NotNull ChunkData chunkData) {
        world(worldInfo).engine().generateChunk(chunkX, chunkZ, new ChunkDataWriter(chunkData));
    }

    @Override
    public @NotNull BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
        return new ArkBiomeProvider(world(worldInfo).engine());
    }

    @Override
    public @NotNull List<BlockPopulator> getDefaultPopulators(@NotNull World world) {
        return List.of(new FeaturePopulator(plugin, preset), new StructurePopulator(plugin, preset));
    }

    @Override
    public int getBaseHeight(@NotNull WorldInfo worldInfo, @NotNull Random random, int x, int z,
                             @NotNull HeightMap heightMap) {
        ArkWorld world = world(worldInfo);
        int surface = world.engine().surfaceHeight(x, z);
        int water = world.engine().waterLevel(x, z);
        return switch (heightMap) {
            case OCEAN_FLOOR, OCEAN_FLOOR_WG -> surface;
            default -> Math.max(surface, surface < water ? water : surface);
        };
    }

    @Override
    public @NotNull Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        ArkWorld ark = plugin.worlds().get(world, preset);
        // Walk outwards from the origin until dry, reasonably flat land shows up.
        //
        // Both halves of that sentence used to be untrue. The test was
        // `biomeAt(x, z).trees.length >= 0`, which reads like "a biome that can hold trees" and is
        // in fact always true - an array length is never negative - so it filtered nothing while
        // costing a biome lookup to work that out. And nothing checked flatness at all, which is how
        // a world came out with its spawn on a tundra peak at y=147.
        for (int radius = 0; radius < 4096; radius += 32) {
            for (int angle = 0; angle < 8; angle++) {
                int x = (int) Math.round(Math.cos(angle * Math.PI / 4.0) * radius);
                int z = (int) Math.round(Math.sin(angle * Math.PI / 4.0) * radius);
                int height = ark.engine().surfaceHeight(x, z);
                if (height > ark.engine().waterLevel(x, z) + 1 && flatEnough(ark, x, z, height)) {
                    return new Location(world, x + 0.5, height + 1.0, z + 0.5);
                }
            }
        }
        return new Location(world, 0.5, ark.engine().settings().seaLevel + 2.0, 0.5);
    }

    /**
     * Whether somebody would want to stand here.
     *
     * <p>Four neighbours at eight blocks, and the spread between them under seven. Only ever asked
     * of a candidate that already passed the water check, and candidates are rare - measured across
     * six seeds and three presets, the search finds land within ten samples - so this costs four
     * column lookups in the chunks it has already built, not a search of its own.</p>
     */
    private static boolean flatEnough(ArkWorld ark, int x, int z, int height) {
        int lowest = height;
        int highest = height;
        for (int[] step : new int[][]{{8, 0}, {-8, 0}, {0, 8}, {0, -8}}) {
            int neighbour = ark.engine().surfaceHeight(x + step[0], z + step[1]);
            lowest = Math.min(lowest, neighbour);
            highest = Math.max(highest, neighbour);
        }
        return highest - lowest < 7;
    }

    @Override
    public boolean isParallelCapable() {
        return plugin.arkConfig().parallel();
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        // Not a decoration setting so much as the switch that makes vanilla structures real. The
        // server places a structure's blocks inside its decoration pass, not its structure pass, so
        // with this off the ocean monuments and mineshafts were being planned and never built.
        // Turning it on brings the vanilla features along with them; that is the trade the flag
        // makes, and it is why it follows the same config key.
        return plugin.arkConfig().vanillaStructures();
    }

    @Override
    public boolean shouldGenerateStructures() {
        // Let the server place its own structures. They come from the game's own definitions, so an
        // ocean monument really is an ocean monument, and they follow the vanilla biome keys the
        // biome provider reports. The built-in equivalents step aside in config.
        return plugin.arkConfig().vanillaStructures();
    }

    @Override
    public boolean shouldGenerateMobs() {
        return plugin.arkConfig().vanillaMobs();
    }
}
