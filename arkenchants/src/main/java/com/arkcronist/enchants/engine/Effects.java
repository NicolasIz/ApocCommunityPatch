package com.arkcronist.enchants.engine;

import com.arkcronist.enchants.item.Items;
import com.arkcronist.enchants.item.Keys;
import com.arkcronist.enchants.text.Colors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** The effects ArkEnchants understands, with AdvancedEnchantments' names and arguments. */
public final class Effects {

    public static final Set<String> KNOWN = Set.of(
            "POTION", "CURE", "INCREASE_DAMAGE", "DECREASE_DAMAGE", "DOUBLE_DAMAGE", "HALF_DAMAGE", "NEGATE_DAMAGE",
            "DO_HARM", "STEAL_HEALTH", "ADD_HEALTH", "ADD_FOOD", "BURN", "EXTINGUISH", "LIGHTNING", "TNT", "FIREBALL",
            "CACTUS", "BLOOD", "PARTICLE", "PLAY_SOUND", "PLAY_SOUND_OUTLOUD", "MESSAGE", "CANCEL_EVENT", "WAIT",
            "PULL_AWAY", "PULL_CLOSER", "BOOST", "TELEPORT_BEHIND", "STOP_KNOCKBACK", "SPAWN_ARROWS", "GUARD", "KILL",
            "DISARM", "REMOVE_RANDOM_ARMOR", "SHUFFLE_HOTBAR", "PUMPKIN", "DROP_HEAD", "EXP", "ADD_SOULS", "REMOVE_SOULS",
            "ADD_DURABILITY_CURRENT_ITEM", "ADD_DURABILITY_ITEM", "ADD_DURABILITY_ARMOR", "REPAIR", "DISABLE_ACTIVATION",
            "RESET_COMBO", "BREAK_BLOCK", "BREAK_TREE", "SET_BLOCK", "MORE_DROPS", "SMELT", "TP_DROPS", "PLANT_SEEDS",
            "AUTO_REEL", "REVIVE", "KEEP_ON_DEATH", "FLY", "AIR", "LAVA_WALKER", "WATER_WALKER", "REMOVE_ENCHANT",
            "CLONES", "DASH", "SLAM", "CHAIN_LIGHTNING", "MARK", "THROW_WEAPON", "HOMING", "MULTISHOT", "REFLECT", "BLINK",
            "DOUBLE_JUMP", "DROP_ITEM", "DOUBLE_CATCH", "REVEAL", "ACTIONBAR");

    private final Engine engine;
    private final Specials specials;

    Effects(Engine engine) {
        this.engine = engine;
        this.specials = new Specials(engine);
    }

