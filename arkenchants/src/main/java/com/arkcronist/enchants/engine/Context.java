package com.arkcronist.enchants.engine;

import com.arkcronist.enchants.model.Trigger;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

/**
 * Everything an enchantment can look at or change when it fires. Roles follow AdvancedEnchantments: on
 * ATTACK the holder is the attacker; on DEFENSE the holder is the victim.
 */
public final class Context {

    public final Trigger trigger;
    public final Player holder;
    public LivingEntity attacker;
    public LivingEntity victim;
    public Event event;
    public DamageMods damage;
    public double baseDamage;
    public Block block;
    public ItemStack item;
    public String enchantId;
    public int level;
    public boolean headshot;
    public boolean critical;
    public int exp;
    public Entity caught;
    public Entity hook;
    public Entity projectile;
    // mining / kill results picked up by the listeners after the effects ran
    public int moreDrops;
    public boolean smelt;
    public boolean tpDrops;
    public Material replaceDrop;
    public boolean revive;
    public final java.util.List<ItemStack> keep = new java.util.ArrayList<>();

    public Context(Trigger trigger, Player holder) {
        this.trigger = trigger;
        this.holder = holder;
    }

    /** A copy for one enchant, sharing the event and the collected results. */
    public Context forEnchant(ItemStack item, String id, int level) {
        Context c = new Context(trigger, holder);
        c.attacker = attacker;
        c.victim = victim;
        c.event = event;
        c.damage = damage;
        c.baseDamage = baseDamage;
        c.block = block;
        c.item = item;
        c.enchantId = id;
        c.level = level;
        c.headshot = headshot;
        c.critical = critical;
        c.exp = exp;
        c.caught = caught;
        c.hook = hook;
        c.projectile = projectile;
        c.parent = this;
        return c;
    }

    /** Results go back to the shared context the listener reads. */
    public Context root() {
        return parent == null ? this : parent.root();
    }

    private Context parent;

    public void cancel() {
        if (event instanceof Cancellable c) {
            c.setCancelled(true);
        }
    }

    /** The other entity in the fight (the one that is not the holder). */
    public LivingEntity other() {
        if (victim != null && victim != holder) {
            return victim;
        }
        return attacker != null && attacker != holder ? attacker : null;
    }
}
