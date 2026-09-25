package com.arkcronist.enchants;

import com.arkcronist.enchants.command.ArkEnchantsCommand;
import com.arkcronist.enchants.engine.EffectLine;
import com.arkcronist.enchants.engine.Effects;
import com.arkcronist.enchants.engine.Engine;
import com.arkcronist.enchants.gui.EnchanterMenu;
import com.arkcronist.enchants.item.Items;
import com.arkcronist.enchants.item.Keys;
import com.arkcronist.enchants.listener.BlockListener;
import com.arkcronist.enchants.listener.BookListener;
import com.arkcronist.enchants.listener.CombatListener;
import com.arkcronist.enchants.listener.JumpListener;
import com.arkcronist.enchants.listener.MiscListener;
import com.arkcronist.enchants.listener.PassiveTask;
import com.arkcronist.enchants.load.EnchantLoader;
import com.arkcronist.enchants.model.Enchant;
import com.arkcronist.enchants.model.EnchantLevel;
import com.arkcronist.enchants.model.Group;
import com.arkcronist.enchants.text.Colors;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * ArkEnchants: custom enchantments configured like AdvancedEnchantments, without packets or resource packs,
 * so ItemsAdder items keep their looks.
 */
public final class ArkEnchants extends JavaPlugin {

    private Settings settings;
    private EnchantRegistry registry;
    private Engine engine;
    private EnchanterMenu enchanter;
    private com.arkcronist.enchants.gui.Menus menus;
    private BukkitTask passive;
    private BukkitTask jumpTask;
    private JumpListener jumps;

    @Override
    public void onEnable() {
        Keys.init(this);
        saveDefaultConfig();
        importOrDefaults();
        load();
        engine = new Engine(this, registry, settings);
        chargeConfig();
        enchanter = new EnchanterMenu(this);
        var pm = getServer().getPluginManager();
        pm.registerEvents(new CombatListener(engine), this);
        pm.registerEvents(new BlockListener(engine), this);
        pm.registerEvents(new MiscListener(engine), this);
        pm.registerEvents(new BookListener(this), this);
        pm.registerEvents(enchanter, this);
        menus = new com.arkcronist.enchants.gui.Menus(this);
        pm.registerEvents(new com.arkcronist.enchants.gui.Menu.Clicks(), this);
        pm.registerEvents(new com.arkcronist.enchants.listener.LootListener(this), this);
        jumps = new JumpListener(settings.passiveInterval);
        pm.registerEvents(jumps, this);
        jumpTask = getServer().getScheduler().runTaskTimer(this, jumps, 4, 4);
        ArkEnchantsCommand cmd = new ArkEnchantsCommand(this);
        for (String name : new String[]{"arkenchants", "enchanter"}) {
            PluginCommand pc = getCommand(name);
            if (pc != null) {
                pc.setExecutor(cmd);
                pc.setTabCompleter(cmd);
            }
        }
        startPassive();
    }

    @Override
    public void onDisable() {
        if (passive != null) {
            passive.cancel();
        }
        if (jumpTask != null) {
            jumpTask.cancel();
        }
        if (engine != null) {
            engine.clones.removeEverything();
        }
    }

    /** First start: bring over AdvancedEnchantments' enchantments.yml and groups.yml if they are there. */
    private void importOrDefaults() {
        File ench = new File(getDataFolder(), "enchantments.yml");
        File groups = new File(getDataFolder(), "groups.yml");
        File ae = new File(getDataFolder().getParentFile(), "AdvancedEnchantments");
        boolean importAe = getConfig().getBoolean("import-advancedenchantments", false);
        if (!ench.exists()) {
            File src = new File(ae, "enchantments.yml");
            if (importAe && src.isFile()) {
                copy(src, ench);
                getLogger().info("Imported " + src.getPath());
            } else {
                saveResource("enchantments.yml", false);
            }
        }
        if (!groups.exists()) {
            File src = new File(ae, "groups.yml");
            if (importAe && src.isFile()) {
                copy(src, groups);
                getLogger().info("Imported " + src.getPath());
            } else {
                saveResource("groups.yml", false);
            }
        }
    }

