package com.arkcronist.content.bukkit.block;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.core.block.Mining;
import com.arkcronist.content.core.definition.Placement;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 5.4: custom blocks mined with their own tool and at their own hardness ({@link Mining}).
 *
 * <p>The client mines a custom block as the vanilla block underneath - a note block, with an axe,
 * hardness 0.8; a waxed copper block, with a pickaxe, hardness 3 - and reckons its progress itself.
 * While a player hits a custom block that has a tool or a hardness of its own, their
 * {@code block_break_speed} attribute is given a modifier that makes that reckoning come out at the
 * custom block's speed: the client draws the cracks at that speed and breaks the block when it is
 * done, and the server, reckoning the same way with the same attribute, accepts the break. The
 * modifier is transient - never saved with the player - and taken off as soon as they stop.</p>
 *
 * <p>Main thread only.</p>
 */
public final class BlockMining implements Listener {

    /** The vanilla block each tool mines at its everyday speed. */
    private static final Map<Mining.Tool, Material> EVERYDAY = new EnumMap<>(Map.of(
            Mining.Tool.PICKAXE, Material.STONE, Mining.Tool.AXE, Material.OAK_PLANKS, Mining.Tool.SHOVEL, Material.DIRT,
            Mining.Tool.HOE, Material.HAY_BLOCK, Mining.Tool.SHEARS, Material.WHITE_WOOL));
    /** For pickaxes: the vanilla block that needs at least each tier - the one tiers are asked of. */
    private static final Map<Mining.Tier, Material> NEEDS = new EnumMap<>(Map.of(
            Mining.Tier.WOOD, Material.STONE, Mining.Tier.STONE, Material.IRON_ORE, Mining.Tier.IRON, Material.DIAMOND_ORE,
            Mining.Tier.DIAMOND, Material.OBSIDIAN));

    private final Plugin plugin;
    private final com.arkcronist.content.bukkit.item.ItemFactory itemFactory;
    private final CustomBlockService cubes;
    private final ShapedBlockRegistry shaped;
    private final NamespacedKey key;
    /** Who is mining a custom block of their own speed now, and which. */
    private final Map<UUID, Block> mining = new HashMap<>();
    private final Map<Material, BlockData> everyday = new HashMap<>();

