package com.arkcronist.content.bukkit.mechanics;

import com.arkcronist.content.bukkit.block.BlockMining;
import com.arkcronist.content.core.block.Mining;
import com.arkcronist.content.core.mechanics.ItemMechanics;
import com.arkcronist.content.core.mechanics.ItemMechanics.Trigger;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 5.4: the triggers of items' {@code events:}, and the mechanics that act on what happens -
 * {@code big-mining}, {@code auto-smelt}, {@code soulbound}, {@code lifesteal}.
 *
 * <p>Every block {@code big-mining} breaks besides the one mined is a {@link BlockBreakEvent} of its
 * own, so protection plugins - and this plugin's custom blocks - decide on each; it never takes a
 * block holding anything (a chest, a furnace), one the tool is not right for, one harder than the
 * block mined, nor anything while the player sneaks.</p>
 */
public final class MechanicsListener implements Listener {

    private final Plugin plugin;
    private final MechanicsService mechanics;
    private final BlockMining mining;
    /** The tick each player last set off each kind of click, so one click sent twice counts once. */
    private final Map<String, Integer> lastClick = new HashMap<>();
    /** Who is breaking the blocks around one they mined: those breaks are not mined again. */
    private final Set<UUID> areaMining = new HashSet<>();
    /** What a furnace makes of each material; filled as asked, emptied when the recipes change. */
    private final Map<Material, Optional<ItemStack>> smelted = new HashMap<>();

    public MechanicsListener(Plugin plugin, MechanicsService mechanics, BlockMining mining) {
        this.plugin = plugin;
        this.mechanics = mechanics;
        this.mining = mining;
    }

    /** The recipes may have changed: smelting is looked up again. */
    public void recipesChanged() {
        smelted.clear();
    }

