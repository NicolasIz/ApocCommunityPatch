package com.arkcronist.content.bukkit.block;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.core.block.BlockShape;
import com.arkcronist.content.core.definition.Placement;
import org.bukkit.Material;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 5.3: every shaped custom block loaded - slab, stairs, transparent, door, trapdoor - by id and by
 * the waxed copper block it is drawn through.
 *
 * <p>A placed one is nothing but that copper block, in whatever state vanilla put it (a top slab,
 * an open door), so the copper block's material alone says which custom block stands there,
 * whatever put it there. Replaced whole by each rebuild and read without locks, as the note block
 * {@link BlockRegistry} is.</p>
 */
public final class ShapedBlockRegistry {

    private record Snapshot(Map<String, CustomItem> byId, Map<Material, CustomItem> byBase) {
    }

    private volatile Snapshot snapshot = new Snapshot(Map.of(), Map.of());

    /**
     * Replaces everything with the shaped blocks among {@code items}. A block whose material is not
     * a copper block of its shape - left without one, made of paper - is never in the world, and
     * left out. Main thread, once per rebuild.
     */
    public void replace(Collection<CustomItem> items) {
        Map<String, CustomItem> byId = new HashMap<>();
        Map<Material, CustomItem> byBase = new HashMap<>();
        for (CustomItem item : items) {
            if (item.placement() instanceof Placement.Block block && block.shape().copper()
                    && block.shape().bases().contains(item.material().name().toLowerCase(Locale.ROOT))) {
                byId.put(item.id(), item);
                byBase.put(item.material(), item);
            }
        }
        this.snapshot = new Snapshot(Map.copyOf(byId), Map.copyOf(byBase));
    }

    public Optional<CustomItem> get(String id) {
        return Optional.ofNullable(snapshot.byId().get(id));
    }

    /** The custom block a copper block draws, if it draws one. */
    public Optional<CustomItem> byBase(Material material) {
        return Optional.ofNullable(snapshot.byBase().get(material));
    }

    /** Whether this copper block draws a custom block - and so is not to be crafted, waxed or scraped. */
    public boolean claimed(Material material) {
        return snapshot.byBase().containsKey(material);
    }

    /** The copper blocks that draw custom blocks. */
    public Set<Material> claimed() {
        return snapshot.byBase().keySet();
    }

    /** The shape a custom block of this one takes. */
    public static BlockShape shape(CustomItem item) {
        return ((Placement.Block) item.placement()).shape();
    }

    public boolean isEmpty() {
        return snapshot.byId().isEmpty();
    }

    public int size() {
        return snapshot.byId().size();
    }
}
