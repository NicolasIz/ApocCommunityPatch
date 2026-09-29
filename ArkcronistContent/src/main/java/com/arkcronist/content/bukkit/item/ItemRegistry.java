package com.arkcronist.content.bukkit.item;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Every custom item currently loaded, by {@code namespace:id}.
 *
 * <p>The maps are built in full by a rebuild and then swapped in with a single write. Readers - the
 * main thread, command suggestions, listeners - never lock and never see half of one load and half
 * of the next: they see the old set of items or the new one. Nothing is ever modified in place.</p>
 */
public final class ItemRegistry {

    /**
     * @param byBareId items reachable by their id alone; an id used in two namespaces is left out,
     *                 so a short name never quietly picks one of them
     */
    private record Snapshot(Map<String, CustomItem> byId, Map<String, CustomItem> byBareId, List<String> ids) {
    }

    private volatile Snapshot snapshot = new Snapshot(Map.of(), Map.of(), List.of());

    /** Replaces everything. Called once per rebuild, on the main thread. */
    public void replace(Collection<CustomItem> items) {
        Map<String, CustomItem> byId = new HashMap<>();
        Map<String, CustomItem> byBareId = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (CustomItem item : items) {
            byId.put(item.id(), item);
            String bare = item.definition().id();
            if (byBareId.putIfAbsent(bare, item) != null) {
                ambiguous.add(bare);
            }
        }
        byBareId.keySet().removeAll(ambiguous);
        this.snapshot = new Snapshot(Map.copyOf(byId), Map.copyOf(byBareId),
                byId.keySet().stream().sorted().toList());
    }

    /** Exact lookup by {@code namespace:id}. */
    public Optional<CustomItem> get(String id) {
        return Optional.ofNullable(snapshot.byId().get(id));
    }

    /** {@code namespace:id}, or a bare id when only one namespace uses it. For typed input. */
    public Optional<CustomItem> find(String input) {
        String id = input.trim().toLowerCase(Locale.ROOT);
        Snapshot current = snapshot;
        return Optional.ofNullable(id.indexOf(':') >= 0 ? current.byId().get(id) : current.byBareId().get(id));
    }

    /** All ids, sorted. */
    public List<String> ids() {
        return snapshot.ids();
    }

    public Collection<CustomItem> all() {
        return snapshot.byId().values();
    }

    public int size() {
        return snapshot.byId().size();
    }
}
