package com.arkcronist.content.bukkit.hooks.mythicarmor;

import com.arkcronist.content.bukkit.ContentPipeline;
import com.arkcronist.content.core.pack.ExternalPack;
import net.mythic.mythicArmor.api.MythicArmorAPI;
import net.mythic.mythicArmor.api.MythicArmorGeneratePackEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.io.File;

/**
 * MythicArmor's armor, in the pack this plugin sends.
 *
 * <p>MythicArmor's API is read-only: it tells other plugins where it generated its resource pack and
 * which armor pieces it holds, and has no way to register armor from outside. What it does allow is
 * the part that makes its armor render: its pack's assets reach players. Each time MythicArmor
 * generates its pack, that folder - its {@code assets/} and the overlays with the shaders its 3D
 * chestplates, leggings and boots are drawn by - is merged into ours and ours is rebuilt, so
 * players get a single pack with both. This plugin's own files win any path both supply; MythicArmor's
 * win over the copies of them in other merged packs, such as an ItemsAdder export.</p>
 *
 * <p>A start on which MythicArmor does not generate its pack again fires no event: once the server
 * has started, the pack it generated last is taken from its API instead.</p>
 */
public final class MythicArmorHook implements Listener {

    private final Plugin plugin;
    private final ContentPipeline pipeline;
    private volatile boolean received;

    public MythicArmorHook(Plugin plugin, ContentPipeline pipeline) {
        this.plugin = plugin;
        this.pipeline = pipeline;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGeneratePack(MythicArmorGeneratePackEvent event) {
        received = true;
        merge(event.getPackRoot());
    }

    /**
     * The pack MythicArmor generated last, when it has not generated one since this plugin enabled.
     * Main thread, once the server has started - after MythicArmor enabled.
     */
    public void mergeCurrent() {
        if (received) {
            return;
        }
        MythicArmorAPI api = Bukkit.getServicesManager().load(MythicArmorAPI.class);
        if (api == null && Bukkit.getPluginManager().getPlugin("MythicArmor") instanceof MythicArmorAPI direct) {
            api = direct;
        }
        File root = api == null ? null : api.getPackRoot();
        if (root == null || !root.exists()) {
            plugin.getLogger().info("MythicArmor has not generated its pack since the server started, and its API does not"
                    + " say where the last one is: its armor joins this plugin's pack when it next generates it.");
            return;
        }
        merge(root);
    }

    private void merge(File root) {
        ExternalPack pack = new ExternalPack("MythicArmor", root.toPath(), true);
        if (Bukkit.isPrimaryThread()) {
            pipeline.mergeExternalPack(pack);
        } else {
            plugin.getServer().getScheduler().runTask(plugin, () -> pipeline.mergeExternalPack(pack));
        }
    }
}
