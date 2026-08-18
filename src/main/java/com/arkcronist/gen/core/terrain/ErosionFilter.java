package com.arkcronist.gen.core.terrain;

import com.arkcronist.gen.core.math.MathUtil;

/**
 * Simulated weathering over the shared coarse height grid.
 *
 * <p>This is a talus limited (thermal) erosion model with sediment deposition, written as a
 * <em>gather</em> stencil: a cell reads its eight neighbours and writes only to itself. That detail
 * is what makes it usable in a chunk generator at all - a scatter based simulation would need global
 * state and would produce different results depending on which chunk was generated first.</p>
 *
 * <p>What it does visually: knocks the sharp fangs off ridges, opens talus fans below cliffs, fills
 * hollows and valley floors, and leaves ranges with the settled, layered look of old mountains while
 * still allowing genuinely steep faces wherever the preset's talus angle permits them.</p>
 */
public final class ErosionFilter {

    private static final double DIAGONAL = Math.sqrt(2.0);

    private ErosionFilter() {
    }

    public static double[] apply(double[] grid, float[] erosionField, int size, TerrainSettings settings) {
        int passes = MathUtil.clamp(settings.erosionPasses, 0, ChunkTerrain.PAD - 1);
        if (passes <= 0 || settings.erosionStrength <= 0.0) {
            return grid;
        }

        double talus = settings.erosionTalus * ChunkTerrain.CELL;
        double[] src = grid;
        double[] dst = new double[grid.length];

        for (int pass = 0; pass < passes; pass++) {
            System.arraycopy(src, 0, dst, 0, src.length);
            for (int z = 1; z < size - 1; z++) {
                for (int x = 1; x < size - 1; x++) {
                    int i = z * size + x;
                    double h = src[i];

                    double removed = 0.0;
                    double deposited = 0.0;
                    for (int oz = -1; oz <= 1; oz++) {
                        for (int ox = -1; ox <= 1; ox++) {
                            if (ox == 0 && oz == 0) {
                                continue;
                            }
                            double localTalus = (ox != 0 && oz != 0) ? talus * DIAGONAL : talus;
                            double delta = h - src[i + oz * size + ox];
                            if (delta > localTalus) {
                                removed += delta - localTalus;
                            } else if (-delta > localTalus) {
                                deposited += -delta - localTalus;
                            }
                        }
                    }

                    // Wetter, older ground weathers harder; young rock keeps its edges.
                    double weathering = 0.45 + erosionField[i] * 1.1;
                    dst[i] = h
                            - settings.erosionStrength * weathering * (removed / 8.0)
                            + settings.depositionStrength * weathering * (deposited / 8.0);
                }
            }
            double[] swap = src;
            src = dst;
            dst = swap;
        }
        return src;
    }
}