    /** enchantments.yml first, then the bundled sets that are switched on, then any other enchantments-*.yml. */
    private java.util.List<File> enchantFiles() {
        java.util.List<File> out = new java.util.ArrayList<>();
        File main = new File(getDataFolder(), "enchantments.yml");
        if (main.isFile()) {
            out.add(main);
        }
        java.util.Map<String, Boolean> bundled = java.util.Map.of("enchantments-extra.yml", settings.setExtra,
                "enchantments-advanced.yml", settings.setAdvanced);
        for (String name : new String[]{"enchantments-extra.yml", "enchantments-advanced.yml"}) {
            File f = new File(getDataFolder(), name);
            if (bundled.get(name)) {
                if (!f.exists() && getResource(name) != null) {
                    saveResource(name, false);
                }
                if (f.isFile()) {
                    out.add(f);
                }
            }
        }
        File[] others = getDataFolder().listFiles((d, n) -> n.startsWith("enchantments-") && n.endsWith(".yml")
                && !bundled.containsKey(n));
        if (others != null) {
            java.util.Arrays.sort(others);
            out.addAll(java.util.List.of(others));
        }
        return out;
    }

    private void copy(File from, File to) {
        try {
            Files.createDirectories(to.getParentFile().toPath());
            Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            getLogger().warning("Could not copy " + from + ": " + e.getMessage());
        }
    }

    /** (Re)reads config.yml, groups.yml and enchantments.yml, and says what could not be understood. */
    public void load() {
        reloadConfig();
        settings = new Settings(getConfig());
        YamlConfiguration groupsYml = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "groups.yml"));
        Map<String, Group> groups = EnchantLoader.groups(groupsYml.getConfigurationSection("groups"),
                getConfig().getConfigurationSection("groups"));
        EnchantLoader.Report report = new EnchantLoader.Report();
        Map<String, Enchant> enchants = new java.util.LinkedHashMap<>();
        for (File f : enchantFiles()) {
            Map<String, Enchant> part = EnchantLoader.enchants(YamlConfiguration.loadConfiguration(f), report);
            int added = 0;
            for (var en : part.entrySet()) {
                if (enchants.putIfAbsent(en.getKey(), en.getValue()) == null) {
                    added++;
                } else {
                    report.broken.add(en.getKey() + " (" + f.getName() + "): the name is already used, skipped");
                }
            }
            getLogger().info(f.getName() + ": " + added + " enchantments");
        }
        registry = new EnchantRegistry(enchants, groups);
        Items.configure(registry, settings);
        if (engine != null) {
            engine.reload(registry, settings);
        }
        chargeConfig();
        Map<String, Integer> unknown = new TreeMap<>();
        int lines = 0;
        for (Enchant e : enchants.values()) {
            for (EnchantLevel l : e.levels().values()) {
                for (EffectLine fx : l.effects()) {
                    lines++;
                    if (!Effects.KNOWN.contains(fx.name())) {
                        unknown.merge(fx.name(), 1, Integer::sum);
                    }
                }
            }
        }
        getLogger().info("Loaded " + enchants.size() + " enchantments (" + lines + " effect lines) in " + groups.size() + " groups.");
        if (!report.unknownTriggers.isEmpty()) {
            getLogger().warning("Types not supported (those enchants just never fire on them): " + report.unknownTriggers);
        }
        if (!unknown.isEmpty()) {
            getLogger().warning("Effects not supported (skipped): " + unknown);
        }
        for (String b : report.broken) {
            getLogger().warning("Could not load enchantment " + b);
        }
    }

    private void chargeConfig() {
        if (engine != null) {
            engine.charges.configure(settings.chargeSeconds, settings.chargeReadySeconds);
        }
    }

    public void reloadAll() {
        load();
        startPassive();
    }

    private void startPassive() {
        if (passive != null) {
            passive.cancel();
        }
        int every = settings.passiveInterval;
        passive = getServer().getScheduler().runTaskTimer(this, new PassiveTask(engine, every), every, every);
    }

    public Settings settings() {
        return settings;
    }

    public EnchantRegistry registry() {
        return registry;
    }

    public com.arkcronist.enchants.gui.Menus menus() {
        return menus;
    }

    public EnchanterMenu enchanter() {
        return enchanter;
    }

    /** Sends messages.&lt;key&gt; with the prefix, replacing pairs of (placeholder, value). */
    public void send(CommandSender to, String key, String... pairs) {
        String m = settings.msg(key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            m = m.replace(pairs[i], pairs[i + 1]);
        }
        to.sendMessage(Colors.of(settings.prefix() + m));
    }
}
