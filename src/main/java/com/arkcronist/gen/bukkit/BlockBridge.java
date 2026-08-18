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

    private static BlockData fallbackFor(String key, Logger logger) {
        // Strip the state, then try the plain material; if that fails too, use stone.
        int bracket = key.indexOf('[');
        String plain = bracket > 0 ? key.substring(0, bracket) : key;
        try {
            return Bukkit.createBlockData(plain);
        } catch (RuntimeException ignored) {
            logger.log(Level.WARNING, "Unknown block " + key + ", substituting stone");
            return Material.STONE.createBlockData();
        }
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
