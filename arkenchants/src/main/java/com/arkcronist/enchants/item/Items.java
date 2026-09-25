package com.arkcronist.enchants.item;

import com.arkcronist.enchants.EnchantRegistry;
import com.arkcronist.enchants.Settings;
import com.arkcronist.enchants.model.Enchant;
import com.arkcronist.enchants.model.Group;
import com.arkcronist.enchants.model.ItemKinds;
import com.arkcronist.enchants.text.Colors;
import com.arkcronist.enchants.text.Roman;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Reads and writes ArkEnchants data on items. Only the PersistentDataContainer and the plugin's own lore
 * lines are touched: model data, item_model and the lore other plugins (ItemsAdder) put there stay as they are.
 */
public final class Items {

    private static EnchantRegistry registry;
    private static Settings settings;

    private Items() {
    }

    public static void configure(EnchantRegistry r, Settings s) {
        registry = r;
        settings = s;
    }

    public static boolean empty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    public static Map<String, Integer> enchants(ItemStack item) {
        if (empty(item) || !item.hasItemMeta()) {
            return Collections.emptyMap();
        }
        String s = item.getItemMeta().getPersistentDataContainer().get(Keys.ENCHANTS, PersistentDataType.STRING);
        return EnchantData.parse(s);
    }

    public static void setEnchants(ItemStack item, Map<String, Integer> enchants) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (enchants.isEmpty()) {
            pdc.remove(Keys.ENCHANTS);
        } else {
            pdc.set(Keys.ENCHANTS, PersistentDataType.STRING, EnchantData.write(enchants));
        }
        render(meta);
        item.setItemMeta(meta);
    }

    /** Re-draws the lore if another plugin replaced it; returns true when the item changed. */
    public static boolean refresh(ItemStack item) {
        if (empty(item) || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(Keys.ENCHANTS) && !pdc.has(Keys.SOUL_TRACKER) && !pdc.has(Keys.PROTECTED)) {
            return false;
        }
        List<String> want = lines(pdc);
        List<Component> lore = meta.lore();
        if (lore != null && lore.size() >= want.size()) {
            boolean same = true;
            for (int i = 0; i < want.size(); i++) {
                if (!Colors.plain(lore.get(i)).equals(Colors.plain(Colors.of(want.get(i))))) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return false;
            }
        }
        render(meta);
        item.setItemMeta(meta);
        return true;
    }

    private static void render(ItemMeta meta) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        // take out the lines we drew last time (wherever they ended up)
        String old = pdc.get(Keys.LORE, PersistentDataType.STRING);
        if (old != null && !old.isEmpty()) {
            List<String> oldLines = List.of(old.split("\n", -1));
            int n = oldLines.size();
            boolean atTop = lore.size() >= n;
            for (int i = 0; atTop && i < n; i++) {
                atTop = Colors.plain(lore.get(i)).equals(oldLines.get(i));
            }
            if (atTop) {
                lore.subList(0, n).clear();
            } else {
                lore.removeIf(c -> oldLines.contains(Colors.plain(c)) && !Colors.plain(c).isEmpty());
            }
        }
        List<String> fresh = lines(pdc);
        List<Component> comps = new ArrayList<>();
        StringBuilder plain = new StringBuilder();
        for (String s : fresh) {
            Component c = Colors.of(s);
            comps.add(c);
            if (plain.length() > 0) {
                plain.append('\n');
            }
            plain.append(Colors.plain(c));
        }
        comps.addAll(lore);
        meta.lore(comps.isEmpty() ? null : comps);
        if (fresh.isEmpty()) {
            pdc.remove(Keys.LORE);
        } else {
            pdc.set(Keys.LORE, PersistentDataType.STRING, plain.toString());
        }
        if (pdc.has(Keys.ENCHANTS)) {
            meta.setEnchantmentGlintOverride(true);
        } else if (meta.hasEnchantmentGlintOverride() && !meta.hasEnchants()) {
            meta.setEnchantmentGlintOverride(null);
        }
    }

    private static List<String> lines(PersistentDataContainer pdc) {
        List<String> out = new ArrayList<>();
        Map<String, Integer> ench = EnchantData.parse(pdc.get(Keys.ENCHANTS, PersistentDataType.STRING));
        List<Map.Entry<String, Integer>> list = new ArrayList<>(ench.entrySet());
        list.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(e -> {
            Enchant en = registry.get(e.getKey());
            return en == null ? 99 : -registry.rank(en.group());
        }).thenComparing(Map.Entry::getKey));
        for (Map.Entry<String, Integer> e : list) {
            Enchant en = registry.get(e.getKey());
            if (en == null) {
                continue;
            }
            out.add(format(settings.loreFormat, en, e.getValue()));
        }
        if (pdc.has(Keys.SOUL_TRACKER)) {
            int souls = pdc.getOrDefault(Keys.SOULS, PersistentDataType.INTEGER, 0);
            out.add(settings.soulsLore.replace("%souls%", String.valueOf(souls)));
        }
        if (pdc.has(Keys.PROTECTED)) {
            out.add(settings.protectedLore);
        }
        return out;
    }

    public static String format(String pattern, Enchant e, int level) {
        Group g = registry.group(e.group());
        String color = g == null ? "&7" : g.color();
        return pattern.replace("%display%", e.display())
                .replace("%group-color%", color)
                .replace("%group-name%", g == null ? e.group() : g.name())
                .replace("%level%", Roman.of(level))
                .replace("%level-number%", String.valueOf(level))
                .replace("%description%", e.description())
                .replace("%applies-to%", e.appliesTo())
                .replace("%max-level%", Roman.of(e.maxLevel()))
                .replace("%group-color%", color);
    }

    public static boolean applicable(Enchant e, ItemStack item) {
        if (empty(item)) {
            return false;
        }
        String m = item.getType().name();
        boolean edible = item.getType().isEdible();
        for (String a : e.applies()) {
            if (ItemKinds.matches(a, m, edible)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ books
    /** An ArkEnchants book: enchant, level and its success/destroy chances in percent. */
    public record Book(String enchant, int level, double success, double destroy) {
    }

    public static ItemStack book(Enchant e, int level, double success, double destroy) {
        ItemStack it = new ItemStack(settings.bookMaterial);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Colors.of(format(settings.bookName, e, level)));
        List<Component> lore = new ArrayList<>();
        for (String l : settings.bookLore) {
            lore.add(Colors.of(format(l, e, level).replace("%success%", com.arkcronist.enchants.text.Percent.fmt(success))
                    .replace("%destroy%", com.arkcronist.enchants.text.Percent.fmt(destroy))));
        }
        meta.lore(lore);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.BOOK, PersistentDataType.STRING, e.id() + ":" + level);
        pdc.set(Keys.SUCCESS, PersistentDataType.DOUBLE, success);
        pdc.set(Keys.DESTROY, PersistentDataType.DOUBLE, destroy);
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ENCHANTS);
        it.setItemMeta(meta);
        return it;
    }

    /** The book on this item, or null when it is not an ArkEnchants book. */
    public static Book readBook(ItemStack item) {
        if (empty(item) || !item.hasItemMeta()) {
            return null;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String b = pdc.get(Keys.BOOK, PersistentDataType.STRING);
        if (b == null) {
            return null;
        }
        Map<String, Integer> m = EnchantData.parse(b);
        if (m.isEmpty()) {
            return null;
        }
        var e = m.entrySet().iterator().next();
        return new Book(e.getKey(), e.getValue(), percent(pdc, Keys.SUCCESS, 100), percent(pdc, Keys.DESTROY, 0));
    }

    /** Books from 1.0-1.2 kept whole numbers; newer ones keep decimals. */
    static double percent(PersistentDataContainer pdc, org.bukkit.NamespacedKey key, double def) {
        if (pdc.has(key, PersistentDataType.DOUBLE)) {
            return pdc.get(key, PersistentDataType.DOUBLE);
        }
        if (pdc.has(key, PersistentDataType.INTEGER)) {
            return pdc.get(key, PersistentDataType.INTEGER);
        }
        return def;
    }

    public static ItemStack mystery(Group g) {
        ItemStack it = new ItemStack(settings.mysteryMaterial);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Colors.of(groupText(settings.mysteryName, g)));
        List<Component> lore = new ArrayList<>();
        for (String l : settings.mysteryLore) {
            lore.add(Colors.of(groupText(l, g)));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(Keys.MYSTERY, PersistentDataType.STRING, g.id());
        meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    public static String mysteryGroup(ItemStack item) {
        if (empty(item) || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(Keys.MYSTERY, PersistentDataType.STRING);
    }

    public static String groupText(String s, Group g) {
        return s.replace("%group-color%", g.color()).replace("%group-name%", g.name())
                .replace("%success-min%", com.arkcronist.enchants.text.Percent.fmt(g.successMin()))
                .replace("%success-max%", com.arkcronist.enchants.text.Percent.fmt(g.successMax()))
                .replace("%destroy-min%", com.arkcronist.enchants.text.Percent.fmt(g.destroyMin()))
                .replace("%destroy-max%", com.arkcronist.enchants.text.Percent.fmt(g.destroyMax()))
                .replace("%cost%", String.valueOf(g.enchanterCost()));
    }

    // ------------------------------------------------------------------ scrolls and protection
    /** Kinds of scroll: extract (enchant back to a book), protect, purify (removes a curse). */
    public static ItemStack scroll(String kind, double rate) {
        Settings.Scroll sc = settings.scrolls.get(kind);
        ItemStack it = new ItemStack(sc.material());
        ItemMeta meta = it.getItemMeta();
        String pct = com.arkcronist.enchants.text.Percent.fmt(rate);
        meta.displayName(Colors.of(sc.name().replace("%success%", pct)));
        List<Component> lore = new ArrayList<>();
        for (String l : sc.lore()) {
            lore.add(Colors.of(l.replace("%success%", pct)));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(Keys.SCROLL, PersistentDataType.STRING, kind);
        meta.getPersistentDataContainer().set(Keys.SCROLL_RATE, PersistentDataType.DOUBLE, rate);
        meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    /** "extract", "protect", "purify", "soul_tracker" or null. */
    public static String scrollKind(ItemStack item) {
        if (empty(item) || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(Keys.SCROLL, PersistentDataType.STRING);
    }

    public static double scrollRate(ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(Keys.SCROLL_RATE, PersistentDataType.DOUBLE, 100.0);
    }

    public static boolean isProtected(ItemStack item) {
        return !empty(item) && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(Keys.PROTECTED);
    }

    public static void setProtected(ItemStack item, boolean on) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        if (on) {
            meta.getPersistentDataContainer().set(Keys.PROTECTED, PersistentDataType.BYTE, (byte) 1);
        } else {
            meta.getPersistentDataContainer().remove(Keys.PROTECTED);
        }
        render(meta);
        item.setItemMeta(meta);
    }

    // ------------------------------------------------------------------ souls
    public static ItemStack tracker() {
        ItemStack it = new ItemStack(settings.trackerMaterial);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Colors.of(settings.trackerName));
        List<Component> lore = new ArrayList<>();
        for (String l : settings.trackerLore) {
            lore.add(Colors.of(l));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(Keys.SCROLL, PersistentDataType.STRING, "soul_tracker");
        it.setItemMeta(meta);
        return it;
    }

    public static boolean isTracker(ItemStack item) {
        return !empty(item) && item.hasItemMeta()
                && "soul_tracker".equals(item.getItemMeta().getPersistentDataContainer().get(Keys.SCROLL, PersistentDataType.STRING));
    }

    public static boolean tracks(ItemStack item) {
        return !empty(item) && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(Keys.SOUL_TRACKER);
    }

    public static int souls(ItemStack item) {
        if (!tracks(item)) {
            return 0;
        }
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(Keys.SOULS, PersistentDataType.INTEGER, 0);
    }

    public static void setSouls(ItemStack item, int souls, boolean track) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (track) {
            pdc.set(Keys.SOUL_TRACKER, PersistentDataType.BYTE, (byte) 1);
        }
        if (!pdc.has(Keys.SOUL_TRACKER)) {
            return;
        }
        pdc.set(Keys.SOULS, PersistentDataType.INTEGER, Math.max(0, souls));
        render(meta);
        item.setItemMeta(meta);
    }

    /**
     * Items enchanted by AdvancedEnchantments carry their enchants as lore lines ("Harvest III"). This turns
     * those lines into ArkEnchants data, once, and returns true when the item changed.
     */
    public static boolean convertLegacy(ItemStack item) {
        if (empty(item) || !item.hasItemMeta() || !settings.convertLegacyLore) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta.getPersistentDataContainer().has(Keys.ENCHANTS) || meta.lore() == null) {
            return false;
        }
        Map<String, String> byName = new java.util.HashMap<>();
        for (Enchant e : registry.all()) {
            byName.put(normal(Colors.plain(Colors.of(e.display().replace("%group-color%", "")))), e.id());
        }
        Map<String, Integer> found = new LinkedHashMap<>();
        List<Component> keep = new ArrayList<>();
        for (Component line : meta.lore()) {
            String plain = Colors.plain(line).trim();
            int sp = plain.lastIndexOf(' ');
            String id = null;
            int lvl = -1;
            if (sp > 0) {
                lvl = com.arkcronist.enchants.text.Roman.parse(plain.substring(sp + 1).trim());
                if (lvl < 0) {
                    try {
                        lvl = Integer.parseInt(plain.substring(sp + 1).trim());
                    } catch (NumberFormatException ignored) {
                        lvl = -1;
                    }
                }
                if (lvl > 0) {
                    id = byName.get(normal(plain.substring(0, sp)));
                }
            }
            if (id != null) {
                found.put(id, lvl);
            } else {
                keep.add(line);
            }
        }
        if (found.isEmpty()) {
            return false;
        }
        meta.lore(keep.isEmpty() ? null : keep);
        item.setItemMeta(meta);
        setEnchants(item, found);
        return true;
    }

    private static String normal(String s) {
        return s.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static Map<String, Integer> copy(Map<String, Integer> m) {
        return new LinkedHashMap<>(m);
    }
}
