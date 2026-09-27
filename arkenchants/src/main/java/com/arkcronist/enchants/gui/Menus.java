package com.arkcronist.enchants.gui;

import static com.arkcronist.enchants.gui.Menu.icon;

import com.arkcronist.enchants.ArkEnchants;
import com.arkcronist.enchants.item.Items;
import com.arkcronist.enchants.item.LootEnchanter;
import com.arkcronist.enchants.model.Enchant;
import com.arkcronist.enchants.model.Group;
import com.arkcronist.enchants.model.Trigger;
import com.arkcronist.enchants.text.Percent;
import com.arkcronist.enchants.text.Roman;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/** The /ake menus: a main page for everyone and an admin section to hand out books, scrolls and more. */
public final class Menus {

    /** What clicking an enchant does in the list. */
    enum Mode { BROWSE, GIVE, APPLY }

    private static final int[] SUCCESS_PRESETS = {100, 75, 50, 25, 10, 1};
    private final ArkEnchants plugin;

    public Menus(ArkEnchants plugin) {
        this.plugin = plugin;
    }

    private boolean admin(Player p) {
        return p.hasPermission("arkenchants.admin");
    }

    // ------------------------------------------------------------------ main
    /** Where the books go, in groups.yml order: two rows of three in the middle of the purple strip. */
    private static final int[] BOOK_SLOTS = {30, 31, 32, 39, 40, 41, 21, 22, 23};
    /** Scrolls down the right-hand column. */
    private static final int[] SCROLL_SLOTS = {17, 26, 35, 44};

    public void main(Player p) {
        Menu m = new Menu(6, plugin.settings().enchanterTitle);
        for (int i = 0; i < m.size(); i++) {
            int col = i % 9;
            pane(m, i, col >= 2 && col <= 6 ? Material.PURPLE_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE);
        }
        pane(m, 22, Material.MAGENTA_STAINED_GLASS_PANE);

        m.set(4, icon(Material.EXPERIENCE_BOTTLE, "&a&lTu experiencia", "&8▪ &fTienes &a" + p.getLevel() + " &fniveles.", "",
                "&7Los libros y pergaminos se", "&7pagan con niveles de experiencia."), null);

        List<Group> groups = plugin.enchanter().groups();
        for (int i = 0; i < groups.size() && i < BOOK_SLOTS.length; i++) {
            Group g = groups.get(i);
            m.set(BOOK_SLOTS[i], bookIcon(p, g), e -> {
                plugin.enchanter().buy(p, g);
                main(p);
            });
        }
        var scrolls = plugin.settings().scrolls.values().stream().filter(sc -> sc.cost() > 0).toList();
        for (int i = 0; i < scrolls.size() && i < SCROLL_SLOTS.length; i++) {
            var sc = scrolls.get(i);
            m.set(SCROLL_SLOTS[i], scrollIcon(p, sc), e -> {
                plugin.enchanter().buyScroll(p, sc.kind());
                main(p);
            });
        }

        m.set(9, icon(Material.BOOKSHELF, "&b&lCatalogo", "&8▪ &fTodos los encantamientos", "&8▪ &fQue hacen y a que se aplican", "",
                "&7" + plugin.registry().all().size() + " encantamientos", "", "&b➟ &fClic para ver"), e -> groups(p, Mode.BROWSE));
        m.set(18, handIcon(p), e -> {
            p.closeInventory();
            p.performCommand("arkenchants info");
        });
        m.set(27, icon(Material.ENCHANTED_BOOK, "&d&lComo se usan", "&8▪ &fCompra un libro de la rareza", "&f  que quieras.",
                "&8▪ &fClic derecho con el libro en", "&f  la mano para descubrirlo.", "&8▪ &fArrastralo encima de tu item.", "",
                "&7Si falla puede romper el item:", "&7usa un &fPergamino de Proteccion&7."), null);
        m.set(36, icon(new ItemStack(Material.LANTERN), "&e&lRarezas", rarityLore()), null);
        if (admin(p)) {
            m.set(45, icon(Material.COMMAND_BLOCK, "&c&lAdmin", "&8▪ &fSacar libros y pergaminos", "&8▪ &fEncantar la mano", "&8▪ &fRecargar",
                    "", "&c➟ &fClic para abrir"), e -> adminMenu(p));
        }
        m.set(49, icon(Material.BARRIER, "&cCerrar"), e -> p.closeInventory());
        m.open(p);
    }

    private static void pane(Menu m, int slot, Material mat) {
        m.set(slot, icon(mat, " "), null);
    }

