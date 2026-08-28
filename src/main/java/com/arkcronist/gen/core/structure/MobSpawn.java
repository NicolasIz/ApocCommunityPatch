package com.arkcronist.gen.core.structure;

/**
 * A request to spawn an entity once the chunk is live.
 *
 * <p>Generation runs off the main thread, so the core never spawns anything itself: it records what
 * should exist and where, and the Bukkit layer materialises it on the server thread when the chunk
 * loads.</p>
 *
 * @param entityType vanilla entity type name, e.g. {@code ZOMBIE}
 * @param tier       difficulty tier; drives health, damage, effects, gear and loot
 * @param miniboss   true for a named boss with abilities, false for ordinary garrison mobs
 * @param name       display name for minibosses, may be null
 */
public record MobSpawn(int x, int y, int z, String entityType, int tier, boolean miniboss, String name) {

    /**
     * Mobs that need three blocks of room rather than two.
     *
     * <p>Height is not a detail. A wither skeleton is 2.4 blocks tall and an iron golem 2.7, so an
     * ordinary room - a floor, two blocks of air, a ceiling - puts their eyes inside that ceiling,
     * and a mob with its eyes in a solid block suffocates. Placed by the two block rule they arrive
     * looking fine and are dead a few seconds later.</p>
     */
    private static final java.util.Set<String> TALL = java.util.Set.of(
            "WITHER_SKELETON", "IRON_GOLEM", "RAVAGER", "ENDERMAN", "WARDEN", "WITHER", "ENDER_DRAGON");

    /**
     * Mobs that do not stand on anything.
     *
     * <p>Everything that swims or flies. They are the exception to every rule about floors: a
     * guardian belongs in the middle of a flooded hall and a phantom belongs in the air over a sky
     * sanctuary, and insisting on solid ground under either would move it somewhere it does not
     * live.</p>
     */
    private static final java.util.Set<String> FLOATS = java.util.Set.of(
            "GUARDIAN", "ELDER_GUARDIAN", "DROWNED", "SQUID", "GLOW_SQUID", "DOLPHIN", "COD", "SALMON",
            "PUFFERFISH", "TROPICAL_FISH", "PHANTOM", "BAT", "VEX", "BREEZE", "ALLAY", "BEE", "GHAST",
            "BLAZE", "WITHER", "ENDER_DRAGON");

    public static MobSpawn mob(int x, int y, int z, String entityType, int tier) {
        return new MobSpawn(x, y, z, entityType, tier, false, null);
    }

    public static MobSpawn boss(int x, int y, int z, String entityType, int tier, String name) {
        return new MobSpawn(x, y, z, entityType, tier, true, name);
    }

    /** How many blocks of clear space this mob needs above its feet. */
    public int height() {
        return TALL.contains(entityType) ? 3 : 2;
    }

    /** Whether this mob has to have something solid under it. */
    public boolean needsFloor() {
        return !FLOATS.contains(entityType);
    }

    /** The same request, moved up or down. */
    public MobSpawn atHeight(int newY) {
        return newY == y ? this : new MobSpawn(x, newY, z, entityType, tier, miniboss, name);
    }
}
