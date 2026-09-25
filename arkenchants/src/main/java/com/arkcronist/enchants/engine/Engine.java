package com.arkcronist.enchants.engine;

import com.arkcronist.enchants.EnchantRegistry;
import com.arkcronist.enchants.Settings;
import com.arkcronist.enchants.item.Items;
import com.arkcronist.enchants.model.Enchant;
import com.arkcronist.enchants.model.EnchantLevel;
import com.arkcronist.enchants.model.Trigger;
import com.arkcronist.enchants.text.Tags;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Decides which enchants fire (trigger, item kind, conditions, chance, cooldown) and runs their effects. */
public final class Engine {

    public final Plugin plugin;
    public final Combos combos = new Combos();
    public final PlacedBlocks placed = new PlacedBlocks();
    public final Effects effects;
    private final Placeholders placeholders = new Placeholders(this);
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<String, Long> disabled = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private final Logger log;
    private EnchantRegistry registry;
    private Settings settings;
    /** Set while our own effects hurt or break things, so they do not fire enchants again. */
    private int busy;

    public Engine(Plugin plugin, EnchantRegistry registry, Settings settings) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.registry = registry;
        this.settings = settings;
        this.effects = new Effects(this);
    }

    public void reload(EnchantRegistry r, Settings s) {
        this.registry = r;
        this.settings = s;
        cooldowns.clear();
    }

    public EnchantRegistry registry() {
        return registry;
    }

    public boolean busy() {
        return busy > 0;
    }

    public void quietly(Runnable r) {
        busy++;
        try {
            r.run();
        } finally {
            busy--;
        }
    }

    /** Fires every enchant with this trigger on the given items. Results land on {@code base}. */
    public void fire(Trigger trigger, Context base, Iterable<ItemStack> items) {
        if (busy > 0 || base.holder == null || settings.disabledWorlds.contains(base.holder.getWorld().getName())) {
            return;
        }
        Set<ItemStack> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (ItemStack item : items) {
            if (Items.empty(item) || !seen.add(item)) {
                continue;
            }
            Map<String, Integer> ench = Items.enchants(item);
            for (Map.Entry<String, Integer> en : ench.entrySet()) {
                Enchant e = registry.get(en.getKey());
                if (e == null || !e.triggers().contains(trigger) || !Items.applicable(e, item)) {
                    continue;
                }
                activate(e, en.getValue(), item, base);
            }
        }
    }

    private void activate(Enchant e, int lvl, ItemStack item, Context base) {
        UUID id = base.holder.getUniqueId();
        long now = System.currentTimeMillis();
        String key = id + "|" + e.id();
        Long off = disabled.get(key);
        if (off != null && off > now) {
            return;
        }
        Long cd = cooldowns.get(key);
        if (cd != null && cd > now) {
            return;
        }
        EnchantLevel level = e.level(lvl);
        if (level == null) {
            return;
        }
        Context c = base.forEnchant(item, e.id(), lvl);
        Condition.Verdict v = Condition.check(level.conditions(), s -> Tags.resolve(placeholders.fill(s, c), ThreadLocalRandom.current()));
        if (v.stop()) {
            return;
        }
        double chance = level.chance() + v.chanceBonus();
        if (!v.force() && ThreadLocalRandom.current().nextDouble(100) >= chance) {
            return;
        }
        if (level.cooldown() > 0) {
            cooldowns.put(key, now + (long) (level.cooldown() * 1000));
        }
        run(c, level.effects(), 0);
    }

    /** Runs effects in order; WAIT:n hands the rest to a task n ticks later. */
    void run(Context c, List<EffectLine> lines, int from) {
        for (int i = from; i < lines.size(); i++) {
            EffectLine line = lines.get(i);
            if (line.name().equals("WAIT")) {
                long ticks = (long) num(fill(line.arg(0, "20"), c), 20);
                int next = i + 1;
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (c.holder.isOnline()) {
                        run(c, lines, next);
                    }
                }, Math.max(1, ticks));
                return;
            }
            if (!line.conditions().isEmpty()) {
                Condition.Verdict v = Condition.check(line.conditions(), s -> fill(s, c));
                if (v.stop()) {
                    continue;
                }
            }
            if (line.chance() < 100 && ThreadLocalRandom.current().nextDouble(100) >= line.chance()) {
                continue;
            }
            try {
                effects.apply(line, c);
            } catch (RuntimeException ex) {
                warnOnce("effect " + line.raw(), "Effect '" + line.raw() + "' in " + c.enchantId + " failed: " + ex);
            }
        }
    }

    /** Placeholders, then random/math tags. */
    public String fill(String text, Context c) {
        return Tags.resolve(placeholders.fill(text, c), ThreadLocalRandom.current());
    }

    public void disable(Entity who, String enchantId, double seconds) {
        if (who instanceof Player p) {
            disabled.put(p.getUniqueId() + "|" + enchantId.toLowerCase(java.util.Locale.ROOT),
                    System.currentTimeMillis() + (long) (seconds * 1000));
        }
    }

    void unknownPlaceholder(String key) {
        warnOnce("ph " + key, "Unknown placeholder %" + key + "% (treated as empty).");
    }

    public void warnOnce(String key, String message) {
        if (warned.add(key)) {
            log.warning(message);
        }
    }

    public Settings settings() {
        return settings;
    }

    static String num(double v) {
        return Tags.number(Math.round(v * 100) / 100.0);
    }

    static double num(String s, double def) {
        try {
            return Double.parseDouble(s.trim());
        } catch (RuntimeException e) {
            return def;
        }
    }

    /** Hits in a row without being hit back (for %combo%). */
    public static final class Combos {
        private final Map<UUID, long[]> map = new HashMap<>();

        public int get(Entity e) {
            if (e == null) {
                return 0;
            }
            long[] v = map.get(e.getUniqueId());
            return v == null || System.currentTimeMillis() - v[1] > 3000 ? 0 : (int) v[0];
        }

        public void hit(Entity attacker) {
            long[] v = map.computeIfAbsent(attacker.getUniqueId(), k -> new long[2]);
            if (System.currentTimeMillis() - v[1] > 3000) {
                v[0] = 0;
            }
            v[0]++;
            v[1] = System.currentTimeMillis();
        }

        public void reset(Entity e) {
            if (e != null) {
                map.remove(e.getUniqueId());
            }
        }
    }

    /** Blocks players placed recently, for %block natural% (bounded so it never grows forever). */
    public static final class PlacedBlocks {
        private static final int MAX = 50_000;
        private final LinkedHashSet<String> set = new LinkedHashSet<>();

        private static String key(Block b) {
            return b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
        }

        public void add(Block b) {
            set.add(key(b));
            if (set.size() > MAX) {
                var it = set.iterator();
                it.next();
                it.remove();
            }
        }

        public boolean contains(Block b) {
            return set.contains(key(b));
        }

        public void remove(Block b) {
            set.remove(key(b));
        }
    }
}