    // ---------------------------------------------------------------- clicks

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEvent event) {
        if (!mechanics.any() || event.getHand() != EquipmentSlot.HAND || event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        Action action = event.getAction();
        Trigger trigger = switch (action) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> Trigger.RIGHT_CLICK;
            case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> Trigger.LEFT_CLICK;
            default -> null;
        };
        if (trigger == null) {
            return;
        }
        Player player = event.getPlayer();
        var found = mechanics.withId(event.getItem());
        if (found.isEmpty() || found.get().getValue().on(trigger).isEmpty() || !once(player, trigger)) {
            return;
        }
        Entity entity = null;
        Block block = event.getClickedBlock();
        if (block == null) {
            // In the air: what the player looks at, further off.
            RayTraceResult hit = player.getWorld().rayTrace(player.getEyeLocation(), player.getEyeLocation().getDirection(),
                    MechanicsService.REACH, FluidCollisionMode.NEVER, true, 0.2,
                    other -> other != player && other instanceof LivingEntity && !other.isDead());
            if (hit != null) {
                entity = hit.getHitEntity();
                block = hit.getHitBlock();
            }
        }
        mechanics.fire(player, found.get().getKey(), found.get().getValue(), trigger,
                new MechanicsService.Context(EquipmentSlot.HAND, entity, block));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClickEntity(PlayerInteractEntityEvent event) {
        if (!mechanics.any() || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        var found = mechanics.withId(player.getInventory().getItemInMainHand());
        if (found.isEmpty() || found.get().getValue().on(Trigger.RIGHT_CLICK).isEmpty() || !once(player, Trigger.RIGHT_CLICK)) {
            return;
        }
        mechanics.fire(player, found.get().getKey(), found.get().getValue(), Trigger.RIGHT_CLICK,
                new MechanicsService.Context(EquipmentSlot.HAND, event.getRightClicked(), null));
    }

    /** One click reaches the server as more than one event - on a block or entity, then in the air. */
    private boolean once(Player player, Trigger trigger) {
        int tick = plugin.getServer().getCurrentTick();
        Integer last = lastClick.put(player.getUniqueId() + "|" + trigger, tick);
        return last == null || tick - last > 1;
    }

    // ---------------------------------------------------------------- fighting

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!mechanics.any() || !(event.getDamager() instanceof Player player)
                || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK || mechanics.dealing(player)) {
            return;
        }
        var found = mechanics.withId(player.getInventory().getItemInMainHand());
        if (found.isEmpty()) {
            return;
        }
        ItemMechanics item = found.get().getValue();
        double lifesteal = item.passive().lifesteal();
        if (lifesteal > 0 && event.getFinalDamage() > 0 && !player.isDead()) {
            var max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            double top = max == null ? 20 : max.getValue();
            player.setHealth(Math.min(top, player.getHealth() + event.getFinalDamage() * lifesteal));
        }
        mechanics.fire(player, found.get().getKey(), item, Trigger.HIT_ENTITY,
                new MechanicsService.Context(EquipmentSlot.HAND, event.getEntity(), null));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (!mechanics.any() || killer == null) {
            return;
        }
        mechanics.withId(killer.getInventory().getItemInMainHand()).ifPresent(found -> mechanics.fire(killer,
                found.getKey(), found.getValue(), Trigger.KILL,
                new MechanicsService.Context(EquipmentSlot.HAND, event.getEntity(), null)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (!mechanics.any() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        Entity from = null;
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            from = byEntity.getDamager();
            if (from instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
                from = shooter;
            }
        }
        for (EquipmentSlot slot : List.of(EquipmentSlot.HAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            Entity source = from;
            mechanics.withId(player.getInventory().getItem(slot)).ifPresent(found -> mechanics.fire(player, found.getKey(),
                    found.getValue(), Trigger.DAMAGED, new MechanicsService.Context(slot, source, null)));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!mechanics.any() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        EquipmentSlot hand = event.getHand();
        mechanics.withId(event.getBow()).ifPresent(found -> mechanics.fire(player, found.getKey(), found.getValue(),
                Trigger.SHOOT, new MechanicsService.Context(hand, event.getProjectile(), null)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!mechanics.any()) {
            return;
        }
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        mechanics.withId(event.getItem()).ifPresent(found -> mechanics.fire(player, found.getKey(), found.getValue(),
                Trigger.CONSUME, new MechanicsService.Context(hand, null, null)));
    }

    // ---------------------------------------------------------------- mining

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!mechanics.any() || areaMining.contains(player.getUniqueId())) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        var found = mechanics.withId(tool);
        if (found.isEmpty()) {
            return;
        }
        ItemMechanics item = found.get().getValue();
        mechanics.fire(player, found.get().getKey(), item, Trigger.BREAK_BLOCK,
                new MechanicsService.Context(EquipmentSlot.HAND, null, event.getBlock()));
        if (item.passive().bigMining() > 0 && !player.isSneaking() && player.getGameMode() != GameMode.CREATIVE) {
            mineAround(player, event.getBlock(), tool, item.passive());
        }
    }

    /** The blocks around {@code origin}, across the face the player mines and as deep as asked. */
    private void mineAround(Player player, Block origin, ItemStack tool, ItemMechanics.Passive passive) {
        BlockFace facing = facing(player);
        int radius = passive.bigMining();
        float hardest = Math.max(origin.getType().getHardness(), 0) + 1.5f;
        List<Block> area = new ArrayList<>();
        for (int depth = 0; depth <= passive.miningDepth(); depth++) {
            for (int a = -radius; a <= radius; a++) {
                for (int b = -radius; b <= radius; b++) {
                    if (depth == 0 && a == 0 && b == 0) {
                        continue;
                    }
                    // Across the face mined, and deeper along where the player looks.
                    int dx;
                    int dy;
                    int dz;
                    if (facing.getModY() != 0) {
                        dx = a;
                        dy = facing.getModY() * depth;
                        dz = b;
                    } else if (facing.getModX() != 0) {
                        dx = facing.getModX() * depth;
                        dy = a;
                        dz = b;
                    } else {
                        dx = a;
                        dy = b;
                        dz = facing.getModZ() * depth;
                    }
                    area.add(origin.getRelative(dx, dy, dz));
                }
            }
        }
        areaMining.add(player.getUniqueId());
        try {
            for (Block block : area) {
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held.isEmpty() || held.getType() != tool.getType()) {
                    return; // the tool broke
                }
                if (!minable(block, held, hardest)) {
                    continue;
                }
                BlockBreakEvent extra = new BlockBreakEvent(block, player);
                if (!extra.callEvent()) {
                    continue;
                }
                Collection<ItemStack> drops = extra.isDropItems() ? block.getDrops(held, player) : List.of();
                BlockData data = block.getBlockData();
                Location centre = block.getLocation().add(0.5, 0.5, 0.5);
                block.setType(Material.AIR);
                block.getWorld().spawnParticle(Particle.BLOCK, centre, 18, 0.3, 0.3, 0.3, data);
                block.getWorld().playSound(centre, data.getSoundGroup().getBreakSound(), 0.6f, 1f);
                for (ItemStack drop : drops) {
                    block.getWorld().dropItemNaturally(centre, passive.autoSmelt() ? smelt(drop) : drop);
                }
                if (extra.getExpToDrop() > 0) {
                    block.getWorld().spawn(centre, ExperienceOrb.class, orb -> orb.setExperience(extra.getExpToDrop()));
                }
                player.damageItemStack(EquipmentSlot.HAND, 1);
            }
        } finally {
            areaMining.remove(player.getUniqueId());
        }
    }

    private boolean minable(Block block, ItemStack tool, float hardest) {
        Material type = block.getType();
        float hardness = type.getHardness();
        if (type.isAir() || block.isLiquid() || hardness <= 0 || hardness > hardest) {
            return false;
        }
        if (block.getState(false) instanceof TileState) {
            return false; // a chest, a furnace, a sign: what it holds would be lost
        }
        Optional<Mining> custom = mining.miningOf(block);
        if (custom.isPresent()) {
            return mining.rightTool(tool, custom.get());
        }
        return block.isPreferredTool(tool);
    }

    /** The face of the block the player mines: up or down when looking steeply, else where they face. */
    private static BlockFace facing(Player player) {
        float pitch = player.getLocation().getPitch();
        if (pitch > 45) {
            return BlockFace.DOWN;
        }
        if (pitch < -45) {
            return BlockFace.UP;
        }
        Vector direction = player.getLocation().getDirection();
        if (Math.abs(direction.getX()) > Math.abs(direction.getZ())) {
            return direction.getX() > 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return direction.getZ() > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    /** What the block mined drops, smelted - the drops vanilla makes, as it makes them. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent event) {
        if (!mechanics.any()) {
            return;
        }
        Player player = event.getPlayer();
        Optional<ItemMechanics> item = mechanics.of(player.getInventory().getItemInMainHand());
        if (item.isEmpty() || !item.get().passive().autoSmelt()) {
            return;
        }
        for (Item drop : event.getItems()) {
            drop.setItemStack(smelt(drop.getItemStack()));
        }
    }

    /** {@code stack} as a furnace makes it - the same amount of what one makes - or as it is. */
    ItemStack smelt(ItemStack stack) {
        Optional<ItemStack> result = smelted.computeIfAbsent(stack.getType(), material -> {
            ItemStack probe = new ItemStack(material);
            for (Iterator<Recipe> it = plugin.getServer().recipeIterator(); it.hasNext(); ) {
                if (it.next() instanceof FurnaceRecipe furnace && ((CookingRecipe<?>) furnace).getInputChoice().test(probe)) {
                    return Optional.of(furnace.getResult());
                }
            }
            return Optional.empty();
        });
        if (result.isEmpty()) {
            return stack;
        }
        ItemStack out = result.get().clone();
        out.setAmount(Math.min(out.getMaxStackSize(), Math.max(1, stack.getAmount() * out.getAmount())));
        return out;
    }

    // ---------------------------------------------------------------- dying and leaving

    /** Soulbound items stay with the player. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!mechanics.any() || event.getKeepInventory()) {
            return;
        }
        for (Iterator<ItemStack> it = event.getDrops().iterator(); it.hasNext(); ) {
            ItemStack drop = it.next();
            if (mechanics.of(drop).map(item -> item.passive().soulbound()).orElse(false)) {
                it.remove();
                event.getItemsToKeep().add(drop);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastClick.keySet().removeIf(key -> key.startsWith(event.getPlayer().getUniqueId() + "|"));
        mechanics.forget(event.getPlayer());
    }
}