    void apply(EffectLine line, Context c) {
        List<String> a = new ArrayList<>(line.args().size());
        for (String s : line.args()) {
            a.add(engine.fill(s, c));
        }
        Targets t = Targets.of(line, c);
        Context root = c.root();
        switch (line.name()) {
            case "POTION" -> {
                PotionEffectType type = Lookups.potion(arg(a, 0, "SPEED"));
                if (type == null) {
                    engine.warnOnce("potion " + a, "Unknown potion in " + line.raw());
                    return;
                }
                int amp = (int) d(a, 1, 0);
                int ticks = (int) d(a, 2, 100);
                for (LivingEntity e : t.entities) {
                    e.addPotionEffect(new PotionEffect(type, ticks, amp, false, true, true));
                }
            }
            case "CURE" -> {
                PotionEffectType type = Lookups.potion(arg(a, 0, "POISON"));
                for (LivingEntity e : t.entities) {
                    if (type != null) {
                        e.removePotionEffect(type);
                    }
                }
            }
            case "INCREASE_DAMAGE" -> {
                if (c.damage != null) {
                    c.damage.percent += d(a, 0, 10);
                }
            }
            case "DECREASE_DAMAGE", "NEGATE_DAMAGE" -> {
                if (c.damage != null) {
                    c.damage.percent -= d(a, 0, 10);
                }
            }
            case "DOUBLE_DAMAGE" -> {
                if (c.damage != null) {
                    c.damage.multiplier *= 2;
                }
            }
            case "HALF_DAMAGE" -> {
                if (c.damage != null) {
                    c.damage.multiplier *= 0.5;
                }
            }
            case "DO_HARM" -> {
                double dmg = d(a, 0, 1);
                engine.quietly(() -> t.entities.forEach(e -> e.damage(dmg)));
            }
            case "STEAL_HEALTH" -> {
                double amount = d(a, 0, 1);
                LivingEntity from = c.other();
                if (from == null) {
                    return;
                }
                engine.quietly(() -> from.damage(amount));
                for (LivingEntity e : t.entities) {
                    heal(e, amount);
                }
            }
            case "ADD_HEALTH" -> t.entities.forEach(e -> heal(e, d(a, 0, 2)));
            case "ADD_FOOD" -> t.entities.forEach(e -> {
                if (e instanceof Player p) {
                    p.setFoodLevel(Math.min(20, p.getFoodLevel() + (int) d(a, 0, 2)));
                    p.setSaturation(Math.min(p.getFoodLevel(), p.getSaturation() + 1));
                }
            });
            case "BURN" -> t.entities.forEach(e -> e.setFireTicks(Math.max(e.getFireTicks(), (int) d(a, 0, 60))));
            case "EXTINGUISH" -> t.entities.forEach(e -> e.setFireTicks(0));
            case "LIGHTNING" -> t.entities.forEach(e -> {
                e.getWorld().strikeLightningEffect(e.getLocation());
                engine.quietly(() -> e.damage(d(a, 0, 4)));
            });
            case "TNT" -> {
                float power = (float) d(a, 0, 2);
                for (LivingEntity e : t.entities) {
                    Location l = e.getLocation();
                    engine.quietly(() -> l.getWorld().createExplosion(l, power, false, false, c.holder));
                }
            }
            case "FIREBALL" -> t.entities.forEach(e -> {
                Vector dir = e.getLocation().add(0, 1, 0).toVector().subtract(c.holder.getEyeLocation().toVector()).normalize();
                SmallFireball f = c.holder.launchProjectile(SmallFireball.class, dir);
                f.setShooter(c.holder);
            });
            case "CACTUS" -> engine.quietly(() -> t.entities.forEach(e -> e.damage(d(a, 0, 1.5))));
            case "BLOOD" -> t.entities.forEach(e -> e.getWorld().spawnParticle(Particle.BLOCK, e.getLocation().add(0, 1, 0), 30,
                    0.3, 0.5, 0.3, Material.REDSTONE_BLOCK.createBlockData()));
            case "PARTICLE" -> {
                Particle p = Lookups.particle(arg(a, 0, "FLAME"));
                if (p == null) {
                    engine.warnOnce("particle " + a, "Unknown particle in " + line.raw());
                    return;
                }
                int count = (int) d(a, 1, 20);
                double speed = d(a, 2, 0.1);
                for (Location l : locations(t, c)) {
                    particle(l, p, count, speed);
                }
            }
            case "PLAY_SOUND" -> {
                Sound s = Lookups.sound(arg(a, 0, "ENTITY_EXPERIENCE_ORB_PICKUP"));
                if (s == null) {
                    return;
                }
                for (LivingEntity e : t.entities) {
                    if (e instanceof Player p) {
                        p.playSound(p.getLocation(), s, (float) d(a, 1, 1), (float) d(a, 2, 1));
                    }
                }
            }
            case "PLAY_SOUND_OUTLOUD" -> {
                Sound s = Lookups.sound(arg(a, 0, "ENTITY_EXPERIENCE_ORB_PICKUP"));
                if (s != null) {
                    for (Location l : locations(t, c)) {
                        l.getWorld().playSound(l, s, (float) d(a, 1, 1), (float) d(a, 2, 1));
                    }
                }
            }
            case "MESSAGE" -> {
                String msg = String.join(":", a);
                t.entities.forEach(e -> e.sendMessage(Colors.of(msg)));
            }
            case "CANCEL_EVENT" -> c.cancel();
            case "PULL_AWAY" -> t.entities.forEach(e -> push(e, c.holder.getLocation(), d(a, 0, 1)));
            case "PULL_CLOSER" -> t.entities.forEach(e -> push(e, c.holder.getLocation(), -d(a, 0, 1)));
            case "BOOST" -> {
                String dir = arg(a, 0, "UP").toUpperCase(Locale.ROOT);
                double power = d(a, 1, 10) / 20.0;
                for (LivingEntity e : t.entities) {
                    Vector v = dir.equals("FORWARD") ? e.getLocation().getDirection().multiply(power)
                            : new Vector(0, dir.equals("DOWN") ? -power : power, 0);
                    e.setVelocity(e.getVelocity().add(v));
                }
            }
            case "TELEPORT_BEHIND" -> t.entities.forEach(e -> {
                if (e == c.holder) {
                    return;
                }
                Location behind = e.getLocation().clone().subtract(e.getLocation().getDirection().setY(0).normalize().multiply(1.5));
                behind.setDirection(e.getLocation().toVector().subtract(behind.toVector()));
                if (behind.getBlock().isPassable() && behind.clone().add(0, 1, 0).getBlock().isPassable()) {
                    c.holder.teleport(behind);
                }
            });
            case "STOP_KNOCKBACK" -> t.entities.forEach(e -> Bukkit.getScheduler().runTask(engine.plugin,
                    () -> e.setVelocity(new Vector(0, Math.min(0, e.getVelocity().getY()), 0))));
            case "SPAWN_ARROWS" -> t.entities.forEach(e -> {
                int n = (int) d(a, 0, 5);
                for (int i = 0; i < n; i++) {
                    Location l = e.getLocation().add(ThreadLocalRandom.current().nextDouble(-1.5, 1.5), 6,
                            ThreadLocalRandom.current().nextDouble(-1.5, 1.5));
                    Arrow ar = e.getWorld().spawnArrow(l, new Vector(0, -1, 0), 1.2f, 4f);
                    ar.setShooter(c.holder);
                    ar.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                    Bukkit.getScheduler().runTaskLater(engine.plugin, ar::remove, 100);
                }
            });
            case "GUARD" -> guard(a, t, c);
            case "KILL" -> engine.quietly(() -> t.entities.forEach(e -> {
                var max = e.getAttribute(Attribute.MAX_HEALTH);
                // never one-shots players or bosses
                if (!(e instanceof Player) && (max == null || max.getValue() <= 100)) {
                    e.setHealth(0);
                }
            }));
            case "DISARM" -> t.entities.forEach(e -> {
                if (e instanceof Player p) {
                    PlayerInventory inv = p.getInventory();
                    ItemStack hand = inv.getItemInMainHand();
                    int free = firstFreeStorage(inv);
                    if (!hand.getType().isAir() && free >= 0) {
                        inv.setItem(free, hand);
                        inv.setItemInMainHand(null);
                    }
                }
            });
            case "REMOVE_RANDOM_ARMOR" -> t.entities.forEach(e -> {
                if (e instanceof Player p) {
                    PlayerInventory inv = p.getInventory();
                    List<EquipmentSlot> worn = new ArrayList<>();
                    for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                        if (!inv.getItem(s).getType().isAir()) {
                            worn.add(s);
                        }
                    }
                    int free = firstFreeStorage(inv);
                    if (!worn.isEmpty() && free >= 0) {
                        EquipmentSlot s = worn.get(ThreadLocalRandom.current().nextInt(worn.size()));
                        inv.setItem(free, inv.getItem(s));
                        inv.setItem(s, null);
                    }
                }
            });
            case "SHUFFLE_HOTBAR" -> t.entities.forEach(e -> {
                if (e instanceof Player p) {
                    PlayerInventory inv = p.getInventory();
                    List<ItemStack> bar = new ArrayList<>();
                    for (int i = 0; i < 9; i++) {
                        bar.add(inv.getItem(i));
                    }
                    Collections.shuffle(bar);
                    for (int i = 0; i < 9; i++) {
                        inv.setItem(i, bar.get(i));
                    }
                }
            });
            case "PUMPKIN" -> t.entities.forEach(e -> {
                if (e instanceof Player p) {
                    // only the victim's screen sees the pumpkin; the real helmet never moves
                    p.sendEquipmentChange(p, EquipmentSlot.HEAD, new ItemStack(Material.CARVED_PUMPKIN));
                    Bukkit.getScheduler().runTaskLater(engine.plugin, () -> {
                        if (p.isOnline()) {
                            p.sendEquipmentChange(p, EquipmentSlot.HEAD, p.getInventory().getHelmet());
                        }
                    }, (long) d(a, 0, 60));
                }
            });
            case "DROP_HEAD" -> t.entities.forEach(e -> {
                ItemStack head = head(e);
                if (head != null) {
                    e.getWorld().dropItemNaturally(e.getLocation(), head);
                }
            });
            case "EXP" -> {
                int xp = (int) d(a, 0, 1);
                if (xp > 0) {
                    c.holder.giveExp(xp);
                }
            }
            case "ADD_SOULS" -> souls(c.item, (int) d(a, 0, 1));
            case "REMOVE_SOULS" -> souls(c.item, -(int) d(a, 0, 1));
            case "ADD_DURABILITY_ITEM" -> durability(c.item, (int) d(a, 0, 1));
            case "ADD_DURABILITY_CURRENT_ITEM" -> t.entities.forEach(e -> durability(
                    e == c.holder ? c.item : e.getEquipment() == null ? null : e.getEquipment().getItemInMainHand(), (int) d(a, 0, 1)));
            case "ADD_DURABILITY_ARMOR" -> t.entities.forEach(e -> {
                if (e.getEquipment() != null) {
                    for (ItemStack i : e.getEquipment().getArmorContents()) {
                        durability(i, (int) d(a, 0, 1));
                    }
                }
            });
            case "REPAIR" -> durability(c.item, Integer.MAX_VALUE / 2);
            case "DISABLE_ACTIVATION" -> {
                String id = arg(a, 0, "");
                double secs = d(a, 1, 3);
                LivingEntity who = line.target() == null ? c.other() : t.entities.isEmpty() ? null : t.entities.get(0);
                if (who != null && !id.isEmpty()) {
                    engine.disable(who, id, secs);
                }
            }
            case "RESET_COMBO" -> t.entities.forEach(engine.combos::reset);
            case "BREAK_BLOCK" -> breakBlocks(t.blocks, c);
            case "BREAK_TREE" -> breakTree(c);
            case "SET_BLOCK" -> {
                Material m = Material.matchMaterial(arg(a, 0, "STONE"));
                if (m != null) {
                    // on the block being mined: it drops this instead
                    root.replaceDrop = m;
                }
            }
            case "MORE_DROPS" -> root.moreDrops += (int) d(a, 0, 1);
            case "SMELT" -> root.smelt = true;
            case "TP_DROPS" -> root.tpDrops = true;
            case "PLANT_SEEDS" -> plant(c, (int) d(a, 0, 1), arg(a, 1, ""));
            case "AUTO_REEL" -> {
                if (c.hook instanceof FishHook h) {
                    Bukkit.getScheduler().runTask(engine.plugin, () -> {
                        if (h.isValid()) {
                            h.retrieve(EquipmentSlot.HAND);
                        }
                    });
                }
            }
            case "REVIVE" -> root.revive = true;
            case "KEEP_ON_DEATH" -> {
                if (c.item != null) {
                    root.keep.add(c.item);
                }
            }
            case "FLY" -> {
                if (c.holder.getGameMode() == GameMode.SURVIVAL || c.holder.getGameMode() == GameMode.ADVENTURE) {
                    c.holder.setAllowFlight(true);
                    c.holder.getPersistentDataContainer().set(Keys.GUARD, PersistentDataType.LONG, System.currentTimeMillis());
                }
            }
            case "AIR" -> t.entities.forEach(e -> e.setRemainingAir(e.getMaximumAir()));
            case "WATER_WALKER" -> walker(c.holder, Material.WATER, Material.FROSTED_ICE, d(a, 0, 2));
            case "LAVA_WALKER" -> walker(c.holder, Material.LAVA, Material.MAGMA_BLOCK, d(a, 0, 2));
            case "REMOVE_ENCHANT" -> engine.warnOnce("remove_enchant", "REMOVE_ENCHANT is not supported by ArkEnchants (ignored).");
            default -> {
                if (!specials.apply(line.name(), a, t, c)) {
                    engine.warnOnce("fx " + line.name(), "Unknown effect " + line.name() + " in " + c.enchantId + " (ignored).");
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers
    private static String arg(List<String> a, int i, String def) {
        return i < a.size() && !a.get(i).isEmpty() ? a.get(i) : def;
    }

    private static double d(List<String> a, int i, double def) {
        return Engine.num(arg(a, i, String.valueOf(def)), def);
    }

    private static void heal(LivingEntity e, double amount) {
        var max = e.getAttribute(Attribute.MAX_HEALTH);
        double cap = max == null ? 20 : max.getValue();
        if (!e.isDead()) {
            e.setHealth(Math.min(cap, e.getHealth() + amount));
        }
    }

    private static void push(LivingEntity e, Location from, double power) {
        Vector v = e.getLocation().toVector().subtract(from.toVector());
        if (v.lengthSquared() < 1e-4) {
            return;
        }
        v.setY(0).normalize().multiply(power).setY(0.25 * Math.signum(power) + (power > 0 ? 0.1 : 0.2));
        e.setVelocity(v);
    }

    private static List<Location> locations(Targets t, Context c) {
        List<Location> out = new ArrayList<>();
        for (LivingEntity e : t.entities) {
            out.add(e.getLocation().add(0, 1, 0));
        }
        for (Block b : t.blocks) {
            out.add(b.getLocation().add(0.5, 0.5, 0.5));
        }
        if (out.isEmpty()) {
            out.add(c.holder.getLocation().add(0, 1, 0));
        }
        return out;
    }

    private void particle(Location l, Particle p, int count, double speed) {
        World w = l.getWorld();
        Class<?> data = p.getDataType();
        try {
            if (data == Void.class) {
                w.spawnParticle(p, l, count, 0.4, 0.5, 0.4, speed);
            } else if (data == Particle.DustOptions.class) {
                w.spawnParticle(p, l, count, 0.4, 0.5, 0.4, speed, new Particle.DustOptions(org.bukkit.Color.RED, 1.2f));
            } else if (data == org.bukkit.block.data.BlockData.class) {
                w.spawnParticle(p, l, count, 0.4, 0.5, 0.4, speed, Material.REDSTONE_BLOCK.createBlockData());
            } else if (data == ItemStack.class) {
                w.spawnParticle(p, l, count, 0.4, 0.5, 0.4, speed, new ItemStack(Material.REDSTONE));
            }
        } catch (RuntimeException ex) {
            engine.warnOnce("particle-data " + p, "Particle " + p + " needs data ArkEnchants cannot guess (skipped).");
        }
    }

    private void guard(List<String> a, Targets t, Context c) {
        EntityType type = Lookups.entity(arg(a, 0, "ZOMBIE"));
        if (type == null || !type.isAlive() || !type.isSpawnable()) {
            return;
        }
        long ticks = (long) (d(a, 1, 8) * 20);
        int amount = (int) Math.max(1, d(a, 2, 1));
        LivingEntity enemy = t.entities.isEmpty() ? c.other() : t.entities.get(0);
        for (int i = 0; i < amount; i++) {
            Location l = c.holder.getLocation().add(ThreadLocalRandom.current().nextDouble(-2, 2), 0,
                    ThreadLocalRandom.current().nextDouble(-2, 2));
            Entity e = c.holder.getWorld().spawnEntity(l, type);
            e.getPersistentDataContainer().set(Keys.GUARD, PersistentDataType.STRING, c.holder.getUniqueId().toString());
            e.setPersistent(false);
            if (e instanceof Mob m) {
                m.setRemoveWhenFarAway(true);
                if (enemy != null && enemy != c.holder) {
                    m.setTarget(enemy);
                }
            }
            Bukkit.getScheduler().runTaskLater(engine.plugin, e::remove, ticks);
        }
    }

    private static int firstFreeStorage(PlayerInventory inv) {
        for (int i = 9; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.getType().isAir()) {
                return i;
            }
        }
        return -1;
    }

    private static ItemStack head(LivingEntity e) {
        if (e instanceof Player p) {
            ItemStack h = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta m = (SkullMeta) h.getItemMeta();
            m.setOwningPlayer(p);
            h.setItemMeta(m);
            return h;
        }
        Material m = switch (e.getType()) {
            case ZOMBIE -> Material.ZOMBIE_HEAD;
            case SKELETON -> Material.SKELETON_SKULL;
            case WITHER_SKELETON -> Material.WITHER_SKELETON_SKULL;
            case CREEPER -> Material.CREEPER_HEAD;
            case PIGLIN -> Material.PIGLIN_HEAD;
            case ENDER_DRAGON -> Material.DRAGON_HEAD;
            default -> null;
        };
        return m == null ? null : new ItemStack(m);
    }

    private static void souls(ItemStack item, int delta) {
        if (Items.tracks(item)) {
            Items.setSouls(item, Items.souls(item) + delta, false);
        }
    }

    private static void durability(ItemStack item, int repair) {
        if (item == null || item.getType().isAir() || item.getType().getMaxDurability() <= 0) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable d && !meta.isUnbreakable()) {
            int max = d.hasMaxDamage() ? d.getMaxDamage() : item.getType().getMaxDurability();
            long dmg = (long) d.getDamage() - repair;
            d.setDamage((int) Math.max(0, Math.min(max - 1, dmg)));
            item.setItemMeta(meta);
        }
    }

    /** Breaks through the player so protection plugins (WorldGuard, GriefPrevention) get their say. */
    private void breakBlocks(List<Block> blocks, Context c) {
        if (blocks.isEmpty()) {
            return;
        }
        engine.quietly(() -> {
            for (Block b : blocks) {
                Material m = b.getType();
                if (m.isAir() || b.isLiquid() || m.getHardness() < 0 || m == Material.BEDROCK) {
                    continue;
                }
                c.holder.breakBlock(b);
            }
        });
    }

    private void breakTree(Context c) {
        if (c.block == null || !Tag.LOGS.isTagged(c.block.getType())) {
            return;
        }
        List<Block> logs = new ArrayList<>();
        java.util.ArrayDeque<Block> q = new java.util.ArrayDeque<>();
        java.util.Set<Block> seen = new java.util.HashSet<>();
        q.add(c.block);
        seen.add(c.block);
        while (!q.isEmpty() && logs.size() < 160) {
            Block b = q.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block n = b.getRelative(dx, dy, dz);
                        if (seen.add(n) && Tag.LOGS.isTagged(n.getType())) {
                            q.add(n);
                            logs.add(n);
                        }
                    }
                }
            }
        }
        breakBlocks(logs, c);
    }

    private void plant(Context c, int radius, String cropName) {
        if (c.block == null) {
            return;
        }
        Material broken = c.block.getType();
        Material crop = cropName.isEmpty() ? null : Material.matchMaterial(cropName);
        if (crop != null && !crop.isBlock()) {
            crop = cropFromItem(crop);
        }
        final Material replant = crop != null ? crop : (c.block.getBlockData() instanceof Ageable ? broken : null);
        if (replant == null) {
            return;
        }
        Block origin = c.block;
        Bukkit.getScheduler().runTask(engine.plugin, () -> {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block b = origin.getRelative(dx, 0, dz);
                    if (b.getType().isAir() && b.getRelative(BlockFace.DOWN).getType() == Material.FARMLAND
                            && (b.equals(origin) || takeSeed(c.holder, replant))) {
                        b.setType(replant);
                    }
                }
            }
        });
    }

    private static Material cropFromItem(Material item) {
        return switch (item) {
            case CARROT -> Material.CARROTS;
            case POTATO -> Material.POTATOES;
            case WHEAT_SEEDS, WHEAT -> Material.WHEAT;
            case BEETROOT_SEEDS, BEETROOT -> Material.BEETROOTS;
            default -> null;
        };
    }

    private static boolean takeSeed(Player p, Material crop) {
        Material seed = switch (crop) {
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case WHEAT -> Material.WHEAT_SEEDS;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            case NETHER_WART -> Material.NETHER_WART;
            default -> null;
        };
        if (seed == null || p.getGameMode() == GameMode.CREATIVE) {
            return seed != null;
        }
        ItemStack one = new ItemStack(seed);
        if (!p.getInventory().containsAtLeast(one, 1)) {
            return false;
        }
        p.getInventory().removeItem(one);
        return true;
    }

    private void walker(Player p, Material liquid, Material solid, double radius) {
        Block feet = p.getLocation().getBlock().getRelative(BlockFace.DOWN);
        int r = (int) Math.max(1, radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                Block b = feet.getRelative(dx, 0, dz);
                if (b.getType() == liquid && b.getRelative(BlockFace.UP).getType().isAir()
                        && b.getBlockData() instanceof org.bukkit.block.data.Levelled lv && lv.getLevel() == 0) {
                    b.setType(solid);
                    if (solid != Material.FROSTED_ICE) {
                        Bukkit.getScheduler().runTaskLater(engine.plugin, () -> {
                            if (b.getType() == solid) {
                                b.setType(liquid);
                            }
                        }, 100);
                    }
                }
            }
        }
    }
}