    private List<String> rarityLore() {
        List<String> l = new ArrayList<>();
        for (Group g : plugin.registry().groups()) {
            if (!g.curse()) {
                l.add("&8▪ " + g.color() + g.name() + " &7" + Percent.fmt(g.successMin()) + "-" + Percent.fmt(g.successMax()) + "% exito");
            }
        }
        l.add("");
        l.add("&7Cuanto mas rara, mas fuerte");
        l.add("&7y mas arriesgada.");
        return l;
    }

    private ItemStack bookIcon(Player p, Group g) {
        List<String> lore = new ArrayList<>();
        lore.add("&fExamina para recibir un");
        lore.add("&flibro de encantamiento " + g.color() + g.name() + "&f.");
        lore.add("");
        lore.add("&8▪ &7Exito: &a" + Percent.fmt(g.successMin()) + "-" + Percent.fmt(g.successMax()) + "%");
        lore.add("&8▪ &7Rotura: &c" + Percent.fmt(g.destroyMin()) + "-" + Percent.fmt(g.destroyMax()) + "%");
        lore.add("&8▪ &7Encantamientos: &f" + plugin.registry().inGroup(g.id()).size());
        lore.add("");
        lore.add(cost(p, g.enchanterCost()));
        return icon(Items.mystery(g), g.color() + "&lEncantamiento " + g.name() + " &7(Clic)", lore);
    }

    private ItemStack scrollIcon(Player p, com.arkcronist.enchants.Settings.Scroll sc) {
        List<String> lore = new ArrayList<>();
        for (String l : sc.lore()) {
            if (!l.contains("%success%")) {
                lore.add(l);
            }
        }
        lore.add("");
        if (sc.min() < 100) {
            lore.add("&8▪ &7Exito: &a" + Percent.fmt(sc.min()) + "-" + Percent.fmt(sc.max()) + "%");
        }
        lore.add(cost(p, sc.cost()));
        return icon(Items.scroll(sc.kind(), sc.max()), sc.name().replace(" &7(%success%%)", "").replace("%success%", "") + " &7(Clic)", lore);
    }

    private static String cost(Player p, int cost) {
        int miss = EnchanterMenu.missing(p, cost);
        return "&b&lCOSTO &f" + cost + " niveles " + (miss > 0 ? "&7(Te faltan &c" + miss + " &7niveles)" : "&a✔");
    }

    // ------------------------------------------------------------------ admin
    void adminMenu(Player p) {
        if (!admin(p)) {
            return;
        }
        Menu m = new Menu(3, "&8Admin &7| &5ArkEnchants");
        m.set(10, icon(Material.ENCHANTED_BOOK, "&6&lSacar libros", "&7Elige cualquier encantamiento,", "&7nivel y % de exito.",
                "", "&eClic para abrir"), e -> groups(p, Mode.GIVE));
        m.set(11, icon(Material.BOOK, "&6&lLibros misteriosos", "&7Uno por rareza.", "", "&eClic para abrir"), e -> mystery(p));
        m.set(12, icon(Material.INK_SAC, "&6&lPergaminos", "&7Extraccion, proteccion, purificacion", "&7y rastreador de almas.", "",
                "&eClic para abrir"), e -> scrolls(p));
        m.set(13, icon(Material.ANVIL, "&6&lEncantar la mano", "&7Pon cualquier encantamiento directamente",
                "&7en el item que tienes en la mano.", "", "&eClic para elegir"), e -> groups(p, Mode.APPLY));
        m.set(14, icon(Material.EXPERIENCE_BOTTLE, "&6&lEncantar al azar", "&7Encanta el item de la mano como",
                "&7si saliera de un cofre de botin.", "", "&eClic para tirar"), e -> {
                    ItemStack hand = p.getInventory().getItemInMainHand();
                    int n = LootEnchanter.roll(hand, plugin.registry(), plugin.settings(), ThreadLocalRandom.current(), true);
                    p.getInventory().setItemInMainHand(hand);
                    plugin.send(p, n > 0 ? "random-done" : "random-none", "%count%", String.valueOf(n));
                    adminMenu(p);
                });
        m.set(15, icon(Material.SPYGLASS, "&6&lQuitar encantamientos", "&7Quita uno (clic) o todos (clic derecho)",
                "&7del item de la mano.", "", "&eClic para abrir"), e -> remove(p));
        m.set(16, icon(Material.REDSTONE, "&c&lRecargar", "&7Vuelve a leer config y encantamientos.", "", "&eClic para recargar"), e -> {
            plugin.reloadAll();
            plugin.send(p, "reloaded", "%count%", String.valueOf(plugin.registry().all().size()));
            adminMenu(p);
        });
        back(m, p);
        m.fill(Material.BLACK_STAINED_GLASS_PANE).open(p);
    }

