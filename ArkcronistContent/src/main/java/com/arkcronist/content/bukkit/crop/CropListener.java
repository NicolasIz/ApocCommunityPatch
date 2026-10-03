package com.arkcronist.content.bukkit.crop;

import com.arkcronist.content.bukkit.hooks.HookManager;
import com.arkcronist.content.bukkit.hooks.SkillXpHook;
import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.protection.Interaction;
import com.arkcronist.content.bukkit.protection.Protection;
import com.arkcronist.content.core.crop.PlantedCrop;
import com.arkcronist.content.core.definition.Placement;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * How players and the world meet crops: planting, bone meal, harvesting, and everything that
 * uproots a crop the way it would uproot wheat - its soil broken or trampled, water running over
 * it, an explosion under it.
 *
 * <p>Harvesting works like furniture: a punch at the crop is followed through the air to it and
 * turned into a real {@link BlockBreakEvent}, so protection plugins decide as for any block.</p>
 *
 * <p>Everything this listener does to a crop on its own account - planting it, bone meal, uprooting
 * it after its soil was trampled, blown up or washed over - is first put to every installed
 * protection plugin through {@link Protection}, and stops if any of them says no. Every handler
 * reacting to a vanilla event also ignores one a protection plugin has already cancelled.</p>
 */
public final class CropListener implements Listener {

    private final Plugin plugin;
    private final CropService crops;
    private final ItemFactory items;
    private final Protection protection;
    private final HookManager hooks;
    private final Logger logger;
    /** The tick each player last used bone meal on a crop. */
    private final Map<UUID, Integer> lastBoneMeal = new HashMap<>();

    public CropListener(Plugin plugin, CropService crops, ItemFactory items, Protection protection, HookManager hooks) {
        this.plugin = plugin;
        this.crops = crops;
        this.items = items;
        this.protection = protection;
        this.hooks = hooks;
        this.logger = plugin.getLogger();
    }

