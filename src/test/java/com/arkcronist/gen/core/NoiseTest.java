package com.arkcronist.gen.core;

import com.arkcronist.gen.core.math.FastRandom;
import com.arkcronist.gen.core.math.Hashing;
import com.arkcronist.gen.core.math.MathUtil;
import com.arkcronist.gen.core.noise.CellularNoise;
import com.arkcronist.gen.core.noise.FractalNoise;
import com.arkcronist.gen.core.noise.SimplexNoise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NoiseTest {

    @Test
    @DisplayName("simplex noise stays inside [-1,1] and is not constant")
    void simplexRange() {
        SimplexNoise noise = new SimplexNoise(42L);
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int i = 0; i < 20000; i++) {
            double value = noise.noise2(i * 0.37, i * -0.19);
            double value3 = noise.noise3(i * 0.11, i * 0.23, i * -0.31);
            assertTrue(value >= -1.0 && value <= 1.0, "2D noise out of range: " + value);
            assertTrue(value3 >= -1.0 && value3 <= 1.0, "3D noise out of range: " + value3);
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        assertTrue(max - min > 1.0, "noise should cover a wide range, saw " + min + ".." + max);
    }

    @Test
    @DisplayName("noise is a pure function of position")
    void simplexDeterminism() {
        SimplexNoise a = new SimplexNoise(7L);
        SimplexNoise b = new SimplexNoise(7L);
        for (int i = 0; i < 1000; i++) {
            assertEquals(a.noise2(i * 1.3, i * 0.7), b.noise2(i * 1.3, i * 0.7));
            assertEquals(a.noise3(i * 1.3, i * 0.7, i * 0.2), b.noise3(i * 1.3, i * 0.7, i * 0.2));
        }
    }

    @Test
    @DisplayName("different seeds produce independent fields")
    void seedsDiffer() {
        SimplexNoise a = new SimplexNoise(1L);
        SimplexNoise b = new SimplexNoise(2L);
        int differences = 0;
        for (int i = 0; i < 500; i++) {
            if (Math.abs(a.noise2(i * 0.9, i * 0.4) - b.noise2(i * 0.9, i * 0.4)) > 1e-6) {
                differences++;
            }
        }
        assertTrue(differences > 450, "seeds should not correlate, only " + differences + " differed");
    }

    @Test
    @DisplayName("fractal noise normalises to [-1,1] for every shape")
    void fractalRange() {
        FractalNoise[] samplers = {
                FractalNoise.fbm(3L, "a", 5, 0.01),
                FractalNoise.ridged(3L, "b", 5, 0.01),
                FractalNoise.billow(3L, "c", 5, 0.01)
        };
        for (FractalNoise sampler : samplers) {
            for (int i = 0; i < 5000; i++) {
                double value = sampler.noise2(i * 3.1, i * -1.7);
                assertTrue(value >= -1.0001 && value <= 1.0001, "fractal out of range: " + value);
                double unsigned = sampler.unsigned2(i * 3.1, i * -1.7);
                assertTrue(unsigned >= -0.0001 && unsigned <= 1.0001);
            }
        }
    }

    @Test
    @DisplayName("cellular noise returns stable cell values and identifiers")
    void cellular() {
        CellularNoise cells = new CellularNoise(11L, "test", 0.01, 0.8);
        long id = cells.cellId(100, 100);
        assertEquals(id, cells.cellId(100, 100));
        assertEquals(cells.cellValue(100, 100), cells.cellValue(100, 100));
        double value = cells.cellValue(100, 100);
        assertTrue(value >= 0.0 && value < 1.0);
        assertTrue(cells.f1(100, 100) >= 0.0);
    }

    @Test
    @DisplayName("FastRandom is reproducible and respects bounds")
    void fastRandom() {
        FastRandom a = new FastRandom(99L);
        FastRandom b = new FastRandom(99L);
        for (int i = 0; i < 1000; i++) {
            assertEquals(a.nextLong(), b.nextLong());
        }
        FastRandom random = new FastRandom(5L);
        for (int i = 0; i < 10000; i++) {
            int value = random.nextInt(7);
            assertTrue(value >= 0 && value < 7);
            int ranged = random.nextInt(-3, 3);
            assertTrue(ranged >= -3 && ranged <= 3);
            double d = random.nextDouble();
            assertTrue(d >= 0.0 && d < 1.0);
        }
    }

    @Test
    @DisplayName("position hashing is stable and well spread")
    void hashing() {
        assertEquals(Hashing.hash(1L, 10, -20), Hashing.hash(1L, 10, -20));
        assertNotEquals(Hashing.hash(1L, 10, -20), Hashing.hash(1L, -20, 10));
        int[] buckets = new int[10];
        for (int x = 0; x < 100; x++) {
            for (int z = 0; z < 100; z++) {
                buckets[(int) (Hashing.value(3L, x, z) * 10)]++;
            }
        }
        for (int count : buckets) {
            assertTrue(count > 700 && count < 1300, "hash distribution is skewed: " + count);
        }
    }

    @Test
    @DisplayName("math helpers behave at their boundaries")
    void math() {
        assertEquals(0.0, MathUtil.normalize(5.0, 5.0, 10.0));
        assertEquals(1.0, MathUtil.normalize(10.0, 5.0, 10.0));
        assertEquals(0.5, MathUtil.lerp(0.5, 0.0, 1.0));
        assertEquals(0.0, MathUtil.smoothStep(-1.0));
        assertEquals(1.0, MathUtil.smoothStep(2.0));
        assertTrue(MathUtil.smoothMin(3.0, 5.0, 2.0) <= 3.0);
        assertTrue(MathUtil.smoothMax(3.0, 5.0, 2.0) >= 5.0 - 1e-9);
        assertEquals(-1, MathUtil.floor(-0.5));
        assertEquals(2, MathUtil.floor(2.9));
    }
}