    private ItemStack handIcon(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        Map<String, Integer> ench = Items.enchants(hand);
        List<String> lore = new ArrayList<>();
        if (Items.empty(hand)) {
            lore.add("&7No tienes nada en la mano.");
        } else if (ench.isEmpty()) {
            lore.add("&7Este item no tiene encantamientos.");
        } else {
            for (Map.Entry<String, Integer> en : ench.entrySet()) {
                Enchant e = plugin.registry().get(en.getKey());
                lore.add(e == null ? "&8" + en.getKey() : Items.format("%group-color%%display% %level%", e, en.getValue()));
            }
        }
        if (Items.isProtected(hand)) {
            lore.add("&f✦ Protegido");
        }
        lore.add("");
        lore.add("&a➟ &fClic para ver los detalles en el chat");
        return icon(Items.empty(hand) ? new ItemStack(Material.ANVIL) : hand, "&a&lTu item", lore);
    }

    // ------------------------------------------------------------------ groups and enchant lists
    void groups(Player p, Mode mode) {
        List<Group> groups = new ArrayList<>();
        for (Group g : plugin.registry().groups()) {
            if (count(g) > 0 && (mode != Mode.BROWSE || !g.curse() || admin(p))) {
                groups.add(g);
            }
        }
        String title = switch (mode) {
            case BROWSE -> "&8Catalogo";
            case GIVE -> "&8Sacar libros";
            case APPLY -> "&8Encantar la mano";
        };
        Menu m = new Menu(3, title);
        int start = Math.max(0, (9 - groups.size()) / 2);
        for (int i = 0; i < groups.size() && i < 9; i++) {
            Group g = groups.get(i);
            ItemStack base = Items.mystery(g);
            m.set(9 + start + i, icon(base, g.color() + "&l" + g.name(), List.of("&7" + count(g) + " encantamientos", "",
                    "&eClic para ver")), e -> list(p, g, mode, 0));
        }
        if (mode == Mode.BROWSE) {
            back(m, p);
        } else {
            m.set(m.size() - 5, icon(Material.OAK_DOOR, "&7« Admin"), e -> adminMenu(p));
        }
        m.fill(Material.BLACK_STAINED_GLASS_PANE).open(p);
    }

    private int count(Group g) {
        int n = 0;
        for (Enchant e : plugin.registry().all()) {
            if (e.group().equalsIgnoreCase(g.id())) {
                n++;
            }
        }
        return n;
    }

    void list(Player p, Group g, Mode mode, int page) {
        List<Enchant> all = new ArrayList<>();
        for (Enchant e : plugin.registry().all()) {
            if (e.group().equalsIgnoreCase(g.id())) {
                all.add(e);
            }
        }
        all.sort((a, b) -> a.id().compareTo(b.id()));
        int pages = Math.max(1, (all.size() + 44) / 45);
        int pg = Math.max(0, Math.min(page, pages - 1));
        Menu m = new Menu(6, g.color() + "&l" + g.name() + " &8(" + (pg + 1) + "/" + pages + ")");
        for (int i = 0; i < 45 && pg * 45 + i < all.size(); i++) {
            Enchant e = all.get(pg * 45 + i);
            m.set(i, enchantIcon(e, mode), click -> onEnchant(p, e, g, mode, pg, click));
        }
        if (pg > 0) {
            m.set(45, icon(Material.ARROW, "&e« Anterior"), e -> list(p, g, mode, pg - 1));
        }
        if (pg < pages - 1) {
            m.set(53, icon(Material.ARROW, "&eSiguiente »"), e -> list(p, g, mode, pg + 1));
        }
        m.set(49, icon(Material.OAK_DOOR, "&7« Volver a las rarezas"), e -> groups(p, mode));
        m.fill(Material.GRAY_STAINED_GLASS_PANE).open(p);
    }

    private ItemStack enchantIcon(Enchant e, Mode mode) {
        List<String> lore = new ArrayList<>();
        lore.add("&7" + e.description());
        lore.add("");
        lore.add("&7Aplica a: &f" + e.appliesTo());
        lore.add("&7Niveles: &f" + (e.maxLevel() <= 1 ? "I" : "I - " + Roman.of(e.maxLevel())));
        lore.add("&7Se activa: &f" + triggers(e));
        lore.add("&8id: " + e.id());
        switch (mode) {
            case GIVE -> {
                lore.add("");
                lore.add("&eClic: &flibro nivel " + Roman.of(e.maxLevel()) + " al 100%");
                lore.add("&eClic derecho: &felegir nivel y % de exito");
            }
            case APPLY -> {
                lore.add("");
                lore.add("&eClic: &fponer nivel " + Roman.of(e.maxLevel()) + " en tu mano");
                lore.add("&eClic derecho: &felegir nivel");
            }
            default -> {
            }
        }
        return icon(new ItemStack(Material.ENCHANTED_BOOK), Items.format("%group-color%&l%display%", e, e.maxLevel()), lore);
    }

