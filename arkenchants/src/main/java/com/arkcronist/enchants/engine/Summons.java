package com.arkcronist.enchants.engine;

import com.arkcronist.enchants.Settings;
import com.arkcronist.enchants.item.Keys;
import com.arkcronist.enchants.text.Colors;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Creatures called by SUMMON: MythicMobs mobs (or a vanilla stand-in) that fight for the player who called them.
 * They follow their owner, go after whatever the owner hits or whatever hits the owner, never turn on the owner,
 * leave no drops, and vanish when their time runs out.
 */
public final class Summons {

    private record Ally(Entity entity, long until) {
    }

    private final Engine engine;
    private final Map<UUID, List<Ally>> byOwner = new HashMap<>();
    private final Map<UUID, UUID> focus = new HashMap<>();
    private BukkitTask task;
    private Object mobManager;
    private Method spawnMob;
    private Method getMythicMob;
    private boolean mythicChecked;

    Summons(Engine engine) {
        this.engine = engine;
    }

    /** Calls {@code amount} creatures of this kind for {@code seconds}; returns how many came. */
    public int summon(Player owner, String what, double seconds, int amount, LivingEntity enemy) {
        Settings s = engine.settings();
        Settings.Creature cr = s.creatures.get(what.toLowerCase(Locale.ROOT));
        List<String> mythic = cr != null ? cr.mythic() : List.of(what);
        EntityType vanilla = cr != null ? cr.vanilla() : Lookups.entity(what);
        String name = cr != null ? cr.name() : what;
        List<Ally> mine = of(owner);
        int made = 0;
        for (int i = 0; i < Math.max(1, Math.min(10, amount)); i++) {
            while (mine.size() >= s.summonMax) {
                vanish(mine.remove(0).entity());
            }
            Location at = spot(owner, i, amount);
            Entity e = spawnMythic(mythic, at);
            if (e == null && vanilla != null && vanilla.isAlive() && vanilla.isSpawnable()) {
                e = owner.getWorld().spawnEntity(at, vanilla);
                if (e instanceof Tameable t) {
                    t.setTamed(true);
                    t.setOwner(owner);
                }
                if (e instanceof IronGolem g) {
                    g.setPlayerCreated(true);
                }
                e.customName(Colors.of("&b" + name + " &7de &f" + owner.getName()));
                e.setCustomNameVisible(true);
            }
            if (e == null) {
                continue;
            }
            e.getPersistentDataContainer().set(Keys.GUARD, PersistentDataType.STRING, owner.getUniqueId().toString());
            e.getPersistentDataContainer().set(Keys.SUMMON, PersistentDataType.BYTE, (byte) 1);
            e.setPersistent(false);
            if (e instanceof Mob m) {
                m.setRemoveWhenFarAway(false);
                m.setCanPickupItems(false);
                if (enemy != null && enemy.isValid() && enemy != owner && !ally(owner.getUniqueId(), enemy)) {
                    m.setTarget(enemy);
                }
            }
            mine.add(new Ally(e, System.currentTimeMillis() + (long) (Math.max(1, seconds) * 1000)));
            at.getWorld().spawnParticle(Particle.END_ROD, at.clone().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.05);
            at.getWorld().spawnParticle(Particle.PORTAL, at.clone().add(0, 0.5, 0), 40, 0.5, 0.5, 0.5, 0.3);
            made++;
        }
        if (made > 0) {
            owner.getWorld().playSound(owner.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1f, 1.2f);
            if (enemy != null && enemy != owner) {
                focus(owner, enemy);
            }
            start();
        }
        return made;
    }

    private static Location spot(Player owner, int i, int amount) {
        double ang = (Math.PI * 2 / Math.max(1, amount)) * i + ThreadLocalRandom.current().nextDouble(0.6);
        Location l = owner.getLocation().add(Math.cos(ang) * 2, 0, Math.sin(ang) * 2);
        return l.getBlock().isPassable() && l.clone().add(0, 1, 0).getBlock().isPassable() ? l : owner.getLocation();
    }

