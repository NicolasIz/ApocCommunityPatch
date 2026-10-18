package com.arkcronist.content.bukkit.hooks;

import com.arkcronist.content.bukkit.ArkContentPlugin;
import com.arkcronist.content.bukkit.hooks.auraskills.AuraSkillsHook;
import com.arkcronist.content.bukkit.hooks.authme.AuthMeHook;
import com.arkcronist.content.bukkit.hooks.betonquest.BetonQuestHook;
import com.arkcronist.content.bukkit.hooks.citizens.CitizensHook;
import com.arkcronist.content.bukkit.hooks.decentholograms.DecentHologramsHook;
import com.arkcronist.content.bukkit.hooks.deluxemenus.DeluxeMenusHook;
import com.arkcronist.content.bukkit.hooks.eco.EcoHook;
import com.arkcronist.content.bukkit.hooks.economyshopgui.EconomyShopGuiHook;
import com.arkcronist.content.bukkit.hooks.executableblocks.ExecutableBlocksHook;
import com.arkcronist.content.bukkit.hooks.geyser.GeyserHook;
import com.arkcronist.content.bukkit.hooks.griefprevention.GriefPreventionProtection;
import com.arkcronist.content.bukkit.hooks.hmccosmetics.HmcCosmeticsHook;
import com.arkcronist.content.bukkit.hooks.iris.IrisHook;
import com.arkcronist.content.bukkit.hooks.itembridge.ItemBridgeHook;
import com.arkcronist.content.bukkit.hooks.jobs.ExcellentJobsHook;
import com.arkcronist.content.bukkit.hooks.jobs.JobsRebornHook;
import com.arkcronist.content.bukkit.hooks.lands.LandsProtection;
import com.arkcronist.content.bukkit.hooks.luckperms.LuckPermsHook;
import com.arkcronist.content.bukkit.hooks.mcmmo.McMMOHook;
import com.arkcronist.content.bukkit.hooks.mimic.MimicHook;
import com.arkcronist.content.bukkit.hooks.mmoitems.MMOItemsHook;
import com.arkcronist.content.bukkit.hooks.modelengine.ModelEngineHook;
import com.arkcronist.content.bukkit.hooks.mythicarmor.MythicArmorHook;
import com.arkcronist.content.bukkit.hooks.mythicmobs.MythicMobsHook;
import com.arkcronist.content.bukkit.hooks.nightcore.NightcoreItemsHook;
import com.arkcronist.content.bukkit.hooks.packetevents.PacketCosmetics;
import com.arkcronist.content.bukkit.hooks.placeholderapi.PlaceholderApiHook;
import com.arkcronist.content.bukkit.hooks.shopgui.ShopGuiPlusHook;
import com.arkcronist.content.bukkit.hooks.skillapi.FabledHook;
import com.arkcronist.content.bukkit.hooks.skillapi.SkillAPIHook;
import com.arkcronist.content.bukkit.hooks.towny.TownyProtection;
import com.arkcronist.content.bukkit.hooks.vault.VaultShop;
import com.arkcronist.content.bukkit.hooks.worldedit.WorldEditHook;
import com.arkcronist.content.bukkit.hooks.worldguard.WorldGuardFlags;
import com.arkcronist.content.bukkit.hooks.worldguard.WorldGuardProtection;
import com.arkcronist.content.bukkit.hooks.zauctionhouse.ZAuctionHouseHook;
import com.arkcronist.content.bukkit.menu.Shop;
import com.arkcronist.content.core.definition.JobReward;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
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
 * <p>For that to hold, this class never names a hook class that extends or implements another
 * plugin's type - not even as what a lambda returns, which the JVM resolves before running it:
 * such a class cannot be loaded without its parent. Each hook here is either a plain class of this
 * plugin's, or a static method on one; {@code HookIsolationTest} checks it.</p>
 *
 * <p>Presence is decided by the classes rather than by the plugin being enabled: MythicArmor is
 * made to load after this plugin, so that it can listen to it from the start, and is not enabled
 * yet when this runs.</p>
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
    /** Jobs plugins, by hook name. */
    private final Map<String, JobsHook> jobHooks = new HashMap<>();
    /** Plugins that know players' skill and job levels, by hook name: the quest boards' requirements. */
    private final Map<String, LevelSource> levelSources = new java.util.LinkedHashMap<>();
    /** AuraSkills' mana, when it is installed. */
    private volatile java.util.function.Function<Player, double[]> mana = player -> null;
    private final java.util.concurrent.atomic.AtomicBoolean manaFailed = new java.util.concurrent.atomic.AtomicBoolean();
    private final List<AutoCloseable> closing = new ArrayList<>();
    private ModelEngineBridge modelEngine;
    private @org.jetbrains.annotations.Nullable com.arkcronist.content.bukkit.cosmetic.CosmeticRenderer packetCosmetics;
    private @org.jetbrains.annotations.Nullable GeyserHook geyser;
    private Shop shop;
    private NpcBridge npcs;
    private Predicate<Block> foreignBlocks = block -> false;
    private Predicate<Entity> mythicMobs = entity -> false;
    private java.util.function.Function<Entity, String> mythicTypes = entity -> null;
    /** Answered by parts of the plugin made after the hooks; read by the expansion on any thread. */
    private final List<PlaceholderSource> placeholders = new CopyOnWriteArrayList<>();
    /** Fills in other plugins' placeholders; leaves the text as it is without PlaceholderAPI. */
    private volatile BiFunction<OfflinePlayer, String, String> placeholderText = (player, text) -> text;

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
        // WorldGuard takes new flags only until it enables.
        create("WorldGuard flags", "com.sk89q.worldguard.protection.flags.registry.FlagRegistry",
                () -> WorldGuardFlags.register(logger));
    }

    /** Detects and starts every other hook. Main thread, from onEnable. */
    public void enable() {
        MythicMobsHook mythicMobs = create("MythicMobs", "io.lumine.mythic.bukkit.events.MythicDropLoadEvent",
                () -> new MythicMobsHook(plugin.items(), plugin.itemFactory(), logger));
        if (mythicMobs != null) {
            listen(mythicMobs);
            contentHooks.add(mythicMobs);
            mythicMobs.reloadIfReadEarly(plugin);
            this.mythicMobs = entity -> {
                try {
                    return mythicMobs.isMythicMob(entity);
                } catch (RuntimeException | LinkageError error) {
                    return false;
                }
            };
            this.mythicTypes = entity -> {
                try {
                    return mythicMobs.mythicType(entity);
                } catch (RuntimeException | LinkageError error) {
                    return null;
                }
            };
        }

        modelEngine = create("ModelEngine", "com.ticxo.modelengine.api.ModelEngineAPI",
                () -> new ModelEngineHook(logger));

        MythicArmorHook mythicArmor = create("MythicArmor", "net.mythic.mythicArmor.api.MythicArmorGeneratePackEvent",
                () -> new MythicArmorHook(plugin, plugin.pipeline()));
        if (mythicArmor != null) {
            listen(mythicArmor);
            // The first tick comes once every plugin has enabled: MythicArmor has generated its pack by
            // then, or kept the one it had.
            plugin.getServer().getScheduler().runTask(plugin, mythicArmor::mergeCurrent);
        }

        // 3.0.5: BetterHud's pack, merged into ours each time BetterHud builds it.
        Listener betterHud = create("BetterHud", "kr.toxicity.hud.api.bukkit.event.PluginReloadedEvent",
                () -> new com.arkcronist.content.bukkit.hooks.betterhud.BetterHudHook(plugin, plugin.pipeline()));
        if (betterHud != null) {
            listen(betterHud);
        }

        // Loads before this plugin; registered at once, so TAB and DeluxeMenus find it from the start.
        PlaceholderApiHook placeholderApi = create("PlaceholderAPI", "me.clip.placeholderapi.expansion.PlaceholderExpansion",
                () -> PlaceholderApiHook.register(plugin.getPluginMeta().getVersion(), plugin.emojis(), plugin.items(),
                        plugin.itemFactory(), placeholders));
        if (placeholderApi != null) {
            this.placeholderText = placeholderApi::resolve;
            if (plugin.settings().fontImages().placeholders()
                    && plugin.getServer().getPluginManager().getPlugin("ItemsAdder") == null) {
                try {
                    if (placeholderApi.registerImages(plugin.getPluginMeta().getVersion(), plugin.fontImages()::current)) {
                        logger.info("PlaceholderAPI: %img_<name>% and %img_offset_<n>% answered for menus written for"
                                + " ItemsAdder.");
                    }
                } catch (RuntimeException | LinkageError error) {
                    logger.log(Level.WARNING, "Could not register the %img_% placeholders", error);
                }
            }
        }

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
        // 3.0: Lands and Towny, asked the same way - and, for guns, whether the target may be hurt.
        create("Lands", "me.angeschossen.lands.api.LandsIntegration", () -> {
            LandsProtection lands = new LandsProtection(plugin);
            plugin.protection().add(lands);
            return lands;
        });
        create("Towny", "com.palmergames.bukkit.towny.TownyAPI", () -> {
            TownyProtection towny = new TownyProtection();
            plugin.protection().add(towny);
            return towny;
        });
        // 3.0: worn cosmetics sent as packets from their own thread, when PacketEvents is there. A
        // lambda, not PacketCosmetics::new: a constructor reference links the class where it stands,
        // outside create's guard, and that fails without PacketEvents (seen live).
        packetCosmetics = create("PacketEvents", "com.github.retrooper.packetevents.PacketEvents",
                () -> new PacketCosmetics());
        // 3.0: LuckPerms prefixes, suffixes and meta with emojis, for TAB, chat and menus.
        LuckPermsHook luckPerms = create("LuckPerms", "net.luckperms.api.LuckPermsProvider",
                () -> new LuckPermsHook(plugin.emojis()));
        if (luckPerms != null) {
            placeholders.add(luckPerms);
        }
        // 3.0: Bedrock players, through Geyser - items, blocks, furniture and the pack, made for them
        // by the pipeline and handed over as Geyser starts. Only with Geyser on this server: on a
        // proxy it takes them from the extension (3.3), and its API classes here - a plugin may ship
        // them - lead nowhere.
        if (plugin.pipeline().geyserHere()) {
            geyser = create("Geyser", "org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomBlocksEvent",
                    () -> new GeyserHook(plugin, plugin.pipeline().bedrockReady()));
        }

        // Skills plugins: experience for custom blocks and crops. AuraSkills also knows the items,
        // for its menus - registered now, before it loads them on the first tick.
        AuraSkillsHook auraSkills = create("AuraSkills", "dev.aurelium.auraskills.api.AuraSkillsBukkit",
                () -> new AuraSkillsHook(plugin.items(), plugin.itemFactory(), logger));
        if (auraSkills != null) {
            contentHooks.add(auraSkills);
            skillHooks.put("AuraSkills", auraSkills);
            levelSources.put("AuraSkills", auraSkills);
            mana = auraSkills::mana;
        }
        skill("mcMMO", "com.gmail.nossr50.api.ExperienceAPI", () -> new McMMOHook());
        // SkillAPI and its fork ProSkillAPI share one API; Fabled is the renamed successor.
        skill("SkillAPI", "com.sucy.skill.api.enums.ExpSource", () -> new SkillAPIHook());
        skill("Fabled", "studio.magemonkey.fabled.api.enums.ExpSource", () -> new FabledHook());

        // Economy: /arkcontent shop sells the items that have a price.
        shop = create("Vault", "net.milkbowl.vault.economy.Economy", () -> new VaultShop(plugin.getServer()));

        // HMCCosmetics, through its library: cosmetics made from this plugin's items.
        // The factory returns a Boolean, never the hook: naming a class that extends HibiscusCommons'
        // Hook - even as a lambda's return type - would fail this class without HibiscusCommons.
        create("HMCCosmetics (HibiscusCommons)", "me.lojosho.hibiscuscommons.hooks.Hook",
                () -> HmcCosmeticsHook.register(plugin.items(), plugin.itemFactory()));

        // Auctions: listed and bought items keep their look.
        ZAuctionHouseHook auctions = create("zAuctionHouse", "fr.maxlego08.zauctionhouse.api.AuctionPlugin",
                () -> new ZAuctionHouseHook(plugin.itemFactory()));
        if (auctions != null) {
            listen(auctions);
        }

        // Jobs plugins: money and job experience for custom blocks and crops.
        job("Jobs Reborn", "com.gamingmesh.jobs.Jobs", () -> new JobsRebornHook(logger));
        job("ExcellentJobs", "su.nightexpress.excellentjobs.JobsAPIProvider",
                () -> new ExcellentJobsHook(plugin.getServer(), (player, amount) -> shop != null
                        && shop.deposit(player, amount), logger));

        npcs = create("Citizens", "net.citizensnpcs.api.CitizensAPI", () -> new CitizensHook());

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

        // 1.6: the registries other plugins share items and blocks through. Each answers from the
        // same place, so an id means the same thing in all of them.
        ContentAccess content = new ContentAccess(plugin.items(), plugin.itemFactory(), plugin.blocks(),
                plugin::customBlocks);
        // 2.0: a hook that changes blocks (WorldEdit) tells the integrity check, made after the hooks.
        content.onSupportTouched((world, x, z) -> {
            var sanity = plugin.sanity();
            if (sanity != null) {
                sanity.touched(world, x, z);
            }
        });
        // eco: EcoItems, EcoArmor, EcoEnchants, EcoMobs, Reforges, StatTrackers, Talismans and the rest.
        create("eco", "com.willfp.eco.core.items.provider.ItemProvider", () -> EcoHook.register(content));
        // nightcore: ExcellentCrates, ExcellentShop, ExcellentEnchants and the rest.
        create("nightcore (ExcellentCrates, ExcellentShop...)",
                "su.nightexpress.nightcore.integration.item.adapter.IdentifiableItemAdapter",
                () -> NightcoreItemsHook.register(content));
        create("Mimic", "ru.endlesscode.mimic.items.BukkitItemsRegistry", () -> MimicHook.register(content, plugin));
        create("ItemBridge", "com.jojodmo.itembridge.ItemBridgeListener",
                () -> ItemBridgeHook.register(content, plugin));
        create("WorldEdit", "com.sk89q.worldedit.internal.registry.InputParser",
                () -> WorldEditHook.register(content, plugin));
        create("DeluxeMenus", "com.extendedclip.deluxemenus.hooks.ItemHook",
                () -> DeluxeMenusHook.register(content, plugin));
        create("BetonQuest", "org.betonquest.betonquest.api.integration.IntegrationService",
                () -> BetonQuestHook.register(content, plugin, plugin.getServer().getServicesManager()));
        if (plugin.settings().delivery().afterLogin()) {
            AuthMeHook authMe = create("AuthMe", "fr.xephi.authme.api.v3.AuthMeApi",
                    () -> new AuthMeHook(plugin.delivery()));
            if (authMe != null) {
                listen(authMe);
            }
        }
    }

    private void skill(String name, String probeClass, Supplier<SkillXpHook> factory) {
        SkillXpHook hook = create(name, probeClass, factory);
        if (hook != null) {
            skillHooks.put(name, hook);
        }
    }

    private void job(String name, String probeClass, Supplier<JobsHook> factory) {
        JobsHook hook = create(name, probeClass, factory);
        if (hook != null) {
            jobHooks.put(name, hook);
            if (hook instanceof LevelSource source) {
                levelSources.put(name, source);
            }
        }
    }

    /**
     * 2.0: a player's AuraSkills mana and their most, for HUD bars; null without AuraSkills, or
     * before it has loaded them. A broken call is told once and answered with null. Main thread.
     */
    public @Nullable double[] auraSkillsMana(Player player) {
        try {
            return mana.apply(player);
        } catch (RuntimeException | LinkageError error) {
            if (manaFailed.compareAndSet(false, true)) {
                logger.warning("AuraSkills could not say a player's mana (" + error + "); HUD bars of it show 0.");
            }
            return null;
        }
    }

    /**
     * A player's level in a skill, as the skills plugins installed say - the highest, should two
     * know it; empty when none of them has that skill. Main thread.
     */
    public OptionalInt skillLevel(Player player, String skill) {
        return level(player, skill, LevelSource::skillLevel);
    }

    /** A player's level in a job, as the jobs plugins installed say; empty when none has it. Main thread. */
    public OptionalInt jobLevel(Player player, String job) {
        return level(player, job, LevelSource::jobLevel);
    }

    private interface LevelQuery {
        OptionalInt ask(LevelSource source, Player player, String name);
    }

    private OptionalInt level(Player player, String name, LevelQuery query) {
        OptionalInt best = OptionalInt.empty();
        for (Map.Entry<String, LevelSource> entry : List.copyOf(levelSources.entrySet())) {
            try {
                OptionalInt level = query.ask(entry.getValue(), player, name);
                if (level.isPresent() && (best.isEmpty() || level.getAsInt() > best.getAsInt())) {
                    best = level;
                }
            } catch (RuntimeException | LinkageError error) {
                levelSources.remove(entry.getKey());
                logger.log(Level.WARNING, entry.getKey() + " failed to say a player's level; quest board requirements"
                        + " do not ask it again until a restart.", error);
            }
        }
        return best;
    }

    /** The plugins answering skill and job levels, for messages. */
    public List<String> levelSources() {
        return List.copyOf(levelSources.keySet());
    }

    /**
     * Pays every jobs plugin for a custom block broken or a ripe crop harvested. Main thread. A hook
     * that throws is logged and dropped; the others still pay.
     */
    public void jobRewards(Player player, Block block, String contentId, Map<String, JobReward> rewards) {
        if (rewards.isEmpty() || jobHooks.isEmpty()) {
            return;
        }
        for (Map.Entry<String, JobsHook> entry : List.copyOf(jobHooks.entrySet())) {
            try {
                entry.getValue().reward(player, block, contentId, rewards);
            } catch (RuntimeException | LinkageError error) {
                jobHooks.remove(entry.getKey());
                logger.log(Level.WARNING, entry.getKey() + " failed to pay " + player.getName() + " for " + contentId
                        + "; nothing more is paid through it until a restart.", error);
            }
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

    /**
     * Fills in other plugins' placeholders in {@code text}, through PlaceholderAPI when it is there.
     * Any thread, as PlaceholderAPI itself.
     */
    public String placeholders(OfflinePlayer player, String text) {
        return placeholderText.apply(player, text);
    }

    /** Adds placeholders to {@code %arkcontent_...%}; they answer whether or not PlaceholderAPI is there. */
    public void addPlaceholders(PlaceholderSource source) {
        placeholders.add(source);
    }

    /** Whether an entity is one of MythicMobs' mobs; always false without MythicMobs. Main thread. */
    public boolean isMythicMob(Entity entity) {
        return mythicMobs.test(entity);
    }

    /** The MythicMobs type of an entity - {@code DE_Umbraxis} - or null for any other. Main thread. */
    public @Nullable String mythicType(Entity entity) {
        return mythicTypes.apply(entity);
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

    /** Cosmetics drawn by packets, when PacketEvents is installed; null otherwise. */
    public @org.jetbrains.annotations.Nullable com.arkcronist.content.bukkit.cosmetic.CosmeticRenderer packetCosmetics() {
        return packetCosmetics;
    }

    /** Geyser, when it is installed and Bedrock content is on; null otherwise. */
    public @org.jetbrains.annotations.Nullable GeyserHook geyser() {
        return geyser;
    }

    /**
     * 3.3.3: with PacketEvents, what Bedrock players are sent has its too-wide glyphs written as the
     * pieces the .mcpack draws them in. Without it, those glyphs stay one small glyph each.
     */
    public void bedrockGlyphText(java.util.function.Predicate<java.util.UUID> bedrock,
                                 Supplier<java.util.Map<Integer, String>> pieces) {
        try {
            Class.forName("com.github.retrooper.packetevents.PacketEvents", false, getClass().getClassLoader());
        } catch (ClassNotFoundException | LinkageError absent) {
            logger.info("[bedrock] PacketEvents is not installed: ranks and other glyphs wider than a letter are"
                    + " drawn small for Bedrock players. With PacketEvents they are drawn full size.");
            return;
        }
        try {
            new com.arkcronist.content.bukkit.hooks.packetevents.BedrockGlyphText(bedrock, pieces);
            logger.info("[bedrock] Ranks and other glyphs wider than a letter are written as pieces for Bedrock"
                    + " players, through PacketEvents.");
        } catch (RuntimeException | LinkageError error) {
            logger.log(Level.WARNING, "[bedrock] Could not watch what Bedrock players are sent for wide glyphs", error);
        }
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
