package com.arkcronist.enchants.item;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** The "id:level,id:level" string ArkEnchants keeps in an item's PersistentDataContainer. */
public final class EnchantData {

    private EnchantData() {
    }

    public static Map<String, Integer> parse(String s) {
        if (s == null || s.isBlank()) {
            return Collections.emptyMap();
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String part : s.split(",")) {
            int c = part.lastIndexOf(':');
            if (c <= 0) {
                continue;
            }
            try {
                int lvl = Integer.parseInt(part.substring(c + 1).trim());
                if (lvl > 0) {
                    out.put(part.substring(0, c).trim().toLowerCase(Locale.ROOT), lvl);
                }
            } catch (NumberFormatException ignored) {
                // skip a damaged entry, keep the rest
            }
        }
        return out;
    }

    public static String write(Map<String, Integer> enchants) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : enchants.entrySet()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(e.getKey()).append(':').append(e.getValue());
        }
        return sb.toString();
    }
}