    // ------------------------------------------------------------------ MythicMobs, through reflection
    private Entity spawnMythic(List<String> ids, Location at) {
        if (ids.isEmpty() || !mythic()) {
            return null;
        }
        for (String id : ids) {
            try {
                Object mob = getMythicMob.invoke(mobManager, id);
                if (mob instanceof Optional<?> o && o.isEmpty()) {
                    continue;
                }
                Object active = spawnMob.invoke(mobManager, id, at);
                if (active == null) {
                    continue;
                }
                Object abstractEntity = active.getClass().getMethod("getEntity").invoke(active);
                Object bukkit = abstractEntity.getClass().getMethod("getBukkitEntity").invoke(abstractEntity);
                if (bukkit instanceof Entity e) {
                    return e;
                }
            } catch (ReflectiveOperationException | RuntimeException ex) {
                engine.warnOnce("summon " + id, "Could not summon MythicMobs mob " + id + ": " + ex);
            }
        }
        return null;
    }

    private boolean mythic() {
        if (mythicChecked) {
            return mobManager != null;
        }
        mythicChecked = true;
        Plugin mm = Bukkit.getPluginManager().getPlugin("MythicMobs");
        if (mm == null || !mm.isEnabled()) {
            return false;
        }
        try {
            ClassLoader cl = mm.getClass().getClassLoader();
            Class<?> mb = Class.forName("io.lumine.mythic.bukkit.MythicBukkit", true, cl);
            Object inst = mb.getMethod("inst").invoke(null);
            Object mgr = inst.getClass().getMethod("getMobManager").invoke(inst);
            spawnMob = mgr.getClass().getMethod("spawnMob", String.class, Location.class);
            getMythicMob = mgr.getClass().getMethod("getMythicMob", String.class);
            mobManager = mgr;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            engine.warnOnce("summon-mm", "MythicMobs is installed but its API could not be reached (" + ex
                    + "); summons use their vanilla creature.");
        }
        return mobManager != null;
    }

    /** Forget the cached MythicMobs hook, e.g. after a reload. */
    public void rehook() {
        mythicChecked = false;
        mobManager = null;
    }

    // ------------------------------------------------------------------ who is whose
    private List<Ally> of(Player owner) {
        List<Ally> list = byOwner.computeIfAbsent(owner.getUniqueId(), k -> new ArrayList<>());
        list.removeIf(a -> !a.entity().isValid());
        return list;
    }

    public int count(Player owner) {
        List<Ally> list = byOwner.get(owner.getUniqueId());
        if (list == null) {
            return 0;
        }
        list.removeIf(a -> !a.entity().isValid());
        return list.size();
    }

    public static boolean isSummon(Entity e) {
        return e != null && e.getPersistentDataContainer().has(Keys.SUMMON);
    }

    /** The player a guard, clone or summon belongs to, or null. */
    public static String owner(Entity e) {
        return e == null || !e.getPersistentDataContainer().has(Keys.GUARD, PersistentDataType.STRING) ? null
                : e.getPersistentDataContainer().get(Keys.GUARD, PersistentDataType.STRING);
    }

    public static boolean ally(UUID owner, Entity e) {
        String o = owner(e);
        return o != null && o.equals(owner.toString());
    }

    /** Sends every ally of the owner after this enemy. */
    public void focus(Player owner, LivingEntity enemy) {
        if (enemy == null || !enemy.isValid() || enemy == owner || ally(owner.getUniqueId(), enemy)) {
            return;
        }
        List<Ally> list = byOwner.get(owner.getUniqueId());
        if (list == null || list.isEmpty()) {
            return;
        }
        focus.put(owner.getUniqueId(), enemy.getUniqueId());
        for (Ally a : list) {
            if (a.entity() instanceof Mob m && m.isValid()) {
                m.setTarget(enemy);
            }
        }
    }

    /** Whether a summon may go after this target: never its owner or fellow allies; players only when fighting the owner. */
    public boolean allowed(UUID owner, LivingEntity target) {
        if (target == null) {
            return true;
        }
        if (target.getUniqueId().equals(owner) || ally(owner, target)) {
            return false;
        }
        UUID f = focus.get(owner);
        if (f != null && f.equals(target.getUniqueId())) {
            return true;
        }
        return !(target instanceof Player) && owner(target) == null;
    }

