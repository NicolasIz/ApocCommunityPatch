package com.arkcronist.gen.core.terrain;

import com.arkcronist.gen.core.biome.BiomeSelector;
import com.arkcronist.gen.core.math.MathUtil;

/**
 * All 2D information for one chunk, computed once and reused by every later stage.
 *
 * <p>The expensive part of terrain is the noise stack, so it is evaluated on a coarse grid (one node
 * every {@value #CELL} blocks) and upsampled with cubic interpolation. The grid is globally aligned
 * and padded, which is what guarantees that two neighbouring chunks compute <em>bit identical</em>
 * heights along their shared border: they interpolate from the same grid nodes with the same
 * weights.</p>
 *
 * <p>Erosion runs on that same padded grid as a gather-only stencil (a cell reads its neighbours but
 * never writes to them), so it stays seamless and order independent as well.</p>
 */
public final class ChunkTerrain {

    /** Grid spacing in blocks. */
    public static final int CELL = 4;
    /** Padding in grid cells: covers cubic interpolation (2) plus up to 3 erosion passes. */
    public static final int PAD = 4;
    /** Grid side length: 4 in-chunk cells plus padding on both sides plus the closing node. */
    public static final int GRID = 16 / CELL + 2 * PAD + 1;

    public final int chunkX;
    public final int chunkZ;

    public final float[] height = new float[256];
    public final float[] water = new float[256];
    public final int[] biome = new int[256];

    public final float[] land = new float[256];
    public final float[] oceanT = new float[256];
    public final float[] mountain = new float[256];
    public final float[] river = new float[256];
    public final float[] lake = new float[256];
    public final float[] erosion = new float[256];
    public final float[] temperature = new float[256];
    public final float[] humidity = new float[256];
    public final float[] canyon = new float[256];

    public int minSurface;
    public int maxSurface;

