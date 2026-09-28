package com.arkcronist.enchants.gui;

import com.arkcronist.enchants.ArkEnchants;
import com.arkcronist.enchants.item.Items;
import com.arkcronist.enchants.model.Group;
import com.arkcronist.enchants.text.Percent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Buying mystery books and scrolls with experience levels. /enchanter opens the /ake menu, where they are sold. */
public final class EnchanterMenu {

    private final ArkEnchants plugin;
    private final Money money;

    public EnchanterMenu(ArkEnchants plugin) {
        this.plugin = plugin;
        this.money = new Money(plugin.getLogger());
    }

    /** Charging money: enchanter.currency is MONEY, Vault is there, and this thing has a money price. */
    private boolean inMoney(double price) {
        return plugin.settings().payWithMoney && price > 0 && money.ready();
    }

    public double groupMoney(Group g) {
        return plugin.settings().groupMoney.getOrDefault(g.id(), 0.0);
    }

    /** Whether it is on sale at all (a price in the currency in use, or in levels). */
    public boolean forSale(int levels, double price) {
        return inMoney(price) || levels > 0;
    }

    /** The price line for menu lore, with what is missing. */
    public String priceLine(Player p, int levels, double price) {
        if (inMoney(price)) {
            double have = p.getGameMode() == GameMode.CREATIVE ? Double.MAX_VALUE : money.balance(p);
            return "&b&lPRECIO &f" + money.format(price) + " " + (have >= price ? "&a✔" : "&7(Te faltan &c" + money.format(price - have) + "&7)");
        }
        int miss = missing(p, levels);
        return "&b&lCOSTO &f" + levels + " niveles " + (miss > 0 ? "&7(Te faltan &c" + miss + " &7niveles)" : "&a✔");
    }

    public void open(Player p) {
        plugin.menus().main(p);
    }

    /** The groups sold here, in groups.yml order. */
    public List<Group> groups() {
        List<Group> out = new ArrayList<>(plugin.registry().groups());
        out.removeIf(g -> !g.inEnchanter() || plugin.registry().inGroup(g.id()).isEmpty()
                || !forSale(g.enchanterCost(), groupMoney(g)));
        return out;
    }

    public void buy(Player p, Group g) {
        int cost = g.enchanterCost();
        double price = groupMoney(g);
        if (!pay(p, cost, price)) {
            return;
        }
        give(p, Items.mystery(g));
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1, 1);
        plugin.send(p, "bought", "%group%", g.color() + g.name(), "%cost%", paidText(cost, price));
    }

    public void buyScroll(Player p, String kind) {
        var sc = plugin.settings().scrolls.get(kind);
        if (sc == null || !pay(p, sc.cost(), sc.money())) {
            return;
        }
        give(p, Items.scroll(kind, Percent.roll(sc.min(), sc.max(), ThreadLocalRandom.current())));
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1, 1.3f);
        plugin.send(p, "bought", "%group%", sc.name().replace(" &7(%success%%)", "").replace("%success%", ""), "%cost%",
                paidText(sc.cost(), sc.money()));
    }

    /** Levels still missing for a price, 0 when it can be paid (always 0 in creative). */
    public static int missing(Player p, int cost) {
        return p.getGameMode() == GameMode.CREATIVE ? 0 : Math.max(0, cost - p.getLevel());
    }

    private String paidText(int levels, double price) {
        return inMoney(price) ? money.format(price) : levels + " niveles";
    }

    private boolean pay(Player p, int cost, double price) {
        if (inMoney(price)) {
            if (p.getGameMode() == GameMode.CREATIVE) {
                return true;
            }
            if (money.balance(p) < price || !money.take(p, price)) {
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
                plugin.send(p, "not-enough-money", "%cost%", money.format(price));
                return false;
            }
            return true;
        }
        if (missing(p, cost) > 0) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1, 1);
            plugin.send(p, "not-enough-xp", "%cost%", String.valueOf(cost));
            return false;
        }
        if (p.getGameMode() != GameMode.CREATIVE) {
            p.setLevel(p.getLevel() - cost);
        }
        return true;
    }

    private static void give(Player p, org.bukkit.inventory.ItemStack it) {
        p.getInventory().addItem(it).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
    }
}
