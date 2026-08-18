package com.arkcronist.gen.core.block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps block state strings to dense integer ids.
 *
 * <p>The terrain core never touches a single Bukkit class. It writes {@code int}s, and the Bukkit
 * layer resolves those ids to {@code BlockData} exactly once at startup. Three things fall out of
 * that: the core is unit testable without a server, generation writes primitives instead of
 * chasing object references, and adding a block from config costs nothing at generation time.</p>
 */
public final class BlockRegistry {

    private final Map<String, Integer> index = new HashMap<>(512);
    private final List<String> keys = new ArrayList<>(512);

    /** Registers (or looks up) a block state string such as {@code minecraft:oak_log[axis=y]}. */
    public synchronized int id(String blockState) {
        Integer existing = index.get(blockState);
        if (existing != null) {
            return existing;
        }
        int id = keys.size();
        keys.add(blockState);
        index.put(blockState, id);
        return id;
    }

    public synchronized String key(int id) {
        return keys.get(id);
    }

    public synchronized int size() {
        return keys.size();
    }

    public synchronized List<String> keys() {
        return Collections.unmodifiableList(new ArrayList<>(keys));
    }
}
