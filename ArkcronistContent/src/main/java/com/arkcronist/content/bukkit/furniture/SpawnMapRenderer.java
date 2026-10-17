package com.arkcronist.content.bukkit.furniture;

import com.arkcronist.content.core.furniture.SpawnMap;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 3.2: the map board's picture, drawn from the server's own world - {@link SpawnMap} of the ground
 * around a point.
 *
 * <p>The chunks are loaded without generating any (ground nobody explored stays bare parchment),
 * each taken as a snapshot on the main thread as it arrives, and the columns read, the picture drawn
 * and the file written off it.</p>
 */
public final class SpawnMapRenderer {

    /** Blocks per pixel at most: 1280 x 704 blocks, a few thousand chunks. */
    public static final int MAX_SCALE = 4;

    private final Plugin plugin;

    public SpawnMapRenderer(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Draws the ground centred on {@code centreX}, {@code centreZ}, {@code scale} blocks to a pixel,
     * with a cross at {@code mark}, into {@code out}. Main thread.
     *
     * @return how many chunks were drawn from, once the file is written
     */
    public CompletableFuture<Integer> render(World world, int centreX, int centreZ, int scale, Location mark, Path out) {
        int width = SpawnMap.WIDTH * scale;
        int height = SpawnMap.HEIGHT * scale;
        int minX = centreX - width / 2;
        int minZ = centreZ - height / 2;
        int minY = world.getMinHeight();
        Map<Long, ChunkSnapshot> snapshots = new HashMap<>();
        List<CompletableFuture<?>> loads = new ArrayList<>();
        for (int cx = minX >> 4; cx <= (minX + width - 1) >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= (minZ + height - 1) >> 4; cz++) {
                long key = key(cx, cz);
                // Completed on the main thread: the snapshot is taken there.
                loads.add(world.getChunkAtAsync(cx, cz, false).thenAccept(chunk -> take(snapshots, key, chunk)));
            }
        }
        int markX = Math.floorDiv(mark.getBlockX() - minX, scale);
        int markY = Math.floorDiv(mark.getBlockZ() - minZ, scale);
        boolean marked = mark.getWorld() != null && mark.getWorld().equals(world);
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).thenApplyAsync(done -> {
            SpawnMap.Column[][] columns = new SpawnMap.Column[SpawnMap.HEIGHT][SpawnMap.WIDTH];
            for (int py = 0; py < SpawnMap.HEIGHT; py++) {
                for (int px = 0; px < SpawnMap.WIDTH; px++) {
                    int x = minX + px * scale + scale / 2;
                    int z = minZ + py * scale + scale / 2;
                    ChunkSnapshot snapshot = snapshots.get(key(x >> 4, z >> 4));
                    columns[py][px] = snapshot == null ? null : column(snapshot, x & 15, z & 15, minY);
                }
            }
            try {
                Files.createDirectories(out.getParent());
                ImageIO.write(SpawnMap.draw(columns, marked ? markX : -1, marked ? markY : -1), "png", out.toFile());
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
            return snapshots.size();
        }, task -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task));
    }

    private static synchronized void take(Map<Long, ChunkSnapshot> snapshots, long key, Chunk chunk) {
        if (chunk != null) {
            snapshots.put(key, chunk.getChunkSnapshot(true, false, false));
        }
    }

    /** One column as a map sees it: its top block with a colour, and how deep the water over it is. */
    static SpawnMap.Column column(ChunkSnapshot snapshot, int x, int z, int minY) {
        int y = snapshot.getHighestBlockYAt(x, z);
        BlockData top = null;
        int rgb = 0;
        for (; y >= minY; y--) {
            top = snapshot.getBlockData(x, y, z);
            rgb = colour(top);
            if (rgb != 0) {
                break;
            }
        }
        if (rgb == 0 || top == null) {
            return null;
        }
        int depth = 0;
        if (top.getMaterial() == Material.WATER) {
            for (int below = y; below >= minY && depth < 12
                    && snapshot.getBlockData(x, below, z).getMaterial() == Material.WATER; below--) {
                depth++;
            }
        }
        return new SpawnMap.Column(rgb, y, depth);
    }

    private static int colour(BlockData block) {
        if (block.getMaterial().isAir()) {
            return 0;
        }
        Color colour = block.getMapColor();
        return colour == null ? 0 : colour.asRGB();
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }
}