    private static String triggers(Enchant e) {
        List<String> out = new ArrayList<>();
        for (Trigger t : e.triggers()) {
            out.add(switch (t) {
                case ATTACK -> "golpear jugadores";
                case ATTACK_MOB -> "golpear mobs";
                case CHARGED_ATTACK -> "ataque cargado";
                case DEFENSE, DEFENSE_MOB -> "recibir golpes";
                case DEFENSE_PROJECTILE -> "recibir flechas";
                case SHOOT, SHOOT_MOB -> "acertar disparos";
                case BOW_FIRE -> "disparar";
                case MINING -> "romper bloques";
                case KILL_MOB, KILL_PLAYER -> "matar";
                case DEATH -> "morir";
                case EFFECT_STATIC, HELD -> "siempre activo";
                case REPEATING -> "cada pocos segundos";
                default -> t.name().toLowerCase(Locale.ROOT).replace('_', ' ');
            });
        }
        return String.join(", ", new java.util.LinkedHashSet<>(out));
    }

    private void onEnchant(Player p, Enchant e, Group g, Mode mode, int page, InventoryClickEvent click) {
        if (mode == Mode.BROWSE) {
            return;
        }
        if (click.isRightClick()) {
            levels(p, e, g, mode, page, 100);
        } else if (mode == Mode.GIVE) {
            giveBook(p, e, e.maxLevel(), 100);
        } else {
            applyHand(p, e, e.maxLevel());
        }
    }

    /** Level buttons, and for books a row of success chances to pick first. */
    private void levels(Player p, Enchant e, Group g, Mode mode, int page, double success) {
        Menu m = new Menu(mode == Mode.GIVE ? 4 : 3, Items.format("%group-color%%display%", e, 1));
        int max = Math.max(1, e.maxLevel());
        int start = Math.max(0, (9 - Math.min(9, max)) / 2);
        for (int l = 1; l <= max && l <= 9; l++) {
            int lvl = l;
            ItemStack b = new ItemStack(Material.ENCHANTED_BOOK, lvl);
            m.set(start + l - 1, icon(b, Items.format("%group-color%%display% %level%", e, lvl),
                    List.of(mode == Mode.GIVE ? "&7Exito: &a" + Percent.fmt(success) + "%" : "&7Se pondra en tu mano", "",
                            "&eClic para " + (mode == Mode.GIVE ? "sacar el libro" : "aplicarlo"))), c -> {
                if (mode == Mode.GIVE) {
                    giveBook(p, e, lvl, success);
                } else {
                    applyHand(p, e, lvl);
                }
            });
        }
        if (mode == Mode.GIVE) {
            for (int i = 0; i < SUCCESS_PRESETS.length; i++) {
                double v = SUCCESS_PRESETS[i];
                boolean on = v == success;
                m.set(18 + i, icon(on ? Material.LIME_DYE : Material.GRAY_DYE, (on ? "&a&l" : "&7") + Percent.fmt(v) + "% de exito",
                        on ? "&aSeleccionado" : "&eClic para elegir"), c -> levels(p, e, g, mode, page, v));
            }
            boolean tiny = success == 0.1;
            m.set(24, icon(tiny ? Material.LIME_DYE : Material.RED_DYE, (tiny ? "&a&l" : "&c") + "0.1% de exito",
                    tiny ? "&aSeleccionado" : "&eClic para elegir"), c -> levels(p, e, g, mode, page, 0.1));
            m.set(25, icon(Material.PURPLE_DYE, "&dAl azar (como su rareza)", "&7Exito " + Percent.fmt(g.successMin()) + "-"
                    + Percent.fmt(g.successMax()) + "%, rotura " + Percent.fmt(g.destroyMin()) + "-" + Percent.fmt(g.destroyMax()) + "%",
                    "", "&eClic: libro nivel " + Roman.of(max) + " con % al azar"), c -> {
                        ThreadLocalRandom r = ThreadLocalRandom.current();
                        give(p, Items.book(e, max, Percent.roll(g.successMin(), g.successMax(), r),
                                Percent.roll(g.destroyMin(), g.destroyMax(), r)));
                    });
        }
        m.set(m.size() - 5, icon(Material.OAK_DOOR, "&7« Volver"), c -> list(p, g, mode, page));
        m.fill(Material.BLACK_STAINED_GLASS_PANE).open(p);
    }