    private ChunkTerrain(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public static int index(int localX, int localZ) {
        return (localZ << 4) | localX;
    }

    public double heightAt(int localX, int localZ) {
        return height[index(localX, localZ)];
    }

    public double waterAt(int localX, int localZ) {
        return water[index(localX, localZ)];
    }

    public int biomeAt(int localX, int localZ) {
        return biome[index(localX, localZ)];
    }

    /**
     * Builds the terrain data for a chunk.
     *
     * @param sampler  the column sampler
     * @param selector biome selection
     * @param chunkX   chunk X
     * @param chunkZ   chunk Z
     */
    public static ChunkTerrain build(TerrainSampler sampler, BiomeSelector selector, int chunkX, int chunkZ) {
        TerrainSettings settings = sampler.settings();
        ChunkTerrain terrain = new ChunkTerrain(chunkX, chunkZ);

        double[] gridHeight = new double[GRID * GRID];
        double[] gridWater = new double[GRID * GRID];
        float[] gridLand = new float[GRID * GRID];
        float[] gridOcean = new float[GRID * GRID];
        float[] gridMountain = new float[GRID * GRID];
        float[] gridRiver = new float[GRID * GRID];
        float[] gridLake = new float[GRID * GRID];
        float[] gridErosion = new float[GRID * GRID];
        float[] gridTemperature = new float[GRID * GRID];
        float[] gridHumidity = new float[GRID * GRID];
        float[] gridWeirdness = new float[GRID * GRID];
        float[] gridCanyon = new float[GRID * GRID];

        ColumnData column = new ColumnData();
        int baseCellX = chunkX * (16 / CELL) - PAD;
        int baseCellZ = chunkZ * (16 / CELL) - PAD;

        for (int gz = 0; gz < GRID; gz++) {
            int worldZ = (baseCellZ + gz) * CELL;
            for (int gx = 0; gx < GRID; gx++) {
                int worldX = (baseCellX + gx) * CELL;
                sampler.sample(worldX, worldZ, column);
                int i = gz * GRID + gx;
                gridHeight[i] = column.height;
                gridWater[i] = column.waterLevel;
                gridLand[i] = (float) column.land;
                gridOcean[i] = (float) column.oceanT;
                gridMountain[i] = (float) column.mountainFactor;
                gridRiver[i] = (float) column.riverStrength;
                gridLake[i] = (float) column.lakeStrength;
                gridErosion[i] = (float) column.erosion;
                gridTemperature[i] = (float) column.temperature;
                gridHumidity[i] = (float) column.humidity;
                gridWeirdness[i] = (float) column.weirdness;
                gridCanyon[i] = (float) column.canyonFactor;
            }
        }

        gridHeight = ErosionFilter.apply(gridHeight, gridErosion, GRID, settings);

        int minSurface = Integer.MAX_VALUE;
        int maxSurface = Integer.MIN_VALUE;
        ColumnData lookup = new ColumnData();

        for (int localZ = 0; localZ < 16; localZ++) {
            double gz = PAD + localZ / (double) CELL;
            int cz = (int) gz;
            double tz = gz - cz;
            for (int localX = 0; localX < 16; localX++) {
                double gx = PAD + localX / (double) CELL;
                int cx = (int) gx;
                double tx = gx - cx;
                int i = index(localX, localZ);

                double h = bicubic(gridHeight, cx, cz, tx, tz);
                double w = bilinear(gridWater, cx, cz, tx, tz);

                terrain.height[i] = (float) h;
                terrain.water[i] = (float) Math.max(w, settings.seaLevel);
                terrain.land[i] = (float) bilinear(gridLand, cx, cz, tx, tz);
                terrain.oceanT[i] = (float) bilinear(gridOcean, cx, cz, tx, tz);
                terrain.mountain[i] = (float) bilinear(gridMountain, cx, cz, tx, tz);
                terrain.river[i] = (float) bilinear(gridRiver, cx, cz, tx, tz);
                terrain.lake[i] = (float) bilinear(gridLake, cx, cz, tx, tz);
                terrain.erosion[i] = (float) bilinear(gridErosion, cx, cz, tx, tz);
                terrain.temperature[i] = (float) bilinear(gridTemperature, cx, cz, tx, tz);
                terrain.humidity[i] = (float) bilinear(gridHumidity, cx, cz, tx, tz);
                terrain.canyon[i] = (float) bilinear(gridCanyon, cx, cz, tx, tz);

                lookup.reset(settings.seaLevel);
                lookup.height = h;
                lookup.waterLevel = terrain.water[i];
                lookup.land = terrain.land[i];
                lookup.oceanT = terrain.oceanT[i];
                lookup.mountainFactor = terrain.mountain[i];
                lookup.riverStrength = terrain.river[i];
                lookup.lakeStrength = terrain.lake[i];
                lookup.erosion = terrain.erosion[i];
                lookup.temperature = terrain.temperature[i];
                lookup.humidity = terrain.humidity[i];
                lookup.weirdness = bilinear(gridWeirdness, cx, cz, tx, tz);
                terrain.biome[i] = selector.select(lookup);

                int surface = (int) Math.floor(h);
                if (surface < minSurface) {
                    minSurface = surface;
                }
                if (surface > maxSurface) {
                    maxSurface = surface;
                }
            }
        }

        terrain.minSurface = MathUtil.clamp(minSurface, settings.minY, settings.maxY);
        terrain.maxSurface = MathUtil.clamp(maxSurface, settings.minY, settings.maxY);
        return terrain;
    }

    private static double bilinear(double[] grid, int cx, int cz, double tx, double tz) {
        double v00 = grid[cz * GRID + cx];
        double v10 = grid[cz * GRID + cx + 1];
        double v01 = grid[(cz + 1) * GRID + cx];
        double v11 = grid[(cz + 1) * GRID + cx + 1];
        return MathUtil.bilinear(tx, tz, v00, v10, v01, v11);
    }

    private static double bilinear(float[] grid, int cx, int cz, double tx, double tz) {
        double v00 = grid[cz * GRID + cx];
        double v10 = grid[cz * GRID + cx + 1];
        double v01 = grid[(cz + 1) * GRID + cx];
        double v11 = grid[(cz + 1) * GRID + cx + 1];
        return MathUtil.bilinear(tx, tz, v00, v10, v01, v11);
    }

    private static double bicubic(double[] grid, int cx, int cz, double tx, double tz) {
        double r0 = rowCubic(grid, cx, cz - 1, tx);
        double r1 = rowCubic(grid, cx, cz, tx);
        double r2 = rowCubic(grid, cx, cz + 1, tx);
        double r3 = rowCubic(grid, cx, cz + 2, tx);
        return MathUtil.cubic(tz, r0, r1, r2, r3);
    }

    private static double rowCubic(double[] grid, int cx, int cz, double tx) {
        int row = cz * GRID;
        return MathUtil.cubic(tx, grid[row + cx - 1], grid[row + cx], grid[row + cx + 1], grid[row + cx + 2]);
    }
}
