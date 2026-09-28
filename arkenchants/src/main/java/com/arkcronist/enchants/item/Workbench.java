package com.arkcronist.enchants.item;

import com.arkcronist.enchants.ArkEnchants;
import com.arkcronist.enchants.model.Enchant;
import com.arkcronist.enchants.model.Group;
import com.arkcronist.enchants.text.Percent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Using one ArkEnchants item on another: a book on gear, a scroll on gear, magic dust on a book, a book on the
 * same book. Shared by dragging in the inventory and by the enchanting table menu (easier on Bedrock).
 */
public final class Workbench {

    /** What happened: whether we took over, and what the tool (the item used) and the target became. */
    public record Use(boolean handled, ItemStack tool, ItemStack target) {
    }

    private final ArkEnchants plugin;

    public Workbench(ArkEnchants plugin) {
        this.plugin = plugin;
    }

    private static Use skip(ItemStack tool, ItemStack target) {
        return new Use(false, tool, target);
    }

    private static Use keep(ItemStack tool, ItemStack target) {
        return new Use(true, tool, target);
    }

    public Use use(Player p, ItemStack tool, ItemStack target) {
        if (Items.empty(tool) || Items.empty(target) || target.getAmount() != 1
                || Items.mysteryGroup(target) != null || Items.scrollKind(target) != null) {
            return skip(tool, target);
        }
        String kind = Items.scrollKind(tool);
        Items.Book onto = Items.readBook(target);
        Items.Book book = Items.readBook(tool);
        if (onto != null) {
            if ("dust".equals(kind)) {
                return dust(p, tool, target, onto);
            }
            return book != null ? combine(p, tool, target, book, onto) : skip(tool, target);
        }
        if (kind != null) {
            return scroll(p, kind, tool, target);
        }
        return book != null ? book(p, book, tool, target) : skip(tool, target);
    }

