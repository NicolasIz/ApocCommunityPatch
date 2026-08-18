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

    public static MobSpawn mob(int x, int y, int z, String entityType, int tier) {
        return new MobSpawn(x, y, z, entityType, tier, false, null);
    }

    public static MobSpawn boss(int x, int y, int z, String entityType, int tier, String name) {
        return new MobSpawn(x, y, z, entityType, tier, true, name);
    }
}