    // ---------------------------------------------------------------- planting and bone meal

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR || event.getHand() == null) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = event.getItem();

        if (held != null && held.getType() == Material.BONE_MEAL) {
            boneMeal(event, player, held);
            return;
        }
        Optional<CustomItem> item = items.identify(held);
        if (item.isEmpty() || !(item.get().placement() instanceof Placement.Crop crop)) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (action != Action.RIGHT_CLICK_BLOCK || clicked == null || event.getBlockFace() != BlockFace.UP
                || !CropService.isSoil(crop, clicked.getType())) {
            return;
        }
        // Clicking soil with seeds is planting, whether or not it works: never tilling, never placing.
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        if (player.getGameMode() != GameMode.ADVENTURE && player.getGameMode() != GameMode.SPECTATOR
                && protection.check(player, clicked.getRelative(BlockFace.UP), Interaction.PLACE)) {
            crops.plant(player, clicked, item.get(), crop, event.getHand());
        }
    }

    private void boneMeal(PlayerInteractEvent event, Player player, ItemStack held) {
        Optional<Block> target = crops.target(player);
        if (target.isEmpty()) {
            return;
        }
        // Aimed at a crop, bone meal is for the crop - never for the grass or farmland behind it.
        event.setCancelled(true);
        // One right click on a block reaches the server twice - as a use on the block, and then, since
        // vanilla bone meal does nothing to farmland, as a use in the air - and both find the crop.
        int tick = plugin.getServer().getCurrentTick();
        Integer last = lastBoneMeal.put(player.getUniqueId(), tick);
        if (last != null && tick - last < 4) {
            return;
        }
        Block block = target.get();
        Optional<PlantedCrop> crop = crops.at(block);
        Optional<Placement.Crop> definition = crop.flatMap(crops::definition);
        if (definition.isPresent() && protection.check(player, block, Interaction.BUILD)
                && crops.boneMeal(block, crop.get(), definition.get())
                && player.getGameMode() != GameMode.CREATIVE) {
            held.setAmount(held.getAmount() - 1);
            player.getInventory().setItem(event.getHand(), held.getAmount() > 0 ? held : null);
        }
    }

    // ---------------------------------------------------------------- harvesting

    // Not ignoreCancelled: a punch at the air arrives already cancelled.
    @EventHandler(priority = EventPriority.HIGH)
    public void onPunch(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.LEFT_CLICK_AIR || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        GameMode mode = event.getPlayer().getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.CREATIVE) {
            return;
        }
        Optional<Block> target = crops.target(event.getPlayer());
        if (target.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Block block = target.get();
        if (!protection.check(event.getPlayer(), block, Interaction.BREAK)) {
            return;
        }
        BlockBreakEvent breakEvent = new BlockBreakEvent(block, event.getPlayer());
        breakEvent.setDropItems(false);
        breakEvent.callEvent();
    }

    /**
     * The crop's own block, broken through the punch above; or its soil, broken any way at all. The
     * crop on broken soil is a block of its own, which may lie in an area the soil does not: it is
     * only uprooted if the player may break it too.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        boolean drop = player.getGameMode() != GameMode.CREATIVE;
        crops.at(block).ifPresent(crop -> {
            // Read before the crop goes: only a ripe one pays.
            double xp = crops.definition(crop).filter(definition -> crop.stage() >= definition.lastStage())
                    .map(Placement.Crop::skillXp).orElse(0.0);
            crops.remove(block, crop, drop);
            if (drop) {
                hooks.skillXp(player, SkillXpHook.Source.CROP, crop.cropId(), xp);
            }
        });
        Block above = block.getRelative(BlockFace.UP);
        crops.above(block).filter(crop -> protection.allows(player, above, Interaction.BREAK))
                .ifPresent(crop -> crops.remove(above, crop, drop));
    }

    // ---------------------------------------------------------------- the world

    /** Nothing may be placed into a crop's light block, which vanilla treats like air. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void protect(BlockPlaceEvent event) {
        // By now the block already has the new type, so the claim is asked by position.
        if (event.getBlockReplacedState().getType() == Material.LIGHT && crops.claims(event.getBlockPlaced())) {
            event.setCancelled(true);
        }
    }

    /** Farmland under a crop does not dry into dirt - vanilla keeps it for its own crops too. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (event.getBlock().getType() == Material.FARMLAND && crops.above(event.getBlock()).isPresent()) {
            event.setCancelled(true);
        }
    }

    /**
     * Trampled farmland: the crop on it is uprooted, as wheat is - when the trampling got past every
     * protection plugin, and the player may break the crop itself.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrample(PlayerInteractEvent event) {
        Block soil = event.getClickedBlock();
        if (event.getAction() == Action.PHYSICAL && soil != null && soil.getType() == Material.FARMLAND
                && protection.allows(event.getPlayer(), soil.getRelative(BlockFace.UP), Interaction.BREAK)) {
            uprootLater(soil);
        }
    }

    /** A mob trampling farmland: judged by where the mob is, as protection plugins judge mobs. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTrample(EntityChangeBlockEvent event) {
        Block soil = event.getBlock();
        if (soil.getType() != Material.FARMLAND || event.getTo() == Material.FARMLAND) {
            return;
        }
        boolean allowed = event.getEntity() instanceof Player player
                ? protection.allows(player, soil.getRelative(BlockFace.UP), Interaction.BREAK)
                : protection.allowsChange(soil.getRelative(BlockFace.UP), event.getEntity().getLocation());
        if (allowed) {
            uprootLater(soil);
        }
    }

    /**
     * Flowing water cannot enter a crop - a light block only takes a water source - so a crop holds
     * water back like a fence. Should a fluid ever flow in, the crop goes, dropping what it would.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        Block to = event.getToBlock();
        crops.at(to).filter(crop -> protection.allowsChange(to, event.getBlock().getLocation()))
                .ifPresent(crop -> crops.remove(to, crop, true));
    }

    /**
     * A bucket emptied onto a crop uproots it, as it would wheat. Water is poured here rather than by
     * vanilla, which would waterlog the crop's light block and so bring an invisible block back.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        Block target = event.getBlock();
        Optional<PlantedCrop> crop = crops.at(target);
        if (crop.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        if (!protection.check(player, target, Interaction.BREAK)) {
            // Left to vanilla, the bucket would waterlog the crop's light block.
            event.setCancelled(true);
            return;
        }
        crops.remove(target, crop.get(), player.getGameMode() != GameMode.CREATIVE);
        if (event.getBucket() == Material.WATER_BUCKET) {
            event.setCancelled(true);
            target.setType(Material.WATER);
            target.getWorld().playSound(target.getLocation().add(0.5, 0.5, 0.5), Sound.ITEM_BUCKET_EMPTY, 1f, 1f);
            if (player.getGameMode() != GameMode.CREATIVE) {
                player.getInventory().setItem(event.getHand(), ItemStack.of(Material.BUCKET));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        uprootAbove(event.blockList(), event.getLocation(), event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        uprootAbove(event.blockList(), event.getBlock().getLocation(), null);
    }

    /**
     * A piston would crush a crop's light block, or push its soil out from under it, and leave its
     * record and display behind. Refused, as for custom blocks and furniture.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (touchesCrop(event.getBlocks(), event.getDirection())
                || touchesCrop(List.of(event.getBlock().getRelative(event.getDirection())), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (touchesCrop(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    private boolean touchesCrop(List<Block> moved, BlockFace direction) {
        for (Block block : moved) {
            Block into = block.getRelative(direction);
            if (crops.claims(block) || crops.claims(into) || crops.above(block).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The light block itself resists explosions; the soil under it does not. By MONITOR the list
     * holds only what protection plugins let the blast destroy - and the crop, a block of its own,
     * is asked about separately.
     */
    private void uprootAbove(List<Block> destroyed, Location origin, @Nullable Entity source) {
        for (Block block : destroyed) {
            Block above = block.getRelative(BlockFace.UP);
            crops.above(block).filter(crop -> protection.allowsExplosion(above, origin, source))
                    .ifPresent(crop -> crops.remove(above, crop, true));
        }
    }

    /** Once the soil has changed - the event fires before it does - a crop no longer on soil comes up. */
    private void uprootLater(Block soil) {
        Block block = soil.getRelative(BlockFace.UP);
        plugin.getServer().getScheduler().runTask(plugin, () -> crops.at(block).ifPresent(crop -> {
            boolean onSoil = crops.definition(crop).map(def -> CropService.isSoil(def, soil.getType())).orElse(false);
            if (!onSoil) {
                crops.remove(block, crop, true);
            }
        }));
    }

    // ---------------------------------------------------------------- loading

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastBoneMeal.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        crops.chunkLoaded(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        crops.chunkUnloaded(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            crops.entityLoaded(entity);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        crops.worldLoaded(event.getWorld());
        String name = event.getWorld().getName();
        crops.store().loadWorld(event.getWorld().getUID()).whenComplete((rows, error) -> {
            if (error != null) {
                logger.warning("Could not read the crops of world " + name + ": " + error.getMessage());
            } else if (rows > 0) {
                logger.info("World " + name + ": " + rows + " crop(s) loaded.");
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        crops.worldUnloaded(event.getWorld());
        crops.store().unloadWorld(event.getWorld().getUID());
    }
}