    public void removeAll(Player owner) {
        List<Ally> list = byOwner.remove(owner.getUniqueId());
        if (list != null) {
            list.forEach(a -> vanish(a.entity()));
        }
        focus.remove(owner.getUniqueId());
    }

    public void removeEverything() {
        byOwner.values().forEach(l -> l.forEach(a -> a.entity().remove()));
        byOwner.clear();
        focus.clear();
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public static void vanish(Entity e) {
        if (e == null || !e.isValid()) {
            return;
        }
        e.getWorld().spawnParticle(Particle.END_ROD, e.getLocation().add(0, 1, 0), 25, 0.4, 0.8, 0.4, 0.05);
        e.getWorld().spawnParticle(Particle.CLOUD, e.getLocation().add(0, 0.5, 0), 15, 0.4, 0.4, 0.4, 0.02);
        e.remove();
    }

    // ------------------------------------------------------------------ keeping them in line
    private void start() {
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(engine.plugin, this::tick, 10, 10);
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Settings s = engine.settings();
        for (Iterator<Map.Entry<UUID, List<Ally>>> it = byOwner.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, List<Ally>> en = it.next();
            Player owner = Bukkit.getPlayer(en.getKey());
            List<Ally> list = en.getValue();
            if (owner == null || !owner.isOnline() || owner.isDead()) {
                list.forEach(a -> vanish(a.entity()));
                it.remove();
                focus.remove(en.getKey());
                continue;
            }
            LivingEntity foe = foe(owner);
            for (Iterator<Ally> ai = list.iterator(); ai.hasNext(); ) {
                Ally a = ai.next();
                Entity e = a.entity();
                if (!e.isValid() || now >= a.until() || e.getWorld() != owner.getWorld()) {
                    vanish(e);
                    ai.remove();
                    continue;
                }
                double dist = e.getLocation().distanceSquared(owner.getLocation());
                if (dist > s.summonLeash * s.summonLeash) {
                    e.teleport(spot(owner, 0, 1));
                    if (e instanceof Mob m) {
                        m.setTarget(null);
                    }
                    continue;
                }
                if (!(e instanceof Mob m)) {
                    continue;
                }
                LivingEntity t = m.getTarget();
                if (t != null && (!t.isValid() || t.isDead() || !allowed(owner.getUniqueId(), t))) {
                    m.setTarget(null);
                    t = null;
                }
                if (t == null) {
                    LivingEntity next = foe != null ? foe : nearestEnemy(owner, e, s.summonAggro);
                    if (next != null) {
                        m.setTarget(next);
                    } else if (dist > 36) {
                        m.getPathfinder().moveTo(owner.getLocation(), 1.2);
                    }
                }
            }
            if (list.isEmpty()) {
                it.remove();
                focus.remove(en.getKey());
            }
        }
        if (byOwner.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    private LivingEntity foe(Player owner) {
        UUID f = focus.get(owner.getUniqueId());
        if (f == null) {
            return null;
        }
        Entity e = Bukkit.getEntity(f);
        if (e instanceof LivingEntity le && le.isValid() && !le.isDead() && le.getWorld() == owner.getWorld()
                && le.getLocation().distanceSquared(owner.getLocation()) < 32 * 32) {
            return le;
        }
        focus.remove(owner.getUniqueId());
        return null;
    }

    private LivingEntity nearestEnemy(Player owner, Entity from, double r) {
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : owner.getNearbyEntities(r, r / 2, r)) {
            if (!(e instanceof LivingEntity le) || !(e instanceof Enemy) || e.isDead() || owner(e) != null
                    || le.isInvulnerable() || e instanceof Mob mob && !mob.hasAI() || e.hasMetadata("NPC")) {
                continue;
            }
            double d = e.getLocation().distanceSquared(from.getLocation());
            if (d < bestD) {
                bestD = d;
                best = le;
            }
        }
        return best;
    }
}
