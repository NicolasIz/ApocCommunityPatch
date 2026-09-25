package com.arkcronist.enchants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.arkcronist.enchants.engine.EffectLine;
import com.arkcronist.enchants.engine.Effects;
import com.arkcronist.enchants.item.Applying;
import com.arkcronist.enchants.load.EnchantLoader;
import com.arkcronist.enchants.model.Enchant;
import com.arkcronist.enchants.model.Group;
import com.arkcronist.enchants.model.Trigger;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class LoaderTest {

    private static YamlConfiguration resource(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                LoaderTest.class.getResourceAsStream("/" + name), StandardCharsets.UTF_8));
    }

    @Test
    void bundledEnchantsLoadAndUseOnlyKnownEffects() {
        EnchantLoader.Report r = new EnchantLoader.Report();
        Map<String, Enchant> m = EnchantLoader.enchants(resource("enchantments.yml"), r);
        assertEquals(29, m.size());
        assertTrue(r.broken.isEmpty(), r.broken.toString());
        assertTrue(r.unknownTriggers.isEmpty(), r.unknownTriggers.toString());
        for (Enchant e : m.values()) {
            e.levels().values().forEach(l -> l.effects().forEach(fx ->
                    assertTrue(Effects.KNOWN.contains(fx.name()), e.id() + ": " + fx.raw())));
        }
        Enchant v = m.get("vampiro");
        assertEquals(3, v.maxLevel());
        assertTrue(v.triggers().contains(Trigger.ATTACK_MOB));
    }

    @Test
    void groupsMergeColourFromGroupsYmlWithBookSettings() {
        YamlConfiguration cfg = resource("config.yml");
        Map<String, Group> g = EnchantLoader.groups(resource("groups.yml").getConfigurationSection("groups"),
                cfg.getConfigurationSection("groups"));
        assertEquals(6, g.size());
        assertEquals("&6", g.get("LEGENDARY").color());
        assertEquals(35, g.get("LEGENDARY").enchanterCost());
        assertEquals(20, g.get("LEGENDARY").successMin());
    }

    @Test
    void applyingRules() {
        Map<String, Enchant> m = EnchantLoader.enchants(resource("enchantments.yml"), new EnchantLoader.Report());
        Enchant v = m.get("vampiro");
        assertEquals(Applying.Result.OK, Applying.check(v, 2, Map.of(), true, 9, true).result());
        assertEquals(3, Applying.check(v, 2, Map.of("vampiro", 2), true, 9, true).newLevel());
        assertEquals(Applying.Result.ALREADY, Applying.check(v, 3, Map.of("vampiro", 3), true, 9, true).result());
        assertEquals(Applying.Result.ALREADY, Applying.check(v, 1, Map.of("vampiro", 2), true, 9, true).result());
        assertEquals(Applying.Result.NO_SLOTS, Applying.check(v, 1, Map.of("a", 1, "b", 1), true, 2, true).result());
        assertEquals(Applying.Result.NOT_APPLICABLE, Applying.check(v, 1, Map.of(), false, 9, true).result());
    }

    /** Runs over a real AdvancedEnchantments file when AE_ENCHANTS points at one (not part of the build). */
    @Test
    void advancedEnchantmentsFileIfGiven() {
        String path = System.getenv("AE_ENCHANTS");
        if (path == null || !new File(path).isFile()) {
            return;
        }
        EnchantLoader.Report r = new EnchantLoader.Report();
        Map<String, Enchant> m = EnchantLoader.enchants(YamlConfiguration.loadConfiguration(new File(path)), r);
        Map<String, Integer> unknown = new TreeMap<>();
        int lines = 0;
        for (Enchant e : m.values()) {
            for (var l : e.levels().values()) {
                for (EffectLine fx : l.effects()) {
                    lines++;
                    if (!Effects.KNOWN.contains(fx.name())) {
                        unknown.merge(fx.name(), 1, Integer::sum);
                    }
                }
            }
        }
        System.out.println("AE file: " + m.size() + " enchants, " + lines + " effect lines, broken " + r.broken
                + ", unknown types " + r.unknownTriggers + ", unknown effects " + unknown);
        assertTrue(r.broken.isEmpty());
    }
}
