package com.arkcronist.gen.bukkit;

import com.arkcronist.gen.core.block.Blocks;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Resolves the core's integer block ids to real {@link BlockData} exactly once.
 *
 * <p>The terrain core deals only in ints; this is the single place where the generator touches
 * Bukkit's block system. Resolving up front means chunk generation never parses a block string, and
 * an unknown block on an older server degrades to a sane fallback instead of throwing millions of
 * times.</p>
 */
public final class BlockBridge {

    private static volatile BlockData[] byId = new BlockData[0];
    private static volatile boolean ready;

    private BlockBridge() {
    }

    public static synchronized void initialize(Logger logger) {
        List<String> keys = Blocks.REGISTRY.keys();
        BlockData[] resolved = new BlockData[keys.size()];
        int failures = 0;
        for (int id = 0; id < keys.size(); id++) {
            String key = keys.get(id);
            try {
                resolved[id] = Bukkit.createBlockData(key);
            } catch (IllegalArgumentException | NullPointerException exception) {
                resolved[id] = fallbackFor(key, logger);
                failures++;
            }
        }
        byId = resolved;
        ready = true;
        logger.info("Resolved " + resolved.length + " block states"
                + (failures > 0 ? " (" + failures + " fell back to a substitute)" : ""));
    }

    /**
     * Blocks the game has renamed, and what they are called now.
     *
     * <p>A schematic keeps the block names of the version it was saved in, and a rename turns every
     * one of them into an unknown block. The substitute for an unknown block is stone, so the
     * symptom is not a missing block but a grey cube: five of the prefabs shipped here were built
     * before {@code grass} became {@code short_grass} in 1.20.3, and every tuft of grass in three
     * castles, a wizard tower and a desert temple was being laid as solid stone.</p>
     *
     * <p>Only renames of the same block belong here. Mapping something to a block that merely looks
     * similar would place the wrong block silently, which is worse than the warning it replaces.</p>
     */
    private static final java.util.Map<String, String> RENAMED = java.util.Map.of(
            "minecraft:grass", "minecraft:short_grass",       // 1.20.3
            "minecraft:grass_path", "minecraft:dirt_path");   // 1.17

    private static BlockData fallbackFor(String key, Logger logger) {
        int bracket = key.indexOf('[');
        String plain = bracket > 0 ? key.substring(0, bracket) : key;

        String renamed = RENAMED.get(plain);
        if (renamed != null) {
            try {
                BlockData data = Bukkit.createBlockData(
                        bracket > 0 ? renamed + key.substring(bracket) : renamed);
                logger.info(plain + " is called " + renamed + " in this version; using that.");
                return data;
            } catch (RuntimeException ignored) {
                // The state does not fit the new block either. Fall through to the plain forms.
            }
            try {
                logger.info(plain + " is called " + renamed + " in this version; using that.");
                return Bukkit.createBlockData(renamed);
            } catch (RuntimeException ignored) {
                // Renamed again since, or not in this version at all.
            }
        }

        // Strip the state, then try the plain material; if that fails too, use stone.
        try {
            return Bukkit.createBlockData(plain);
        } catch (RuntimeException ignored) {
            logger.log(Level.WARNING, "Unknown block " + key + ", substituting stone");
            return Material.STONE.createBlockData();
        }
    }

    /** The rename table, for tests. */
    public static java.util.Map<String, String> renamedBlocks() {
        return RENAMED;
    }

    public static boolean ready() {
        return ready;
    }

    public static BlockData get(int id) {
        BlockData[] table = byId;
        return id >= 0 && id < table.length ? table[id] : null;
    }

    public static int size() {
        return byId.length;
    }
}
