package com.arkcronist.enchants.command;

import com.arkcronist.enchants.ArkEnchants;
import com.arkcronist.enchants.item.Items;
import com.arkcronist.enchants.model.Enchant;
import com.arkcronist.enchants.model.Group;
import com.arkcronist.enchants.text.Colors;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** /arkenchants and /enchanter. */
public final class ArkEnchantsCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("help", "reload", "list", "give", "mystery", "tracker", "apply", "remove",
            "info", "enchanter");
    private final ArkEnchants plugin;

    public ArkEnchantsCommand(ArkEnchants plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (cmd.getName().equalsIgnoreCase("enchanter")) {
            if (s instanceof Player p) {
                plugin.enchanter().open(p);
            }
            return true;
        }
        String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
        if (!sub.equals("enchanter") && !sub.equals("info") && !s.hasPermission("arkenchants.admin")) {
            plugin.send(s, "no-permission");
            return true;
        }
        switch (sub) {
            case "reload" -> {
                plugin.reloadAll();
                plugin.send(s, "reloaded", "%count%", String.valueOf(plugin.registry().all().size()));
            }
            case "list" -> list(s, a.length > 1 ? a[1] : null);
            case "give" -> give(s, a);
            case "mystery" -> mystery(s, a);
            case "tracker" -> {
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : s instanceof Player p ? p : null;
                if (t == null) {
                    plugin.send(s, "player-not-found");
                    return true;
                }
                give(t, Items.tracker());
            }
            case "apply" -> apply(s, a);
            case "remove" -> remove(s, a);
            case "info" -> info(s);
            case "random" -> {
                if (s instanceof Player p) {
                    ItemStack hand = p.getInventory().getItemInMainHand();
                    int n = com.arkcronist.enchants.item.LootEnchanter.roll(hand, plugin.registry(), plugin.settings(),
                            java.util.concurrent.ThreadLocalRandom.current(), true);
                    p.getInventory().setItemInMainHand(hand);
                    plugin.send(s, n > 0 ? "random-done" : "random-none", "%count%", String.valueOf(n));
                }
            }
            case "enchanter" -> {
                if (s instanceof Player p) {
                    plugin.enchanter().open(p);
                }
            }
            default -> help(s, label);
        }
        return true;
    }

    private void help(CommandSender s, String label) {
        String[] lines = {
            "&5&lArkEnchants &7- comandos",
            "&d/" + label + " give <jugador> <encanto> [nivel] [exito] [destruccion] &7- libro",
            "&d/" + label + " mystery <jugador> <grupo> [cantidad] &7- libro misterioso",
            "&d/" + label + " apply <encanto> [nivel] &7- en el item de la mano",
            "&d/" + label + " remove <encanto> &7- quitarlo del item de la mano",
            "&d/" + label + " tracker [jugador] &7- rastreador de almas",
            "&d/" + label + " list [grupo] &7- lista de encantamientos",
            "&d/" + label + " info &7- encantamientos del item de la mano",
            "&d/" + label + " random &7- encantar al azar el item de la mano (como el botin)",
            "&d/" + label + " reload &7- recargar configuracion",
            "&d/enchanter &7- abrir el encantador"};
        for (String l : lines) {
            s.sendMessage(Colors.of(l));
        }
    }

    private void list(CommandSender s, String group) {
        for (Group g : plugin.registry().groups()) {
            if (group != null && !g.id().equalsIgnoreCase(group)) {
                continue;
            }
            List<Enchant> es = plugin.registry().inGroup(g.id());
            if (es.isEmpty()) {
                continue;
            }
            StringBuilder sb = new StringBuilder(g.color() + "&l" + g.name() + " &7(" + es.size() + "): ");
            for (int i = 0; i < es.size(); i++) {
                sb.append(i == 0 ? "" : "&7, ").append(g.color()).append(es.get(i).id());
            }
            s.sendMessage(Colors.of(sb.toString()));
        }
    }

    private void give(CommandSender s, String[] a) {
        if (a.length < 3) {
            plugin.send(s, "usage", "%usage%", "/arkenchants give <jugador> <encanto> [nivel] [exito] [destruccion]");
            return;
        }
        Player t = Bukkit.getPlayerExact(a[1]);
        Enchant e = plugin.registry().get(a[2]);
        if (t == null || e == null) {
            plugin.send(s, t == null ? "player-not-found" : "unknown-enchant", "%enchant%", a[2]);
            return;
        }
        int lvl = a.length > 3 ? parse(a[3], 1) : e.maxLevel();
        int success = a.length > 4 ? parse(a[4], 100) : 100;
        int destroy = a.length > 5 ? parse(a[5], 0) : 0;
        give(t, Items.book(e, Math.max(1, Math.min(lvl, e.maxLevel())), success, destroy));
        plugin.send(s, "given", "%player%", t.getName());
    }

    private void mystery(CommandSender s, String[] a) {
        if (a.length < 3) {
            plugin.send(s, "usage", "%usage%", "/arkenchants mystery <jugador> <grupo> [cantidad]");
            return;
        }
        Player t = Bukkit.getPlayerExact(a[1]);
        Group g = null;
        for (Group x : plugin.registry().groups()) {
            if (x.id().equalsIgnoreCase(a[2])) {
                g = x;
            }
        }
        if (t == null || g == null) {
            plugin.send(s, t == null ? "player-not-found" : "unknown-group", "%group%", a[2]);
            return;
        }
        ItemStack it = Items.mystery(g);
        it.setAmount(Math.max(1, Math.min(64, a.length > 3 ? parse(a[3], 1) : 1)));
        give(t, it);
        plugin.send(s, "given", "%player%", t.getName());
    }

    private void apply(CommandSender s, String[] a) {
        if (!(s instanceof Player p) || a.length < 2) {
            plugin.send(s, "usage", "%usage%", "/arkenchants apply <encanto> [nivel]");
            return;
        }
        Enchant e = plugin.registry().get(a[1]);
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (e == null || Items.empty(hand)) {
            plugin.send(s, e == null ? "unknown-enchant" : "hold-item", "%enchant%", a[1]);
            return;
        }
        Map<String, Integer> m = new LinkedHashMap<>(Items.enchants(hand));
        m.put(e.id(), Math.max(1, a.length > 2 ? parse(a[2], 1) : e.maxLevel()));
        Items.setEnchants(hand, m);
        p.getInventory().setItemInMainHand(hand);
        plugin.send(s, "applied", "%enchant%", Items.format("%group-color%%display% %level%", e, m.get(e.id())));
    }

    private void remove(CommandSender s, String[] a) {
        if (!(s instanceof Player p) || a.length < 2) {
            plugin.send(s, "usage", "%usage%", "/arkenchants remove <encanto|all>");
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        Map<String, Integer> m = new LinkedHashMap<>(Items.enchants(hand));
        if (a[1].equalsIgnoreCase("all")) {
            m.clear();
        } else {
            m.remove(a[1].toLowerCase(Locale.ROOT));
        }
        Items.setEnchants(hand, m);
        p.getInventory().setItemInMainHand(hand);
        plugin.send(s, "removed");
    }

    private void info(CommandSender s) {
        if (!(s instanceof Player p)) {
            return;
        }
        Map<String, Integer> m = Items.enchants(p.getInventory().getItemInMainHand());
        if (m.isEmpty()) {
            plugin.send(s, "no-enchants");
            return;
        }
        for (Map.Entry<String, Integer> en : m.entrySet()) {
            Enchant e = plugin.registry().get(en.getKey());
            s.sendMessage(Colors.of(e == null ? "&c" + en.getKey() + " (ya no existe)"
                    : Items.format("%group-color%%display% %level% &8- &7%description%", e, en.getValue())));
        }
    }

    private static void give(Player t, ItemStack it) {
        t.getInventory().addItem(it).values().forEach(rest -> t.getWorld().dropItemNaturally(t.getLocation(), rest));
    }

    private static int parse(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        if (cmd.getName().equalsIgnoreCase("enchanter")) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(SUBS);
        } else if (a.length == 2 && List.of("give", "mystery", "tracker").contains(a[0].toLowerCase(Locale.ROOT))) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (a.length == 3 && a[0].equalsIgnoreCase("give") || a.length == 2 && List.of("apply", "remove").contains(a[0].toLowerCase(Locale.ROOT))) {
            plugin.registry().all().forEach(e -> out.add(e.id()));
        } else if (a.length == 3 && a[0].equalsIgnoreCase("mystery") || a.length == 2 && a[0].equalsIgnoreCase("list")) {
            plugin.registry().groups().forEach(g -> out.add(g.id()));
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