    // ------------------------------------------------------------------ books on gear
    private Use book(Player p, Items.Book book, ItemStack tool, ItemStack target) {
        Enchant ench = plugin.registry().get(book.enchant());
        if (ench == null) {
            return skip(tool, target);
        }
        boolean astral = ench.group().equals("ASTRAL");
        if (astral && (!Items.ownedBy(tool, p) || !Items.ownedBy(target, p))) {
            plugin.send(p, "astral-not-yours");
            return keep(tool, target);
        }
        Map<String, Integer> current = new LinkedHashMap<>(Items.enchants(target));
        Applying.Outcome o = Applying.check(ench, book.level(), current, Items.applicable(ench, target),
                Items.maxSlots(target), plugin.settings().upgradeOnSameLevel);
        switch (o.result()) {
            case NOT_APPLICABLE -> plugin.send(p, "not-applicable", "%applies-to%", ench.appliesTo());
            case ALREADY -> plugin.send(p, "already-has");
            case CONFLICT -> plugin.send(p, "conflict", "%enchant%", o.detail());
            case MISSING_REQUIRED -> plugin.send(p, "requires", "%enchant%", o.detail());
            case NO_SLOTS -> plugin.send(p, "no-slots", "%max%", o.detail());
            case OK -> {
                ItemStack left = consume(tool);
                ThreadLocalRandom r = ThreadLocalRandom.current();
                if (Percent.chance(book.success(), r)) {
                    current.put(ench.id(), o.newLevel());
                    Items.setEnchants(target, current);
                    if (astral && Items.owner(target) == null) {
                        Items.bind(target, p);
                    }
                    good(p);
                    plugin.send(p, "applied", "%enchant%", Items.format("%group-color%%display% %level%", ench, o.newLevel()));
                    return keep(left, target);
                }
                if (Percent.chance(book.destroy(), r)) {
                    if (Items.isProtected(target)) {
                        Items.setProtected(target, false);
                        bad(p);
                        plugin.send(p, "protection-saved");
                        return keep(left, target);
                    }
                    p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1, 0.7f);
                    plugin.send(p, "destroyed");
                    return keep(left, null);
                }
                bad(p);
                plugin.send(p, "failed", "%success%", Percent.fmt(book.success()));
                return keep(left, target);
            }
        }
        return keep(tool, target);
    }

    // ------------------------------------------------------------------ books on books
    /** Magic dust raises a book's success chance. */
    private Use dust(Player p, ItemStack tool, ItemStack target, Items.Book onto) {
        Enchant ench = plugin.registry().get(onto.enchant());
        if (ench == null) {
            return skip(tool, target);
        }
        if (onto.success() >= 100) {
            plugin.send(p, "dust-full");
            return keep(tool, target);
        }
        double rate = Items.scrollRate(tool);
        double success = Math.min(100, onto.success() + rate);
        ItemStack out = Items.book(ench, onto.level(), success, onto.destroy());
        Items.copyOwner(target, out);
        good(p);
        plugin.send(p, "dust-applied", "%success%", Percent.fmt(success));
        return keep(consume(tool), out);
    }

    /** Two books of the same enchant and level make one book a level higher. */
    private Use combine(Player p, ItemStack tool, ItemStack target, Items.Book a, Items.Book b) {
        if (!a.enchant().equals(b.enchant())) {
            return skip(tool, target);
        }
        Enchant ench = plugin.registry().get(a.enchant());
        if (ench == null) {
            return skip(tool, target);
        }
        if (a.level() != b.level()) {
            plugin.send(p, "combine-level");
            return keep(tool, target);
        }
        if (a.level() >= ench.maxLevel()) {
            plugin.send(p, "combine-max");
            return keep(tool, target);
        }
        if (ench.group().equals("ASTRAL") && (!Items.ownedBy(tool, p) || !Items.ownedBy(target, p))) {
            plugin.send(p, "astral-not-yours");
            return keep(tool, target);
        }
        ItemStack out = Items.book(ench, a.level() + 1, (a.success() + b.success()) / 2, Math.max(a.destroy(), b.destroy()));
        Items.copyOwner(target, out);
        good(p);
        plugin.send(p, "combined", "%enchant%", Items.format("%group-color%%display% %level%", ench, a.level() + 1));
        return keep(consume(tool), out);
    }

    // ------------------------------------------------------------------ scrolls on gear
    private Use scroll(Player p, String kind, ItemStack tool, ItemStack target) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        boolean single = target.getType().getMaxStackSize() == 1;
        switch (kind) {
            case "soul_tracker" -> {
                if (Items.tracks(target) || !single) {
                    return skip(tool, target);
                }
                Items.setSouls(target, 0, true);
                good(p);
                plugin.send(p, "tracker-applied");
                return keep(consume(tool), target);
            }
            case "protect" -> {
                if (!single) {
                    return skip(tool, target);
                }
                if (Items.isProtected(target)) {
                    plugin.send(p, "already-protected");
                    return keep(tool, target);
                }
                Items.setProtected(target, true);
                good(p);
                plugin.send(p, "protected");
                return keep(consume(tool), target);
            }
            case "slots" -> {
                if (!single) {
                    return skip(tool, target);
                }
                int have = Items.extraSlots(target);
                int max = plugin.settings().maxExtraSlots;
                if (have >= max) {
                    plugin.send(p, "slots-max", "%max%", String.valueOf(max));
                    return keep(tool, target);
                }
                int now = Math.min(max, have + Math.max(1, (int) Math.round(Items.scrollRate(tool))));
                Items.setExtraSlots(target, now);
                good(p);
                plugin.send(p, "slots-added", "%slots%", String.valueOf(Items.maxSlots(target)));
                return keep(consume(tool), target);
            }
            case "extract", "purify", "cleanse" -> {
                boolean curse = !kind.equals("extract");
                Map<String, Integer> current = new LinkedHashMap<>(Items.enchants(target));
                if (current.isEmpty()) {
                    return skip(tool, target);
                }
                if (kind.equals("purify") && plugin.settings().cursesPermanent) {
                    plugin.send(p, "curse-permanent");
                    return keep(tool, target);
                }
                if (kind.equals("cleanse") && !plugin.settings().cleanseEnabled) {
                    plugin.send(p, "cleanse-disabled");
                    return keep(tool, target);
                }
                List<String> options = new ArrayList<>();
                for (String id : current.keySet()) {
                    Enchant en = plugin.registry().get(id);
                    boolean isCurse = en != null && en.group().equals("CURSE");
                    // extraction takes only removable enchants; purifying takes only curses
                    if (en == null || (curse ? isCurse : !isCurse && en.removable())) {
                        options.add(id);
                    }
                }
                if (options.isEmpty()) {
                    plugin.send(p, curse ? "no-curse" : "nothing-to-extract");
                    return keep(tool, target);
                }
                ItemStack left = consume(tool);
                double rate = Items.scrollRate(tool);
                if (!Percent.chance(rate, r)) {
                    bad(p);
                    plugin.send(p, "scroll-failed", "%success%", Percent.fmt(rate));
                    return keep(left, target);
                }
                String id = options.get(r.nextInt(options.size()));
                int level = current.remove(id);
                Items.setEnchants(target, current);
                good(p);
                Enchant en = plugin.registry().get(id);
                if (curse || en == null) {
                    plugin.send(p, "purified", "%enchant%", en == null ? id : Items.format("%group-color%%display% %level%", en, level));
                    return keep(left, target);
                }
                Group g = plugin.registry().group(en.group());
                ItemStack b = Items.book(en, level, Percent.roll(g.successMin(), g.successMax(), r),
                        Percent.roll(g.destroyMin(), g.destroyMax(), r));
                p.getInventory().addItem(b).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
                plugin.send(p, "extracted", "%enchant%", Items.format("%group-color%%display% %level%", en, level));
                return keep(left, target);
            }
            default -> {
                return skip(tool, target);
            }
        }
    }

    // ------------------------------------------------------------------ helpers
    /** One of the tool used up: what is left of the stack, or null. */
    static ItemStack consume(ItemStack tool) {
        if (tool.getAmount() > 1) {
            ItemStack left = tool.clone();
            left.setAmount(tool.getAmount() - 1);
            return left;
        }
        return null;
    }

    static void good(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4);
    }

    static void bad(Player p) {
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 1.2f);
        p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);
    }
}
