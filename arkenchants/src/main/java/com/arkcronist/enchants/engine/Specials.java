package com.arkcronist.enchants.engine;

import com.arkcronist.enchants.item.Keys;
import com.arkcronist.enchants.text.Colors;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/** The ArkEnchants-only effects: clones, dash, ground slam, chain lightning, thrown weapons, homing arrows... */
final class Specials {

    static final Set<String> NAMES = Set.of("CLONES", "DASH", "SLAM", "CHAIN_LIGHTNING", "MARK", "THROW_WEAPON", "HOMING",
            "MULTISHOT", "REFLECT", "BLINK", "DOUBLE_JUMP", "DROP_ITEM", "DOUBLE_CATCH", "REVEAL", "ACTIONBAR");

    private final Engine engine;

    Specials(Engine engine) {
        this.engine = engine;
    }

    boolean apply(String name, List<String> a, Targets t, Context c) {
        switch (name) {
            case "CLONES" -> clones(c, first(t, c), (int) d(a, 0, 1), d(a, 1, 8), d(a, 2, 40));
            case "DASH" -> dash(c, d(a, 0, 6), d(a, 1, 6));
            case "SLAM" -> slam(c, t, d(a, 0, 4), d(a, 1, 6), d(a, 2, 0.6));
            case "CHAIN_LIGHTNING" -> chain(c, first(t, c), (int) d(a, 0, 3), d(a, 1, 4));
            case "MARK" -> t.entities.forEach(e -> {
                long ms = (long) (d(a, 0, 5) * 1000);
                e.getPersistentDataContainer().set(Keys.MARK, PersistentDataType.LONG, System.currentTimeMillis() + ms);
                e.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, (int) (ms / 50), 0, false, false, false));
                e.getWorld().spawnParticle(Particle.DUST, e.getLocation().add(0, e.getHeight() + 0.4, 0), 12, 0.2, 0.1, 0.2, 0,
                        new Particle.DustOptions(Color.fromRGB(200, 0, 0), 1.4f));
            });
            case "THROW_WEAPON" -> throwWeapon(c, d(a, 0, 6), d(a, 1, 12));
            case "HOMING" -> homing(c, d(a, 0, 0.3));
            case "MULTISHOT" -> multishot(c, (int) d(a, 0, 2), d(a, 1, 10));
            case "REFLECT" -> {
                LivingEntity from = c.attacker;
                if (from != null && from != c.holder && c.baseDamage > 0) {
                    double back = c.baseDamage * d(a, 0, 20) / 100.0;
                    engine.quietly(() -> from.damage(back, c.holder));
                    from.getWorld().spawnParticle(Particle.ENCHANTED_HIT, from.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.1);
                }
            }
            case "BLINK" -> blink(c, d(a, 0, 8));
            case "DOUBLE_JUMP" -> {
                Player p = c.holder;
                p.getPersistentDataContainer().set(Keys.JUMP, PersistentDataType.DOUBLE, d(a, 0, 1));
                p.getPersistentDataContainer().set(Keys.JUMP_TIME, PersistentDataType.LONG, System.currentTimeMillis());
                if (((Entity) p).isOnGround() && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)) {
                    p.setAllowFlight(true);
                }
            }
            case "DROP_ITEM" -> {
                Material m = Material.matchMaterial(arg(a, 0, "DIAMOND"));
                if (m != null && m.isItem()) {
                    Location l = !t.blocks.isEmpty() ? t.blocks.get(0).getLocation().add(0.5, 0.5, 0.5)
                            : !t.entities.isEmpty() ? t.entities.get(0).getLocation() : c.holder.getLocation();
                    l.getWorld().dropItemNaturally(l, new ItemStack(m, Math.max(1, (int) d(a, 1, 1))));
                    l.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, l, 20, 0.3, 0.3, 0.3, 0.2);
                }
            }
            case "DOUBLE_CATCH" -> {
                if (c.caught instanceof Item it) {
                    ItemStack s = it.getItemStack();
                    s.setAmount(Math.min(s.getMaxStackSize(), (int) Math.round(s.getAmount() * d(a, 0, 2))));
                    it.setItemStack(s);
                }
            }
            case "REVEAL" -> {
                double r = d(a, 0, 12);
                int ticks = (int) (d(a, 1, 3) * 20);
                for (Entity e : c.holder.getNearbyEntities(r, r, r)) {
                    if (e instanceof LivingEntity le && e instanceof Enemy) {
                        le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, ticks, 0, false, false, false));
                    }
                }
            }
            case "ACTIONBAR" -> {
                String msg = String.join(":", a);
                t.entities.forEach(e -> {
                    if (e instanceof Player p) {
                        p.sendActionBar(Colors.of(msg));
                    }
                });
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static String arg(List<String> a, int i, String def) {
        return i < a.size() && !a.get(i).isEmpty() ? a.get(i) : def;
    }

    private static double d(List<String> a, int i, double def) {
        return Engine.num(arg(a, i, String.valueOf(def)), def);
    }

    private static LivingEntity first(Targets t, Context c) {
        for (LivingEntity e : t.entities) {
            if (e != c.holder) {
                return e;
            }
        }
        return c.other();
    }

    /** Anything an area effect may hurt: not the holder, not their clones or guards, not armor stands. */
    private boolean foe(Entity e, Player holder) {
        if (!(e instanceof LivingEntity) || e == holder || e.isDead() || e instanceof ArmorStand) {
            return false;
        }
        String owner = e.getPersistentDataContainer().get(Keys.GUARD, PersistentDataType.STRING);
        return owner == null || !owner.equals(holder.getUniqueId().toString());
    }

    // ------------------------------------------------------------------ shadow clones
    private void clones(Context c, LivingEntity enemy, int amount, double seconds, double percent) {
        Player owner = c.holder;
        int room = Math.max(0, Math.min(5, amount) - engine.clones.count(owner));
        if (room == 0 || enemy == null) {
            if (enemy != null) {
                engine.clones.focus(owner, enemy);
            }
            return;
        }
        AttributeInstance atk = owner.getAttribute(Attribute.ATTACK_DAMAGE);
        double dmg = Math.max(1, (atk == null ? 1 : atk.getValue()) * percent / 100.0);
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(owner);
        head.setItemMeta(sm);
        EntityEquipment eq = owner.getEquipment();
        for (int i = 0; i < room; i++) {
            double ang = (Math.PI * 2 / room) * i + ThreadLocalRandom.current().nextDouble(0.5);
            Location l = owner.getLocation().add(Math.cos(ang) * 1.6, 0, Math.sin(ang) * 1.6);
            if (!l.getBlock().isPassable()) {
                l = owner.getLocation();
            }
            Zombie z = owner.getWorld().spawn(l, Zombie.class, zo -> {
                zo.setAdult();
                zo.setShouldBurnInDay(false);
                zo.setSilent(true);
                zo.setCanPickupItems(false);
                zo.setPersistent(false);
                zo.setRemoveWhenFarAway(false);
                zo.customName(Colors.of("&8Sombra de &7" + owner.getName()));
                zo.setCustomNameVisible(true);
                zo.getPersistentDataContainer().set(Keys.GUARD, PersistentDataType.STRING, owner.getUniqueId().toString());
                zo.getPersistentDataContainer().set(Keys.CLONE, PersistentDataType.BYTE, (byte) 1);
                EntityEquipment ze = zo.getEquipment();
                ze.setHelmet(head.clone());
                ze.setChestplate(copy(eq.getChestplate()));
                ze.setLeggings(copy(eq.getLeggings()));
                ze.setBoots(copy(eq.getBoots()));
                ze.setItemInMainHand(copy(eq.getItemInMainHand()));
                ze.setHelmetDropChance(0);
                ze.setChestplateDropChance(0);
                ze.setLeggingsDropChance(0);
                ze.setBootsDropChance(0);
                ze.setItemInMainHandDropChance(0);
                set(zo, Attribute.ATTACK_DAMAGE, dmg);
                set(zo, Attribute.MOVEMENT_SPEED, 0.34);
                set(zo, Attribute.FOLLOW_RANGE, 32);
                set(zo, Attribute.MAX_HEALTH, 20);
                zo.setHealth(20);
                zo.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            });
            engine.clones.add(owner, z);
            owner.getWorld().spawnParticle(Particle.LARGE_SMOKE, l.clone().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.02);
            new BukkitRunnable() {
                int ticks;

                @Override
                public void run() {
                    ticks += 5;
                    if (!z.isValid() || !owner.isOnline() || ticks >= seconds * 20) {
                        if (z.isValid()) {
                            z.getWorld().spawnParticle(Particle.LARGE_SMOKE, z.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.02);
                            z.remove();
                        }
                        cancel();
                        return;
                    }
                    z.getWorld().spawnParticle(Particle.SQUID_INK, z.getLocation().add(0, 1, 0), 3, 0.2, 0.4, 0.2, 0.01);
                }
            }.runTaskTimer(engine.plugin, 5, 5);
        }
        owner.getWorld().playSound(owner.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.2f, 0.8f);
        engine.clones.focus(owner, enemy);
    }

    private static ItemStack copy(ItemStack i) {
        return i == null ? null : i.clone();
    }

    private static void set(LivingEntity e, Attribute a, double v) {
        AttributeInstance in = e.getAttribute(a);
        if (in != null) {
            in.setBaseValue(v);
        }
    }

    // ------------------------------------------------------------------ dash
    private void dash(Context c, double distance, double damage) {
        Player p = c.holder;
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1e-4) {
            return;
        }
        dir.normalize();
        p.setVelocity(dir.clone().multiply(distance / 5.0).setY(0.25));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1, 1.2f);
        Set<Entity> hit = new HashSet<>();
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!p.isOnline() || ticks++ > 10) {
                    cancel();
                    return;
                }
                p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, p.getLocation().add(0, 1, 0), 2, 0.3, 0.3, 0.3, 0);
                p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0, 1, 0), 8, 0.3, 0.4, 0.3, 0.05);
                for (Entity e : p.getNearbyEntities(1.6, 1.6, 1.6)) {
                    if (foe(e, p) && hit.add(e)) {
                        LivingEntity le = (LivingEntity) e;
                        le.setNoDamageTicks(0);
                        engine.quietly(() -> le.damage(damage, p));
                    }
                }
            }
        }.runTaskTimer(engine.plugin, 1, 1);
    }

    // ------------------------------------------------------------------ ground slam
    private void slam(Context c, Targets t, double radius, double damage, double knock) {
        Player p = c.holder;
        Location at = p.getLocation();
        Block ground = at.getBlock().getRelative(BlockFace.DOWN);
        Material look = ground.getType().isSolid() ? ground.getType() : Material.DIRT;
        for (int i = 0; i < 24; i++) {
            double ang = Math.PI * 2 * i / 24;
            Location l = at.clone().add(Math.cos(ang) * radius * 0.8, 0.2, Math.sin(ang) * radius * 0.8);
            p.getWorld().spawnParticle(Particle.BLOCK, l, 6, 0.2, 0.1, 0.2, 0, look.createBlockData());
        }
        p.getWorld().spawnParticle(Particle.EXPLOSION, at, 2, 0.5, 0.1, 0.5, 0);
        p.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1, 0.6f);
        for (Entity e : p.getNearbyEntities(radius, radius / 2 + 1, radius)) {
            if (!foe(e, p)) {
                continue;
            }
            LivingEntity le = (LivingEntity) e;
            le.setNoDamageTicks(0);
            engine.quietly(() -> le.damage(damage, p));
            Vector out = le.getLocation().toVector().subtract(at.toVector()).setY(0);
            out = out.lengthSquared() < 1e-4 ? new Vector() : out.normalize().multiply(0.45);
            le.setVelocity(out.setY(knock));
        }
    }

    // ------------------------------------------------------------------ chain lightning
    private void chain(Context c, LivingEntity start, int jumps, double damage) {
        if (start == null) {
            return;
        }
        Player p = c.holder;
        Set<Entity> hit = new HashSet<>();
        new BukkitRunnable() {
            LivingEntity current = start;
            Location from = p.getEyeLocation();
            int n;

            @Override
            public void run() {
                if (current == null || n++ >= jumps || !p.isOnline()) {
                    cancel();
                    return;
                }
                LivingEntity cur = current;
                hit.add(cur);
                Location to = cur.getLocation().add(0, cur.getHeight() / 2, 0);
                line(from, to);
                cur.setNoDamageTicks(0);
                engine.quietly(() -> cur.damage(damage, p));
                cur.getWorld().playSound(to, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.6f, 1.6f);
                from = to;
                LivingEntity next = null;
                double best = 36;
                for (Entity e : cur.getNearbyEntities(6, 4, 6)) {
                    if (foe(e, p) && !hit.contains(e)) {
                        double dd = e.getLocation().distanceSquared(cur.getLocation());
                        if (dd < best) {
                            best = dd;
                            next = (LivingEntity) e;
                        }
                    }
                }
                current = next;
            }
        }.runTaskTimer(engine.plugin, 0, 3);
    }

    private static void line(Location a, Location b) {
        Vector step = b.toVector().subtract(a.toVector());
        double len = step.length();
        if (len < 0.1) {
            return;
        }
        step.normalize().multiply(0.35);
        Location l = a.clone();
        for (double dd = 0; dd < len; dd += 0.35) {
            l.add(step);
            a.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, l, 1, 0.05, 0.05, 0.05, 0);
        }
    }

    // ------------------------------------------------------------------ thrown weapon (boomerang)
    private void throwWeapon(Context c, double damage, double distance) {
        Player p = c.holder;
        ItemStack look = c.item == null ? new ItemStack(Material.IRON_AXE) : c.item.clone();
        Location start = p.getEyeLocation().add(p.getLocation().getDirection().multiply(0.6));
        Vector dir = p.getLocation().getDirection().normalize();
        ItemDisplay d = p.getWorld().spawn(start, ItemDisplay.class, it -> {
            it.setItemStack(look);
            it.setTeleportDuration(1);
            it.setPersistent(false);
        });
        p.getWorld().playSound(start, Sound.ITEM_TRIDENT_THROW, 1, 0.8f);
        Set<Entity> out = new HashSet<>();
        Set<Entity> back = new HashSet<>();
        new BukkitRunnable() {
            int ticks;
            double traveled;
            boolean returning;
            float spin;

            @Override
            public void run() {
                ticks++;
                if (!d.isValid() || !p.isOnline() || ticks > 100) {
                    d.remove();
                    cancel();
                    return;
                }
                Location l = d.getLocation();
                Vector move;
                if (!returning) {
                    move = dir.clone().multiply(0.9);
                    traveled += 0.9;
                    Location next = l.clone().add(move);
                    if (traveled >= distance || !next.getBlock().isPassable()) {
                        returning = true;
                    }
                } else {
                    Vector to = p.getEyeLocation().toVector().subtract(l.toVector());
                    if (to.length() < 1.2) {
                        d.remove();
                        p.getWorld().playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 1, 1.4f);
                        cancel();
                        return;
                    }
                    move = to.normalize().multiply(1.1);
                }
                spin += 0.8f;
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(spin, 0, 0, 1),
                        new Vector3f(1.2f, 1.2f, 1.2f), new AxisAngle4f()));
                d.teleport(l.add(move));
                d.getWorld().spawnParticle(Particle.CRIT, d.getLocation(), 2, 0.1, 0.1, 0.1, 0);
                Set<Entity> hit = returning ? back : out;
                for (Entity e : d.getNearbyEntities(1.3, 1.3, 1.3)) {
                    if (foe(e, p) && hit.add(e)) {
                        LivingEntity le = (LivingEntity) e;
                        le.setNoDamageTicks(0);
                        engine.quietly(() -> le.damage(damage, p));
                    }
                }
            }
        }.runTaskTimer(engine.plugin, 1, 1);
    }

    // ------------------------------------------------------------------ arrows
    private void homing(Context c, double strength) {
        if (!(c.projectile instanceof Projectile pr)) {
            return;
        }
        Player p = c.holder;
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!pr.isValid() || ticks++ > 80 || pr instanceof AbstractArrow ar && ar.isInBlock()) {
                    cancel();
                    return;
                }
                Vector v = pr.getVelocity();
                double speed = v.length();
                if (speed < 0.2) {
                    return;
                }
                LivingEntity best = null;
                double bestScore = Double.MAX_VALUE;
                for (Entity e : pr.getNearbyEntities(20, 12, 20)) {
                    if (!foe(e, p)) {
                        continue;
                    }
                    Vector to = ((LivingEntity) e).getEyeLocation().toVector().subtract(pr.getLocation().toVector());
                    double angle = to.angle(v);
                    if (angle > Math.toRadians(70)) {
                        continue;
                    }
                    double score = to.length() * (1 + angle);
                    if (score < bestScore) {
                        bestScore = score;
                        best = (LivingEntity) e;
                    }
                }
                if (best != null) {
                    Vector to = best.getLocation().add(0, best.getHeight() * 0.6, 0).toVector()
                            .subtract(pr.getLocation().toVector()).normalize();
                    Vector nv = v.clone().normalize().multiply(1 - strength).add(to.multiply(strength)).normalize().multiply(speed);
                    pr.setVelocity(nv);
                    pr.getWorld().spawnParticle(Particle.END_ROD, pr.getLocation(), 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(engine.plugin, 2, 1);
    }

    private void multishot(Context c, int extra, double spread) {
        Player p = c.holder;
        double speed = c.projectile == null ? 3 : c.projectile.getVelocity().length();
        boolean crit = c.projectile instanceof AbstractArrow a && a.isCritical();
        List<Arrow> arrows = new ArrayList<>();
        for (int i = 1; i <= extra; i++) {
            double yaw = Math.toRadians(spread * ((i + 1) / 2) * (i % 2 == 0 ? 1 : -1));
            Vector dir = p.getLocation().getDirection().rotateAroundY(yaw).normalize().multiply(speed);
            Arrow ar = p.launchProjectile(Arrow.class, dir);
            ar.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);
            ar.setCritical(crit);
            arrows.add(ar);
        }
        Bukkit.getScheduler().runTaskLater(engine.plugin, () -> arrows.forEach(ar -> {
            if (ar.isValid()) {
                ar.remove();
            }
        }), 200);
    }

    // ------------------------------------------------------------------ escape teleport
    private void blink(Context c, double distance) {
        Player p = c.holder;
        Location base = p.getLocation();
        Vector away = c.attacker != null && c.attacker != p
                ? base.toVector().subtract(c.attacker.getLocation().toVector()).setY(0) : base.getDirection().setY(0).multiply(-1);
        if (away.lengthSquared() < 1e-4) {
            away = new Vector(1, 0, 0);
        }
        away.normalize();
        for (int tries = 0; tries < 12; tries++) {
            double ang = Math.toRadians(ThreadLocalRandom.current().nextDouble(-70, 70));
            Location target = base.clone().add(away.clone().rotateAroundY(ang).multiply(distance));
            Location safe = ground(target);
            if (safe != null) {
                safe.setYaw(base.getYaw());
                safe.setPitch(base.getPitch());
                p.getWorld().spawnParticle(Particle.PORTAL, base.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.5);
                p.teleport(safe);
                p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, safe.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.1);
                p.getWorld().playSound(safe, Sound.ENTITY_ENDERMAN_TELEPORT, 1, 1);
                return;
            }
        }
    }

    private static Location ground(Location l) {
        Block b = l.getBlock();
        for (int dy = 3; dy >= -5; dy--) {
            Block feet = b.getRelative(0, dy, 0);
            if (feet.isPassable() && !feet.isLiquid() && feet.getRelative(BlockFace.UP).isPassable()
                    && feet.getRelative(BlockFace.DOWN).getType().isSolid()) {
                return feet.getLocation().add(0.5, 0, 0.5);
            }
        }
        return null;
    }
}
