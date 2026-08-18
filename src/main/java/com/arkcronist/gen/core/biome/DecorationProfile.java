package com.arkcronist.gen.core.biome;

/**
 * Per biome decoration densities, expressed as "attempts per column".
 *
 * <p>Everything is a probability, which keeps decoration order independent: a column decides its own
 * contents from its own hash, so populators never depend on neighbouring chunks being generated
 * first.</p>
 */
public final class DecorationProfile {

    public double grass = 0.0;
    public double tallGrass = 0.0;
    public double fern = 0.0;
    public double flowers = 0.0;
    public double deadBush = 0.0;
    public double mushrooms = 0.0;
    public double cactus = 0.0;
    public double sugarCane = 0.0;
    public double bamboo = 0.0;
    public double berryBush = 0.0;
    public double pumpkinPatch = 0.0;
    public double melon = 0.0;
    public double boulders = 0.0;
    public double deadLogs = 0.0;
    public double vines = 0.0;
    public double mossPatches = 0.0;
    public double lilyPads = 0.0;
    public double snowLayer = 0.0;
    public double iceSheet = 0.0;
    public double kelp = 0.0;
    public double seagrass = 0.0;
    public double coral = 0.0;
    public double seaPickles = 0.0;
    public double sponges = 0.0;
    public double magmaVents = 0.0;
    public double glowLichen = 0.0;
    public double dripstone = 0.0;

    public DecorationProfile copy() {
        DecorationProfile copy = new DecorationProfile();
        copy.grass = grass;
        copy.tallGrass = tallGrass;
        copy.fern = fern;
        copy.flowers = flowers;
        copy.deadBush = deadBush;
        copy.mushrooms = mushrooms;
        copy.cactus = cactus;
        copy.sugarCane = sugarCane;
        copy.bamboo = bamboo;
        copy.berryBush = berryBush;
        copy.pumpkinPatch = pumpkinPatch;
        copy.melon = melon;
        copy.boulders = boulders;
        copy.deadLogs = deadLogs;
        copy.vines = vines;
        copy.mossPatches = mossPatches;
        copy.lilyPads = lilyPads;
        copy.snowLayer = snowLayer;
        copy.iceSheet = iceSheet;
        copy.kelp = kelp;
        copy.seagrass = seagrass;
        copy.coral = coral;
        copy.seaPickles = seaPickles;
        copy.sponges = sponges;
        copy.magmaVents = magmaVents;
        copy.glowLichen = glowLichen;
        copy.dripstone = dripstone;
        return copy;
    }

    /** Scales every density, used by the global decoration multiplier and by presets. */
    public DecorationProfile scaled(double factor) {
        DecorationProfile copy = copy();
        copy.grass *= factor;
        copy.tallGrass *= factor;
        copy.fern *= factor;
        copy.flowers *= factor;
        copy.deadBush *= factor;
        copy.mushrooms *= factor;
        copy.cactus *= factor;
        copy.sugarCane *= factor;
        copy.bamboo *= factor;
        copy.berryBush *= factor;
        copy.pumpkinPatch *= factor;
        copy.melon *= factor;
        copy.boulders *= factor;
        copy.deadLogs *= factor;
        copy.vines *= factor;
        copy.mossPatches *= factor;
        copy.lilyPads *= factor;
        copy.kelp *= factor;
        copy.seagrass *= factor;
        copy.coral *= factor;
        copy.seaPickles *= factor;
        copy.sponges *= factor;
        copy.magmaVents *= factor;
        copy.glowLichen *= factor;
        copy.dripstone *= factor;
        return copy;
    }
}
