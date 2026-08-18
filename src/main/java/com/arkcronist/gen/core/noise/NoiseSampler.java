package com.arkcronist.gen.core.noise;

/**
 * A stateless, thread safe noise source.
 *
 * <p>Implementations must be pure: the same coordinates always return the same value, no matter
 * which thread asks or in which order. That is the contract the entire generator relies on for
 * seamless chunk borders and reproducible seeds.</p>
 */
public interface NoiseSampler {

    double noise2(double x, double z);

    double noise3(double x, double y, double z);
}
