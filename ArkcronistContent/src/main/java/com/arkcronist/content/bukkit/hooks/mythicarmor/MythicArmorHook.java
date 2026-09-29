package com.arkcronist.content.bukkit.hooks.mythicarmor;

import com.arkcronist.content.bukkit.ContentPipeline;
import com.arkcronist.content.core.pack.ExternalPack;
import net.mythic.mythicArmor.api.MythicArmorGeneratePackEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * MythicArmor's armor, in the pack this plugin sends.
 *
 * <p>MythicArmor's API is read-only: it tells other plugins where it generated its resource pack and
 * which armor pieces it holds, and has no way to register armor from outside. What it does allow is
 * the part that makes its armor render: its pack's assets reach players. Each time MythicArmor
 * generates its pack, that folder's {@code assets/} are merged into ours and ours is rebuilt, so
 * players get a single pack with both. This plugin's own files win any path both supply.</p>
 */
public final class MythicArmorHook implements Listener {

    private final Plugin plugin;
    private final ContentPipeline pipeline;

    public MythicArmorHook(Plugin plugin, ContentPipeline pipeline) {
        this.plugin = plugin;
        this.pipeline = pipeline;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGeneratePack(MythicArmorGeneratePackEvent event) {
        ExternalPack pack = new ExternalPack("MythicArmor", event.getPackRoot().toPath());
        if (Bukkit.isPrimaryThread()) {
            pipeline.mergeExternalPack(pack);
        } else {
            plugin.getServer().getScheduler().runTask(plugin, () -> pipeline.mergeExternalPack(pack));
        }
    }
}
