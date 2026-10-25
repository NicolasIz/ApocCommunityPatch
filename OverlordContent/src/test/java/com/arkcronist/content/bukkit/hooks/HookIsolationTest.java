package com.arkcronist.content.bukkit.hooks;

import com.arkcronist.content.bukkit.ArkContentPlugin;
import com.arkcronist.content.bukkit.command.ContentAdminCommand;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plugin enables with none of the plugins it hooks into installed.
 *
 * <p>The tests run on a classpath with Paper and nothing else, like a server with no other plugins.
 * Looking up a class's methods resolves every type their signatures name - lambdas included, which
 * the JVM also resolves when it first runs one - so a hook class that extends another plugin's type
 * and is named in one of those signatures fails here as it would fail on that server.</p>
 */
class HookIsolationTest {

    @Test
    void noneOfTheHookedPluginsIsOnThisClasspath() {
        for (String foreign : List.of("me.clip.placeholderapi.expansion.PlaceholderExpansion",
                "dev.aurelium.auraskills.api.AuraSkillsBukkit", "com.gmail.nossr50.api.ExperienceAPI",
                "net.milkbowl.vault.economy.Economy", "net.citizensnpcs.api.CitizensAPI",
                "eu.decentsoftware.holograms.api.DHAPI", "io.lumine.mythic.bukkit.MythicBukkit",
                "com.gamingmesh.jobs.Jobs", "su.nightexpress.excellentjobs.JobsAPIProvider",
                "me.lojosho.hibiscuscommons.hooks.Hook", "fr.maxlego08.zauctionhouse.api.AuctionPlugin",
                "com.willfp.eco.core.items.provider.ItemProvider",
                "su.nightexpress.nightcore.integration.item.adapter.IdentifiableItemAdapter",
                "ru.endlesscode.mimic.items.BukkitItemsRegistry", "com.jojodmo.itembridge.ItemBridgeListener",
                "com.extendedclip.deluxemenus.hooks.ItemHook",
                "org.betonquest.betonquest.api.integration.IntegrationService", "fr.xephi.authme.api.v3.AuthMeApi",
                "com.sk89q.worldedit.internal.registry.InputParser", "com.sk89q.worldguard.WorldGuard",
                "com.github.retrooper.packetevents.PacketEvents", "org.geysermc.geyser.api.GeyserApi",
                "net.luckperms.api.LuckPermsProvider", "me.angeschossen.lands.api.LandsIntegration",
                "com.palmergames.bukkit.towny.TownyAPI", "kr.toxicity.hud.api.bukkit.event.PluginReloadedEvent")) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(foreign), foreign);
        }
    }

    @Test
    void whatStartsTheHooksLoadsWithoutThem() {
        for (Class<?> type : List.of(HookManager.class, ArkContentPlugin.class, ContentAdminCommand.class,
                com.arkcronist.content.bukkit.advancement.AdvancementService.class, ContentAccess.class,
                com.arkcronist.content.bukkit.gun.GunService.class, com.arkcronist.content.bukkit.gun.GunListener.class,
                com.arkcronist.content.bukkit.liquid.LiquidService.class,
                com.arkcronist.content.bukkit.liquid.LiquidListener.class,
                com.arkcronist.content.bukkit.hud.HudService.class, com.arkcronist.content.bukkit.hud.ActionBars.class,
                com.arkcronist.content.bukkit.ContentPipeline.class,
                com.arkcronist.content.bukkit.bedrock.BedrockService.class,
                com.arkcronist.content.bukkit.cosmetic.CosmeticService.class,
                com.arkcronist.content.bukkit.cosmetic.CosmeticListener.class,
                com.arkcronist.content.bukkit.cosmetic.EntityCosmetics.class,
                com.arkcronist.content.bukkit.minigame.MinigameService.class,
                com.arkcronist.content.bukkit.minigame.MinigameListener.class,
                com.arkcronist.content.bukkit.pack.PackDelivery.class)) {
            assertDoesNotThrow(type::getDeclaredMethods, type.getName());
            assertDoesNotThrow(type::getDeclaredFields, type.getName());
        }
    }

    /**
     * {@code PacketCosmetics::new} kept 3.0 from enabling on a server without PacketEvents: a
     * constructor reference links its class where it stands - before {@code create} could catch
     * anything - and linking a class that hands PacketEvents' wrappers around needs PacketEvents.
     * A lambda links it only when it runs, inside the guard. Read from HookManager's constant pool.
     */
    @Test
    void hookManagerReferencesNoHookConstructorDirectly() throws IOException {
        byte[] bytes;
        try (InputStream in = HookManager.class.getResourceAsStream("HookManager.class")) {
            bytes = java.util.Objects.requireNonNull(in).readAllBytes();
        }
        DataInputStream data = new DataInputStream(new ByteArrayInputStream(bytes));
        data.skipBytes(8);
        int count = data.readUnsignedShort();
        String[] utf8 = new String[count];
        int[] className = new int[count];
        int[] memberClass = new int[count];
        List<int[]> handles = new ArrayList<>();
        for (int i = 1; i < count; i++) {
            int tag = data.readUnsignedByte();
            switch (tag) {
                case 1 -> utf8[i] = data.readUTF();
                case 3, 4 -> data.skipBytes(4);
                case 5, 6 -> {
                    data.skipBytes(8);
                    i++;
                }
                case 7 -> className[i] = data.readUnsignedShort();
                case 8, 16, 19, 20 -> data.skipBytes(2);
                case 9, 10, 11 -> {
                    memberClass[i] = data.readUnsignedShort();
                    data.skipBytes(2);
                }
                case 12, 17, 18 -> data.skipBytes(4);
                case 15 -> handles.add(new int[]{data.readUnsignedByte(), data.readUnsignedShort()});
                default -> throw new IllegalStateException("constant pool tag " + tag + " at " + i);
            }
        }
        List<String> constructors = new ArrayList<>();
        for (int[] handle : handles) {
            if (handle[0] == 8) { // REF_newInvokeSpecial: Type::new
                constructors.add(utf8[className[memberClass[handle[1]]]]);
            }
        }
        assertTrue(constructors.stream().noneMatch(owner -> owner.startsWith("com/arkcronist/content/bukkit/hooks/")),
                "hook classes built by constructor reference: " + constructors);
    }
}