    public BlockMining(Plugin plugin, com.arkcronist.content.bukkit.item.ItemFactory itemFactory, CustomBlockService cubes,
                       ShapedBlockRegistry shaped) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
        this.cubes = cubes;
        this.shaped = shaped;
        this.key = com.arkcronist.content.bukkit.Keys.of("mining_speed");
        // A modifier left on a player who stopped in a way no event reports: taken off within two seconds.
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::sweep, 40L, 40L);
    }

    /** How the custom block at {@code block} is mined, if it is one with a tool or hardness of its own. */
    public Optional<Mining> miningOf(Block block) {
        Optional<Placement.Block> placement = placement(block);
        return placement.map(Placement.Block::mining);
    }

    private Optional<Placement.Block> placement(Block block) {
        Optional<CustomBlock> cube = cubes.identify(block);
        if (cube.isPresent()) {
            return Optional.of(cube.get().placement());
        }
        return shaped.byBase(block.getType()).map(CustomItem::placement)
                .filter(Placement.Block.class::isInstance).map(Placement.Block.class::cast);
    }

    /**
     * Whether {@code tool} is right for a block mined so: of its kind - it mines that kind's everyday
     * block faster than a hand - and good enough for its tier.
     */
    public boolean rightTool(@Nullable ItemStack tool, Mining mining) {
        if (mining.tool() == Mining.Tool.NONE) {
            return true;
        }
        if (tool == null || tool.isEmpty() || data(EVERYDAY.get(mining.tool())).getDestroySpeed(tool, false) <= 1) {
            return false;
        }
        if (mining.tier() == Mining.Tier.ANY) {
            return true;
        }
        Material needs = mining.tool() == Mining.Tool.PICKAXE ? NEEDS.get(mining.tier()) : null;
        if (needs != null) {
            // Vanilla's own judgement, so a custom pickaxe with a tool component of its own is judged too.
            return data(needs).isPreferredTool(tool);
        }
        return mining.tier().admits(Mining.Tier.ofMaterial(tool.getType().name()));
    }

    /** The speed of {@code tool} against a block mined so, as against vanilla's everyday block for its tool. */
    private double speed(@Nullable ItemStack tool, Mining mining) {
        if (tool == null || tool.isEmpty() || mining.tool() == Mining.Tool.NONE) {
            return 1;
        }
        return Math.max(1, data(EVERYDAY.get(mining.tool())).getDestroySpeed(tool, true));
    }

    private BlockData data(Material material) {
        return everyday.computeIfAbsent(material, Material::createBlockData);
    }

    // ---------------------------------------------------------------- mining

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStart(BlockDamageEvent event) {
        Player player = event.getPlayer();
        clear(player);
        Optional<Mining> rule = miningOf(event.getBlock());
        if (rule.isEmpty() || player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        Mining mining = rule.get();
        Block block = event.getBlock();
        ItemStack tool = event.getItemInHand();
        BlockData base = block.getBlockData();
        double factor = mining.speedFactor(base.getDestroySpeed(tool, true), block.getType().getHardness(),
                base.isPreferredTool(tool), speed(tool, mining), rightTool(tool, mining));
        // Haste, water and the rest, as they are for this player now: the progress the factor gives.
        double progress = block.getBreakSpeed(player) * factor;
        event.setInstaBreak(progress >= 1);
        if (Math.abs(factor - 1) < 1e-9) {
            return;
        }
        AttributeInstance speed = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (speed == null) {
            return;
        }
        speed.addTransientModifier(new AttributeModifier(key, factor - 1,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        this.mining.put(player.getUniqueId(), block);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAbort(BlockDamageAbortEvent event) {
        clear(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBroken(BlockBreakEvent event) {
        clear(event.getPlayer());
    }

    /** Another item in hand: the client starts over, and so does the modifier, when it hits again. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onHeld(PlayerItemHeldEvent event) {
        clear(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        clear(event.getPlayer());
    }

    // ---------------------------------------------------------------- drops of shaped blocks

    /** Doors' other halves that come down with a half broken by the wrong tool: they drop nothing. */
    private final java.util.Set<Block> dropless = new java.util.HashSet<>();

    /**
     * A shaped block with a tool of its own drops by its rule, not copper's - copper wants a pickaxe
     * - so vanilla's drop is called off here and its own given at {@link #dropShaped}.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void noCopperDrop(BlockBreakEvent event) {
        if (shapedMining(event.getBlock()) != null) {
            event.setDropItems(false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void dropShaped(BlockBreakEvent event) {
        Block block = event.getBlock();
        Mining rule = shapedMining(block);
        if (rule == null || event.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        CustomItem item = shaped.byBase(block.getType()).orElseThrow();
        boolean drops = rule.drops(rightTool(event.getPlayer().getInventory().getItemInMainHand(), rule));
        BlockData data = block.getBlockData();
        if (data instanceof org.bukkit.block.data.type.Door door && door.getHalf() == org.bukkit.block.data.Bisected.Half.TOP) {
            // The lower half comes down by itself, and is what drops the door.
            if (!drops) {
                Block lower = block.getRelative(org.bukkit.block.BlockFace.DOWN);
                dropless.add(lower);
                plugin.getServer().getScheduler().runTask(plugin, () -> dropless.remove(lower));
            }
            return;
        }
        int count = data instanceof org.bukkit.block.data.type.Slab slab
                && slab.getType() == org.bukkit.block.data.type.Slab.Type.DOUBLE ? 2 : 1;
        if (drops && ((Placement.Block) item.placement()).dropSelf()) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5),
                    itemFactory.create(item, count));
        }
    }

    /** Whether the half of a door at {@code block} came down with a half broken by the wrong tool. */
    public boolean dropless(Block block) {
        return dropless.contains(block);
    }

    private @Nullable Mining shapedMining(Block block) {
        return shaped.byBase(block.getType()).map(CustomItem::placement)
                .filter(Placement.Block.class::isInstance).map(placement -> ((Placement.Block) placement).mining())
                .orElse(null);
    }

    /** Takes the modifier off a player, whatever they were mining. */
    public void clear(Player player) {
        mining.remove(player.getUniqueId());
        AttributeInstance speed = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (speed != null && speed.getModifier(key) != null) {
            speed.removeModifier(key);
        }
    }

    /** Players no longer looking at the block they were mining - turned away, the block gone - let go. */
    private void sweep() {
        for (Iterator<Map.Entry<UUID, Block>> it = mining.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Block> entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            Block looked = player.getTargetBlockExact(6);
            if (!entry.getValue().equals(looked) || miningOf(entry.getValue()).isEmpty()) {
                it.remove();
                AttributeInstance speed = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
                if (speed != null && speed.getModifier(key) != null) {
                    speed.removeModifier(key);
                }
            }
        }
    }

    /** Every player's modifier off: the plugin is stopping. */
    public void clearAll() {
        for (UUID id : Map.copyOf(mining).keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                clear(player);
            }
        }
        mining.clear();
    }
}
