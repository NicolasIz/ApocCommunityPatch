package com.arkcronist.gen.core.noise;

import com.arkcronist.gen.core.math.Hashing;

/**
 * Fractal (multi octave) wrapper around a base sampler.
 *
 * <p>Three fractal shapes cover everything the generator needs:</p>
 * <ul>
 *     <li>{@link Type#FBM} - smooth rolling terrain, climate fields, decoration masks.</li>
 *     <li>{@link Type#RIDGED} - mountain ridges, river valleys, canyon walls.</li>
 *     <li>{@link Type#BILLOW} - dunes, cave cheese, cloudy blobs.</li>
 * </ul>
 *
 * <p>Octave amplitudes are normalised up front so the output stays inside [-1,1] regardless of the
 * octave count. That keeps preset tuning intuitive: amplitudes are expressed in blocks, not in
 * arbitrary noise units.</p>
 */
public final class FractalNoise implements NoiseSampler {

    public enum Type {
        FBM,
        RIDGED,
        BILLOW
    }

    private final SimplexNoise[] octaves;
    private final double[] amplitudes;
    private final double frequency;
    private final double lacunarity;
    private final Type type;
    private final double normalisation;

    private FractalNoise(long seed, String name, int octaveCount, double frequency, double lacunarity,
                         double gain, Type type) {
        this.frequency = frequency;
        this.lacunarity = lacunarity;
        this.type = type;
        this.octaves = new SimplexNoise[Math.max(1, octaveCount)];
        this.amplitudes = new double[octaves.length];
        double amplitude = 1.0;
        double sum = 0.0;
        for (int i = 0; i < octaves.length; i++) {
            octaves[i] = new SimplexNoise(Hashing.salt(seed, name + "#octave" + i));
            amplitudes[i] = amplitude;
            sum += amplitude;
            amplitude *= gain;
        }
        this.normalisation = 1.0 / sum;
    }

    public static FractalNoise fbm(long seed, String name, int octaves, double frequency) {
        return new FractalNoise(seed, name, octaves, frequency, 2.0, 0.5, Type.FBM);
    }

    public static FractalNoise ridged(long seed, String name, int octaves, double frequency) {
        return new FractalNoise(seed, name, octaves, frequency, 2.0, 0.5, Type.RIDGED);
    }

    public static FractalNoise billow(long seed, String name, int octaves, double frequency) {
        return new FractalNoise(seed, name, octaves, frequency, 2.0, 0.5, Type.BILLOW);
    }

    public static FractalNoise of(long seed, String name, int octaves, double frequency, double lacunarity,
                                  double gain, Type type) {
        return new FractalNoise(seed, name, octaves, frequency, lacunarity, gain, type);
    }

    @Override
    public double noise2(double x, double z) {
        double sum = 0.0;
        double freq = frequency;
        for (int i = 0; i < octaves.length; i++) {
            double value = octaves[i].noise2(x * freq, z * freq);
            sum += amplitudes[i] * shape(value);
            freq *= lacunarity;
        }
        return finish(sum * normalisation);
    }

    @Override
    public double noise3(double x, double y, double z) {
        double sum = 0.0;
        double freq = frequency;
        for (int i = 0; i < octaves.length; i++) {
            double value = octaves[i].noise3(x * freq, y * freq, z * freq);
            sum += amplitudes[i] * shape(value);
            freq *= lacunarity;
        }
        return finish(sum * normalisation);
    }

    private double shape(double value) {
        return switch (type) {
            case FBM -> value;
            case RIDGED -> 1.0 - Math.abs(value);
            case BILLOW -> Math.abs(value);
        };
    }

    private double finish(double value) {
        // RIDGED/BILLOW live in [0,1]; rescale to [-1,1] so every sampler shares one range.
        return type == Type.FBM ? value : value * 2.0 - 1.0;
    }

    /** Raw fractal output in [0,1] for ridged/billow shapes, handy for masks. */
    public double unsigned2(double x, double z) {
        return (noise2(x, z) + 1.0) * 0.5;
    }

    public double unsigned3(double x, double y, double z) {
        return (noise3(x, y, z) + 1.0) * 0.5;
    }

    public int octaveCount() {
        return octaves.length;
    }
}
