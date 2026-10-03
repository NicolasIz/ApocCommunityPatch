package com.arkcronist.content.bukkit.hooks;

import com.arkcronist.content.bukkit.ArkContentPlugin;
import com.arkcronist.content.bukkit.hooks.auraskills.AuraSkillsHook;
import com.arkcronist.content.bukkit.hooks.citizens.CitizensHook;
import com.arkcronist.content.bukkit.hooks.decentholograms.DecentHologramsHook;
import com.arkcronist.content.bukkit.hooks.economyshopgui.EconomyShopGuiHook;
import com.arkcronist.content.bukkit.hooks.executableblocks.ExecutableBlocksHook;
import com.arkcronist.content.bukkit.hooks.griefprevention.GriefPreventionProtection;
import com.arkcronist.content.bukkit.hooks.iris.IrisHook;
import com.arkcronist.content.bukkit.hooks.mcmmo.McMMOHook;
import com.arkcronist.content.bukkit.hooks.mmoitems.MMOItemsHook;
import com.arkcronist.content.bukkit.hooks.modelengine.ModelEngineHook;
import com.arkcronist.content.bukkit.hooks.mythicarmor.MythicArmorHook;
import com.arkcronist.content.bukkit.hooks.mythicmobs.MythicMobsHook;
import com.arkcronist.content.bukkit.hooks.placeholderapi.ArkContentExpansion;
import com.arkcronist.content.bukkit.hooks.shopgui.ShopGuiPlusHook;
import com.arkcronist.content.bukkit.hooks.skillapi.FabledHook;
import com.arkcronist.content.bukkit.hooks.skillapi.SkillAPIHook;
import com.arkcronist.content.bukkit.hooks.vault.VaultShop;
import com.arkcronist.content.bukkit.hooks.worldguard.WorldGuardProtection;
import com.arkcronist.content.bukkit.menu.Shop;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Integrations with other plugins, each switched on only when that plugin is there.
 *
 * <p>Every hook lives in its own package and is the only code that names the other plugin's
 * classes. Those classes are visible because paper-plugin.yml lists the plugin as an optional
 * dependency with {@code join-classpath}; when it is not installed they do not exist at all, so a
 * hook is created only after one of its classes has been found, and whatever goes wrong while
 * creating it - a missing class, an API that changed shape - is logged and leaves the rest of the
 * plugin running without that hook.</p>
 *
 * <p>Presence is decided by the classes rather than by the plugin being enabled: MythicMobs and
 * MythicArmor are made to load after this plugin, so that it can listen to them from the start,
 * and are not enabled yet when this runs.</p>
 *
 * <p>A hook keeps failing safe after it has started, too: a skills plugin whose API throws while
 * paying out experience is logged once and left out from then on, and the break or harvest that
 * earned it goes ahead regardless.</p>
 */
public final class HookManager {

    private final ArkContentPlugin plugin;
    private final Logger logger;
    private final List<String> active = new ArrayList<>();
    private final List<ContentHook> contentHooks = new ArrayList<>();
    /** Skills plugins, by hook name. */
    private final Map<String, SkillXpHook> skillHooks = new HashMap<>();
    private final List<AutoCloseable> closing = new ArrayList<>();
    /** Plugins that work with this one's items as they are, with no hook needed. */
    private final List<String> natively = new ArrayList<>();
    private ModelEngineBridge modelEngine;
    private Shop shop;
    private NpcBridge npcs;
    private Predicate<Block> foreignBlocks = block -> false;

