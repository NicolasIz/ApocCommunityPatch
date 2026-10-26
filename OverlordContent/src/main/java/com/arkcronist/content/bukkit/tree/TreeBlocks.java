package com.arkcronist.content.bukkit.tree;

import com.arkcronist.content.bukkit.block.BlockRegistry;
import com.arkcronist.content.bukkit.block.CustomBlock;
import com.arkcronist.content.bukkit.block.CustomLeaves;
import com.arkcronist.content.bukkit.block.LeavesRegistry;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.core.block.BlockAxis;
import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.tree.TreeSpec;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 5.4: the blocks a tree is made of, as the world takes them: a custom block's state - a note block
 * held in its own, the waxed copper block a shaped one is drawn through, or (5.5) the vanilla leaves
 * state custom leaves are - or a vanilla block.
 *
 * <p>Vanilla leaves need their distance from the trunk set, or they rot away on their first random
 * tick: a tree is set without physics, so nothing works it out for them. They decay as vanilla's do
 * when the trunk is a vanilla log; on a custom trunk, which vanilla leaves do not know for wood,
 * they are made persistent. Custom leaves are set as grown, and fall as such ({@link LeavesDecay}).</p>
 *
 * <p>5.5: a log lies as its blueprint cell says, in its turned state when it is a custom log that
 * turns, or with its {@code axis} when it is vanilla's; a vanilla block with a facing of its own - a
 * vine - is turned and mirrored with the tree.</p>
 */
public final class TreeBlocks {

    /** One block a tree names, resolved. */
    private record Resolved(BlockData data, @Nullable CustomBlock custom, @Nullable CustomLeaves leaves) {
    }

    private final Map<String, Resolved> blocks;
    private final Map<BlockAxis, Resolved> logs;
    private final boolean decays;

    private TreeBlocks(Map<String, Resolved> blocks, Map<BlockAxis, Resolved> logs, boolean decays) {
        this.blocks = blocks;
        this.logs = logs;
        this.decays = decays;
    }

    /** What a tree is made of, or empty - with why - when a block it names does not exist. */
    public static Optional<TreeBlocks> resolve(TreeSpec tree, ItemRegistry items, BlockRegistry cubes,
                                               LeavesRegistry leaves, List<String> problems) {
        Map<String, Resolved> blocks = new HashMap<>();
        for (String key : tree.blocks()) {
            String id = key.equals(TreeSpec.LOG) ? tree.log() : key.equals(TreeSpec.LEAVES) ? tree.leaves() : key;
            Optional<Resolved> resolved = block(id, items, cubes, leaves, problems);
            if (resolved.isEmpty()) {
                return Optional.empty();
            }
            blocks.put(key, resolved.get());
        }
        Resolved log = blocks.get(TreeSpec.LOG);
        Map<BlockAxis, Resolved> logs = new EnumMap<>(BlockAxis.class);
        for (BlockAxis axis : BlockAxis.values()) {
            if (log.custom() != null) {
                CustomBlock turned = cubes.get(log.custom().id(), axis).orElse(log.custom());
                logs.put(axis, new Resolved(turned.data(), turned, null));
            } else if (log.data() instanceof Orientable orientable
                    && orientable.getAxes().contains(org.bukkit.Axis.valueOf(axis.name()))) {
                Orientable lying = (Orientable) orientable.clone();
                lying.setAxis(org.bukkit.Axis.valueOf(axis.name()));
                logs.put(axis, new Resolved(lying, null, null));
            } else {
                logs.put(axis, log);
            }
        }
        Resolved crown = blocks.get(TreeSpec.LEAVES);
        boolean decays = crown.data() instanceof Leaves && crown.leaves() == null && log.custom() == null
                && Tag.LOGS.isTagged(log.data().getMaterial());
        return Optional.of(new TreeBlocks(Map.copyOf(blocks), logs, decays));
    }