    private void giveBook(Player p, Enchant e, int level, double success) {
        give(p, Items.book(e, level, success, 0));
    }

    private void applyHand(Player p, Enchant e, int level) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (Items.empty(hand)) {
            plugin.send(p, "hold-item");
            return;
        }
        Map<String, Integer> m = new LinkedHashMap<>(Items.enchants(hand));
        m.put(e.id(), level);
        Items.setEnchants(hand, m);
        p.getInventory().setItemInMainHand(hand);
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1, 1.2f);
        plugin.send(p, "applied", "%enchant%", Items.format("%group-color%%display% %level%", e, level));
    }

    // ------------------------------------------------------------------ mystery books, scrolls, removing
    void mystery(Player p) {
        Menu m = new Menu(3, "&8Libros misteriosos");
        List<Group> groups = new ArrayList<>();
        for (Group g : plugin.registry().groups()) {
            if (!plugin.registry().inGroup(g.id()).isEmpty()) {
                groups.add(g);
            }
        }
        int start = Math.max(0, (9 - groups.size()) / 2);
        for (int i = 0; i < groups.size() && i < 9; i++) {
            Group g = groups.get(i);
            m.set(9 + start + i, icon(Items.mystery(g), null, List.of("&eClic: &f1  &8|  &eClic derecho: &f16")), c -> {
                ItemStack it = Items.mystery(g);
                it.setAmount(c.isRightClick() ? 16 : 1);
                give(p, it);
            });
        }
        m.set(m.size() - 5, icon(Material.OAK_DOOR, "&7« Admin"), e -> adminMenu(p));
        m.fill(Material.BLACK_STAINED_GLASS_PANE).open(p);
    }

    void scrolls(Player p) {
        Menu m = new Menu(3, "&8Pergaminos");
        int slot = 10;
        for (var sc : plugin.settings().scrolls.values()) {
            String name = sc.name().replace(" &7(%success%%)", "").replace("%success%", "");
            m.set(slot, icon(Items.scroll(sc.kind(), 100), name, List.of("&eClic: &fal 100%",
                    "&eClic derecho: &fcon % al azar (" + Percent.fmt(sc.min()) + "-" + Percent.fmt(sc.max()) + "%)")), c -> {
                        double rate = c.isRightClick() ? Percent.roll(sc.min(), sc.max(), ThreadLocalRandom.current()) : 100;
                        give(p, Items.scroll(sc.kind(), rate));
                    });
            slot += 2;
        }
        m.set(16, icon(Items.tracker(), null, List.of("&eClic para sacarlo")), c -> give(p, Items.tracker()));
        m.set(m.size() - 5, icon(Material.OAK_DOOR, "&7« Admin"), e -> adminMenu(p));
        m.fill(Material.BLACK_STAINED_GLASS_PANE).open(p);
    }

    void remove(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        Map<String, Integer> ench = new LinkedHashMap<>(Items.enchants(hand));
        Menu m = new Menu(3, "&8Quitar encantamientos");
        int i = 0;
        for (Map.Entry<String, Integer> en : ench.entrySet()) {
            if (i >= 18) {
                break;
            }
            Enchant e = plugin.registry().get(en.getKey());
            String name = e == null ? "&8" + en.getKey() : Items.format("%group-color%%display% %level%", e, en.getValue());
            m.set(i++, icon(Material.ENCHANTED_BOOK, name, "&eClic: quitar este", "&eClic derecho: quitar todos"), c -> {
                Map<String, Integer> now = new LinkedHashMap<>(Items.enchants(p.getInventory().getItemInMainHand()));
                if (c.isRightClick()) {
                    now.clear();
                } else {
                    now.remove(en.getKey());
                }
                ItemStack h = p.getInventory().getItemInMainHand();
                Items.setEnchants(h, now);
                p.getInventory().setItemInMainHand(h);
                plugin.send(p, "removed");
                remove(p);
            });
        }
        if (ench.isEmpty()) {
            m.set(13, icon(Material.BARRIER, "&7El item de tu mano no tiene encantamientos"), null);
        }
        m.set(m.size() - 5, icon(Material.OAK_DOOR, "&7« Admin"), e -> adminMenu(p));
        m.fill(Material.BLACK_STAINED_GLASS_PANE).open(p);
    }

    private void back(Menu m, Player p) {
        m.set(m.size() - 5, icon(Material.OAK_DOOR, "&7« Menu principal"), e -> main(p));
    }

    private static void give(Player p, ItemStack it) {
        p.getInventory().addItem(it).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
    }
}
