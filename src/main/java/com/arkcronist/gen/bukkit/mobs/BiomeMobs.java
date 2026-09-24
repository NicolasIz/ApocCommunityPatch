package com.arkcronist.gen.bukkit.mobs;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Which custom mobs belong to a biome rather than to a vanilla entity type.
 *
 * <p>The entity table answers "what should a husk be here", and for most packs that is the right
 * question. It is the wrong one for a pack built around a place. An ice pack's yeti keyed to
 * VINDICATOR is a yeti in the desert; six woods of tree ent keyed to HUSK are sakura ents in the
 * tundra. The pack's author sorted those mobs by where they live, and a table keyed by entity type
 * throws that away.</p>
 *
 * <p>So a biome may name its own list, and it wins over the entity table where it does. Anything a
 * biome does not name falls through to the entity table exactly as before, which is what keeps the
 * goblins everywhere while the yetis stay in the snow.</p>
 *
 * <p>A biome can be written either way in config - {@code snowy_plains} or
 * {@code minecraft:snowy_plains} - and a datapack's own biome by its full key,
 * {@code terralith:alpine_grove}. Both spellings are tried, because an admin reading
 * {@code /ag biome} sees one form and an admin reading a datapack sees the other.</p>
 */
public final class BiomeMobs {

    private BiomeMobs() {
    }

    /**
     * The list a biome names, or null when it names none.
     *
     * @param table    the configured biome table, keyed in upper case
     * @param fullKey  the biome's namespaced key, e.g. {@code terralith:alpine_grove}
     */
    public static List<String> candidates(Map<String, List<String>> table, String fullKey) {
        if (table.isEmpty() || fullKey == null || fullKey.isEmpty()) {
            return null;
        }
        String full = fullKey.toUpperCase(Locale.ROOT);
        List<String> named = table.get(full);
        if (named != null && !named.isEmpty()) {
            return named;
        }
        int colon = full.indexOf(':');
        if (colon >= 0) {
            List<String> byPath = table.get(full.substring(colon + 1));
            if (byPath != null && !byPath.isEmpty()) {
                return byPath;
            }
        }
        return null;
    }

    /**
     * The list discovery gave a biome, used for this spawn or not.
     *
     * <p>Only a share of the spawns in a biome with mobs of its own use them; the rest come from the
     * general mix. A snowy forest is then mostly yetis and frost mites, and still has the odd goblin
     * - which is how a pack sorted by place reads: the place's mobs are what it is known for, not
     * the only thing in it.</p>
     *
     * @param discovered what discovery gave this biome, or null
     * @param share      the share of spawns that use it, 0 to 1
     * @param roll       a random number in [0, 1)
     * @return the list to use, or null to fall through to the general mix
     */
    public static List<String> discovered(List<String> discovered, double share, double roll) {
        if (discovered == null || discovered.isEmpty() || roll >= share) {
            return null;
        }
        return discovered;
    }

    /** The biome key at a location, or null when the world will not say. */
    public static String keyAt(org.bukkit.World world, org.bukkit.Location where) {
        try {
            org.bukkit.block.Biome biome = world.getBiome(where);
            return biome == null ? null : biome.getKey().toString();
        } catch (RuntimeException | LinkageError exception) {
            // A biome the server cannot resolve is not worth failing a spawn over.
            return null;
        }
    }
}
