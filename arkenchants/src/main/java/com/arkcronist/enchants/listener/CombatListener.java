package com.arkcronist.enchants.listener;

import com.arkcronist.enchants.engine.Context;
import com.arkcronist.enchants.engine.DamageMods;
import com.arkcronist.enchants.engine.Engine;
import com.arkcronist.enchants.item.Items;
import com.arkcronist.enchants.item.Keys;
import com.arkcronist.enchants.model.Trigger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Hits, shots, kills and deaths. */
public final class CombatListener implements Listener {

    private final Engine engine;
    /** The bow/crossbow/trident each flying projectile came from. */
    private final Map<UUID, ItemStack> launchers = new HashMap<>();
    /** Items KEEP_ON_DEATH saved, handed back on respawn. */
    private final Map<UUID, List<ItemStack>> kept = new HashMap<>();
    private final Map<UUID, List<ItemStack>> pendingKeep = new HashMap<>();

    public CombatListener(Engine engine) {
        this.engine = engine;
    }

    // ------------------------------------------------------------------ shots
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (e.getEntity() instanceof Player p && e.getBow() != null && !Items.enchants(e.getBow()).isEmpty()) {
            remember(e.getProjectile(), e.getBow().clone());
            Context c = new Context(Trigger.BOW_FIRE, p);
            c.attacker = p;
            c.event = e;
            c.projectile = e.getProjectile();
            engine.fire(Trigger.BOW_FIRE, c, List.of(e.getBow()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (e.getEntity() instanceof Trident t && t.getShooter() instanceof Player) {
            ItemStack item = t.getItemStack();
            if (!Items.enchants(item).isEmpty()) {
                remember(t, item.clone());
            }
        }
    }

    private void remember(Entity projectile, ItemStack item) {
        UUID id = projectile.getUniqueId();
        launchers.put(id, item);
        Bukkit.getScheduler().runTaskLater(engine.plugin, () -> launchers.remove(id), 20 * 30);
    }

    // ------------------------------------------------------------------ hits
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (engine.busy() || !(e.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        Entity damager = e.getDamager();
        // guards never hurt the player who summoned them
        String owner = damager.getPersistentDataContainer().get(Keys.GUARD, PersistentDataType.STRING);
        if (owner != null && owner.equals(victim.getUniqueId().toString())) {
            e.setCancelled(true);
            return;
        }
        // nobody hurts their own clones or guards
        String victimOwner = victim.getPersistentDataContainer().get(Keys.GUARD, PersistentDataType.STRING);
        Entity real = damager instanceof Projectile pr0 && pr0.getShooter() instanceof Entity sh ? sh : damager;
        if (victimOwner != null && victimOwner.equals(real.getUniqueId().toString())) {
            e.setCancelled(true);
            return;
        }
        // a clone's hit counts as its owner's: kills, drops and protection plugins see the player
        if (owner != null && damager.getPersistentDataContainer().has(Keys.CLONE)) {
            Player ownerP = Bukkit.getPlayer(UUID.fromString(owner));
            e.setCancelled(true);
            if (ownerP != null && ownerP.isOnline() && victimOwner == null) {
                double dmg = e.getDamage();
                victim.setNoDamageTicks(0);
                engine.quietly(() -> victim.damage(dmg, ownerP));
                victim.getWorld().spawnParticle(Particle.SQUID_INK, victim.getLocation().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0.02);
            }
            return;
        }
        Player shooter = null;
        ItemStack launcher = null;
        Player melee = null;
        if (damager instanceof Player p) {
            melee = p;
        } else if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) {
            shooter = p;
            launcher = launchers.get(pr.getUniqueId());
        }
        LivingEntity attacker = melee != null ? melee : shooter != null ? shooter
                : damager instanceof LivingEntity le ? le
                : damager instanceof Projectile pr && pr.getShooter() instanceof LivingEntity le2 ? le2 : null;
        DamageMods mods = new DamageMods();
        double base = e.getDamage();
        boolean projectile = damager instanceof Projectile;
        boolean headshot = projectile && damager.getLocation().getY() >= victim.getEyeLocation().getY() - 0.35;

        if (melee != null) {
            Context c = new Context(victim instanceof Player ? Trigger.ATTACK : Trigger.ATTACK_MOB, melee);
            c.attacker = melee;
            c.victim = victim;
            c.event = e;
            c.damage = mods;
            c.baseDamage = base;
            c.critical = e.isCritical();
            engine.combos.hit(melee);
            engine.fire(c.trigger, c, Gear.handAndArmor(melee));
            if (engine.charges.consume(melee, System.currentTimeMillis())) {
                Context ch = new Context(Trigger.CHARGED_ATTACK, melee);
                ch.attacker = melee;
                ch.victim = victim;
                ch.event = e;
                ch.damage = mods;
                ch.baseDamage = base;
                ch.critical = c.critical;
                engine.fire(Trigger.CHARGED_ATTACK, ch, Gear.hand(melee));
            }
            if (engine.clones.count(melee) > 0 && victim.getPersistentDataContainer().get(Keys.GUARD, PersistentDataType.STRING) == null) {
                engine.clones.focus(melee, victim);
            }
        } else if (shooter != null) {
            Context c = new Context(victim instanceof Player ? Trigger.SHOOT : Trigger.SHOOT_MOB, shooter);
            c.attacker = shooter;
            c.victim = victim;
            c.event = e;
            c.damage = mods;
            c.baseDamage = base;
            c.headshot = headshot;
            List<ItemStack> items = new ArrayList<>(Gear.handAndArmor(shooter));
            if (launcher != null) {
                items.add(0, launcher);
            }
            engine.fire(c.trigger, c, items);
        }
        if (victim instanceof Player defender && attacker != null && attacker != defender) {
            engine.combos.reset(defender);
            Trigger t = projectile ? Trigger.DEFENSE_PROJECTILE : attacker instanceof Player ? Trigger.DEFENSE : Trigger.DEFENSE_MOB;
            Context c = new Context(t, defender);
            c.attacker = attacker;
            c.victim = defender;
            c.event = e;
            c.damage = mods;
            c.baseDamage = base;
            c.headshot = headshot;
            engine.fire(t, c, Gear.all(defender));
            // arrows count as a hit from a player or mob too, like AdvancedEnchantments' DEFENSE;DEFENSE_PROJECTILE setups
            if (projectile) {
                Trigger also = attacker instanceof Player ? Trigger.DEFENSE : Trigger.DEFENSE_MOB;
                Context c2 = new Context(also, defender);
                c2.attacker = attacker;
                c2.victim = defender;
                c2.event = e;
                c2.damage = mods;
                c2.baseDamage = base;
                engine.fire(also, c2, Gear.all(defender));
            }
        }
        if (!e.isCancelled() && mods.changed()) {
            e.setDamage(mods.apply(base));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGuardTarget(EntityTargetLivingEntityEvent e) {
        String owner = e.getEntity().getPersistentDataContainer().get(Keys.GUARD, PersistentDataType.STRING);
        if (owner == null || e.getTarget() == null) {
            return;
        }
        if (owner.equals(e.getTarget().getUniqueId().toString())) {
            e.setCancelled(true);
        } else if (e.getEntity().getPersistentDataContainer().has(Keys.CLONE)
                && !engine.clones.allowed(UUID.fromString(owner), e.getTarget())) {
            // clones only go after what their owner is fighting
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ environment and lethal hits
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (engine.busy() || !(e.getEntity() instanceof Player p)) {
            return;
        }
        Trigger t = switch (e.getCause()) {
            case FALL -> Trigger.FALL_DAMAGE;
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> Trigger.FIRE;
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> Trigger.EXPLOSION;
            default -> null;
        };
        if (t != null) {
            Context c = new Context(t, p);
            c.victim = p;
            c.event = e;
            c.damage = new DamageMods();
            c.baseDamage = e.getDamage();
            engine.fire(t, c, Gear.all(p));
            if (!e.isCancelled() && c.damage.changed()) {
                e.setDamage(c.damage.apply(e.getDamage()));
            }
        }
    }

    /** DEATH fires on the hit that would kill, so REVIVE can still save the player. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent e) {
        if (engine.busy() || !(e.getEntity() instanceof Player p) || e.getFinalDamage() < p.getHealth()) {
            return;
        }
        if (p.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
                || p.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING) {
            return;
        }
        Context c = new Context(Trigger.DEATH, p);
        c.victim = p;
        c.event = e;
        if (e instanceof EntityDamageByEntityEvent be) {
            c.attacker = be.getDamager() instanceof LivingEntity le ? le
                    : be.getDamager() instanceof Projectile pr && pr.getShooter() instanceof LivingEntity le2 ? le2 : null;
        }
        engine.fire(Trigger.DEATH, c, Gear.all(p));
        if (c.revive) {
            e.setCancelled(true);
            var max = p.getAttribute(Attribute.MAX_HEALTH);
            p.setHealth(Math.max(1, (max == null ? 20 : max.getValue()) / 2));
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.5, 1, 0.5, 0.3);
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1, 1);
        } else if (!c.keep.isEmpty()) {
            pendingKeep.put(p.getUniqueId(), new ArrayList<>(c.keep));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player dead = e.getEntity();
        engine.clones.removeAll(dead);
        List<ItemStack> keep = pendingKeep.remove(dead.getUniqueId());
        if (keep != null && !e.getKeepInventory()) {
            List<ItemStack> saved = new ArrayList<>();
            for (ItemStack k : keep) {
                var it = e.getDrops().iterator();
                while (it.hasNext()) {
                    ItemStack d = it.next();
                    if (d.isSimilar(k)) {
                        saved.add(d);
                        it.remove();
                        break;
                    }
                }
            }
            if (!saved.isEmpty()) {
                kept.put(dead.getUniqueId(), saved);
            }
        }
        Player killer = dead.getKiller();
        if (killer != null && killer != dead) {
            Context c = new Context(Trigger.KILL_PLAYER, killer);
            c.attacker = killer;
            c.victim = dead;
            c.event = e;
            c.exp = e.getDroppedExp();
            engine.fire(Trigger.KILL_PLAYER, c, Gear.handAndArmor(killer));
            addSoul(killer);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        List<ItemStack> back = kept.remove(e.getPlayer().getUniqueId());
        if (back != null) {
            for (ItemStack i : back) {
                e.getPlayer().getInventory().addItem(i).values()
                        .forEach(rest -> e.getPlayer().getWorld().dropItemNaturally(e.getPlayer().getLocation(), rest));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMobDeath(EntityDeathEvent e) {
        if (e instanceof PlayerDeathEvent) {
            return;
        }
        Player killer = e.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        Context c = new Context(Trigger.KILL_MOB, killer);
        c.attacker = killer;
        c.victim = e.getEntity();
        c.event = e;
        c.exp = e.getDroppedExp();
        engine.fire(Trigger.KILL_MOB, c, Gear.handAndArmor(killer));
        if (c.moreDrops > 0) {
            List<ItemStack> extra = new ArrayList<>();
            for (ItemStack d : e.getDrops()) {
                for (int i = 0; i < c.moreDrops; i++) {
                    extra.add(d.clone());
                }
            }
            e.getDrops().addAll(extra);
        }
        if (c.tpDrops) {
            for (ItemStack d : e.getDrops()) {
                killer.getInventory().addItem(d).values()
                        .forEach(rest -> killer.getWorld().dropItemNaturally(killer.getLocation(), rest));
            }
            e.getDrops().clear();
        }
    }

    private static void addSoul(Player killer) {
        ItemStack hand = killer.getInventory().getItemInMainHand();
        if (Items.tracks(hand)) {
            Items.setSouls(hand, Items.souls(hand) + 1, false);
        }
    }
}