    private static Optional<Resolved> block(String id, ItemRegistry items, BlockRegistry cubes, LeavesRegistry leaves,
                                            List<String> problems) {
        Optional<CustomItem> item = items.get(id);
        if (item.isPresent()) {
            if (!(item.get().placement() instanceof Placement.Block block)) {
                problems.add(id + " is not a block a tree can be made of");
                return Optional.empty();
            }
            if (block.leaves() != null) {
                Optional<CustomLeaves> custom = leaves.get(id);
                if (custom.isEmpty()) {
                    problems.add(id + " got no leaves state, so no tree can be made of it");
                    return Optional.empty();
                }
                return Optional.of(new Resolved(custom.get().natural(), null, custom.get()));
            }
            if (block.noteBlock()) {
                Optional<CustomBlock> cube = cubes.get(id);
                if (cube.isEmpty()) {
                    problems.add(id + " got no note block state, so no tree can be made of it");
                    return Optional.empty();
                }
                return Optional.of(new Resolved(cube.get().data(), cube.get(), null));
            }
            // A shaped block: the copper block it is drawn through, which is what it is in the world.
            return Optional.of(new Resolved(item.get().material().createBlockData(), null, null));
        }
        // Not a custom block: the vanilla block of that name - a bare name in the YAML came with the pack's namespace.
        String name = id.substring(id.indexOf(':') + 1);
        int state = name.indexOf('[');
        Material material = Material.matchMaterial(state < 0 ? name : name.substring(0, state));
        if (material == null || !material.isBlock() || material.isAir()) {
            problems.add("no custom block or vanilla block called " + id);
            return Optional.empty();
        }
        if (state < 0) {
            return Optional.of(new Resolved(material.createBlockData(), null, null));
        }
        try {
            return Optional.of(new Resolved(Bukkit.createBlockData("minecraft:" + name), null, null));
        } catch (IllegalArgumentException wrong) {
            problems.add(id + " is not a state that block has (" + wrong.getMessage() + ")");
            return Optional.empty();
        }
    }

    /** The trunk's block, upright. */
    public BlockData log() {
        return logs.get(BlockAxis.Y).data();
    }

    /** The crown's block, before its distance is set. */
    public BlockData leaves() {
        return blocks.get(TreeSpec.LEAVES).data();
    }

    /**
     * The block a part of the tree is, as set in the world: a log lying its way, vanilla leaves at
     * their distance from the trunk, anything with a facing turned with the tree.
     *
     * @param parts the whole tree, for a vanilla leaf's distance
     */
    public BlockData data(TreeSpec.Part part, List<TreeSpec.Part> parts) {
        if (part.log()) {
            return logs.get(part.axis()).data();
        }
        Resolved resolved = blocks.get(part.block());
        if (resolved == null) {
            return leaves();
        }
        BlockData data = resolved.data();
        if (resolved.custom() != null || resolved.leaves() != null) {
            return data;
        }
        if (data instanceof Leaves) {
            return leafAt(data, distance(part, parts));
        }
        if (part.turn() == 0) {
            return data;
        }
        BlockData turned = data.clone();
        if (part.mirrored()) {
            turned.mirror(Mirror.FRONT_BACK);
        }
        turned.rotate(switch (part.quarterTurns()) {
            case 1 -> StructureRotation.CLOCKWISE_90;
            case 2 -> StructureRotation.CLOCKWISE_180;
            case 3 -> StructureRotation.COUNTERCLOCKWISE_90;
            default -> StructureRotation.NONE;
        });
        return turned;
    }

    /** The custom note block a part is, to be recorded as placed; null for anything else. */
    public @Nullable CustomBlock recorded(TreeSpec.Part part) {
        if (part.log()) {
            return logs.get(part.axis()).custom();
        }
        Resolved resolved = blocks.get(part.block());
        return resolved == null ? null : resolved.custom();
    }

    /** The crown's block for a leaf this far from the trunk: vanilla leaves get the distance vanilla would give them. */
    public BlockData leafAt(int distance) {
        return leafAt(leaves(), distance);
    }

    private BlockData leafAt(BlockData leaves, int distance) {
        if (!(leaves instanceof Leaves leaf)) {
            return leaves;
        }
        Leaves placed = (Leaves) leaf.clone();
        placed.setPersistent(!decays);
        placed.setDistance(Math.max(1, Math.min(7, distance)));
        return placed;
    }

    /** A leaf's distance from the trunk as vanilla counts it, near enough for a crown that touches it. */
    public static int distance(TreeSpec.Part leaf, List<TreeSpec.Part> logs) {
        int best = Integer.MAX_VALUE;
        for (TreeSpec.Part log : logs) {
            if (log.log()) {
                best = Math.min(best, Math.abs(log.dx() - leaf.dx()) + Math.abs(log.dy() - leaf.dy())
                        + Math.abs(log.dz() - leaf.dz()));
            }
        }
        return best;
    }
}
