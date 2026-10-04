package com.arkcronist.content.bukkit.hooks;

import com.arkcronist.content.bukkit.ArkContentPlugin;
import com.arkcronist.content.bukkit.command.ContentAdminCommand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
                "com.sk89q.worldedit.internal.registry.InputParser")) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(foreign), foreign);
        }
    }

    @Test
    void whatStartsTheHooksLoadsWithoutThem() {
        for (Class<?> type : List.of(HookManager.class, ArkContentPlugin.class, ContentAdminCommand.class,
                com.arkcronist.content.bukkit.advancement.AdvancementService.class, ContentAccess.class)) {
            assertDoesNotThrow(type::getDeclaredMethods, type.getName());
            assertDoesNotThrow(type::getDeclaredFields, type.getName());
        }
    }
}
