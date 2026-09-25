package com.arkcronist.enchants.listener;

import com.arkcronist.enchants.engine.Context;
import com.arkcronist.enchants.engine.Engine;
import com.arkcronist.enchants.model.Trigger;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

/** MINING: fires on break; MORE_DROPS, SMELT, TP_DROPS and SET_BLOCK change the drops that follow. */
public final class BlockListener implements Listener {

    private final Engine engine;
    private final Map<Location, Context> pending = new HashMap<>();
    private Map<Material, ItemStack> smelting;

    public BlockListener(Engine engine) {
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        engine.placed.add(e.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (engine.busy()) {
            return;
        }
        Player p = e.getPlayer();
        Context c = new Context(Trigger.MINING, p);
        c.attacker = p;
        c.block = e.getBlock();
        c.event = e;
        c.exp = e.getExpToDrop();
        engine.fire(Trigger.MINING, c, Gear.hand(p));
        if (c.moreDrops > 0 || c.smelt || c.tpDrops || c.replaceDrop != null) {
            Location key = e.getBlock().getLocation();
            pending.put(key, c);
            Bukkit.getScheduler().runTask(engine.plugin, () -> pending.remove(key));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBroken(BlockBreakEvent e) {
        engine.placed.remove(e.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent e) {
        Context c = pending.remove(e.getBlock().getLocation());
        if (c == null) {
            return;
        }
        Player p = e.getPlayer();
        for (Iterator<Item> it = e.getItems().iterator(); it.hasNext(); ) {
            Item drop = it.next();
            ItemStack s = drop.getItemStack();
            if (c.replaceDrop != null && c.replaceDrop.isItem()) {
                s = new ItemStack(c.replaceDrop, s.getAmount());
            }
            if (c.smelt) {
                ItemStack out = smelt(s.getType());
                if (out != null) {
                    s = new ItemStack(out.getType(), s.getAmount() * out.getAmount());
                }
            }
            if (c.moreDrops > 0) {
                s.setAmount(Math.min(s.getMaxStackSize(), s.getAmount() * (1 + c.moreDrops)));
            }
            if (c.tpDrops) {
                p.getInventory().addItem(s).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
                it.remove();
            } else {
                drop.setItemStack(s);
            }
        }
    }

    private ItemStack smelt(Material input) {
        if (smelting == null) {
            smelting = new HashMap<>();
            for (Iterator<Recipe> it = Bukkit.recipeIterator(); it.hasNext(); ) {
                if (it.next() instanceof FurnaceRecipe r) {
                    for (Material m : Material.values()) {
                        if (m.isItem() && !smelting.containsKey(m) && r.getInputChoice().test(new ItemStack(m))) {
                            smelting.put(m, r.getResult());
                        }
                    }
                }
            }
        }
        return smelting.get(input);
    }
}
