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

    public EnchanterMenu(ArkEnchants plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        plugin.menus().main(p);
    }

    /** The groups sold here, in groups.yml order. */
    public List<Group> groups() {
        List<Group> out = new ArrayList<>(plugin.registry().groups());
        out.removeIf(g -> !g.inEnchanter() || plugin.registry().inGroup(g.id()).isEmpty());
        return out;
    }

    public void buy(Player p, Group g) {
        int cost = g.enchanterCost();
        if (!pay(p, cost)) {
            return;
        }
        give(p, Items.mystery(g));
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1, 1);
        plugin.send(p, "bought", "%group%", g.color() + g.name(), "%cost%", String.valueOf(cost));
    }

    public void buyScroll(Player p, String kind) {
        var sc = plugin.settings().scrolls.get(kind);
        if (sc == null || !pay(p, sc.cost())) {
            return;
        }
        give(p, Items.scroll(kind, Percent.roll(sc.min(), sc.max(), ThreadLocalRandom.current())));
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1, 1.3f);
        plugin.send(p, "bought", "%group%", sc.name().replace(" &7(%success%%)", "").replace("%success%", ""), "%cost%",
                String.valueOf(sc.cost()));
    }

    /** Levels still missing for a price, 0 when it can be paid (always 0 in creative). */
    public static int missing(Player p, int cost) {
        return p.getGameMode() == GameMode.CREATIVE ? 0 : Math.max(0, cost - p.getLevel());
    }

    private boolean pay(Player p, int cost) {
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
