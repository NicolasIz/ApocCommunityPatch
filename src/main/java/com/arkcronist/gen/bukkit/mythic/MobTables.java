package com.arkcronist.gen.bukkit.mythic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Puts what was discovered together with what the operator wrote, without either erasing the other.
 *
 * <p>Two different merges, because the two tables mean different things, and both rules are chosen so
 * that turning discovery on can never quietly change an answer the config already gave.</p>
 *
 * <ul>
 *   <li><b>The swap table is merged per key.</b> An entry like {@code ZOMBIE: [am_goblin_melee]} is a
 *       decision about zombies, so discovery is not allowed near it - it only fills in the entity
 *       types the config says nothing about. Merging the lists instead would mean that writing one
 *       name under ZOMBIE gets you that name a fifth of the time, which is not what writing it
 *       said.</li>
 *   <li><b>An ambient pool is merged as a union.</b> There is only one list per habitat and it is a
 *       pool rather than a set of decisions, so the configured names keep their place at the front
 *       and the discovered ones are added after. A name already written by hand is not added twice,
 *       because a repeat in these lists is the weighting mechanism and a silent doubling would skew
 *       it.</li>
 * </ul>
 *
 * <p>Pure and static: given the same two tables it returns the same answer, which is the only way the
 * merge itself can be tested without a server.</p>
 */
public final class MobTables {

    private MobTables() {
    }

    /**
     * The swap table to use, with the configured entries winning outright over discovery.
     *
     * @param configured what config.yml names, keyed by upper-case vanilla entity type
     * @param discovered what discovery found for this habitat, keyed the same way
     */
    public static Map<String, List<String>> swap(Map<String, List<String>> configured,
                                                 Map<String, List<String>> discovered) {
        if (discovered == null || discovered.isEmpty()) {
            return configured == null ? Map.of() : configured;
        }
        if (configured == null || configured.isEmpty()) {
            return discovered;
        }
        Map<String, List<String>> merged = new LinkedHashMap<>(configured);
        discovered.forEach(merged::putIfAbsent);
        return Map.copyOf(merged);
    }

    /** A pool with the configured names first and the discovered ones after, each appearing once. */
    public static List<String> pool(List<String> configured, List<String> discovered) {
        if (discovered == null || discovered.isEmpty()) {
            return configured == null ? List.of() : configured;
        }
        if (configured == null || configured.isEmpty()) {
            return discovered;
        }
        // Case-insensitive, because MythicMobs answers to a name however it is typed and two spellings
        // of one mob in a pool would double its share of the world.
        Set<String> seen = new LinkedHashSet<>();
        for (String name : configured) {
            seen.add(name.toLowerCase(java.util.Locale.ROOT));
        }
        List<String> merged = new ArrayList<>(configured);
        for (String name : discovered) {
            if (seen.add(name.toLowerCase(java.util.Locale.ROOT))) {
                merged.add(name);
            }
        }
        return List.copyOf(merged);
    }

    /**
     * A pool without the mobs that have biomes of their own.
     *
     * <p>A yeti given the snowy biomes must not also sit in the general pool, or it turns up in the
     * desert anyway - one pick in twenty instead of every pick, which is still a yeti in the
     * desert.</p>
     */
    public static List<String> without(List<String> pool, Set<String> placed) {
        if (pool == null || pool.isEmpty() || placed == null || placed.isEmpty()) {
            return pool == null ? List.of() : pool;
        }
        Set<String> lower = new java.util.HashSet<>();
        for (String name : placed) {
            lower.add(name.toLowerCase(java.util.Locale.ROOT));
        }
        List<String> kept = new ArrayList<>();
        for (String name : pool) {
            if (!lower.contains(name.toLowerCase(java.util.Locale.ROOT))) {
                kept.add(name);
            }
        }
        return List.copyOf(kept);
    }

    /** A swap table without the mobs that have biomes of their own; emptied entries go. */
    public static Map<String, List<String>> without(Map<String, List<String>> table,
                                                    Set<String> placed) {
        if (table == null || table.isEmpty() || placed == null || placed.isEmpty()) {
            return table == null ? Map.of() : table;
        }
        Map<String, List<String>> kept = new LinkedHashMap<>();
        table.forEach((body, names) -> {
            List<String> left = without(names, placed);
            if (!left.isEmpty()) {
                kept.put(body, left);
            }
        });
        return Map.copyOf(kept);
    }
}