    public HookManager(ArkContentPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * The hooks that must be in place before other plugins enable: MMOItems reads its item configs
     * as it enables, and the stat they use has to be registered by then. Main thread, from onLoad.
     */
    public void load() {
        create("MMOItems", "net.Indyuce.mmoitems.stat.type.StringStat", () -> {
            MMOItemsHook.register(plugin::items, logger);
            return Boolean.TRUE;
        });
    }

    /** Detects and starts every other hook. Main thread, from onEnable. */
    public void enable() {
        MythicMobsHook mythicMobs = create("MythicMobs", "io.lumine.mythic.bukkit.events.MythicDropLoadEvent",
                () -> new MythicMobsHook(plugin.items(), plugin.itemFactory(), logger));
        if (mythicMobs != null) {
            listen(mythicMobs);
            contentHooks.add(mythicMobs);
        }

        modelEngine = create("ModelEngine", "com.ticxo.modelengine.api.ModelEngineAPI",
                () -> new ModelEngineHook(logger));

        MythicArmorHook mythicArmor = create("MythicArmor", "net.mythic.mythicArmor.api.MythicArmorGeneratePackEvent",
                () -> new MythicArmorHook(plugin, plugin.pipeline()));
        if (mythicArmor != null) {
            listen(mythicArmor);
        }

        // Loads before this plugin; registered at once, so TAB and DeluxeMenus find it from the start.
        create("PlaceholderAPI", "me.clip.placeholderapi.expansion.PlaceholderExpansion", () -> {
            ArkContentExpansion expansion = new ArkContentExpansion(plugin.getPluginMeta().getVersion(),
                    plugin.emojis(), plugin.items(), plugin.itemFactory());
            if (!expansion.register()) {
                throw new IllegalStateException("PlaceholderAPI refused the arkcontent expansion");
            }
            return expansion;
        });

        // Loads after this plugin: the hook listens for the moment ShopGUI+ asks for item providers.
        ShopGuiPlusHook shopGui = create("ShopGUI+", "net.brcdev.shopgui.event.ShopGUIPlusPostEnableEvent",
                () -> new ShopGuiPlusHook(plugin.items(), plugin.itemFactory(), logger));
        if (shopGui != null) {
            listen(shopGui);
        }

        // Iris loads first - it generates worlds - so its data service is running by now.
        create("Iris", "com.volmit.iris.core.link.ExternalDataProvider",
                () -> new IrisHook(plugin.getName(), plugin.blocks(), plugin.items(), plugin.itemFactory()));

        // Protection plugins: asked before this plugin changes a block on its own.
        create("WorldGuard", "com.sk89q.worldguard.protection.regions.RegionQuery", () -> {
            WorldGuardProtection worldGuard = new WorldGuardProtection();
            plugin.protection().add(worldGuard);
            return worldGuard;
        });
        create("GriefPrevention", "me.ryanhamshire.GriefPrevention.Claim", () -> {
            GriefPreventionProtection griefPrevention = new GriefPreventionProtection();
            plugin.protection().add(griefPrevention);
            return griefPrevention;
        });

        // Skills plugins: experience for custom blocks and crops. AuraSkills also knows the items,
        // for its menus - registered now, before it loads them on the first tick.
        AuraSkillsHook auraSkills = create("AuraSkills", "dev.aurelium.auraskills.api.AuraSkillsBukkit",
                () -> new AuraSkillsHook(plugin.items(), plugin.itemFactory(), logger));
        if (auraSkills != null) {
            contentHooks.add(auraSkills);
            skillHooks.put("AuraSkills", auraSkills);
        }
        skill("mcMMO", "com.gmail.nossr50.api.ExperienceAPI", McMMOHook::new);
        // SkillAPI and its fork ProSkillAPI share one API; Fabled is the renamed successor.
        skill("SkillAPI", "com.sucy.skill.api.enums.ExpSource", SkillAPIHook::new);
        skill("Fabled", "studio.magemonkey.fabled.api.enums.ExpSource", FabledHook::new);

        // Economy: /arkcontent shop sells the items that have a price.
        shop = create("Vault", "net.milkbowl.vault.economy.Economy", () -> new VaultShop(plugin.getServer()));

        npcs = create("Citizens", "net.citizensnpcs.api.CitizensAPI", CitizensHook::new);

        DecentHologramsHook holograms = create("DecentHolograms", "eu.decentsoftware.holograms.api.DHAPI",
                () -> new DecentHologramsHook(plugin, plugin::crops, plugin.items()));
        if (holograms != null) {
            listen(holograms);
            closing.add(holograms);
        }

        // The item provider API is EconomyShopGUI Premium's; the free edition has none.
        EconomyShopGuiHook economyShop = create("EconomyShopGUI Premium",
                "me.gypopo.economyshopgui.api.events.ItemProviderPreLoadEvent",
                () -> new EconomyShopGuiHook(plugin, plugin.items(), plugin.itemFactory(), logger));
        if (economyShop != null) {
            listen(economyShop);
        }

        // ExecutableBlocks itself, not just SCore: the placed-blocks manager is its class.
        ExecutableBlocksHook executableBlocks = create("ExecutableBlocks",
                "com.ssomar.executableblocks.executableblocks.placedblocks.ExecutableBlocksPlacedManager", () -> {
                    try {
                        return new ExecutableBlocksHook(this::ours);
                    } catch (ReflectiveOperationException exception) {
                        throw new IllegalStateException(exception);
                    }
                });
        if (executableBlocks != null) {
            listen(executableBlocks);
            foreignBlocks = block -> {
                try {
                    return executableBlocks.claims(block);
                } catch (RuntimeException | LinkageError error) {
                    return false;
                }
            };
        }

        // No API to hook into, and none needed: DailyShop sells the stacks it is given, whole.
        for (String name : List.of("DailyShop", "EconomyShopGUI")) {
            if (plugin.getServer().getPluginManager().getPlugin(name) != null) {
                natively.add(name);
                logger.info(name + " found: it sells custom items as the stacks it is given - add them from"
                        + " your hand in its editor; no hook is needed.");
            }
        }
    }

    private void skill(String name, String probeClass, Supplier<SkillXpHook> factory) {
        SkillXpHook hook = create(name, probeClass, factory);
        if (hook != null) {
            skillHooks.put(name, hook);
        }
    }

    /** Whether this plugin has content standing at {@code block}: a custom block, furniture or a crop. */
    private boolean ours(Block block) {
        return plugin.placed().at(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()) != null
                || (plugin.crops() != null && plugin.crops().claims(block));
    }

    /**
     * Pays every skills plugin for a custom block broken or a ripe crop harvested. Main thread. A
     * hook that throws is logged and dropped; the others are still paid.
     */
    public void skillXp(Player player, SkillXpHook.Source source, String contentId, double xp) {
        if (xp <= 0 || skillHooks.isEmpty()) {
            return;
        }
        for (Map.Entry<String, SkillXpHook> entry : List.copyOf(skillHooks.entrySet())) {
            try {
                entry.getValue().reward(player, source, contentId, xp);
            } catch (RuntimeException | LinkageError error) {
                skillHooks.remove(entry.getKey());
                logger.log(Level.WARNING, entry.getKey() + " failed to give " + player.getName() + " experience for "
                        + contentId + "; no more experience is given through it until a restart.", error);
            }
        }
    }

    /** The economy for {@code /arkcontent shop}, or null without Vault. */
    public @Nullable Shop shop() {
        return shop;
    }

    /** Citizens NPCs, or null without Citizens. */
    public @Nullable NpcBridge npcs() {
        return npcs;
    }

    /** Blocks another plugin has placed and keeps records of, which are never this plugin's custom blocks. */
    public Predicate<Block> foreignBlocks() {
        return foreignBlocks;
    }

    /** Plugins that work with the items as they are, with no hook. */
    public List<String> natively() {
        return List.copyOf(natively);
    }

    /** On disable: hooks that leave things in the world - temporary holograms - clear them. */
    public void disable() {
        for (AutoCloseable hook : closing) {
            try {
                hook.close();
            } catch (Exception | LinkageError error) {
                logger.log(Level.WARNING, "A plugin hook failed to shut down cleanly", error);
            }
        }
    }

    /** The ModelEngine bridge, or null without ModelEngine. */
    public @Nullable ModelEngineBridge modelEngine() {
        return modelEngine;
    }

    /** Main thread, after every rebuild. */
    public void contentReloaded() {
        for (ContentHook hook : contentHooks) {
            try {
                hook.contentReloaded();
            } catch (RuntimeException | LinkageError error) {
                logger.log(Level.WARNING, "A plugin hook failed after the rebuild", error);
            }
        }
    }

    /** Names of the hooks that are running. */
    public List<String> active() {
        return List.copyOf(active);
    }

    private <T> T create(String name, String probeClass, Supplier<T> factory) {
        try {
            Class.forName(probeClass, false, getClass().getClassLoader());
        } catch (ClassNotFoundException | LinkageError absent) {
            return null;
        }
        try {
            T hook = factory.get();
            active.add(name);
            logger.info("Hooked into " + name + ".");
            return hook;
        } catch (RuntimeException | LinkageError error) {
            logger.log(Level.WARNING, name + " is installed but its hook could not start - carrying on without it."
                    + " A newer or older " + name + " than this plugin was built against may have changed its API.", error);
            return null;
        }
    }

    private void listen(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }
}
